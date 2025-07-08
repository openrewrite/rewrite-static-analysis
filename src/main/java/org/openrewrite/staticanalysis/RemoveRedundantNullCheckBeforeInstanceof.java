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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantNullCheckBeforeInstanceof extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove redundant null checks before instanceof";
    }

    @Override
    public String getDescription() {
        return "Removes redundant null checks before instanceof operations since instanceof returns false for null.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1697");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary bi = (J.Binary) super.visitBinary(binary, ctx);

                // Look for pattern: x != null && x instanceof Type
                if (bi.getOperator() == J.Binary.Type.And &&
                    bi.getLeft() instanceof J.Binary &&
                    bi.getRight() instanceof J.InstanceOf) {

                    J.Binary check = (J.Binary) bi.getLeft();
                    J.InstanceOf instanceOf = (J.InstanceOf) bi.getRight();

                    // Check if left is a null check (x != null or null != x)
                    if (check.getOperator() == J.Binary.Type.NotEqual) {
                        Expression nullCheckedExpr = null;

                        if (check.getLeft() instanceof J.Literal &&
                            ((J.Literal) check.getLeft()).getType() == JavaType.Primitive.Null) {
                            nullCheckedExpr = check.getRight();
                        } else if (check.getRight() instanceof J.Literal &&
                                   ((J.Literal) check.getRight()).getType() == JavaType.Primitive.Null) {
                            nullCheckedExpr = check.getLeft();
                        }

                        // Check if the same expression is used in both null check and instanceof
                        if (nullCheckedExpr != null &&
                            SemanticallyEqual.areEqual(nullCheckedExpr, instanceOf.getExpression())) {
                            // Return just the instanceof check, preserving the original prefix
                            return instanceOf.withPrefix(bi.getPrefix());
                        }
                    }
                }

                return bi;
            }
        };
    }
}
