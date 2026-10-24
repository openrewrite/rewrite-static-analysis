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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class UseLocaleWithCaseConversions extends Recipe {

    private static final MethodMatcher TO_LOWER_CASE_METHOD_MATCHER = new MethodMatcher("java.lang.String toLowerCase()");
    private static final MethodMatcher TO_UPPER_CASE_METHOD_MATCHER = new MethodMatcher("java.lang.String toUpperCase()");

    @Option(displayName = "Locale",
            description = "The name of the constant field on `java.util.Locale` to use, such as `ROOT` or `US`. " +
                    "Defaults to `ROOT` when not provided, which is recommended for locale-independent, " +
                    "algorithmic case conversions.",
            example = "ROOT",
            required = false)
    @Nullable
    String locale;

    @Override
    public String getDisplayName() {
        return "Specify a `Locale` when comparing locale-dependent data";
    }

    @Override
    public String getDescription() {
        return "Explicitly specify a `Locale` when calling `String#toLowerCase()` or `String#toUpperCase()`, " +
                "as the result of case conversion can vary between locales. For example, converting the letter " +
                "`I` to lower case yields a dotless `ı` under a Turkish locale instead of the expected `i`. " +
                "Relying on the platform default locale can therefore lead to unexpected and hard-to-reproduce " +
                "bugs. See [STR02-J](https://wiki.sei.cmu.edu/confluence/display/java/STR02-J.+Specify+an+appropriate+locale+when+comparing+locale-dependent+data) " +
                "for more information.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("STR02-J");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String localeConstant = (locale == null || locale.isEmpty()) ? "ROOT" : locale;
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(TO_LOWER_CASE_METHOD_MATCHER),
                        new UsesMethod<>(TO_UPPER_CASE_METHOD_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (TO_LOWER_CASE_METHOD_MATCHER.matches(m) || TO_UPPER_CASE_METHOD_MATCHER.matches(m)) {
                            m = JavaTemplate.builder("java.util.Locale." + localeConstant)
                                    .contextSensitive()
                                    .imports("java.util.Locale")
                                    .build()
                                    .apply(updateCursor(m), m.getCoordinates().replaceArguments());
                            maybeAddImport("java.util.Locale");
                        }
                        return m;
                    }
                }
        );
    }
}
