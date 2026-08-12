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
import java.util.ArrayList;
import java.util.List;
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

    String description = "Format strings should use %n rather than \\n to produce " +
            "platform-specific line separators. Hard-coded `\\n` characters produce " +
            "incorrect line endings on Windows, whereas `%n` adapts to the runtime " +
            "platform automatically.";

    Set<String> tags = singleton("RSPEC-S3457");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

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
                StringBuilder translatedSource = new StringBuilder(source.length());
                List<Integer> rawStarts = new ArrayList<>();
                List<Integer> rawEnds = new ArrayList<>();
                int translatedBackslashes = 0;
                for (int i = 0; i < source.length();) {
                    int rawStart = i;
                    char translated = source.charAt(i++);
                    if (translated == '\\') {
                        int unicode = i;
                        while (unicode < source.length() && source.charAt(unicode) == 'u') {
                            unicode++;
                        }
                        if (translatedBackslashes % 2 == 0 && unicode > i && unicode + 4 <= source.length()) {
                            try {
                                translated = (char) Integer.parseInt(source.substring(unicode, unicode + 4), 16);
                                i = unicode + 4;
                            } catch (NumberFormatException ignored) {
                                // Keep the raw backslash; an invalid Unicode escape is not valid Java source.
                            }
                        }
                    }
                    translatedSource.append(translated);
                    translatedBackslashes = translated == '\\' ? translatedBackslashes + 1 : 0;
                    rawStarts.add(rawStart);
                    rawEnds.add(i);
                }

                List<int[]> replacements = new ArrayList<>();
                List<Integer> replacedNewlines = new ArrayList<>();
                boolean textBlock = translatedSource.toString().startsWith("\"\"\"");
                boolean openingTextBlockLine = textBlock;
                int newlineIndex = 0;
                int consecutiveBackslashes = 0;
                for (int i = textBlock ? 3 : 1; i < translatedSource.length(); i++) {
                    char current = translatedSource.charAt(i);
                    if (current == '\\') {
                        consecutiveBackslashes++;
                        continue;
                    }
                    if (current == 'n' && consecutiveBackslashes % 2 == 1) {
                        replacements.add(new int[]{rawStarts.get(i - 1), rawEnds.get(i)});
                        replacedNewlines.add(newlineIndex++);
                    } else if (consecutiveBackslashes % 2 == 1 && current >= '0' && current <= '7') {
                        int octal = current - '0';
                        int maxDigits = current <= '3' ? 3 : 2;
                        int digits = 1;
                        while (digits < maxDigits && i + 1 < translatedSource.length()) {
                            char next = translatedSource.charAt(i + 1);
                            if (next < '0' || next > '7') {
                                break;
                            }
                            octal = octal * 8 + next - '0';
                            digits++;
                            i++;
                        }
                        if (octal == '\n') {
                            newlineIndex++;
                        }
                    } else if (textBlock && (current == '\n' || current == '\r')) {
                        boolean crlf = current == '\r' && i + 1 < translatedSource.length() &&
                                translatedSource.charAt(i + 1) == '\n';
                        if (openingTextBlockLine) {
                            openingTextBlockLine = false;
                        } else if (consecutiveBackslashes % 2 == 0) {
                            newlineIndex++;
                        }
                        if (crlf) {
                            i++;
                        }
                    }
                    consecutiveBackslashes = 0;
                }
                if (!replacedNewlines.isEmpty()) {
                    StringBuilder transformedSource = new StringBuilder(source);
                    for (int i = replacements.size() - 1; i >= 0; i--) {
                        int[] replacement = replacements.get(i);
                        transformedSource.replace(replacement[0], replacement[1], "%n");
                    }
                    StringBuilder transformedValue = new StringBuilder(value.length());
                    newlineIndex = 0;
                    for (int i = 0; i < value.length(); i++) {
                        char current = value.charAt(i);
                        if (current == '\n' && replacedNewlines.contains(newlineIndex++)) {
                            transformedValue.append("%n");
                        } else {
                            transformedValue.append(current);
                        }
                    }
                    return literal.withValue(transformedValue.toString()).withValueSource(transformedSource.toString());
                }
            }
        }
        return maybeLiteral;
    }
}
