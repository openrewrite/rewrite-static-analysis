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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.tree.G;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
                    // `instanceof` is a compile error (JLS 15.20.2) where `isInstance` just returns false
                    if (hasRecursiveBounds(objectExpression.getType()) ||
                            hasRecursiveBounds(fieldAccessPart.getTarget().getType()) ||
                            cannotBeInstanceOf(objectExpression.getType(), (JavaType.FullyQualified) fieldAccessPart.getTarget().getType())) {
                        return super.visitMethodInvocation(method, ctx);
                    }
                    JavaCoordinates coordinates = method.getCoordinates().replace();
                    // Only the dummy operand needs erasure; substitution retains the original expression and type.
                    J updated = JavaTemplate.apply("#{any(java.lang.Object)} instanceof Object", getCursor(), coordinates, objectExpression);
                    updated = mapInstanceOf(updated,
                            instanceOf -> instanceOf.withClazz(fieldAccessPart.getTarget().withPrefix(instanceOf.getClazz().getPrefix())));
                    updated = maybeAutoFormat(method, updated, ctx);
                    if (getCursor().firstEnclosing(G.CompilationUnit.class) != null &&
                            Boolean.FALSE.equals(StatementRemoval.isValueUsed(getCursor()))) {
                        return new G.ExpressionStatement(Tree.randomId(), (Expression) updated);
                    }
                    return updated;
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

    // Legacy shared runtimes compare substituted generic bounds structurally without cycle protection.
    // Retain reflection when that graph is recursive: erasure alone cannot prove Java cast compatibility.
    private static boolean hasRecursiveBounds(@Nullable JavaType type) {
        Set<JavaType> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<JavaType> pending = new ArrayList<>();
        if (type != null) {
            pending.add(type);
        }
        for (int i = 0; i < pending.size(); i++) {
            JavaType current = pending.get(i);
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof JavaType.GenericTypeVariable &&
                    boundReachesVariable((JavaType.GenericTypeVariable) current)) {
                return true;
            }
            addTypeReferences(current, pending);
        }
        return false;
    }

    // Require a path from a variable's bounds back to that exact variable. Ordinary class cycles
    // (String implements Comparable<String>) and acyclic wildcard bounds remain eligible.
    private static boolean boundReachesVariable(JavaType.GenericTypeVariable variable) {
        Set<JavaType> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<JavaType> pending = new ArrayList<>(variable.getBounds());
        for (int i = 0; i < pending.size(); i++) {
            JavaType current = pending.get(i);
            if (current == variable) {
                return true;
            }
            if (visited.add(current)) {
                addTypeReferences(current, pending);
            }
        }
        return false;
    }

    private static void addTypeReferences(JavaType type, List<JavaType> pending) {
        if (type instanceof JavaType.GenericTypeVariable) {
            pending.addAll(((JavaType.GenericTypeVariable) type).getBounds());
        } else if (type instanceof JavaType.Array) {
            pending.add(((JavaType.Array) type).getElemType());
        } else if (type instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
            pending.addAll(fq.getTypeParameters());
            if (fq.getSupertype() != null) {
                pending.add(fq.getSupertype());
            }
            pending.addAll(fq.getInterfaces());
        }
    }

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
