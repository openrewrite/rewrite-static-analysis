/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

import static java.util.Collections.singleton;

public class PrimitiveWrapperClassConstructorToValueOf extends Recipe {

    @Getter
    @Option(displayName = "Only if immediately unboxed",
            description = "Only replace constructors whose result is immediately unboxed in a primitive variable " +
                    "initializer, primitive cast, or primitive value accessor. This preserves fresh wrapper identity " +
                    "when references can escape. Defaults to false for the standalone migration; enabled by CommonStaticAnalysis.",
            required = false)
    @Nullable
    final Boolean onlyIfImmediatelyUnboxed;

    public PrimitiveWrapperClassConstructorToValueOf() {
        this(null);
    }

    @JsonCreator
    public PrimitiveWrapperClassConstructorToValueOf(@JsonProperty("onlyIfImmediatelyUnboxed") @Nullable Boolean onlyIfImmediatelyUnboxed) {
        this.onlyIfImmediatelyUnboxed = onlyIfImmediatelyUnboxed;
    }

    @Getter
    final String displayName = "Use primitive wrapper `valueOf` method";

    @Getter
    final String description = "The constructors of all primitive wrapper types have been deprecated in favor of using the static " +
            "factory method `valueOf` available for each of the primitive type wrappers. " +
            "Using `valueOf` enables object caching for frequently used values, reducing unnecessary heap allocations. " +
            "Note that this changes identity semantics: `valueOf` may return cached instances (such as `Boolean.TRUE` or " +
            "`Integer` values in `[-128, 127]`), so code that compares boxed values with `==`/`!=`, relies on " +
            "`System.identityHashCode`, or synchronizes on the boxed value may behave differently after this change. " +
            "Enable `onlyIfImmediatelyUnboxed` to preserve allocations in contexts where wrapper identity can be observed.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2129");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> condition = Preconditions.or(
                new UsesType<>("java.lang.Boolean", false),
                new UsesType<>("java.lang.Byte", false),
                new UsesType<>("java.lang.Character", false),
                new UsesType<>("java.lang.Double", false),
                new UsesType<>("java.lang.Float", false),
                new UsesType<>("java.lang.Integer", false),
                new UsesType<>("java.lang.Long", false),
                new UsesType<>("java.lang.Short", false)
        );
        return Preconditions.check(condition, new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(nc.getType());
                if (type != null && nc.getArguments().size() == 1 &&
                        (!Boolean.TRUE.equals(onlyIfImmediatelyUnboxed) || isImmediatelyUnboxed())) {
                    Expression arg = nc.getArguments().get(0);
                    JavaTemplate.Builder valueOf;
                    switch (type.getFullyQualifiedName()) {
                        case "java.lang.Boolean":
                            valueOf = JavaTemplate.builder("Boolean.valueOf(#{any(boolean)})");
                            break;
                        case "java.lang.Byte":
                            valueOf = JavaTemplate.builder("Byte.valueOf(#{any(byte)})");
                            break;
                        case "java.lang.Character":
                            valueOf = JavaTemplate.builder("Character.valueOf(#{any(char)})");
                            break;
                        case "java.lang.Double":
                            valueOf = JavaTemplate.builder("Double.valueOf(#{any(double)})");
                            break;
                        case "java.lang.Integer":
                            valueOf = JavaTemplate.builder("Integer.valueOf(#{any(int)})");
                            break;
                        case "java.lang.Long":
                            valueOf = JavaTemplate.builder("Long.valueOf(#{any(long)})");
                            break;
                        case "java.lang.Short":
                            valueOf = JavaTemplate.builder("Short.valueOf(#{any(short)})");
                            break;
                        case "java.lang.Float":
                            JavaType argType = arg.getType();
                            if (TypeUtils.isOfClassType(argType, "java.lang.Double")) {
                                valueOf = JavaTemplate.builder("Float.valueOf(#{any(java.lang.Double)}.floatValue())");
                            } else if (JavaType.Primitive.Double == argType) {
                                // A cast binds tighter than these operators, so unparenthesized it covers only the
                                // first operand, either failing to compile or rounding one step too early
                                valueOf = JavaTemplate.builder(arg instanceof J.Binary || arg instanceof J.Ternary ||
                                        arg instanceof J.Assignment || arg instanceof J.AssignmentOperation ?
                                        "Float.valueOf((float) (#{any(double)}))" :
                                        "Float.valueOf((float) #{any(double)})");
                            } else {
                                valueOf = JavaTemplate.builder("Float.valueOf(#{any(float)})");
                            }
                            break;
                        default:
                            return nc;
                    }
                    return valueOf.build().apply(updateCursor(nc), nc.getCoordinates().replace(), arg);
                }
                return nc;
            }

            private boolean isImmediatelyUnboxed() {
                // Dynamic languages may dispatch on the wrapper rather than unbox as Java does.
                if (getCursor().firstEnclosing(J.CompilationUnit.class) == null) {
                    return false;
                }
                Cursor parent = getCursor().getParentTreeCursor();
                while (parent.getValue() instanceof J.Parentheses) {
                    parent = parent.getParentTreeCursor();
                }
                Object context = parent.getValue();
                if (context instanceof J.VariableDeclarations.NamedVariable) {
                    return isPrimitiveValue(((J.VariableDeclarations.NamedVariable) context).getType());
                }
                if (context instanceof J.TypeCast) {
                    return isPrimitiveValue(((J.TypeCast) context).getType());
                }
                if (context instanceof J.MethodInvocation) {
                    J.MethodInvocation method = (J.MethodInvocation) context;
                    // Only the known wrapper accessors consume no reference and return a primitive value.
                    return method.getSelect() != null &&
                            (method.getArguments().isEmpty() || method.getArguments().get(0) instanceof J.Empty) &&
                            method.getSimpleName().matches("(?:boolean|byte|short|char|int|long|float|double)Value") &&
                            method.getMethodType() != null && isPrimitiveValue(method.getMethodType().getReturnType());
                }
                return false;
            }

            private boolean isPrimitiveValue(@Nullable JavaType type) {
                return type instanceof JavaType.Primitive && type != JavaType.Primitive.String &&
                        type != JavaType.Primitive.Void && type != JavaType.Primitive.Null && type != JavaType.Primitive.None;
            }
        });
    }
}
