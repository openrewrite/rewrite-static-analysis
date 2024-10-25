/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.FieldAccess;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class ReplaceClassIsInstanceWithInstanceof extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace `A.class.isInstance(a)` with `a instanceof A`";
    }

    @Override
    public String getDescription() {
        return "There should be no `A.class.isInstance(a)`, it should be replaced by `a instanceof A`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S6202");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        // use JavaVisitor instead of JavaIsoVisitor because we changed the type of LST
        return new JavaVisitor<ExecutionContext>() {

            private final MethodMatcher matcher = new MethodMatcher("java.lang.Class isInstance(java.lang.Object)");

            @Override
            public J visitMethodInvocation(MethodInvocation method, ExecutionContext ctx) {
                // make sure we find the right method and the left part is something like "SomeClass.class"
                if (matcher.matches(method) && isObjectClass(method.getSelect())) {
                    // for code like "A.class.isInstance(a)", select is "String.class", name is "isInstance", argument is "a"
                    Identifier objectExpression = (Identifier) method.getArguments().get(0);
                    FieldAccess fieldAccessPart = (FieldAccess) method.getSelect();
                    String className = ((JavaType.Class) fieldAccessPart.getTarget().getType()).getClassName();
                    // upcast to type J, so J.MethodInvocation can be replaced by J.InstanceOf
                    J.InstanceOf instanceOf = JavaTemplate.apply("#{any(org.openrewrite.java.tree.Expression)} instanceof #{}",
                            getCursor(), method.getCoordinates().replace(), objectExpression, className);
                    return maybeAutoFormat(method, instanceOf, ctx);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            private boolean isObjectClass(@Nullable Expression expression) {
                if (expression instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                    if (fieldAccess.getTarget() instanceof Identifier) {
                        Identifier identifier = (Identifier) fieldAccess.getTarget();
                        return identifier.getType() instanceof JavaType.Class;
                    }
                }
                return false;
            }
        };
    }
}