/*
 * Copyright 2026 the original author or authors.
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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import java.util.Comparator;

@RecipeDescriptor(
        name = "Use `String.CASE_INSENSITIVE_ORDER`",
        description = "Replaces case-insensitive string comparator lambdas and method references " +
                      "with the JDK constant `String.CASE_INSENSITIVE_ORDER`. Improves readability " +
                      "and removes one closure allocation per call site."
)
public class UseStringCaseInsensitiveOrder {

    @RecipeDescriptor(
            name = "Use `String.CASE_INSENSITIVE_ORDER` instead of a lambda",
            description = "Replace `(a, b) -> a.compareToIgnoreCase(b)` with " +
                          "`String.CASE_INSENSITIVE_ORDER` when used as a `Comparator<String>`."
    )
    public static class FromLambda {
        @BeforeTemplate
        Comparator<String> before() {
            return (a, b) -> a.compareToIgnoreCase(b);
        }

        @AfterTemplate
        Comparator<String> after() {
            return String.CASE_INSENSITIVE_ORDER;
        }
    }

    @RecipeDescriptor(
            name = "Use `String.CASE_INSENSITIVE_ORDER` instead of `String::compareToIgnoreCase`",
            description = "Replace the method reference `String::compareToIgnoreCase` with " +
                          "`String.CASE_INSENSITIVE_ORDER` when used as a `Comparator<String>`."
    )
    public static class FromMethodReference {
        @BeforeTemplate
        Comparator<String> before() {
            return String::compareToIgnoreCase;
        }

        @AfterTemplate
        Comparator<String> after() {
            return String.CASE_INSENSITIVE_ORDER;
        }
    }
}
