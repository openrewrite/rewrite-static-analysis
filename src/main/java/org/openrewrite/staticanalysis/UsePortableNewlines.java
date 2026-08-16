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

    private static final String PORTABLE_NEWLINE = "%n";
    private static final String TEXT_BLOCK_DELIMITER = "\"\"\"";
    private static final String STRING_DELIMITER = "\"";
    private static final int UNICODE_ESCAPE_HEX_DIGITS = 4;
    private static final int HEX_RADIX = 16;
    private static final int OCTAL_RADIX = 8;

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
                        int hexStart = i;
                        while (hexStart < source.length() && source.charAt(hexStart) == 'u') {
                            hexStart++;
                        }
                        // JLS 3.3: a backslash starts a Unicode escape only when preceded by an even number of backslashes
                        if (translatedBackslashes % 2 == 0 && hexStart > i && hexStart + UNICODE_ESCAPE_HEX_DIGITS <= source.length()) {
                            try {
                                translated = (char) Integer.parseInt(source.substring(hexStart, hexStart + UNICODE_ESCAPE_HEX_DIGITS), HEX_RADIX);
                                i = hexStart + UNICODE_ESCAPE_HEX_DIGITS;
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
                boolean textBlock = translatedSource.toString().startsWith(TEXT_BLOCK_DELIMITER);
                boolean openingTextBlockLine = textBlock;
                int newlineIndex = 0;
                int consecutiveBackslashes = 0;
                for (int i = (textBlock ? TEXT_BLOCK_DELIMITER : STRING_DELIMITER).length(); i < translatedSource.length(); i++) {
                    char current = translatedSource.charAt(i);
                    if (current == '\\') {
                        consecutiveBackslashes++;
                        continue;
                    }
                    // An odd run of preceding backslashes means the current character is escaped
                    boolean escaped = consecutiveBackslashes % 2 == 1;
                    if (current == 'n' && escaped) {
                        replacements.add(new int[]{rawStarts.get(i - 1), rawEnds.get(i)});
                        replacedNewlines.add(newlineIndex++);
                    } else if (escaped && isOctalDigit(current)) {
                        int octal = current - '0';
                        int maxDigits = maxOctalEscapeDigits(current);
                        int digits = 1;
                        while (digits < maxDigits && i + 1 < translatedSource.length()) {
                            char next = translatedSource.charAt(i + 1);
                            if (!isOctalDigit(next)) {
                                break;
                            }
                            octal = octal * OCTAL_RADIX + next - '0';
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
                        } else if (!escaped) {
                            // An escaped line terminator is a text-block continuation, which produces no newline
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
                        transformedSource.replace(replacement[0], replacement[1], PORTABLE_NEWLINE);
                    }
                    StringBuilder transformedValue = new StringBuilder(value.length());
                    newlineIndex = 0;
                    for (int i = 0; i < value.length(); i++) {
                        char current = value.charAt(i);
                        if (current == '\n' && replacedNewlines.contains(newlineIndex++)) {
                            transformedValue.append(PORTABLE_NEWLINE);
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

    private static boolean isOctalDigit(char c) {
        return '0' <= c && c <= '7';
    }

    // JLS 3.10.6: octal escapes are at most \377, so a third digit is only allowed after a leading 0-3
    private static int maxOctalEscapeDigits(char firstOctalDigit) {
        return firstOctalDigit <= '3' ? 3 : 2;
    }
}
