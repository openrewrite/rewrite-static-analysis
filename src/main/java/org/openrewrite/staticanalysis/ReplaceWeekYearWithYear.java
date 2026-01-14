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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceWeekYearWithYear extends Recipe {
    private static final MethodMatcher SIMPLE_DATE_FORMAT_CONSTRUCTOR_MATCHER = new MethodMatcher("java.text.SimpleDateFormat <constructor>(..)");
    private static final MethodMatcher OF_PATTERN_MATCHER = new MethodMatcher("java.time.format.DateTimeFormatter ofPattern(..)");

    @Getter
    final String displayName = "Week Year (YYYY) should not be used for date formatting";

    @Getter
    final String description = "For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of " +
            "December and the first week of January, Week Year could produce unexpected results.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S3986");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(SIMPLE_DATE_FORMAT_CONSTRUCTOR_MATCHER),
                        new UsesMethod<>(OF_PATTERN_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        if (OF_PATTERN_MATCHER.matches(mi)) {
                            getCursor().putMessage("KEY", mi);
                        }
                        return super.visitMethodInvocation(mi, ctx);
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass nc, ExecutionContext ctx) {
                        if (SIMPLE_DATE_FORMAT_CONSTRUCTOR_MATCHER.matches(nc)) {
                            getCursor().putMessage("KEY", nc);
                        }
                        return super.visitNewClass(nc, ctx);
                    }

                    @Override
                    public J.Literal visitLiteral(J.Literal li, ExecutionContext ctx) {
                        if (li.getValue() instanceof String) {
                            Cursor c = getCursor().dropParentWhile(is -> is instanceof J.Parentheses || !(is instanceof Tree));
                            if (c.getMessage("KEY") != null) {
                                Object value = li.getValue();
                                String newValue = replaceY(value.toString());
                                if (!newValue.equals(value.toString())) {
                                    return li.withValueSource("\"" + newValue + "\"").withValue(newValue);
                                }
                            }
                        }
                        return li;
                    }

                    private String replaceY(String input) {
                        StringBuilder output = new StringBuilder();
                        boolean insideQuotes = false;

                        for (int i = 0; i < input.length(); i++) {
                            char currentChar = input.charAt(i);
                            if (currentChar == '\'') {
                                insideQuotes = !insideQuotes;
                                output.append(currentChar);
                            } else if (currentChar == 'Y' && !insideQuotes) {
                                output.append('y');
                            } else if (currentChar == 'w' && !insideQuotes) {
                                return input;
                            } else {
                                output.append(currentChar);
                            }
                        }

                        return output.toString();
                    }
                }
        );
    }

}
