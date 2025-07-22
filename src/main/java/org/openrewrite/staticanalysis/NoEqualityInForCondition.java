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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class NoEqualityInForCondition extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use comparison rather than equality checks in for conditions";
    }

    @Override
    public String getDescription() {
        return "Testing for loop termination using an equality operator (`==` and `!=`) is dangerous, because it could set up an infinite loop. Using a relational operator instead makes it harder to accidentally write an infinite loop.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S888");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitForControl(J.ForLoop.Control control, ExecutionContext ctx) {
                if (control.getCondition() instanceof J.Binary) {
                    J.Binary condition = (J.Binary) control.getCondition();

                    if (isNumericalType(condition) &&
                            control.getUpdate().size() == 1 &&
                            control.getUpdate().get(0) instanceof J.Unary) {
                        J.Unary update = (J.Unary) control.getUpdate().get(0);
                        if (updatedExpressionInConditional(update.getExpression(), condition)) {
                            if (condition.getOperator() == J.Binary.Type.NotEqual) {
                                switch (update.getOperator()) {
                                    case PreIncrement:
                                    case PostIncrement:
                                        return control.withCondition(condition.withOperator(J.Binary.Type.LessThan));
                                    case PreDecrement:
                                    case PostDecrement:
                                        return control.withCondition(condition.withOperator(J.Binary.Type.GreaterThan));
                                }
                            }
                        }

                    }
                }

                return super.visitForControl(control, ctx);
            }

            private boolean updatedExpressionInConditional(Expression updatedExpression, J.Binary condition) {
                final String simpleName;
                if (updatedExpression instanceof J.Identifier) {
                    simpleName = ((J.Identifier) updatedExpression).getSimpleName();
                } else if (updatedExpression instanceof J.FieldAccess) {
                    simpleName = ((J.FieldAccess) updatedExpression).getSimpleName();
                } else {
                    return false;
                }

                if (condition.getLeft() instanceof J.Identifier) {
                    return simpleName.equals(((J.Identifier) condition.getLeft()).getSimpleName());
                }
                if (condition.getLeft() instanceof J.FieldAccess) {
                    return simpleName.equals(((J.FieldAccess) condition.getLeft()).getSimpleName());
                }
                if (condition.getRight() instanceof J.Identifier) {
                    return simpleName.equals(((J.Identifier) condition.getRight()).getSimpleName());
                }
                if (condition.getRight() instanceof J.FieldAccess) {
                    return simpleName.equals(((J.FieldAccess) condition.getRight()).getSimpleName());
                }
                return false;
            }

            private boolean isNumericalType(J.Binary condition) {
                JavaType type = condition.getRight().getType();
                return type == JavaType.Primitive.Short ||
                       type == JavaType.Primitive.Byte ||
                       type == JavaType.Primitive.Int ||
                       type == JavaType.Primitive.Long;
            }
        };
        return Preconditions.check(
                Preconditions.or(
                        // Avoid running on JS/TS, Python, Kotlin for now
                        new JavaFileChecker<>(),
                        new GroovyFileChecker<>(),
                        new CSharpFileChecker<>()),
                javaVisitor);
    }
}
