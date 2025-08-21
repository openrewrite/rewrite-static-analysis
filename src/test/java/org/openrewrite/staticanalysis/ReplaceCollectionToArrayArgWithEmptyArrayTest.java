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

@SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
class ReplaceCollectionToArrayArgWithEmptyArrayTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceCollectionToArrayArgWithEmptyArray());
    }

    @DocumentExample
    @Test
    void replaceSizeArgumentWithZero() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer [] array = args.toArray(new Integer[args.size()]);
                  }
              }
              """,
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer [] array = args.toArray(new Integer[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceConstantValueWithZero() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer[] array = args.toArray(new Integer[4]);
                  }
              }
              """,
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer[] array = args.toArray(new Integer[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceEmptyArrayInitializerWithZero() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer[] array = args.toArray(new Integer[]{});
                  }
              }
              """,
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer[] array = args.toArray(new Integer[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void dontChangeNonEmptyInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              class A {
                  void test(Collection<Integer> args){
                      Integer[] array = args.toArray(new Integer[]{1, 2, 3});
                  }
              }
              """
          )
        );
    }
}
