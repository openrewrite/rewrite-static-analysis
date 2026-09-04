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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class NullableOnMethodReturnTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NullableOnMethodReturnType());
    }

    @DocumentExample
    @Test
    void nullableOnMethodReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable
                  public String test() {
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public @Nullable String test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedType() {
        rewriteRun(
          //language=java
          java(
            """
              package a;
              public class B {
                  public static class C {}
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              import a.B;
              import org.openrewrite.internal.lang.Nullable;

              public class Foo {
                  @Nullable
                  public B.C bar() {
                      return null;
                  }
              }
              """,
            """
              import a.B;
              import org.openrewrite.internal.lang.Nullable;

              public class Foo {
                  public @Nullable B.C bar() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail("Annotation is placed before the qualified name, producing `@Nullable Map.Entry` which javac rejects")
    @Test
    void moveNullableToNestedTypeSimpleName() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Map;

              class Test<K, V> {
                  @Nullable
                  public Map.Entry<K, V> entry() {
                      return null;
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Map;

              class Test<K, V> {
                  public Map.@Nullable Entry<K, V> entry() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void dontTouchArguments() {
        rewriteRun(
          java(
            //language=java
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  void test(@Nullable String s) {
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForPackagePrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              class Test {
                  @Nullable
                  String test() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveNullableToArrayType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable
                  public String[] test() {
                      return null;
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public String @Nullable[] test() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveNullableToPrimitiveArrayType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable
                  public int[] test() {
                      return null;
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public int @Nullable[] test() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveNullableToMultiDimensionalArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable
                  public String[][] test() {
                      return null;
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public String @Nullable[][] test() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForNullableElements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public @Nullable String[] test() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForDeclarationOnlyAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target(ElementType.METHOD)
              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  @Nullable
                  public String scalar() {
                      return null;
                  }

                  @Nullable
                  public String[] array() {
                      return null;
                  }

                  @Nullable
                  public String[][] multiDimensional() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForAnnotationInModifierPosition() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target(ElementType.METHOD)
              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  public @Nullable String[] afterAllModifiers() {
                      return null;
                  }

                  public @Nullable static String[] betweenModifiers() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target(ElementType.TYPE_USE)
              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  @Nullable
                  public Test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForUnknownAnnotationType() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package example;

              import com.unknown.Nullable;

              class Test {
                  @Nullable
                  public String[] value() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForAnnotationWithoutTarget() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  @Nullable
                  public String[] value() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveTypeUseAnnotationToArrayType() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target(ElementType.TYPE_USE)
              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  @Nullable
                  public String[] value() {
                      return null;
                  }
              }
              """,
            """
              package example;

              class Test {
                  public String @Nullable[] value() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveAnnotationTargetingBothMethodAndTypeUse() {
        rewriteRun(
          //language=java
          java(
            """
              package example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target({ElementType.METHOD, ElementType.TYPE_USE})
              public @interface Nullable {
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              package example;

              class Test {
                  @Nullable
                  public String[] value() {
                      return null;
                  }
              }
              """,
            """
              package example;

              class Test {
                  public String @Nullable[] value() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void declarationOnlyAnnotationDoesNotBlockTypeUseAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  @javax.annotation.Nullable
                  @org.jspecify.annotations.Nullable
                  public String test() {
                      return null;
                  }
              }
              """,
            """
              class Test {
                  @javax.annotation.Nullable
                  public @org.jspecify.annotations.Nullable String test() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
