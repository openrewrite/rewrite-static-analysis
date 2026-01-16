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
import org.apache.commons.text.StringEscapeUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.singleton;

/**
 * Recipe to use {@link String#replace(CharSequence, CharSequence)} when the fist argument is not a regular expression.
 * <p>
 * The underlying implementation of {@link String#replaceAll(String, String)} calls the {@link Pattern#compile(String)}
 * method each time it is called even if the first argument is not a regular expression. This has a significant
 * performance cost and therefore should be used with care.
 *
 * @see <a href="https://rules.sonarsource.com/java/RSPEC-S5361"></a>
 * @see <a href="https://docs.oracle.com/javase/tutorial/essential/regex/index.html"></a>
 */
public class UseStringReplace extends Recipe {

    @Getter
    final String displayName = "Use `String::replace()` when first parameter is not a real regular expression";

    @Getter
    final String description = "When `String::replaceAll` is used, the first argument should be a real regular expression. " +
            "If it’s not the case, `String::replace` does exactly the same thing as `String::replaceAll` without the performance drawback of the regex.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5361");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UseStringReplaceVisitor();
    }

    private static class UseStringReplaceVisitor extends JavaVisitor<ExecutionContext> {

        private static final MethodMatcher REPLACE_ALL = new MethodMatcher("java.lang.String replaceAll(..)");
        private static final Pattern ESCAPED_CHARACTER = Pattern.compile("\\\\\\.");
        private static final Pattern METACHARACTERS = Pattern.compile("[(\\[{\\\\^\\-$!|\\]})?*+.]|\\?=|<=");
        private static final Pattern CHARACTER_CLASSES = Pattern.compile("\\\\d|\\\\D|\\\\s|\\\\S|\\\\w|\\\\W");

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation invocation = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

            // Checks if method invocation matches with String#replaceAll
            if (REPLACE_ALL.matches(invocation)) {
                // Checks if the second argument is a string literal with $ or \ in it as this has special meaning
                // https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/regex/Matcher.html#replaceAll(java.lang.String)
                Expression secondArgument = invocation.getArguments().get(1);
                if (!isStringLiteral(secondArgument)) {
                    return invocation; // Might contain special characters; unsafe to replace
                }
                String secondValue = (String) ((J.Literal) secondArgument).getValue();
                if (secondValue != null && (secondValue.contains("$") || secondValue.contains("\\"))) {
                    return invocation; // Does contain special characters; unsafe to replace
                }

                // Checks if the first argument is a String literal
                Expression firstArgument = invocation.getArguments().get(0);
                if (isStringLiteral(firstArgument)) {
                    // Checks if the String literal may not be a regular expression,
                    // if so, then change the method invocation name
                    String firstValue = (String) ((J.Literal) firstArgument).getValue();
                    if (firstValue != null && !mayBeRegExp(firstValue)) {
                        String unEscapedLiteral = unEscapeCharacters(firstValue);
                        invocation = invocation
                                .withName(invocation.getName().withSimpleName("replace"))
                                .withArguments(ListUtils.mapFirst(invocation.getArguments(), arg -> ((J.Literal) arg)
                                        .withValue(unEscapedLiteral)
                                        .withValueSource(String.format("\"%s\"", StringEscapeUtils.escapeJava(unEscapedLiteral)))));
                    }
                }
            }

            return invocation;
        }

        private boolean isStringLiteral(Expression expression) {
            return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
        }

        private boolean mayBeRegExp(String argument) {
            //Remove all escaped characters and then checks if argument contains any metacharacter or any character class
            String cleanedValue = ESCAPED_CHARACTER.matcher(argument).replaceAll("");
            return METACHARACTERS.matcher(cleanedValue).find() || CHARACTER_CLASSES.matcher(cleanedValue).find();
        }

        private String unEscapeCharacters(String argument) {
            return argument.replace("\\\\", "\\")
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\", "");
        }
    }
}
