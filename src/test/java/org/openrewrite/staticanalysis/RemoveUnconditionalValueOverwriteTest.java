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

class RemoveUnconditionalValueOverwriteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnconditionalValueOverwrite());
    }

    @DocumentExample
    @Test
    void removeOverwrittenMapPut() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key", 1);
                      map.put("key", 2);
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key", 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOverwrittenWithVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test(String key) {
                      Map<String, Integer> map = new HashMap<>();
                      map.put(key, 1);
                      map.put(key, 2);
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test(String key) {
                      Map<String, Integer> map = new HashMap<>();
                      map.put(key, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentKeys() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key1", 1);
                      map.put("key2", 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentMaps() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map1 = new HashMap<>();
                      Map<String, Integer> map2 = new HashMap<>();
                      map1.put("key", 1);
                      map2.put("key", 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonConsecutivePuts() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key", 1);
                      System.out.println("between");
                      map.put("key", 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultipleOverwrites() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key", 1);
                      map.put("key", 2);
                      map.put("key", 3);
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("key", 3);
                  }
              }
              """
          )
        );
    }
}
