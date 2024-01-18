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
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedPrivateFieldsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedPrivateFields());
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3061")
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
              
                  public doSomethingWithAVehicle() {
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
              """, """
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
              """, """
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
              """, """
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
              """, """
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
              """, """
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
              """, """
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
		@Test
		void doNotRemoveFieldsIfLombokDataAnnotationIsPresent() {
				rewriteRun(
						spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
						//language=java
						java(
								"""
									import lombok.Data;
									@Data
									class Test {
											private int a = 1;
									}
									"""
						)
				);
		}
}

