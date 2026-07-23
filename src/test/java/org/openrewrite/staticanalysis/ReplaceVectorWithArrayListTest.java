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

class ReplaceVectorWithArrayListTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceVectorWithArrayList());
    }

    @DocumentExample
    @Test
    void replaceConfinedVector() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  int test() {
                      Vector<Integer> v = new Vector<>();
                      v.add(1);
                      v.add(2);
                      return v.size();
                  }
              }
              """,
            """
              import java.util.ArrayList;

              class Test {
                  int test() {
                      ArrayList<Integer> v = new ArrayList<>();
                      v.add(1);
                      v.add(2);
                      return v.size();
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
              import java.util.Vector;

              class Test {
                  Vector<Integer> test() {
                      Vector<Integer> v = new Vector<>();
                      v.add(1);
                      return v;
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
              import java.util.Vector;

              class Test {
                  Vector<Integer> field;

                  void test() {
                      Vector<Integer> v = new Vector<>();
                      v.add(1);
                      this.field = v;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenPassedAsArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void consume(Vector<Integer> other) {
                  }

                  void test() {
                      Vector<Integer> v = new Vector<>();
                      v.add(1);
                      consume(v);
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
              import java.util.Vector;

              class Test {
                  Vector<Integer> field = new Vector<>();
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenVectorSpecificMethodUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void test() {
                      Vector<Integer> v = new Vector<>();
                      v.addElement(1);
                      v.elementAt(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceCapacityIncrementConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void test() {
                      Vector<Integer> v = new Vector<>(10, 5);
                      v.add(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWhenDeclaredAsListInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Vector;

              class Test {
                  int test() {
                      List<Integer> l = new Vector<>();
                      l.add(1);
                      return l.size();
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  int test() {
                      List<Integer> l = new ArrayList<>();
                      l.add(1);
                      return l.size();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenAliasedToVectorTypedLocal() {
        // Converting `v` would leave `Vector<Integer> v2 = v;` assigning an ArrayList to a Vector.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void test() {
                      Vector<Integer> v = new Vector<>();
                      Vector<Integer> v2 = v;
                      v2.add(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceMultiVariableDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void test() {
                      Vector<Integer> a = new Vector<>(), b = new Vector<>();
                      a.add(1);
                      b.add(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithInitialCapacity() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Vector;

              class Test {
                  void test() {
                      Vector<Integer> v = new Vector<>(10);
                      v.add(1);
                  }
              }
              """,
            """
              import java.util.ArrayList;

              class Test {
                  void test() {
                      ArrayList<Integer> v = new ArrayList<>(10);
                      v.add(1);
                  }
              }
              """
          )
        );
    }
}
