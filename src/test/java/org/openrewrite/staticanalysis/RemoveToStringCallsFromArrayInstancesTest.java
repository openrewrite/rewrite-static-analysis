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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ImplicitArrayToString", "UnnecessaryLocalVariable", "RedundantStringFormatCall", "MalformedFormatString", "PrimitiveArrayArgumentToVarargsMethod"})
class RemoveToStringCallsFromArrayInstancesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveToStringCallsFromArrayInstances());
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
                  Integer number = 5;
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
                  return new int[]{1, 2, 3, 4};
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
                  return new int[]{1, 2, 3, 4};
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
    void printStringConcatenationTest() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String[] arr = new String[]{"string ", "array"};
                  System.out.print("Array: " + arr);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  String[] arr = new String[]{"string ", "array"};
                  System.out.print("Array: " + Arrays.toString(arr));
                }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunOnNormalStringConcat() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String strOne = "hello, ";
                  String strTwo = "world!";
                  System.out.print(strOne + strTwo);
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

    @Test
    void worksWithObjectsToString() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Objects;

              class SomeClass {
                public static void main(String[] args) {
                  int[] arr = new int[]{1, 2, 3};
                  String str_rep = Objects.toString(arr);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  int[] arr = new int[]{1, 2, 3};
                  String str_rep = Arrays.toString(arr);
                }
              }
              """
          )
        );
    }

    @Test
    void worksWithValueOf() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  String[] strings = new String[]{"bar"};

                  String str_rep = String.valueOf(strings);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  String[] strings = new String[]{"bar"};

                  String str_rep = Arrays.toString(strings);
                }
              }
              """
          )
        );
    }

    @Test
    void worksWithInsert() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  StringBuilder builder = new StringBuilder("builder");
                  String[] strings = new String[]{"string", "array"};

                  builder.insert(0, strings);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  StringBuilder builder = new StringBuilder("builder");
                  String[] strings = new String[]{"string", "array"};

                  builder.insert(0, Arrays.toString(strings));
                }
              }
              """
          )
        );
    }

    @Test
    void worksWithAppend() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                public static void main(String[] args) {
                  StringBuilder builder = new StringBuilder("builder");
                  String[] strings = new String[]{"array"};

                  builder.append(strings);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  StringBuilder builder = new StringBuilder("builder");
                  String[] strings = new String[]{"array"};

                  builder.append(Arrays.toString(strings));
                }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunOnPrintStreamFormat() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.PrintStream;

              class SomeClass {
                public static void main(String[] args) {
                  PrintStream ps = new PrintStream(System.out);
                  String[] arr = new String[]{"test", "array"};

                  ps.format("formatting array: %s", arr);
                  ps.flush();
                }
              }
              """
          )
        );
    }

    @Test
    void printStreamPrintWorks() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.PrintStream;

              class SomeClass {
                public static void main(String[] args) {
                  PrintStream ps = new PrintStream(System.out);
                  String[] arr = new String[]{"test", "array"};

                  ps.print(arr);
                  ps.flush();
                }
              }
              """,
            """
              import java.io.PrintStream;
              import java.util.Arrays;

              class SomeClass {
                public static void main(String[] args) {
                  PrintStream ps = new PrintStream(System.out);
                  String[] arr = new String[]{"test", "array"};

                  ps.print(Arrays.toString(arr));
                  ps.flush();
                }
              }
              """
          )
        );
    }

    @Test
    void varargs() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                String foo(Object[] strings) {
                    return String.format("%s %s", strings);
                }
              }
              """
          )
        );
    }

    @Test
    void varargsButTwoArrays() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                String foo(Object[] array1, Object[] array2) {
                    return String.format("%s %s", array1, array2);
                }
              }
              """,
            """
              import java.util.Arrays;

              class SomeClass {
                String foo(Object[] array1, Object[] array2) {
                    return String.format("%s %s", Arrays.toString(array1), Arrays.toString(array2));
                }
              }
              """
          )
        );
    }
}
