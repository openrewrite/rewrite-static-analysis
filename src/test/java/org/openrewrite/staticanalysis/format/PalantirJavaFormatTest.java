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
package org.openrewrite.staticanalysis.format;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class PalantirJavaFormatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PalantirJavaFormat(null, null));
    }

    @DocumentExample
    @Test
    void formatsAndKeepsTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;
              import java.util.List;
              import java.util.ArrayList;
              class A {
                List<String> names( Map<String,Integer> counts ) {
                  List<String> result = new ArrayList<>(  );
                  for(String name : counts.keySet()) { if (counts.get(name) > 1) result.add(name.toUpperCase()); }
                  return result;
                }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
              import java.util.Map;

              class A {
                  List<String> names(Map<String, Integer> counts) {
                      List<String> result = new ArrayList<>();
                      for (String name : counts.keySet()) {
                          if (counts.get(name) > 1) result.add(name.toUpperCase());
                      }
                      return result;
                  }
              }
              """,
            spec -> spec.noTrim().afterRecipe(cu -> assertThat(cu.getClasses().getFirst().getType()).isNotNull())
          )
        );
    }

    @Test
    void removesUnusedImports() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Set;

              class A {
                  List<String> names;
              }
              """,
            """
              import java.util.List;

              class A {
                  List<String> names;
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void breaksLongLines() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String join(String first, String second, String third, String fourth, String fifth, String sixth) {
                      return first.concat(second).concat(third).concat(fourth).concat(fifth).concat(sixth).concat(first).concat(second);
                  }
              }
              """,
            """
              class A {
                  String join(String first, String second, String third, String fourth, String fifth, String sixth) {
                      return first.concat(second)
                              .concat(third)
                              .concat(fourth)
                              .concat(fifth)
                              .concat(sixth)
                              .concat(first)
                              .concat(second);
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void unchangedWhenAlreadyFormatted() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {}
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void aospStyle() {
        rewriteRun(
          spec -> spec.recipe(new PalantirJavaFormat("AOSP", null)),
          //language=java
          java(
            """
              class A {
                void m() { int x = 1; }
              }
              """,
            """
              class A {
                  void m() {
                      int x = 1;
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void formatsUntilTheFormatterSettles() {
        // palantir-java-format moves the comment onto a line of its own only on its second pass, so a file
        // formatted once is one that Spotless' check still rejects
        rewriteRun(
          //language=java
          java(
            """
              interface ByteConsumer {
                  ByteConsumer NOP = t -> {/* NOP */ };

                  void accept(byte t);
              }
              """,
            """
              interface ByteConsumer {
                  ByteConsumer NOP = t -> {
                      /* NOP */
                  };

                  void accept(byte t);
              }
              """,
            SourceSpec::noTrim
          )
        );
    }
}
