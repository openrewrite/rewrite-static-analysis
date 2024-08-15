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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("deprecation")
public class MoveFieldAnnotationToTypeTest implements RewriteTest {

    @Test
    void alreadyOnInnerClass() {
        rewriteRun(
          spec -> spec.recipe(new MoveFieldAnnotationToType("*..*")),
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
          spec -> spec.recipe(new MoveFieldAnnotationToType("org.openrewrite..*")),
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
    void methodAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new MoveFieldAnnotationToType(null)),
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
}
