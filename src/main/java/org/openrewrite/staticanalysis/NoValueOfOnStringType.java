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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.java.ParenthesizeVisitor.maybeParenthesize;

public class NoValueOfOnStringType extends Recipe {
    private static final MethodMatcher VALUE_OF = new MethodMatcher("java.lang.String valueOf(..)");

    @Getter
    final String displayName = "Unnecessary `String#valueOf(..)`";

    @Getter
    final String description = "Replace unnecessary `String#valueOf(..)` method invocations with the argument directly. " +
            "This occurs when the argument to `String#valueOf(arg)` is a string literal, such as `String.valueOf(\"example\")`. " +
            "Or, when the `String#valueOf(..)` invocation is used in a concatenation, such as `\"example\" + String.valueOf(\"example\")`. " +
            "The wrapping call is redundant since Java already performs the conversion implicitly in these contexts.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1153");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(4);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(VALUE_OF), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (VALUE_OF.matches(method.getSelect())) {
                    return method;
                }

                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (VALUE_OF.matches(mi) && mi.getArguments().size() == 1) {
                    Expression argument = mi.getArguments().get(0);
                    if ((TypeUtils.isString(argument.getType()) && isNeverNull(argument)) || removeValueOfForStringConcatenation(argument)) {
                        return maybeParenthesize(argument.withPrefix(mi.getPrefix()), updateCursor(mi));
                    }
                }
                return mi;
            }

            /**
             * {@code String.valueOf(s)} only equals {@code s} when {@code s} is not null; for a null {@code String}
             * it yields {@code "null"} instead. Removing the call is therefore only safe for arguments that cannot
             * be null. Method invocations were already excluded for this reason; identifiers, field accesses and
             * casts are no safer, so require the argument to be demonstrably non-null instead.
             *
             * @param argument The argument of the valueOf method.
             * @return True if the argument can never be null.
             */
            private boolean isNeverNull(Expression argument) {
                Expression e = argument;
                while (e instanceof J.Parentheses) {
                    J tree = ((J.Parentheses<?>) e).getTree();
                    if (!(tree instanceof Expression)) {
                        return false;
                    }
                    e = (Expression) tree;
                }
                if (e instanceof J.Literal) {
                    return ((J.Literal) e).getValue() != null;
                }
                // String concatenation always produces a non-null String.
                return e instanceof J.Binary &&
                        ((J.Binary) e).getOperator() == J.Binary.Type.Addition &&
                        TypeUtils.isString(e.getType());
            }

            /**
             * If the String#valueOf method is within a binary expression and the argument is a primitive or a String,
             * the valueOf can be removed if the binary expression's type is a String. A String argument is safe here
             * even when it is null, because concatenation renders a null operand as {@code "null"} exactly as
             * String#valueOf would.
             *
             * @param argument The argument of the valueOf method.
             * @return True if the method can be removed.
             */
            private boolean removeValueOfForStringConcatenation(Expression argument) {
                if (TypeUtils.asPrimitive(argument.getType()) != null || TypeUtils.isString(argument.getType())) {
                    J parent = getCursor().getParent() != null ? getCursor().getParent().firstEnclosing(J.class) : null;
                    if (parent instanceof J.Binary) {
                        J.Binary b = (J.Binary) parent;
                        JavaType otherType = b.getRight() == getCursor().getValue() ? b.getLeft().getType() : b.getRight().getType();
                        return TypeUtils.isString(otherType) && b.getOperator() == J.Binary.Type.Addition;
                    }
                }
                return false;
            }
        });
    }
}
