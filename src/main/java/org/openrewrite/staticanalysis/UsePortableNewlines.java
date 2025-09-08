/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class UsePortableNewlines extends Recipe {

    private static final MethodMatcher STRING_FORMAT = new MethodMatcher("java.lang.String format(java.lang.String, ..)");
    private static final MethodMatcher STRING_FORMATTED = new MethodMatcher("java.lang.String formatted(..)");
    private static final MethodMatcher PRINT_STREAM_PRINTF = new MethodMatcher("java.io.PrintStream printf(java.lang.String, ..)");
    private static final MethodMatcher PRINT_WRITER_PRINTF = new MethodMatcher("java.io.PrintWriter printf(java.lang.String, ..)");
    private static final MethodMatcher FORMATTER_FORMAT = new MethodMatcher("java.util.Formatter format(java.lang.String, ..)");
    private static final MethodMatcher CONSOLE_PRINTF = new MethodMatcher("java.io.Console printf(java.lang.String, ..)");

    @Override
    public String getDisplayName() {
        return "Use %n instead of \\n in format strings";
    }

    @Override
    public String getDescription() {
        return "Format strings should use %n rather than \\n to produce platform-specific line separators.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3457");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(STRING_FORMAT),
                        new UsesMethod<>(STRING_FORMATTED),
                        new UsesMethod<>(PRINT_STREAM_PRINTF),
                        new UsesMethod<>(PRINT_WRITER_PRINTF),
                        new UsesMethod<>(FORMATTER_FORMAT),
                        new UsesMethod<>(CONSOLE_PRINTF)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);

                // Check if this literal contains \n and could be a format string
                if (l.getValue() instanceof String && l.getValueSource() != null) {
                    String source = l.getValueSource();
                    if (source.contains("\\n")) {
                        // Walk up the cursor path to find the context
                        Cursor cursor = getCursor();
                        while (cursor != null && cursor.getValue() != cursor.getRoot()) {
                            Object value = cursor.getValue();

                            // Direct use in method invocation
                            if (value instanceof J.MethodInvocation) {
                                J.MethodInvocation mi = (J.MethodInvocation) value;
                                if (isFormatMethod(mi) && !mi.getArguments().isEmpty() && mi.getArguments().get(0) == literal) {
                                    return replaceNewlineInLiteral(l);
                                }
                            }
                            // Used in variable declaration with format-related name
                            else if (value instanceof J.VariableDeclarations.NamedVariable) {
                                J.VariableDeclarations.NamedVariable var = (J.VariableDeclarations.NamedVariable) value;
                                // Check if this literal is used as the initializer (either directly or within the expression)
                                if (var.getInitializer() != null && isExpressionContainsLiteral(var.getInitializer(), literal)) {
                                    String varName = var.getSimpleName().toLowerCase();
                                    // Only consider it a format string if the variable name strongly suggests it
                                    if (varName.contains("format") || varName.contains("fmt")) {
                                        return replaceNewlineInLiteral(l);
                                    }
                                }
                            }

                            cursor = cursor.getParent();
                        }
                    }
                }

                return l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Handle String.formatted() - format string is the select
                if (STRING_FORMATTED.matches(mi)) {
                    if (mi.getSelect() instanceof J.Literal) {
                        J.Literal literal = (J.Literal) mi.getSelect();
                        J.Literal updated = replaceNewlineInLiteral(literal);
                        if (updated != literal) {
                            return mi.withSelect(updated);
                        }
                    }
                    return mi;
                }

                // Handle other format methods - format string is the first argument
                if (isFormatMethod(mi)) {
                    return replaceNewlineInFormatString(mi);
                }

                return mi;
            }

            private boolean isFormatMethod(J.MethodInvocation mi) {
                return STRING_FORMAT.matches(mi) ||
                        PRINT_STREAM_PRINTF.matches(mi) ||
                        PRINT_WRITER_PRINTF.matches(mi) ||
                        FORMATTER_FORMAT.matches(mi) ||
                        CONSOLE_PRINTF.matches(mi);
            }

            private boolean isExpressionContainsLiteral(Expression expr, J.Literal literal) {
                return expr == literal;
            }

            private J.Literal replaceNewlineInLiteral(J.Literal literal) {
                if (literal.getValue() instanceof String && literal.getValueSource() != null) {
                    String source = literal.getValueSource();
                    // Check if the source contains the escape sequence \n
                    if (source.contains("\\n")) {
                        String updatedSource = source.replace("\\n", "%n");
                        String value = (String) literal.getValue();
                        String updatedValue = value.replace("\n", "%n");
                        return literal
                                .withValue(updatedValue)
                                .withValueSource(updatedSource);
                    }
                }
                return literal;
            }

            private J.MethodInvocation replaceNewlineInFormatString(J.MethodInvocation mi) {
                // Get the format string argument (first argument)
                if (mi.getArguments().isEmpty()) {
                    return mi;
                }

                J firstArg = mi.getArguments().get(0);
                if (firstArg instanceof J.Literal) {
                    J.Literal literal = (J.Literal) firstArg;
                    J.Literal updated = replaceNewlineInLiteral(literal);
                    if (updated != literal) {
                        return mi.withArguments(ListUtils.mapFirst(mi.getArguments(), arg -> updated));
                    }
                }

                return mi;
            }
        });
    }
}
