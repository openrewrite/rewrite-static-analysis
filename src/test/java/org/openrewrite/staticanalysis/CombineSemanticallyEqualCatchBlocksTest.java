/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"EmptyTryBlock", "TryWithIdenticalCatches", "CatchMayIgnoreException"})
class CombineSemanticallyEqualCatchBlocksTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CombineSemanticallyEqualCatchBlocks());
    }

    @Test
    void doNotCombineDifferentCatchBlocks() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                          String s = "foo";
                      } catch (B ex) {
                          String s = "bar";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void childClassIsCaughtBeforeParentClass() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends BaseException {}"),
          //language=java
          java("class BaseException extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                      } catch (B ex) { // Is subtype of BaseException with a unique block.
                          String diff;
                      } catch (BaseException ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void blocksContainDifferentComments() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                          // Comment 1
                      } catch (B ex) {
                          // Comment 2
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void blocksContainSameComments() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                          // Same
                      } catch (B ex) {
                          // Same
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                          // Same
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void combineSameSemanticallyEquivalentMethodTypes() {
        rewriteRun(
          //language=java
          java("class A extends BaseException {}"),
          //language=java
          java("class B extends BaseException {}"),
          //language=java
          java("class BaseException extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                          base(ex);
                      } catch (B ex) {
                          base(ex);
                      }
                  }
                  void base(BaseException ex) {}
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                          base(ex);
                      }
                  }
                  void base(BaseException ex) {}
              }
              """
          )
        );
    }

    @Test
    void combineCatchesIntoNewMultiCatch() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                      } catch (B ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fromMultiCatchCombineWithCatch() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java("class C extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                      } catch (B | C ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B | C ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fromCatchCombineWithMultiCatch() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java("class C extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                      } catch (C ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B | C ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fromMultiCatchCombineWithMultiCatch() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java("class C extends RuntimeException {}"),
          //language=java
          java("class D extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                      } catch (C | D ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B | C | D ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveOrderOfCatchesWhenPossible() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends BaseException {}"),
          //language=java
          java("class C extends BaseException {}"),
          //language=java
          java("class BaseException extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                      } catch (B ex) {
                          String diff;
                      } catch (C ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | C ex) {
                      } catch (B ex) {
                          String diff;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantChildClasses() {
        rewriteRun(
          //language=java
          java("class A extends BaseException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java("class BaseException extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                      } catch (B ex) {
                      } catch (BaseException ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (B | BaseException ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantChildClassesWithExistingMultiCatches() {
        rewriteRun(
          //language=java
          java("class A extends BaseException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java("class BaseException extends RuntimeException {}"),
          //language=java
          java("class Other extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                      } catch (BaseException | Other ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (B | BaseException | Other ex) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontCombineCatchBlocksWithDifferentMethodInvocationParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      try {
                      } catch (IllegalStateException e) {
                          log("ise" + e.getMessage());
                      } catch (RuntimeException e) {
                          log(e.getMessage());
                      }
                  }
                  void log(String msg) {}
              }
              """
          )
        );
    }


    @Test
    void combineSameCatchBlocksWithVariableDeclaration() {
        rewriteRun(
          //language=java
          java("class A extends RuntimeException {}"),
          //language=java
          java("class B extends RuntimeException {}"),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (A ex) {
                          String s = "foo";
                      } catch (B ex) {
                          String s = "foo";
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (A | B ex) {
                          String s = "foo";
                      }
                  }
              }
              """
          )
        );
    }
}
