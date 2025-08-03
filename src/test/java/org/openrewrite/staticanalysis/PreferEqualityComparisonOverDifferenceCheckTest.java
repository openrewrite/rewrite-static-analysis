package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PreferEqualityComparisonOverDifferenceCheckTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferEqualityComparisonOverDifferenceCheck());
    }

    @Test
    void basicSubtractionEqualityComparison() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return a - b == 0;
                }
            }
            """,
            """
            class Test {
                boolean test(int a, int b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void subtractionInParenthesesEqualityComparison() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return (a - b) == 0;
                }
            }
            """,
            """
            class Test {
                boolean test(int a, int b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void reverseOrderZeroComparison() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return 0 == a - b;
                }
            }
            """,
            """
            class Test {
                boolean test(int a, int b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void reverseOrderZeroComparisonWithParentheses() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return 0 == (a - b);
                }
            }
            """,
            """
            class Test {
                boolean test(int a, int b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void worksWithLongType() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(long a, long b) {
                    return a - b == 0L;
                }
            }
            """,
            """
            class Test {
                boolean test(long a, long b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void worksWithFloatType() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(float a, float b) {
                    return a - b == 0.0f;
                }
            }
            """,
            """
            class Test {
                boolean test(float a, float b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void worksWithDoubleType() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(double a, double b) {
                    return a - b == 0.0;
                }
            }
            """,
            """
            class Test {
                boolean test(double a, double b) {
                    return a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void worksWithComplexExpressions() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int[] arr) {
                    return arr[0] - arr[1] == 0;
                }
            }
            """,
            """
            class Test {
                boolean test(int[] arr) {
                    return arr[0] == arr[1];
                }
            }
            """
          )
        );
    }

    @Test
    void worksWithMethodCalls() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test() {
                    return getValue1() - getValue2() == 0;
                }

                int getValue1() { return 1; }
                int getValue2() { return 2; }
            }
            """,
            """
            class Test {
                boolean test() {
                    return getValue1() == getValue2();
                }

                int getValue1() { return 1; }
                int getValue2() { return 2; }
            }
            """
          )
        );
    }

    @Test
    void worksInIfStatement() {
        rewriteRun(
          java(
            """
            class Test {
                void test(int a, int b) {
                    if (a - b == 0) {
                        System.out.println("Equal");
                    }
                }
            }
            """,
            """
            class Test {
                void test(int a, int b) {
                    if (a == b) {
                        System.out.println("Equal");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void worksInComplexBooleanExpressions() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b, int c, int d) {
                    return (a - b == 0) && (c - d == 0);
                }
            }
            """,
            """
            class Test {
                boolean test(int a, int b, int c, int d) {
                    return (a == b) && (c == d);
                }
            }
            """
          )
        );
    }

    @Test
    void worksInAssignment() {
        rewriteRun(
          java(
            """
            class Test {
                void test(int a, int b) {
                    boolean result = a - b == 0;
                }
            }
            """,
            """
            class Test {
                void test(int a, int b) {
                    boolean result = a == b;
                }
            }
            """
          )
        );
    }

    @Test
    void doesNotChangeNonZeroComparisons() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return a - b == 1;
                }
            }
            """
          )
        );
    }

    @Test
    void doesNotChangeNonSubtractionComparisons() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return a + b == 0;
                }
            }
            """
          )
        );
    }

    @Test
    void doesNotChangeInequalityComparisons() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return a - b != 0;
                }
            }
            """
          )
        );
    }

    @Test
    void doesNotChangeFloatComparisonWhenPrecisionMatters() {
        rewriteRun(
          java(
            """
            class Test {
                boolean test(float a, float b) {
                    return a - b == 0.00001f;
                }
            }
            """
          )
        );
    }

}
