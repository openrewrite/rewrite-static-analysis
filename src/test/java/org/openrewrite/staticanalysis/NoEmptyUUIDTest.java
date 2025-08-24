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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoEmptyUUIDTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoEmptyUUID())
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-params"));
    }

    @Test
    void uuidIsBlankInIfCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      if (id.toString().isBlank()) {
                          System.out.println("This will never happen");
                      }
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void uuidToStringIsBlankAlwaysFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      boolean b = id.toString().isBlank(); // Always false
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      boolean b = false; // Always false
                  }
              }
              """
          )
        );
    }

    @Test
    void nonsensicalIfConditionWithUUID() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      if (id.toString() != null && id.toString().isEmpty()) { // Always false
                          System.out.println("Dead code");
                      }
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      if (id.toString() != null && false) { // Always false
                          System.out.println("Dead code");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void complexNonsensicalCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      if ((id.toString().length() > 0) && id.toString().isEmpty()) { // Always false
                          System.out.println("Impossible condition");
                      }
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      if ((id.toString().length() > 0) && false) { // Always false
                          System.out.println("Impossible condition");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void whileLoopWithImpossibleCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      while (id.toString().isEmpty()) { // Always false, infinite loop won't execute
                          System.out.println("This will never run");
                      }
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      while (false) { // Always false, infinite loop won't execute
                          System.out.println("This will never run");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doWhileWithImpossibleCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      do {
                          System.out.println("This runs once but condition is always false");
                      } while (id.toString().isEmpty()); // Always false
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      do {
                          System.out.println("This runs once but condition is always false");
                      } while (false); // Always false
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsBlankWithTrimChaining() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      boolean b = UUID.randomUUID().toString().trim().isBlank(); // Always false
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      boolean b = false; // Always false
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsEmptyAfterSubstringOperation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      boolean b = id.toString().substring(0, 5).isEmpty(); // Always false (substring of UUID won't be empty)
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      boolean b = false; // Always false (substring of UUID won't be empty)
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsEmptyInMethodChain() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      processUUID(UUID.randomUUID().toString().isEmpty()); // Always false
                  }
                  private void processUUID(boolean isEmpty) {}
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      processUUID(false); // Always false
                  }
                  private void processUUID(boolean isEmpty) {}
              }
              """
          )
        );
    }

    @Test
    void uuidIsBlankInReturnStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public boolean test() {
                      return UUID.randomUUID().toString().isBlank(); // Always returns false
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public boolean test() {
                      return false; // Always returns false
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsEmptyInStreamOperation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              import java.util.List;
              public class Test {
                  public void test() {
                      List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
                      boolean anyEmpty = uuids.stream()
                          .anyMatch(uuid -> uuid.toString().isEmpty()); // Always false
                  }
              }
              """,
            """
              import java.util.UUID;
              import java.util.List;
              public class Test {
                  public void test() {
                      List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
                      boolean anyEmpty = uuids.stream()
                          .anyMatch(uuid -> false); // Always false
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsBlankInStreamFilter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              import java.util.List;
              public class Test {
                  public void test() {
                      List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
                      List<UUID> emptyOnes = uuids.stream()
                          .filter(uuid -> uuid.toString().isBlank()) // Always empty list
                          .toList();
                  }
              }
              """,
            """
              import java.util.UUID;
              import java.util.List;
              public class Test {
                  public void test() {
                      List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
                      List<UUID> emptyOnes = uuids.stream()
                          .filter(uuid -> false) // Always empty list
                          .toList();
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsEmptyInSwitchCaseExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test(int value) {
                      switch (value) {
                          case 1 -> System.out.println(UUID.randomUUID().toString().isEmpty()); // Always false
                          default -> System.out.println("default");
                      }
                  }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test(int value) {
                      switch (value) {
                          case 1 -> System.out.println(false); // Always false
                          default -> System.out.println("default");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void uuidIsBlankInTernaryWithMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      String result = id.toString().isBlank() ? getEmptyMessage() : getNonEmptyMessage(); // Always calls getNonEmptyMessage()
                  }
                  private String getEmptyMessage() { return "empty"; }
                  private String getNonEmptyMessage() { return "not empty"; }
              }
              """,
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      UUID id = UUID.randomUUID();
                      String result = false ? getEmptyMessage() : getNonEmptyMessage(); // Always calls getNonEmptyMessage()
                  }
                  private String getEmptyMessage() { return "empty"; }
                  private String getNonEmptyMessage() { return "not empty"; }
              }
              """
          )
        );
    }

    @Test
    void skipValidStringIsEmptyChecks() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      String potentiallyEmpty = getStringFromSomewhere();
                      boolean validCheck = potentiallyEmpty.isEmpty(); // OK - not a UUID

                      UUID id = UUID.randomUUID();
                      String uuidString = id.toString();
                      boolean alsoValid = uuidString.isEmpty(); // OK - variable is String type
                  }
                  private String getStringFromSomewhere() { return ""; }
              }
              """
          )
        );
    }

    @Test
    void skipValidStringIsBlankChecks() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.UUID;
              public class Test {
                  public void test() {
                      String potentiallyBlank = getStringFromSomewhere();
                      boolean validCheck = potentiallyBlank.isBlank(); // OK - not a UUID
                  }
                  private String getStringFromSomewhere() { return "   "; }
              }
              """
          )
        );
    }
}
