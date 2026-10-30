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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindNonSerializableComparatorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNonSerializableComparators());
    }

    @DocumentExample
    @Test
    void comparatorThatIsNotSerializable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Comparator;

              public class FruitComparator implements Comparator<String> {
                  @Override
                  public int compare(String a, String b) {
                      return a.compareTo(b);
                  }
              }
              """,
            """
              import java.util.Comparator;

              public class /*~~(A `Comparator` that is not `Serializable` makes every `TreeMap`, `TreeSet` or `PriorityQueue` built with it unserializable. Implement `Serializable` as well.)~~>*/FruitComparator implements Comparator<String> {
                  @Override
                  public int compare(String a, String b) {
                      return a.compareTo(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void comparatorThroughASupertype() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Comparator;

              public abstract class BaseComparator implements Comparator<String> {
              }
              """,
            """
              import java.util.Comparator;

              public abstract class /*~~(A `Comparator` that is not `Serializable` makes every `TreeMap`, `TreeSet` or `PriorityQueue` built with it unserializable. Implement `Serializable` as well.)~~>*/BaseComparator implements Comparator<String> {
              }
              """
          ),
          //language=java
          java(
            """
              public class FruitComparator extends BaseComparator {
                  @Override
                  public int compare(String a, String b) {
                      return a.compareTo(b);
                  }
              }
              """,
            """
              public class /*~~(A `Comparator` that is not `Serializable` makes every `TreeMap`, `TreeSet` or `PriorityQueue` built with it unserializable. Implement `Serializable` as well.)~~>*/FruitComparator extends BaseComparator {
                  @Override
                  public int compare(String a, String b) {
                      return a.compareTo(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void recordComparatorIsNotImplicitlySerializable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Comparator;

              record ByLength() implements Comparator<String> {
                  @Override
                  public int compare(String a, String b) {
                      return Integer.compare(a.length(), b.length());
                  }
              }
              """,
            """
              import java.util.Comparator;

              record /*~~(A `Comparator` that is not `Serializable` makes every `TreeMap`, `TreeSet` or `PriorityQueue` built with it unserializable. Implement `Serializable` as well.)~~>*/ByLength() implements Comparator<String> {
                  @Override
                  public int compare(String a, String b) {
                      return Integer.compare(a.length(), b.length());
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void serializableComparator() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.io.Serializable;
                  import java.util.Comparator;

                  public class FruitComparator implements Comparator<String>, Serializable {
                      private static final long serialVersionUID = 1;

                      @Override
                      public int compare(String a, String b) {
                          return a.compareTo(b);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void serializableThroughASupertype() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.io.Serializable;
                  import java.util.Comparator;

                  public abstract class BaseComparator implements Comparator<String>, Serializable {
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class FruitComparator extends BaseComparator {
                      @Override
                      public int compare(String a, String b) {
                          return a.compareTo(b);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void serializableRecordComparator() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.io.Serializable;
                  import java.util.Comparator;

                  record ByLength() implements Comparator<String>, Serializable {
                      @Override
                      public int compare(String a, String b) {
                          return Integer.compare(a.length(), b.length());
                      }
                  }
                  """
              )
            );
        }

        @Test
        void enumsAreAlreadySerializable() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Comparator;

                  public enum FruitComparator implements Comparator<String> {
                      INSTANCE;

                      @Override
                      public int compare(String a, String b) {
                          return a.compareTo(b);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void anonymousClassesAndLambdasAreNotMarked() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Comparator;

                  public class A {
                      Comparator<String> anonymous = new Comparator<String>() {
                          @Override
                          public int compare(String a, String b) {
                              return a.compareTo(b);
                          }
                      };

                      Comparator<String> lambda = (a, b) -> a.compareTo(b);
                  }
                  """
              )
            );
        }

        @Test
        void classThatIsNotAComparator() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Comparator;

                  public class A {
                      Comparator<String> byLength = Comparator.comparingInt(String::length);
                  }
                  """
              )
            );
        }

        @Test
        void interfaceExtendingComparator() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Comparator;

                  interface ByLength extends Comparator<String> {
                  }
                  """
              )
            );
        }
    }
}
