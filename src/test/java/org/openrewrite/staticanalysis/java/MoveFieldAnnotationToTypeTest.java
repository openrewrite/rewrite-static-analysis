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
package org.openrewrite.staticanalysis.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("deprecation")
class MoveFieldAnnotationToTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveFieldAnnotationToType("org.openrewrite..*"));
    }

    @DocumentExample
    @Test
    void fieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  @Nullable Xml.Tag tag;
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  Xml.@Nullable Tag tag;
              }
              """
          )
        );
    }

    @Test
    void alreadyOnInnerClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  Xml.@Nullable Tag tag;
              }
              """
          )
        );
    }

    @Test
    void fullyQualifiedFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable java.util.List<String> l;
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  java.util.@Nullable List<String> l;
              }
              """
          )
        );
    }

    @Test
    void arrayFieldAnnotationUnchanged() {
        // As per: https://jspecify.dev/docs/user-guide/#type-use-annotation-syntax
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class ArrayOfNullableElements {
                  @Nullable String[] l;
              }
              """
          ),
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class ArrayItselfNullable {
                  String @Nullable[] l;
              }
              """
          ),
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class NullableArrayOfNullableElements {
                  @Nullable String @Nullable[] l;
              }
              """
          )
        );
    }

    @Test
    void parameterizedFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import java.util.List;
              class Test {
                  @Nullable List<String> l;
              }
              """
          )
        );
    }

    @Test
    void publicFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  public @Nullable Xml.Tag tag;
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  public Xml.@Nullable Tag tag;
              }
              """
          )
        );
    }

    @Test
    void methodAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              interface Test {
                  @Nullable Xml.Tag tag();
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              interface Test {
                  Xml.@Nullable Tag tag();
              }
              """
          )
        );
    }

    @Test
    void publicMethodAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              interface Test {
                  synchronized @Nullable Xml.Tag tag();
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              interface Test {
                  synchronized Xml.@Nullable Tag tag();
              }
              """
          )
        );
    }

    @Test
    void fullyDefinedAnnotationInMethodDeclaration() {
        rewriteRun(
          java(
            """
              package org.openrewrite;

              public class Test {
                 public void someFunction(@org.openrewrite.internal.lang.Nullable org.openrewrite.internal.MetricsHelper metrics) {
                 }
              }
              """,
            """
              package org.openrewrite;

              import org.openrewrite.internal.lang.Nullable;

              public class Test {
                 public void someFunction(org.openrewrite.internal.@Nullable MetricsHelper metrics) {
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

                  public B.@Nullable C bar() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
