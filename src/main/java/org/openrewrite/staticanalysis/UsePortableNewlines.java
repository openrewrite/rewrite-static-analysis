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
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class UsePortableNewlines extends Recipe {

    private static final MethodMatcher STRING_FORMATTED = new MethodMatcher("java.lang.String formatted(..)");

    private static final MethodMatcher STRING_FORMAT = new MethodMatcher("java.lang.String format(java.lang.String, ..)");
    private static final MethodMatcher PRINT_STREAM_PRINTF = new MethodMatcher("java.io.PrintStream printf(java.lang.String, ..)");
    private static final MethodMatcher PRINT_WRITER_PRINTF = new MethodMatcher("java.io.PrintWriter printf(java.lang.String, ..)");
    private static final MethodMatcher FORMATTER_FORMAT = new MethodMatcher("java.util.Formatter format(java.lang.String, ..)");
    private static final MethodMatcher CONSOLE_PRINTF = new MethodMatcher("java.io.Console printf(java.lang.String, ..)");

    String displayName = "Use %n instead of \\n in format strings";

    String description = "Format strings should use %n rather than \\n to produce platform-specific line separators.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S3457");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(STRING_FORMATTED),
                        new UsesMethod<>(STRING_FORMAT),
                        new UsesMethod<>(PRINT_STREAM_PRINTF),
                        new UsesMethod<>(PRINT_WRITER_PRINTF),
                        new UsesMethod<>(FORMATTER_FORMAT),
                        new UsesMethod<>(CONSOLE_PRINTF)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        // Handle String.formatted() - format string is the select
                        if (STRING_FORMATTED.matches(method) && method.getSelect() != null) {
                            return method.withSelect(replaceNewlineInLiteral(method.getSelect()));
                        }
                        if (STRING_FORMAT.matches(method) ||
                                PRINT_STREAM_PRINTF.matches(method) ||
                                PRINT_WRITER_PRINTF.matches(method) ||
                                FORMATTER_FORMAT.matches(method) ||
                                CONSOLE_PRINTF.matches(method)) {
                            return method.withArguments(ListUtils.mapFirst(
                                    method.getArguments(), UsePortableNewlines::replaceNewlineInLiteral));
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                });
    }

    private static Expression replaceNewlineInLiteral(Expression maybeLiteral) {
        if (maybeLiteral instanceof J.Literal) {
            J.Literal literal = (J.Literal) maybeLiteral;
            if (literal.getValue() instanceof String && literal.getValueSource() != null) {
                String source = literal.getValueSource();
                String value = (String) literal.getValue();
                // Check if the source contains the escape sequence \n
                if (source.contains("\\n")) {
                    return literal
                            .withValue(value.replace("\n", "%n"))
                            .withValueSource(source.replace("\\n", "%n"));
                }
            }
        }
        return maybeLiteral;
    }
}
