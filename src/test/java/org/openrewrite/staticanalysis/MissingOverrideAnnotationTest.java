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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"MethodMayBeStatic", "FunctionName"})
class MissingOverrideAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MissingOverrideAnnotation(null));
    }

    @Language("java")
    String testInterface = """
      package com.example;
      
      interface TestInterface {
          void testInterface();
      }
      """;

    @Language("java")
    String testInterface0 = """
      package com.example;
      
      interface TestInterface0 {
          void testInterface0();
      }
      """;

    @Language("java")
    String testInterfaceExtension = """
      package com.example;
      
      interface TestInterfaceExtension extends TestInterface0 {
          void testInterfaceExtension();
      }
      """;

    @Language("java")
    String testParentParent = """
      package com.example;
      
      class TestParentParent {
          public void testParentParent() {
          }
      }
      """;

    @Language("java")
    String testParent = """
      package com.example;
      
      class TestParent extends TestParentParent {
          public void testParent() {
          }
      }
      """;

    @Language("java")
    String abstractTestParent = """
      package com.example;
      
      abstract class AbstractTestParent {
          abstract boolean isAbstractBoolean();

          boolean isBoolean() {
              return true;
          }
      }
      """;

    @Test
    void whenAMethodOverridesFromAParent() {
        rewriteRun(
          //language=java
          java(testParentParent),
          //language=java
          java(
            """
              package com.example;
              
              class Test extends TestParentParent {
                  public void testParentParent() {
                  }

                  public void localMethod() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test extends TestParentParent {
                  @Override
                  public void testParentParent() {
                  }

                  public void localMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenAMethodOverridesMultipleLayersOfParents() {
        rewriteRun(
          //language=java
          java(testParent),
          //language=java
          java(testParentParent),
          //language=java
          java(
            """
              package com.example;
              
              class Test extends TestParent {
                  public void testParent() {
                  }

                  public void localMethod() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test extends TestParent {
                  @Override
                  public void testParent() {
                  }

                  public void localMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenAMethodImplementsAnInterface() {
        rewriteRun(
          //language=java
          java(testInterface),
          //language=java
          java(
            """
              package com.example;
              
              class Test implements TestInterface {
                  public void testInterface() {
                  }

                  public void localMethod() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test implements TestInterface {
                  @Override
                  public void testInterface() {
                  }

                  public void localMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMethodsAreImplementedFromMultipleInterfaces() {
        rewriteRun(
          //language=java
          java(testInterface),
          //language=java
          java(testInterface0),
          //language=java
          java(
            """
              package com.example;
              
              class Test implements TestInterface, TestInterface0 {
                  public void testInterface() {
                  }

                  public void localMethod() {
                  }

                  public void testInterface0() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test implements TestInterface, TestInterface0 {
                  @Override
                  public void testInterface() {
                  }

                  public void localMethod() {
                  }

                  @Override
                  public void testInterface0() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMethodsAreImplementedFromMultipleLayersOfInterfaces() {
        rewriteRun(
          //language=java
          java(testInterfaceExtension),
          //language=java
          java(testInterface0),
          //language=java
          java(
            """
              package com.example;
              
              class Test implements TestInterfaceExtension {
                  public void testInterfaceExtension() {
                  }

                  public void localMethod() {
                  }

                  public void testInterface0() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test implements TestInterfaceExtension {
                  @Override
                  public void testInterfaceExtension() {
                  }

                  public void localMethod() {
                  }

                  @Override
                  public void testInterface0() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenAMethodOverridesFromAParentAndAMethodImplementsAnInterface() {
        rewriteRun(
          //language=java
          java(testParent),
          //language=java
          java(testParentParent),
          //language=java
          java(testInterface),
          //language=java
          java(
            """
              package com.example;
              
              class Test extends TestParent implements TestInterface {
                  public void testParent() {
                  }

                  public void localMethod() {
                  }

                  public void testInterface() {
                  }
              }
              """,
            """
              package com.example;
              
              class Test extends TestParent implements TestInterface {
                  @Override
                  public void testParent() {
                  }

                  public void localMethod() {
                  }

                  @Override
                  public void testInterface() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTheMethodIsStatic() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              
              import java.util.Collection;
              import java.util.Collections;

              class TestBase {
                  protected static Collection<Object[]> parameters() {
                      return Collections.emptyList();
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              
              import java.util.Collection;
              import java.util.Collections;

              class Test extends TestBase {
                  protected static Collection<Object[]> parameters() {
                      return Collections.emptyList();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTheSuperclassHasAbstractAndNonAbstractMethods() {
        rewriteRun(
          //language=java
          java(abstractTestParent),
          //language=java
          java(
            """
              package com.example;
              
              class Test extends AbstractTestParent {
                  public boolean isAbstractBoolean() {
                      return false;
                  }

                  public boolean isBoolean() {
                      return true;
                  }
              }
              """,
            """
              package com.example;
              
              class Test extends AbstractTestParent {
                  @Override
                  public boolean isAbstractBoolean() {
                      return false;
                  }

                  @Override
                  public boolean isBoolean() {
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void whenAMethodAlreadyHasAnAnnotation() {
        rewriteRun(
          //language=java
          java(testParent),
          //language=java
          java(testParentParent),
          //language=java
          java(
            """
              package com.example;
              
              class Test extends TestParent {
                  @Override
                  public void testParent() {
                  }
              }
              """
          )
        );
    }

    @Test
    void whenIgnoreAnonymousClassMethodsIsTrueAndAMethodOverridesWithinAnAnonymousClass() {
        rewriteRun(
          spec -> spec.recipe(new MissingOverrideAnnotation(true)),
          //language=java
          java(
            """
              package com.example;
              
              class Test {
                  public void method() {
                      //noinspection all
                      Runnable t = new Runnable() {
                          public void run() {
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void whenIgnoreAnonymousClassMethodsIsFalseAndAMethodOverridesWithinAnAnonymousClass() {
        rewriteRun(
          spec -> spec.recipe(new MissingOverrideAnnotation(false)),
          //language=java
          java(
            """
              package com.example;
              
              class Test {
                  public void method() {
                      //noinspection all
                      Runnable t = new Runnable() {
                          public void run() {
                          }
                      };
                  }
              }
              """,
            """
              package com.example;
              
              class Test {
                  public void method() {
                      //noinspection all
                      Runnable t = new Runnable() {
                          @Override
                          public void run() {
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void whenIgnoreObjectMethodsIsFalseAndAMethodOverridesFromTheBaseObjectClass() {
        rewriteRun(
          spec -> spec.recipe(new MissingOverrideAnnotation(null)),
          //language=java
          java(
            """
              package com.example;
              
              class Test {
                  public String toString() {
                      return super.toString();
                  }
              }
              """,
            """
              package com.example;
              
              class Test {
                  @Override
                  public String toString() {
                      return super.toString();
                  }
              }
              """
          )
        );
    }
}
