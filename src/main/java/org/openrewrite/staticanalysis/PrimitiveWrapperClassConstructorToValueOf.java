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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
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
    final String displayName = "Use primitive wrapper `valueOf` method";

    @Getter
    final String description = "The constructor of all primitive types has been deprecated in favor of using the static factory method `valueOf` available for each of the primitive type wrappers.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2129");
    }

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
                if (type != null && nc.getArguments().size() == 1) {
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
                            if (arg instanceof J.Literal && JavaType.Primitive.Double == ((J.Literal) arg).getType()) {
                                arg = ((J.Literal) arg).withType(JavaType.Primitive.String);
                                arg = ((J.Literal) arg).withValueSource("\"" + ((J.Literal) arg).getValue() + "\"");
                            }

                            JavaType argType = arg.getType();
                            if (TypeUtils.isOfClassType(argType, "java.lang.Double")) {
                                valueOf = JavaTemplate.builder("Float.valueOf(#{any(java.lang.Double)}.floatValue())");
                            } else if (JavaType.Primitive.Double == arg.getType()) {
                                valueOf = JavaTemplate.builder("Float.valueOf((float) #{any(double)})");
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
        });
    }
}
