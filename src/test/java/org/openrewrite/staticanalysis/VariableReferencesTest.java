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
import org.openrewrite.Cursor;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

@SuppressWarnings({"unused", "UnusedAssignment", "ParameterCanBeLocal"})
class VariableReferencesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                Cursor parent = getCursor().getParentTreeCursor();
                boolean effectivelyFinal = parent.getValue() instanceof J.VariableDeclarations.NamedVariable &&
                                           ((J.VariableDeclarations.NamedVariable) parent.getValue()).getName() == identifier ?
                  VariableReferences.isEffectivelyFinal(parent, parent.getValue()) :
                  VariableReferences.isEffectivelyFinal(getCursor());
                return effectivelyFinal ? SearchResult.found(identifier) : identifier;
            }
        }));
    }

    @DocumentExample
    @Test
    void parametersAndLocals() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.SortedSet;

              class Test {
                  void f(SortedSet<Object> set, List<String> names) {
                      if (set.isEmpty()) set = other();
                      String s;
                      s = names.get(0);
                      int n = 0;
                      for (String name : names) n++;
                      String t;
                      if (s.isEmpty()) t = "a"; else t = "b";
                  }

                  SortedSet<Object> other() {
                      return null;
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.SortedSet;

              class Test {
                  void f(SortedSet<Object> set, List<String> /*~~>*/names) {
                      if (set.isEmpty()) set = other();
                      String /*~~>*/s;
                      /*~~>*/s = /*~~>*/names.get(0);
                      int n = 0;
                      for (String /*~~>*/name : /*~~>*/names) n++;
                      String t;
                      if (/*~~>*/s.isEmpty()) t = "a"; else t = "b";
                  }

                  SortedSet<Object> other() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void writesThroughParentheses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void f() {
                      int a = 0;
                      (a)++;
                      int b = 0;
                      (b) += 1;
                      int c = 0;
                      (c) = 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void sameNameInSiblingBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void f() {
                      {
                          int a = 1;
                          a++;
                      }
                      {
                          int a = 1;
                          System.out.println(a);
                      }
                  }
              }
              """,
            """
              class Test {
                  void f() {
                      {
                          int a = 1;
                          a++;
                      }
                      {
                          int /*~~>*/a = 1;
                          System.out.println(/*~~>*/a);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void singleAssignmentInsideLoop() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void f(List<String> names) {
                      String last;
                      for (String name : names) {
                          last = name;
                      }
                      for (String name : names) {
                          final String first;
                          first = name;
                      }
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void f(List<String> /*~~>*/names) {
                      String last;
                      for (String /*~~>*/name : /*~~>*/names) {
                          last = /*~~>*/name;
                      }
                      for (String /*~~>*/name : /*~~>*/names) {
                          final String /*~~>*/first;
                          /*~~>*/first = /*~~>*/name;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void declarationPositions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void f(List<String> names, int k) throws Exception {
                      names.forEach(name -> System.out.println(name));
                      try (AutoCloseable resource = null) {
                          System.out.println(resource);
                      } catch (RuntimeException e) {
                          System.out.println(e);
                      }
                      for (int i = 0, n = names.size(); i < n; i++) {
                          System.out.println(i + n);
                      }
                      switch (k) {
                          case 1:
                              int x = 1;
                              System.out.println(x);
                      }
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void f(List<String> /*~~>*/names, int /*~~>*/k) throws Exception {
                      /*~~>*/names.forEach(/*~~>*/name -> System.out.println(/*~~>*/name));
                      try (AutoCloseable /*~~>*/resource = null) {
                          System.out.println(/*~~>*/resource);
                      } catch (RuntimeException /*~~>*/e) {
                          System.out.println(/*~~>*/e);
                      }
                      for (int i = 0, /*~~>*/n = /*~~>*/names.size(); i < /*~~>*/n; i++) {
                          System.out.println(i + /*~~>*/n);
                      }
                      switch (/*~~>*/k) {
                          case 1:
                              int /*~~>*/x = 1;
                              System.out.println(/*~~>*/x);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldsAndPatternVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int field;

                  void f(Object o) {
                      record Point(int x, int y) {}
                      System.out.println(field);
                      Object anonymous = new Object() {
                          int counter;
                      };
                      if (o instanceof String s) {
                          System.out.println(s);
                      }
                      switch (o) {
                          case Integer i -> System.out.println(i);
                          case Point(int x, int y) -> System.out.println(x);
                          default -> {}
                      }
                  }
              }
              """,
            """
              class Test {
                  int field;

                  void f(Object /*~~>*/o) {
                      record Point(int x, int y) {}
                      System.out.println(field);
                      Object /*~~>*/anonymous = new Object() {
                          int counter;
                      };
                      if (/*~~>*/o instanceof String s) {
                          System.out.println(s);
                      }
                      switch (/*~~>*/o) {
                          case Integer i -> System.out.println(i);
                          case Point(int x, int y) -> System.out.println(x);
                          default -> {}
                      }
                  }
              }
              """
          )
        );
    }
}
