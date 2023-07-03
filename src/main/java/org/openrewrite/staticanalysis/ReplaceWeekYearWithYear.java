/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;

public class ReplaceWeekYearWithYear extends Recipe {
    public static final MethodMatcher SIMPLE_DATE_FORMAT_CONSTRUCTOR_MATCHER = new MethodMatcher("java.text.SimpleDateFormat <constructor>(..)");
    public static final MethodMatcher OF_PATTERN_MATCHER = new MethodMatcher("java.time.format.DateTimeFormatter ofPattern(..)");

    @Override
    public String getDisplayName() {
        return "Week Year (YYYY) should not be used for date formatting";
    }

    @Override
    public String getDescription() {
        return "For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of" +
               " December and first week of January Week Year could produce unexpected results.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3986");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("java.util.Date", false),
                        new UsesType<>("java.time.format.DateTimeFormatter", false),
                        new UsesType<>("java.text.SimpleDateFormat", false)
                ),
                new ReplaceWeekYearVisitor()
        );
    }

    private static class ReplaceWeekYearVisitor extends JavaIsoVisitor<ExecutionContext> {
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
                    String value = li.getValueSource();

                    if (value == null) {
                        return li;
                    }

                    String newValue = replaceY(value);

                    return li.withValueSource(newValue).withValue(newValue);
                }
            }

            return li;
        }

        public static String replaceY(String input) {
            StringBuilder output = new StringBuilder();
            boolean insideQuotes = false;

            for (int i = 0; i < input.length(); i++) {
                char currentChar = input.charAt(i);
                char nextChar = (i < input.length() - 1) ? input.charAt(i + 1) : '\0';

                if (currentChar == '\'') {
                    insideQuotes = !insideQuotes;
                    output.append(currentChar);
                } else if (currentChar == 'Y' && !insideQuotes) {
                    output.append('y');
                } else if (currentChar == 'Y' && nextChar == '\'') {
                    output.append(currentChar);
                } else {
                    output.append(currentChar);
                }
            }

            return output.toString();
        }
    }
}
