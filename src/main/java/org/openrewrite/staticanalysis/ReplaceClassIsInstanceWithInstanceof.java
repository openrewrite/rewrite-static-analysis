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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.FieldAccess;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceClassIsInstanceWithInstanceof extends Recipe {

    private static final MethodMatcher ISINSTANCE_MATCHER = new MethodMatcher("java.lang.Class isInstance(..)");

    String displayName = "Replace `A.class.isInstance(a)` with `a instanceof A`";

    String description = "There should be no `A.class.isInstance(a)`, it should be replaced by " +
            "`a instanceof A`. Using `instanceof` enables the compiler to catch type " +
            "incompatibilities at compile time rather than silently passing at runtime, " +
            "which helps detect dead code early.";

    Set<String> tags = singleton("RSPEC-S6202");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ISINSTANCE_MATCHER), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(MethodInvocation method, ExecutionContext ctx) {
                if (ISINSTANCE_MATCHER.matches(method) && isObjectClass(method.getSelect()) && !StatementRemoval.needsStatementExpression(getCursor())) {
                    Expression objectExpression = method.getArguments().get(0);
                    FieldAccess fieldAccessPart = (FieldAccess) method.getSelect();
                    // `instanceof` is a compile error where `isInstance` just returns false
                    if (cannotBeInstanceOf(objectExpression.getType(), (JavaType.FullyQualified) fieldAccessPart.getTarget().getType())) {
                        return super.visitMethodInvocation(method, ctx);
                    }
                    JavaCoordinates coordinates = method.getCoordinates().replace();
                    J updated = JavaTemplate.apply("#{any()} instanceof Object", getCursor(), coordinates, objectExpression);
                    updated = mapInstanceOf(updated,
                            instanceOf -> instanceOf.withClazz(fieldAccessPart.getTarget().withPrefix(instanceOf.getClazz().getPrefix())));
                    return maybeAutoFormat(method, updated, ctx);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            private J mapInstanceOf(J tree, Function<J.InstanceOf, J.InstanceOf> fun) {
                if (tree instanceof J.InstanceOf) {
                    return fun.apply((J.InstanceOf) tree);
                }
                if (tree instanceof J.Parentheses) {
                    J.Parentheses<J> par = (J.Parentheses<J>) tree;
                    J inner = mapInstanceOf(par.getTree(), fun);
                    return par.withTree(inner);
                }
                throw new IllegalArgumentException("Expected J.InstanceOf or J.Parentheses, but got: " + tree.getClass());
            }

            private boolean isObjectClass(@Nullable Expression expression) {
                if (expression instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                    return "class".equals(fieldAccess.getName().getSimpleName()) &&
                            fieldAccess.getTarget().getType() instanceof JavaType.Class;
                }
                return false;
            }
        });
    }

    /**
     * Whether {@code expr instanceof Target} is a compile error, as it is when the operand is a primitive or its
     * type can't be cast to the target (JLS 15.20.2). Unknown types are not treated as incompatible.
     *
     * @see <a href="https://github.com/openrewrite/rewrite-static-analysis/issues/1116">C12 of #1116</a>
     */
    private static boolean cannotBeInstanceOf(@Nullable JavaType type, JavaType.FullyQualified target) {
        if (type instanceof JavaType.Primitive) {
            // String literals and `null` are typed as primitives too, and both are fine as `instanceof` operands
            return type != JavaType.Primitive.String && type != JavaType.Primitive.Null;
        }
        if (type instanceof JavaType.GenericTypeVariable) {
            JavaType.GenericTypeVariable typeVariable = (JavaType.GenericTypeVariable) type;
            if (typeVariable.getVariance() == JavaType.GenericTypeVariable.Variance.CONTRAVARIANT) {
                return false;
            }
            // A cast from a type variable is checked against its bounds
            for (JavaType bound : typeVariable.getBounds()) {
                if (cannotBeInstanceOf(bound, target)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof JavaType.Array) {
            // An array is only an `Object`, `Cloneable` or `Serializable`
            return !TypeUtils.isAssignableTo(target, type);
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null || fq instanceof JavaType.Unknown ||
                TypeUtils.isAssignableTo(target, fq) || TypeUtils.isAssignableTo(fq, target)) {
            return false;
        }
        boolean typeIsInterface = isInterface(fq);
        boolean targetIsInterface = isInterface(target);
        if (typeIsInterface && targetIsInterface) {
            return false;
        }
        if (!typeIsInterface && !targetIsInterface) {
            // Neither class extends the other
            return true;
        }
        // Only a subclass could implement the interface, and a final class has none
        return (typeIsInterface ? target : fq).hasFlags(Flag.Final);
    }

    private static boolean isInterface(JavaType.FullyQualified type) {
        return type.getKind() == JavaType.FullyQualified.Kind.Interface ||
                type.getKind() == JavaType.FullyQualified.Kind.Annotation;
    }
}
