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

class RemoveJavaDocAuthorTagTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveJavaDocAuthorTag());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1640")
    @Test
    @DocumentExample
    void preserveDocsBeforeTag() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * Java doc text.
               *
               * @author fname.lname, fname.lname
               */
              class Test {}
              """,
            """
              /**
               * Java doc text.
               *
               */
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1640")
    @Test
    void tagOnFirstLine() {
        rewriteRun(
          //language=java
          java(
            """
              /** @author foo.bar
               */
              class Test {}
              """,
            """
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/119")
    @Test
    void tagOnSecondLine() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * @author foo.bar
               */
              class Test {}
              """,
            """
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/119")
    @Test
    void tagOnSecondLineSourroundedByEmptyLines() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               *
               * @author foo.bar
               *
               */
              class Test {}
              """,
            """
              class Test {}
              """
          )
        );
    }
}
