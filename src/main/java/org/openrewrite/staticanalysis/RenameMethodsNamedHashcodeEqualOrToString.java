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
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class RenameMethodsNamedHashcodeEqualOrToString extends Recipe {
    private static final MethodMatcher NO_ARGS = new MethodMatcher("*..* *()", true);
    private static final MethodMatcher OBJECT_ARG = new MethodMatcher("*..* *(java.lang.Object)", true);

    @Getter
    final String displayName = "Rename methods named `hashcode`, `equal`, or `tostring`";

    @Getter
    final String description = "Methods should not be named `hashcode`, `equal`, or `tostring`. " +
            "Any of these are confusing as they appear to be intended as overridden methods from " +
            "the `Object` base class, despite being case-insensitive. These near-miss names are " +
            "almost certainly spelling mistakes that silently introduce a new method instead of " +
            "overriding the intended one.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1221");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), Preconditions.or(new DeclaresMethod<>(NO_ARGS), new DeclaresMethod<>(OBJECT_ARG))), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getMethodType() != null && method.getReturnTypeExpression() != null) {
                    String sn = method.getSimpleName();
                    JavaType rte = method.getReturnTypeExpression().getType();
                    JavaType.Method t = method.getMethodType();
                    if (equalsIgnoreCaseExclusive(sn, "hashCode") && JavaType.Primitive.Int == rte && NO_ARGS.matches(t) && canRenameTo(t, "hashCode", NO_ARGS)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "hashCode", true, false).getVisitor());
                    } else if ("equal".equalsIgnoreCase(sn) && JavaType.Primitive.Boolean == rte && OBJECT_ARG.matches(t) && canRenameTo(t, "equals", OBJECT_ARG)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "equals", true, false).getVisitor());
                    } else if (equalsIgnoreCaseExclusive(sn, "toString") && TypeUtils.isString(rte) && NO_ARGS.matches(t) && canRenameTo(t, "toString", NO_ARGS)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "toString", true, false).getVisitor());
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            /**
             * Method names are case sensitive, so a type may legally declare both the near-miss and the correctly
             * named method; renaming would emit a duplicate. The rename reaches overrides too, so subtypes in the
             * same file must be free of the target as well, and renaming onto an inherited `final` method or onto
             * one of `Object`'s public instance methods would emit an illegal override.
             * <p>
             * Declarations come from the LST rather than the type model, which for records and Lombok `@Data`
             * classes also carries generated members an explicit declaration would replace rather than collide with.
             */
            private boolean canRenameTo(JavaType.Method methodType, String targetName, MethodMatcher signature) {
                Set<Flag> flags = methodType.getFlags();
                if (!flags.contains(Flag.Public) || flags.contains(Flag.Static)) {
                    return false;
                }
                JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                AtomicBoolean targetAlreadyDeclared = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration existing, AtomicBoolean declared) {
                        JavaType.Method existingType = existing.getMethodType();
                        if (existingType != null && targetName.equals(existing.getSimpleName()) &&
                                signature.matches(existingType) &&
                                TypeUtils.isAssignableTo(declaringType, existingType.getDeclaringType())) {
                            declared.set(true);
                        }
                        return super.visitMethodDeclaration(existing, declared);
                    }
                }.visit(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), targetAlreadyDeclared);
                return !targetAlreadyDeclared.get() &&
                        !TypeUtils.findDeclaredMethod(declaringType.getSupertype(), targetName, methodType.getParameterTypes())
                                .filter(m -> m.getFlags().contains(Flag.Final))
                                .isPresent();
            }

            private boolean equalsIgnoreCaseExclusive(String inputToCheck, String targetToCheck) {
                return inputToCheck.equalsIgnoreCase(targetToCheck) && !inputToCheck.equals(targetToCheck);
            }
        });
    }
}
