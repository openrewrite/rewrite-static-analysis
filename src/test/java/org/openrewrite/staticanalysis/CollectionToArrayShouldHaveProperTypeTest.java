/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "RedundantCast"})
class CollectionToArrayShouldHaveProperTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CollectionToArrayShouldHaveProperType());
    }

    @DocumentExample
    @Test
    void basicCase() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      String[] array = (String[]) list.toArray();
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      String[] array = list.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void withFullyQualifiedType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(java.util.List<String> list) {
                      String[] array = (String[]) list.toArray();
                  }
              }
              """,
            """
              class Test {
                  void test(java.util.List<String> list) {
                      String[] array = list.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void withCustomType() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package com.example;

              public class MyClass {
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;

              import java.util.List;

              class Test {
                  void test(List<MyClass> list) {
                      MyClass[] array = (MyClass[]) list.toArray();
                  }
              }
              """,
            """
              package com.example;

              import java.util.List;

              class Test {
                  void test(List<MyClass> list) {
                      MyClass[] array = list.toArray(new MyClass[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void withImportNeeded() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.time.LocalDate;

              class Test {
                  void test(List<LocalDate> list) {
                      LocalDate[] array = (LocalDate[]) list.toArray();
                  }
              }
              """,
            """
              import java.util.List;
              import java.time.LocalDate;

              class Test {
                  void test(List<LocalDate> list) {
                      LocalDate[] array = list.toArray(new LocalDate[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void withFullyQualifiedTypeNeedsImport() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<java.time.LocalDate> list) {
                      java.time.LocalDate[] array = (java.time.LocalDate[]) list.toArray();
                  }
              }
              """,
            """
              import java.time.LocalDate;
              import java.util.List;

              class Test {
                  void test(List<LocalDate> list) {
                      LocalDate[] array = list.toArray(new LocalDate[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesMethodChaining() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      int length = ((String[]) list.toArray()).length;
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      int length = (list.toArray(new String[0])).length;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeToArrayWithArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      String[] array = list.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonCollectionToArray() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      MyObject obj = new MyObject();
                      String[] array = (String[]) obj.toArray();
                  }

                  class MyObject {
                      Object[] toArray() {
                          return new Object[0];
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeCastToNonArrayType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> list) {
                      Object obj = (Object) list.toArray();
                  }
              }
              """
          )
        );
    }

    @Test
    void worksWithSets() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;

              class Test {
                  void test(Set<String> set) {
                      String[] array = (String[]) set.toArray();
                  }
              }
              """,
            """
              import java.util.Set;

              class Test {
                  void test(Set<String> set) {
                      String[] array = set.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void worksWithArrayLists() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;

              class Test {
                  void test(ArrayList<String> list) {
                      String[] array = (String[]) list.toArray();
                  }
              }
              """,
            """
              import java.util.ArrayList;

              class Test {
                  void test(ArrayList<String> list) {
                      String[] array = list.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void inMethodParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void process(String[] array) {}

                  void test(List<String> list) {
                      process((String[]) list.toArray());
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void process(String[] array) {}

                  void test(List<String> list) {
                      process(list.toArray(new String[0]));
                  }
              }
              """
          )
        );
    }

    @Test
    void inReturnStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  String[] test(List<String> list) {
                      return (String[]) list.toArray();
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  String[] test(List<String> list) {
                      return list.toArray(new String[0]);
                  }
              }
              """
          )
        );
    }
}
