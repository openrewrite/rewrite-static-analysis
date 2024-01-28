/*
 * Copyright 2024 the original author or authors.
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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import java.net.URI;
import java.net.URL;

@SuppressWarnings("UrlHashCode")
@RecipeDescriptor(
        name = "URL Equals and Hash Code",
        description = "Uses of `equals()` and `hashCode()` cause `java.net.URL` to make blocking internet connections. " +
                      "Instead, use `java.net.URI`.",
        tags = {"RSPEC-2112"}
)
public class URLEqualsHashCode {

    @RecipeDescriptor(
            name = "URL Equals",
            description = "Uses of `equals()` cause `java.net.URL` to make blocking internet connections. " +
                          "Instead, use `java.net.URI`.",
            tags = {"RSPEC-2112"}
    )
    public static class URLEquals {
        @BeforeTemplate
        boolean before(URL a, URL b) {
            return a.equals(b);
        }

        @AfterTemplate
        boolean after(URL a, URL b) {
            return URI.create(a.toString()).equals(URI.create(b.toString()));
        }
    }

    @RecipeDescriptor(
            name = "URL Hash Code",
            description = "Uses of `hashCode()` cause `java.net.URL` to make blocking internet connections. " +
                          "Instead, use `java.net.URI`.",
            tags = {"RSPEC-2112"}
    )
    public static class URLHashCode {
        @BeforeTemplate
        int before(URL a) {
            return a.hashCode();
        }

        @AfterTemplate
        int after(URL a) {
            return URI.create(a.toString()).hashCode();
        }
    }
}
