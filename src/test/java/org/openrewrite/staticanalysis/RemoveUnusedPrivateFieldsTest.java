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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedPrivateFieldsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedPrivateFields());
    }

    @DocumentExample
    @Test
    void removeUnusedPrivateField() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String notUsed;
              }
              """,
            """
              public class Test {
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveSerialVersionUid() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test implements java.io.Serializable {
                  private static final long serialVersionUID = 42L;
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveAnnotatedField() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  @Deprecated
                  public String annotated;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFieldsOnClassWithNativeMethod() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  public String notUsed;
                  public native void method();
              }
              """
          )
        );
    }

    @Test
    void notPrivateField() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  public String notUsed;
              }
              """
          )
        );
    }

    @Test
    void fieldIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String value;
                  void method() {
                      String useValue = value;
                  }
              }
              """
          )
        );
    }

    @Test
    void usedInClassScope() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String value = "";
                  private String useValue = method(value);
                  String method(String arg0) {
                      return arg0 + useValue;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedPrivateFieldImport() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              public class Test {
                  private List notUsed;
              }
              """,
            """
              public class Test {
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Test
    void nameIsShadowed() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String value;
                  void method() {
                      String value = "name shadow";
                      String shadowedUse = value;
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      String value = "name shadow";
                      String shadowedUse = value;
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyRemoveUnusedNamedVariable() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String aOne, aTwo, aThree;
                  private String bOne, bTwo, bThree;
                  private String cOne, cTwo, cThree;
                  void method() {
                      String removeAOne = aTwo + aThree;
                      String removeBTwo = bOne + bThree;
                      String removeCThree = cOne + cTwo;
                  }
              }
              """,
            """
              public class Test {
                  private String aTwo, aThree;
                  private String bOne, bThree;
                  private String cOne, cTwo;
                  void method() {
                      String removeAOne = aTwo + aThree;
                      String removeBTwo = bOne + bThree;
                      String removeCThree = cOne + cTwo;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3061")
    @Test
    void findReferencesInOuterScope() {
        rewriteRun(
          //language=java
          java(
            """
              public class Vehicle {
                  private VehicleUsage vehicleUsage;

                  public class VehicleUsage {
                      private final String vehicleId;
                  }

                  public void doSomethingWithAVehicle() {
                      vehicleUsage = new VehicleUsage();
                      vehicleUsage.vehicleId = "vu50";
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCommentsPrefix() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                 // Some comment
                 private int a;
              }
              """,
                """
              public class Test {
              }
              """
          )
        );
    }

    @Test
    void removeCommentsLastExpression() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private int a; // Some comment
              }
              """,
                """
              public class Test {
              }
              """
          )
        );
    }

    @Test
    void removeCommentsSameLine() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private int a;
                  private int b; // Some comment

                  public void test() {
                      a = 42;
                  }
              }
              """,
                """
              public class Test {
                  private int a;

                  public void test() {
                      a = 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCommentsMultiLine() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private int a;
                  private int b; /*
                    Some
                    multiline
                    comment
                  */

                  public void test() {
                      a = 42;
                  }
              }
              """,
                """
              public class Test {
                  private int a;

                  public void test() {
                      a = 42;
                  }
              }
              """
          )
        );

    }

    @Test
    void doNotRemoveCommentsIfNewline() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private int a;
                  private int b;
                  // Some comment

                  public void test() {
                      a = 42;
                  }
              }
              """,
                """
              public class Test {
                  private int a;
                  // Some comment

                  public void test() {
                      a = 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveCommentsIfNotRemovedWholeVariableDeclarations() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private int a, b; // Some comment

                  public void test() {
                      a = 42;
                  }
              }
              """,
                """
              public class Test {
                  private int a; // Some comment

                  public void test() {
                      a = 42;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/242")
    @ParameterizedTest
    @ValueSource(strings = {
      "@lombok.Data",
      "@lombok.Value",
      "@lombok.Getter",
      "@lombok.Setter",
    })
    void doNotRemoveFieldsIfLombokDataAnnotationIsPresent(String annotation) {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              %s
              class A {
                  private int a = 1;
              }
              """.formatted(annotation)
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/242")
    @Test
    void doRemoveFieldsIfLombokLoggingAnnotationIsPresent() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              @lombok.extern.slf4j.Slf4j
              class A {
                  private int a = 1;
              }
              """,
            """
              @lombok.extern.slf4j.Slf4j
              class A {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/524")
    @Test
    void removeUntilStable() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String a = "a";
                  private String ab = a + "b";
                  private String abc = ab + "c";
              }
              """,
            """
              public class Test {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/524")
    @Test
    void doNotRemoveWhenThereAreMissingTypes() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().identifiers(false)),
          //language=java
          java(
            """
              import can.not.be.Found;
              public class Test {
                  private Found notUsed;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/321")
    @Test
    void doNotRemoveFieldAfterTypeChange() {
        rewriteRun(
          spec -> spec.recipes(
            new UseCollectionInterfaces(),
            new RemoveUnusedPrivateFields()
          ),
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.HashSet;

              public class Main {
                  private static final HashSet<String> allowedMethods = new HashSet<>(Arrays.asList(
                      "GET", "HEAD", "TRACE", "OPTIONS"));

                  public boolean matches(String method) {
                      if (allowedMethods.contains(method)) {
                          return false;
                      }
                      return true;
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.HashSet;
              import java.util.Set;

              public class Main {
                  private static final Set<String> allowedMethods = new HashSet<>(Arrays.asList(
                      "GET", "HEAD", "TRACE", "OPTIONS"));

                  public boolean matches(String method) {
                      if (allowedMethods.contains(method)) {
                          return false;
                      }
                      return true;
                  }
              }
              """
          )
        );
    }
}
