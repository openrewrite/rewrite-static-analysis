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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class RenameMethodsNamedHashcodeEqualOrToString extends Recipe {
    private static final MethodMatcher NO_ARGS = new MethodMatcher("*..* *()", true);
    private static final MethodMatcher OBJECT_ARG = new MethodMatcher("*..* *(java.lang.Object)", true);

    @Override
    public String getDisplayName() {
        return "Rename methods named `hashcode`, `equal`, or `tostring`";
    }

    @Override
    public String getDescription() {
        return "Methods should not be named `hashcode`, `equal`, or `tostring`. " +
               "Any of these are confusing as they appear to be intended as overridden methods from the `Object` base class, despite being case-insensitive.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1221");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), Preconditions.or(new DeclaresMethod<>(NO_ARGS), new DeclaresMethod<>(OBJECT_ARG))), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getMethodType() != null && method.getReturnTypeExpression() != null) {
                    String sn = method.getSimpleName();
                    JavaType rte = method.getReturnTypeExpression().getType();
                    JavaType.Method t = method.getMethodType();
                    if (equalsIgnoreCaseExclusive(sn, "hashCode") && JavaType.Primitive.Int == rte && NO_ARGS.matches(t)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "hashCode", true, false).getVisitor());
                    } else if ("equal".equalsIgnoreCase(sn) && JavaType.Primitive.Boolean == rte && OBJECT_ARG.matches(t)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "equals", true, false).getVisitor());
                    } else if (equalsIgnoreCaseExclusive(sn, "toString") && TypeUtils.isString(rte) && NO_ARGS.matches(t)) {
                        doAfterVisit(new ChangeMethodName(MethodMatcher.methodPattern(method), "toString", true, false).getVisitor());
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private boolean equalsIgnoreCaseExclusive(String inputToCheck, String targetToCheck) {
                return inputToCheck.equalsIgnoreCase(targetToCheck) && !inputToCheck.equals(targetToCheck);
            }
        });
    }
}
