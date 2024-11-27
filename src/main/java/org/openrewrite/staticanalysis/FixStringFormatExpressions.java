/*
 * Copyright 2022 the original author or authors.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixStringFormatExpressions extends Recipe {

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final Pattern FS_PATTERN = Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

    private static final MethodMatcher FORMAT_MATCHER = new MethodMatcher("java.lang.String format(..)");
    private static final MethodMatcher FORMATTED_MATCHER = new MethodMatcher("java.lang.String formatted(..)");

    @Override
    public String getDisplayName() {
        return "Fix `String#format` and `String#formatted` expressions";
    }

    @Override
    public String getDescription() {
        return "Fix `String#format` and `String#formatted` expressions by replacing `\\n` newline characters with `%n` and removing any unused arguments. Note this recipe is scoped to only transform format expressions which do not specify the argument index.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3457");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(FORMAT_MATCHER), new UsesMethod<>(FORMATTED_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (FORMAT_MATCHER.matches(mi) || FORMATTED_MATCHER.matches(mi)) {
                            boolean isStringFormattedExpression = false;
                            J.Literal fmtArg = null;
                            if (FORMAT_MATCHER.matches(mi) && mi.getArguments().get(0) instanceof J.Literal) {
                                fmtArg = (J.Literal) mi.getArguments().get(0);
                            } else if (FORMATTED_MATCHER.matches(mi) && mi.getSelect() instanceof J.Literal) {
                                fmtArg = (J.Literal) mi.getSelect();
                                isStringFormattedExpression = true;
                            }

                            if (fmtArg == null || fmtArg.getValue() == null || fmtArg.getValueSource() == null) {
                                return mi;
                            }

                            // Replace any new line chars with %n
                            if (isStringFormattedExpression) {
                                mi = mi.withSelect(replaceNewLineChars(mi.getSelect()));
                            } else {
                                mi = mi.withArguments(ListUtils.mapFirst(mi.getArguments(), this::replaceNewLineChars));
                            }

                            // Trim any extra args
                            String val = (String) fmtArg.getValue();
                            Matcher m = FS_PATTERN.matcher(val);
                            int argIndex = isStringFormattedExpression ? 0 : 1;
                            while (m.find()) {
                                if (m.group(1) != null || m.group(2).contains("<")) {
                                    return mi;
                                }
                                argIndex++;
                            }
                            int finalArgIndex = argIndex;
                            mi = mi.withArguments(ListUtils.map(mi.getArguments(), (i, arg) -> {
                                if (i == 0 || i < finalArgIndex) {
                                    return arg;
                                }
                                return null;
                            }));
                            return mi;
                        }
                        return mi;
                    }

                    private Expression replaceNewLineChars(Expression arg0) {
                        if (arg0 instanceof J.Literal) {
                            J.Literal fmt = (J.Literal) arg0;
                            if (fmt.getValue() != null) {
                                fmt = fmt.withValue(fmt.getValue().toString().replaceAll("(?<!\\\\)\n", "%n"));
                            }
                            if (fmt.getValueSource() != null) {
                                fmt = fmt.withValueSource(fmt.getValueSource().replaceAll("(?<!\\\\)\\\\n", "%n"));
                            }
                            return fmt;
                        }
                        return arg0;
                    }
                }
        );
    }
}
