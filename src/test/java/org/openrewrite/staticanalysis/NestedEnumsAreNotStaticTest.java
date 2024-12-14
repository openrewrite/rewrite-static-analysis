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

@SuppressWarnings("UnnecessaryModifier")
class NestedEnumsAreNotStaticTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NestedEnumsAreNotStatic());
    }

    @Test
    void enumIsNotNested() {
        rewriteRun(
          //language=java
          java(
            """
              static enum ABC {
                  A, B, C
              }
              """
          )
        );
    }

    @Test
    void nestedEnumIsNotStatic() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  enum ABC {
                      A, B, C
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void nestedEnumIsStatic() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
              
                  static enum ABC {
                      A, B, C
                  }
              
                  private static enum DEF {
                      D, E, F
                  }
              }
              """,
            """
              class A {
              
                  enum ABC {
                      A, B, C
                  }
              
                  private enum DEF {
                      D, E, F
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1222")
    @Test
    void doesNotReformatWholeEnum() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  public static enum testEnum {
                      Account;
              
                      public final String field;
              
                      private testEnum() {this.field = this.name();}
                  }
              }
              """,
            """
              public class Test {
                  public enum testEnum {
                      Account;
              
                      public final String field;
              
                      private testEnum() {this.field = this.name();}
                  }
              }
              """
          )
        );
    }
}
