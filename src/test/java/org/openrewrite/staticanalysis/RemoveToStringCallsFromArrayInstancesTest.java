/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ImplicitArrayToString", "UnnecessaryLocalVariable", "RedundantStringFormatCall", "MalformedFormatString", "PrimitiveArrayArgumentToVarargsMethod"})
public class RemoveToStringCallsFromArrayInstancesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion())
          .recipe(new RemoveToStringCallsFromArrayInstances());
    }

    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/44")
    void fixNonCompliantToString() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String argStr = args.toString();
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  String argStr = Arrays.toString(args);
                }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunOnNonArrayInstances() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  int number = 5;
                  System.out.println(number.toString());
                }
              }
              """
          )
        );
    }

    @Test
    void runsOnNonStringArrays() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String arrStr = getNumArr().toString();
                }
                
                public int[] getNumArr() {
                  int[] nums = {1, 2, 3, 4};
                  return nums;
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  String arrStr = Arrays.toString(getNumArr());
                }
                
                public int[] getNumArr() {
                  int[] nums = {1, 2, 3, 4};
                  return nums;
                }
              }
              """
          )
        );
    }

    @Test
    void selectIsAMethod() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String arrStr = getArr().toString();
                }
                
                public String[] getArr() {
                  String[] arr = {"test", "array"};
                  return arr;
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  String arrStr = Arrays.toString(getArr());
                }
                
                public String[] getArr() {
                  String[] arr = {"test", "array"};
                  return arr;
                }
              }
              """
          )
        );
    }

    @Test
    void printlnEdgeCase() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  public static void main(String[] args) {
                      int[] s = new int[]{1,2,3};
                      System.out.println(s);
                  }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                  public static void main(String[] args) {
                      int[] s = new int[]{1,2,3};
                      System.out.println(Arrays.toString(s));
                  }
              }
              """
          )
        );
    }

    @Test
    void stringFormatEdgeCase() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  int[] s = new int[]{1, 2, 3};
                  System.out.println(String.format("s=%s", s));
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  int[] s = new int[]{1, 2, 3};
                  System.out.println(String.format("s=%s", Arrays.toString(s)));
                }
              }
              """
          )
        );
    }

    @Test
    void stringFormatMultipleArraysPassedIn() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  int[] s1 = new int[]{1, 2, 3};
                  int[] s2 = new int[]{4, 5, 6};
                  
                  System.out.println(String.format("s1=%s, s2=%s", s1, s2));
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  int[] s1 = new int[]{1, 2, 3};
                  int[] s2 = new int[]{4, 5, 6};
                  
                  System.out.println(String.format("s1=%s, s2=%s", Arrays.toString(s1), Arrays.toString(s2)));
                }
              }
              """
          )
        );
    }

    @Test
    void stringFormatMultipleValuesWithArraysPassedIn() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  int[] s1 = new int[]{1, 2, 3};
                  int[] s2 = new int[]{4, 5, 6};
                  String name = "First array:";
                  String secondName = "Second array:";
                  
                  System.out.println(String.format("%s %s, %s %s", name, s1, secondName, s2));
                }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                public static void main(String[] args) {
                  int[] s1 = new int[]{1, 2, 3};
                  int[] s2 = new int[]{4, 5, 6};
                  String name = "First array:";
                  String secondName = "Second array:";
                  
                  System.out.println(String.format("%s %s, %s %s", name, Arrays.toString(s1), secondName, Arrays.toString(s2)));
                }
              }
              """
          )
        );
    }
}
