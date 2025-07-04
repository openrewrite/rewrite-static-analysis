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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class URLEqualsHashCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new URLEqualsHashCodeRecipes());
    }

    @Test
    @DocumentExample
    void urlHashCode() {
        rewriteRun(
          java(
                """
              import java.net.URL;

              class Test {
                  public void test() {
                      URL url = new URL("https://example.com");
                      int hash = url.hashCode();
                  }
              }
              """,
            """
              import java.net.URI;
              import java.net.URL;

              class Test {
                  public void test() {
                      URL url = new URL("https://example.com");
                      int hash = URI.create(url.toString()).hashCode();
                  }
              }
              """
          )
        );
    }

    @Test
    void urlEquals() {
        rewriteRun(
          java(
                """
              import java.net.URL;

              class Test {
                  public void test() {
                      URL url1 = new URL("https://example.com");
                      URL url2 = new URL("https://example.com");
                      boolean equals = url1.equals(url2);
                  }
              }
              """,
            """
              import java.net.URI;
              import java.net.URL;

              class Test {
                  public void test() {
                      URL url1 = new URL("https://example.com");
                      URL url2 = new URL("https://example.com");
                      boolean equals = URI.create(url1.toString()).equals(URI.create(url2.toString()));
                  }
              }
              """
          )
        );
    }
}
