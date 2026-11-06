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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class NoEqualityInForCondition extends Recipe {
    @Getter
    final String displayName = "Use comparison rather than equality checks in for conditions";

    @Getter
    final String description = "Testing for loop termination using an equality operator (`==` and `!=`) is dangerous, because it could set up an infinite loop. Using a relational operator instead makes it harder to accidentally write an infinite loop.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S888");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> javaVisitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ForLoop.Control visitForControl(J.ForLoop.Control control, ExecutionContext ctx) {
                if (control.getCondition() instanceof J.Binary) {
                    J.Binary condition = (J.Binary) control.getCondition();

                    if (condition.getOperator() == J.Binary.Type.NotEqual &&
                            isNumericalType(condition) &&
                            control.getUpdate().size() == 1 &&
                            control.getUpdate().get(0) instanceof J.Unary) {
                        J.Unary update = (J.Unary) control.getUpdate().get(0);
                        J.Unary.Type op = update.getOperator();
                        boolean increment = op == J.Unary.Type.PreIncrement || op == J.Unary.Type.PostIncrement;
                        boolean decrement = op == J.Unary.Type.PreDecrement || op == J.Unary.Type.PostDecrement;
                        boolean onLeft = SemanticallyEqual.areEqual(update.getExpression(), condition.getLeft());
                        if ((increment || decrement) &&
                                (onLeft || SemanticallyEqual.areEqual(update.getExpression(), condition.getRight()))) {
                            return control.withCondition(condition.withOperator(
                                    increment == onLeft ? J.Binary.Type.LessThan : J.Binary.Type.GreaterThan));
                        }
                    }
                }

                return super.visitForControl(control, ctx);
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
