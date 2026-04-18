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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class SimplifyRedundantLogicalExpression extends Recipe {

    final String displayName = "Identical expressions used with logical operators should be simplified";

    final String description = "When the same expression appears on both sides of `&&`, `||`, `&`, or `|`, " +
            "the result is always equal to that expression. For example, `x && x` is always " +
            "just `x`. This is typically a copy-paste error where one side should have been different.";

    final Set<String> tags = singleton("RSPEC-S1764");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

                switch (b.getOperator()) {
                    case And:
                    case Or:
                    case BitAnd:
                    case BitOr:
                        if (SemanticallyEqual.areEqual(b.getLeft(), b.getRight()) &&
                                !hasSideEffects(b.getLeft())) {
                            return b.getLeft().unwrap().withPrefix(b.getPrefix());
                        }
                        break;
                    default:
                        break;
                }
                return b;
            }

            private boolean hasSideEffects(J tree) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean result) {
                        result.set(true);
                        return method;
                    }

                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean result) {
                        result.set(true);
                        return assignment;
                    }

                    @Override
                    public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean result) {
                        result.set(true);
                        return assignOp;
                    }

                    @Override
                    public J.Unary visitUnary(J.Unary unary, AtomicBoolean result) {
                        switch (unary.getOperator()) {
                            case PreIncrement:
                            case PreDecrement:
                            case PostIncrement:
                            case PostDecrement:
                                result.set(true);
                                return unary;
                            default:
                                return super.visitUnary(unary, result);
                        }
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean result) {
                        result.set(true);
                        return newClass;
                    }

                    @Override
                    public @Nullable J visit(@Nullable Tree t, AtomicBoolean result) {
                        if (result.get()) {
                            return (J) t;
                        }
                        return super.visit(t, result);
                    }
                }.reduce(tree, new AtomicBoolean(false)).get();
            }
        };
    }
}
