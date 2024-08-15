/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.staticanalysis.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("deprecation")
public class MoveFieldAnnotationToTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveFieldAnnotationToType("org.openrewrite..*"));
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
    void fullyQualifiedFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  @Nullable java.util.List<String> l;
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  java.util.@Nullable List<String> l;
              }
              """
          )
        );
    }

    @Test
    void arrayFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  @Nullable String[] l;
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              import org.openrewrite.xml.tree.Xml;
              class Test {
                  String @Nullable[] l;
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
}
