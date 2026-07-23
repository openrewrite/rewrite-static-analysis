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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceHashtableWithHashMapTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceHashtableWithHashMap());
    }

    @DocumentExample
    @Test
    void replaceConfinedHashtable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;

              class Test {
                  int test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      table.put("a", 1);
                      return table.size();
                  }
              }
              """,
            """
              import java.util.HashMap;

              class Test {
                  int test() {
                      HashMap<String, Integer> table = new HashMap<>();
                      table.put("a", 1);
                      return table.size();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenReturned() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;

              class Test {
                  Hashtable<String, Integer> test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      table.put("a", 1);
                      return table;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenAssignedToField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;

              class Test {
                  Hashtable<String, Integer> field;

                  void test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      this.field = table;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;

              class Test {
                  Hashtable<String, Integer> field = new Hashtable<>();
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenEnumerationMethodUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Enumeration;
              import java.util.Hashtable;

              class Test {
                  void test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      table.put("a", 1);
                      Enumeration<String> keys = table.keys();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenAliasedToDictionaryTypedLocal() {
        // Converting `table` would leave `Dictionary<...> d = table;` assigning a HashMap to a Dictionary.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Dictionary;
              import java.util.Hashtable;

              class Test {
                  void test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      Dictionary<String, Integer> d = table;
                      d.put("a", 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenContainsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;

              class Test {
                  boolean test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      table.put("a", 1);
                      return table.contains(1);
                  }
              }
              """
          )
        );
    }
}
