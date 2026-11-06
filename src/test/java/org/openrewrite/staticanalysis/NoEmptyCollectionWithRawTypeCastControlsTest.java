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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoEmptyCollectionWithRawTypeCastControlsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoEmptyCollectionWithRawType());
    }

    @Test
    void retainParameterizedMapAndSetCasts() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collections;
              import java.util.Map;
              import java.util.Set;

              class A {
                  @SuppressWarnings("unchecked")
                  Map<String, Integer> map() {
                      return (Map<String, Integer>) Collections.EMPTY_MAP;
                  }

                  @SuppressWarnings("unchecked")
                  Set<String> set() {
                      return (Set<String>) Collections.EMPTY_SET;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainFullyQualifiedCollectionCasts() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              @SuppressWarnings("unchecked")
              class A {
                  List<String> list = (List<String>) java.util.Collections.EMPTY_LIST;
                  Map<String, Integer> map = (Map<String, Integer>) java.util.Collections.EMPTY_MAP;
                  Set<String> set = (Set<String>) java.util.Collections.EMPTY_SET;
              }
              """
          )
        );
    }

    @Test
    void retainParenthesizedCastOperands() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collections;
              import java.util.List;

              @SuppressWarnings("unchecked")
              class A {
                  List<String> parenthesizedOperand = (List<String>) ((Collections.EMPTY_LIST));
                  List<String> parenthesizedCast = ((List<String>) Collections.EMPTY_LIST);
              }
              """
          )
        );
    }

    @Test
    void retainStaticImportedCollectionCasts() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              import static java.util.Collections.EMPTY_LIST;
              import static java.util.Collections.EMPTY_MAP;
              import static java.util.Collections.EMPTY_SET;

              @SuppressWarnings("unchecked")
              class A {
                  List<String> list = (List<String>) EMPTY_LIST;
                  Map<String, Integer> map = (Map<String, Integer>) EMPTY_MAP;
                  Set<String> set = (Set<String>) (EMPTY_SET);
              }
              """
          )
        );
    }

    @Test
    void convertOrdinaryStaticUsesAlongsideRetainedCast() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static java.util.Collections.EMPTY_LIST;

              @SuppressWarnings("unchecked")
              class A {
                  List<String> cast = (List<String>) (EMPTY_LIST);
                  List<String> ordinary = EMPTY_LIST;
                  List<String> throughCall = (List<String>) identity(EMPTY_LIST);

                  static Object identity(Object value) {
                      return value;
                  }
              }
              """,
            """
              import java.util.List;

              import static java.util.Collections.EMPTY_LIST;
              import static java.util.Collections.emptyList;

              @SuppressWarnings("unchecked")
              class A {
                  List<String> cast = (List<String>) (EMPTY_LIST);
                  List<String> ordinary = emptyList();
                  List<String> throughCall = (List<String>) identity(emptyList());

                  static Object identity(Object value) {
                      return value;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainForeignFieldWhileConvertingGenuineJdkField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collections;
              import java.util.List;

              class OtherCollections {
                  static final List<String> EMPTY_LIST = Collections.emptyList();
              }

              class A {
                  List<String> foreign = OtherCollections.EMPTY_LIST;
                  List<String> ordinary = Collections.EMPTY_LIST;
              }
              """,
            """
              import java.util.Collections;
              import java.util.List;

              class OtherCollections {
                  static final List<String> EMPTY_LIST = Collections.emptyList();
              }

              class A {
                  List<String> foreign = OtherCollections.EMPTY_LIST;
                  List<String> ordinary = Collections.emptyList();
              }
              """
          )
        );
    }
}
