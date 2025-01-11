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

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class StringLiteralEquality extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `String.equals()` on `String` literals";
    }

    @Override
    public String getDescription() {
        return "`String.equals()` should be used when checking value equality on String literals. " +
               "Using `==` or `!=` compares object references, not the actual value of the Strings. " +
               "This only modifies code where at least one side of the binary operation (`==` or `!=`) is a String literal, such as `\"someString\" == someVariable;`. " +
               "This is to prevent inadvertently changing code where referential equality is the user's intent.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S4973");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Don't change for other language than Java, because other languages uses different constructs.
        // For example, in Kotlin `==` means structural equality, so it is redundant to call equals().
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), new UsesType<>("java.lang.String", false)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                if (isStringLiteral(binary.getLeft()) || isStringLiteral(binary.getRight())) {
                    J after = null;
                    if (binary.getOperator() == J.Binary.Type.Equal) {
                        after = asEqualsMethodInvocation(binary).withPrefix(binary.getPrefix());
                    } else if (binary.getOperator() == J.Binary.Type.NotEqual) {
                        after = asNegatedUnary(asEqualsMethodInvocation(binary)).withPrefix(binary.getPrefix());
                    }
                    if (after != null) {
                        doAfterVisit(new EqualsAvoidsNull().getVisitor());
                        return after;
                    }
                }
                return super.visitBinary(binary, ctx);
            }

            private boolean isStringLiteral(Expression expression) {
                return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
            }

            /**
             * Transform a binary expression into a method invocation on String.equals. For example,
             * <p>
             * {@code "foo" == "bar"} into {@code "foo".equals("bar")}
             */
            private J.MethodInvocation asEqualsMethodInvocation(J.Binary binary) {
                return new J.MethodInvocation(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        new JRightPadded<>(binary.getLeft().withPrefix(Space.EMPTY), Space.EMPTY, Markers.EMPTY),
                        null,
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "equals", null, null),
                        JContainer.build(singletonList(new JRightPadded<>(binary.getRight().withPrefix(Space.EMPTY), Space.EMPTY, Markers.EMPTY))),
                        new JavaType.Method(
                                null,
                                Flag.Public.getBitMask(),
                                TypeUtils.asFullyQualified(JavaType.buildType("java.lang.String")),
                                "equals",
                                JavaType.Primitive.Boolean,
                                singletonList("o"),
                                singletonList(JavaType.buildType("java.lang.Object")),
                                null, null, null
                        )
                );
            }

            /**
             * Wrap a method invocation within a negated unary expression. For example,
             * <p>
             * {@code "foo".equals("bar")} into {@code !"foo".equals("bar")}
             */
            private J.Unary asNegatedUnary(J.MethodInvocation mi) {
                return new J.Unary(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                        mi,
                        JavaType.Primitive.Boolean
                );
            }
        });
    }
}
