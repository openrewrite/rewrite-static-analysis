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
import org.openrewrite.staticanalysis.table.MapKeySetIterations;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"KeySetIterationMayUseEntrySet", "unused", "Java8MapApi", "Convert2Lambda", "rawtypes"})
class UseMapEntrySetIterationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseMapEntrySetIteration());
    }

    @DocumentExample
    @Test
    void keySetIterationWithGet() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  void test(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          Integer w = map.get(key);
                          System.out.println(key + "=" + w);
                      }
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  void test(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          Integer w = entry.getValue();
                          System.out.println(entry.getKey() + "=" + w);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loopShapesThatAreConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.List;
              import java.util.Map;

              class Test {
                  private final Map<String, Integer> field = new HashMap<>();

                  int keyOnlyUsedToLookUpTheValue(Map<String, Integer> map) {
                      int sum = 0;
                      for (String key : map.keySet()) {
                          sum += map.get(key);
                      }
                      return sum;
                  }

                  void mapIsAField() {
                      for (String key : this.field.keySet()) {
                          System.out.println(this.field.get(key));
                      }
                  }

                  void bodyIsNotABlock(Map<String, Integer> map) {
                      for (String key : map.keySet())
                          System.out.println(map.get(key));
                  }

                  void otherMapMethodsAreLeftAlone(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          if (map.containsKey(key) && map.size() > 1) {
                              System.out.println(map.get(key));
                          }
                      }
                  }

                  void anotherMapIsModified(Map<String, List<String>> source, Map<String, List<String>> target) {
                      for (String key : source.keySet()) {
                          if (target.containsKey(key)) {
                              target.get(key).addAll(source.get(key));
                          } else {
                              target.put(key, source.get(key));
                          }
                      }
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.List;
              import java.util.Map;

              class Test {
                  private final Map<String, Integer> field = new HashMap<>();

                  int keyOnlyUsedToLookUpTheValue(Map<String, Integer> map) {
                      int sum = 0;
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          sum += entry.getValue();
                      }
                      return sum;
                  }

                  void mapIsAField() {
                      for (Map.Entry<String, Integer> entry : this.field.entrySet()) {
                          System.out.println(entry.getValue());
                      }
                  }

                  void bodyIsNotABlock(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet())
                          System.out.println(entry.getValue());
                  }

                  void otherMapMethodsAreLeftAlone(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          if (map.containsKey(entry.getKey()) && map.size() > 1) {
                              System.out.println(entry.getValue());
                          }
                      }
                  }

                  void anotherMapIsModified(Map<String, List<String>> source, Map<String, List<String>> target) {
                      for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                          if (target.containsKey(entry.getKey())) {
                              target.get(entry.getKey()).addAll(entry.getValue());
                          } else {
                              target.put(entry.getKey(), entry.getValue());
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void entryTypeArgumentsAreResolvedForTheMap() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Map;

              class Test {
                  void genericValueType(Map<String, List<Integer>> map) {
                      for (String key : map.keySet()) {
                          List<Integer> values = map.get(key);
                          System.out.println(key + values.size());
                      }
                  }

                  void valueDeclaredWithVar(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          var value = map.get(key);
                          System.out.println(key + ":" + value);
                      }
                  }

                  void valueDeclaredAsASupertype(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          Object value = map.get(key);
                          System.out.println(key + ":" + value);
                      }
                  }

                  static class Generic<K, V> {
                      void typeVariablesOfTheEnclosingClass(Map<K, V> map) {
                          for (K key : map.keySet()) {
                              V value = map.get(key);
                              System.out.println(key + ":" + value);
                          }
                      }
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Map;

              class Test {
                  void genericValueType(Map<String, List<Integer>> map) {
                      for (Map.Entry<String, List<Integer>> entry : map.entrySet()) {
                          List<Integer> values = entry.getValue();
                          System.out.println(entry.getKey() + values.size());
                      }
                  }

                  void valueDeclaredWithVar(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          var value = entry.getValue();
                          System.out.println(entry.getKey() + ":" + value);
                      }
                  }

                  void valueDeclaredAsASupertype(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          Object value = entry.getValue();
                          System.out.println(entry.getKey() + ":" + value);
                      }
                  }

                  static class Generic<K, V> {
                      void typeVariablesOfTheEnclosingClass(Map<K, V> map) {
                          for (Map.Entry<K, V> entry : map.entrySet()) {
                              V value = entry.getValue();
                              System.out.println(entry.getKey() + ":" + value);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void entryTypeUsesTheNameTheFileAlreadyGivesTheType() {
        rewriteRun(
          //language=java
          java(
            """
              package a;

              public class Widget {
              }
              """
          ),
          //language=java
          java(
            """
              package a;

              import java.util.Map;

              class Test {
                  void valueIsDeclaredInTheLoop(Map<String, Widget> map) {
                      for (String key : map.keySet()) {
                          Widget w = map.get(key);
                          System.out.println(key + w);
                      }
                  }

                  void typeIsOnlyNamedElsewhereInTheMethod(Map<String, Widget> map) {
                      for (String key : map.keySet()) {
                          System.out.println(map.get(key));
                      }
                  }
              }
              """,
            """
              package a;

              import java.util.Map;

              class Test {
                  void valueIsDeclaredInTheLoop(Map<String, Widget> map) {
                      for (Map.Entry<String, Widget> entry : map.entrySet()) {
                          Widget w = entry.getValue();
                          System.out.println(entry.getKey() + w);
                      }
                  }

                  void typeIsOnlyNamedElsewhereInTheMethod(Map<String, Widget> map) {
                      for (Map.Entry<String, Widget> entry : map.entrySet()) {
                          System.out.println(entry.getValue());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mapIsASubtypeAndImportIsAdded() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.TreeMap;

              class Test {
                  void test(TreeMap<String, Integer> map) {
                      for (String key : map.keySet()) {
                          System.out.println(key + map.get(key));
                      }
                  }
              }
              """,
            """
              import java.util.Map;
              import java.util.TreeMap;

              class Test {
                  void test(TreeMap<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          System.out.println(entry.getKey() + entry.getValue());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void entryVariableNameAvoidsCollisions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  void nestedLoops(Map<String, Integer> outer, Map<String, Integer> inner) {
                      for (String key : outer.keySet()) {
                          for (String innerKey : inner.keySet()) {
                              System.out.println(outer.get(key) + inner.get(innerKey));
                          }
                      }
                  }

                  void loopBodyDeclaresEntry(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          String entry = key + map.get(key);
                          System.out.println(entry);
                      }
                  }
              }

              class ParameterNamedEntry {
                  void test(Map<String, Integer> map, String entry) {
                      for (String key : map.keySet()) {
                          System.out.println(entry + key + map.get(key));
                      }
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  void nestedLoops(Map<String, Integer> outer, Map<String, Integer> inner) {
                      for (Map.Entry<String, Integer> entry1 : outer.entrySet()) {
                          for (Map.Entry<String, Integer> entry : inner.entrySet()) {
                              System.out.println(entry1.getValue() + entry.getValue());
                          }
                      }
                  }

                  void loopBodyDeclaresEntry(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry1 : map.entrySet()) {
                          String entry = entry1.getKey() + entry1.getValue();
                          System.out.println(entry);
                      }
                  }
              }

              class ParameterNamedEntry {
                  void test(Map<String, Integer> map, String entry) {
                      for (Map.Entry<String, Integer> entry1 : map.entrySet()) {
                          System.out.println(entry + entry1.getKey() + entry1.getValue());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhatCannotBeProvenSafe() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;
              import java.util.concurrent.ConcurrentHashMap;

              class Test {
                  Map<String, Integer> getMap() {
                      return null;
                  }

                  void valueIsNeverRead(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          System.out.println(key);
                      }
                  }

                  void getIsOnAnotherMap(Map<String, Integer> map, Map<String, Integer> other) {
                      for (String key : map.keySet()) {
                          System.out.println(other.get(key));
                      }
                  }

                  void getIsCalledWithAnotherKey(Map<String, Integer> map, String other) {
                      for (String key : map.keySet()) {
                          System.out.println(map.get(key) + map.get(other));
                      }
                  }

                  void mapIsModifiedInTheLoop(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          map.put(key, map.get(key) + 1);
                      }
                  }

                  void loopVariableIsReassigned(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          Integer value = map.get(key);
                          key = key.trim();
                          System.out.println(key + value);
                      }
                  }

                  void loopVariableIsCapturedByALambda(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          Integer value = map.get(key);
                          Runnable r = () -> System.out.println(key + value);
                          r.run();
                      }
                  }

                  void loopVariableIsCapturedByAnAnonymousClass(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          Runnable r = new Runnable() {
                              @Override
                              public void run() {
                                  System.out.println(map.get(key));
                              }
                          };
                          r.run();
                      }
                  }

                  void mapExpressionMayHaveSideEffects() {
                      for (String key : getMap().keySet()) {
                          System.out.println(getMap().get(key));
                      }
                  }

                  void concurrentMap(ConcurrentHashMap<String, Integer> map) {
                      for (String key : map.keySet()) {
                          System.out.println(map.get(key));
                      }
                  }

                  void rawMap(Map map) {
                      for (Object key : map.keySet()) {
                          System.out.println(map.get(key));
                      }
                  }

                  void iterableIsNotAKeySet(Map<String, Integer> map) {
                      for (Integer value : map.values()) {
                          System.out.println(value);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dataTableRecordsUpdatedAndSkippedLoops() {
        rewriteRun(
          spec -> spec.dataTable(MapKeySetIterations.Row.class, rows -> {
              assertThat(rows).hasSize(2);
              MapKeySetIterations.Row updated = rows.getFirst();
              assertThat(updated.getSourcePath()).isEqualTo("Test.java");
              assertThat(updated.getEnclosingClass()).isEqualTo("Test");
              assertThat(updated.getMapExpression()).isEqualTo("map");
              assertThat(updated.isUpdated()).isTrue();
              assertThat(updated.getReason()).isEmpty();

              MapKeySetIterations.Row skipped = rows.get(1);
              assertThat(skipped.isUpdated()).isFalse();
              assertThat(skipped.getReason()).isEqualTo("the map is modified inside the loop");
          }),
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  void updated(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          System.out.println(map.get(key));
                      }
                  }

                  void skipped(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          map.remove(key);
                          System.out.println(map.get(key));
                      }
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  void updated(Map<String, Integer> map) {
                      for (Map.Entry<String, Integer> entry : map.entrySet()) {
                          System.out.println(entry.getValue());
                      }
                  }

                  void skipped(Map<String, Integer> map) {
                      for (String key : map.keySet()) {
                          map.remove(key);
                          System.out.println(map.get(key));
                      }
                  }
              }
              """
          )
        );
    }
}
