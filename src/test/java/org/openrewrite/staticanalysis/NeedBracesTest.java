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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.NeedBracesStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({
  "InfiniteLoopStatement",
  "IfStatementWithIdenticalBranches",
  "LoopStatementThatDoesntLoop",
  "StatementWithEmptyBody",
  "UnusedAssignment",
  "ConstantConditions",
  "ClassInitializerMayBeStatic",
  "UnnecessaryReturnStatement",
  "DuplicateCondition"})
class NeedBracesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NeedBraces());
    }

    private static Consumer<RecipeSpec> needsBraces(UnaryOperator<NeedBracesStyle> with) {
        return spec -> spec.parser(JavaParser.fromJavaVersion().styles(
          singletonList(
            new NamedStyles(
              Tree.randomId(), "test", "test", "test", emptySet(),
              singletonList(with.apply(Checkstyle.needBracesStyle())))))
        );
    }

    @DocumentExample
    @Test
    void addBraces() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void addToWhile() {
                      while (true) ;
                  }
              
                  static void addToWhileWithBody() {
                      while (true) return;
                  }
              
                  static void addToIf(int n) {
                      if (n == 1) return;
                      // foo
                  }
              
                  static void addToIfElse(int n) {
                      if (n == 1) return;
                      else return;
                  }
              
                  static void addToIfElseIfElse(int n) {
                      if (n == 1) return;
                      else if (n == 2) return;
                      else return;
                  }
              
                  static void addToDoWhile(Object obj) {
                      do obj.notify(); while (true);
                  }
              
                  static void addToIterativeFor(Object obj) {
                      for (int i = 0; ; ) obj.notify();
                  }
              }
              """,
            """
              class Test {
                  static void addToWhile() {
                      while (true) {
                      }
                  }
              
                  static void addToWhileWithBody() {
                      while (true) {
                          return;
                      }
                  }
              
                  static void addToIf(int n) {
                      if (n == 1) {
                          return;
                      }
                      // foo
                  }
              
                  static void addToIfElse(int n) {
                      if (n == 1) {
                          return;
                      } else {
                          return;
                      }
                  }
              
                  static void addToIfElseIfElse(int n) {
                      if (n == 1) {
                          return;
                      } else if (n == 2) {
                          return;
                      } else {
                          return;
                      }
                  }
              
                  static void addToDoWhile(Object obj) {
                      do {
                          obj.notify();
                      } while (true);
                  }
              
                  static void addToIterativeFor(Object obj) {
                      for (int i = 0; ; ) {
                          obj.notify();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowEmptyLoopBody() {
        rewriteRun(
          needsBraces(style -> style.withAllowEmptyLoopBody(true)),
          //language=java
          java(
            """
              class Test {
                  static void emptyWhile() {
                      while (true) ;
                  }
              
                  static void emptyForIterative() {
                      for (int i = 0; i < 10; i++) ;
                  }
              }
              """
          )
        );
    }

    @Test
    void allowSingleLineStatement() {
        rewriteRun(
          needsBraces(style -> style.withAllowSingleLineStatement(true)),
          //language=java
          java(
            """
              class Test {
                  static void allowIf(int n) {
                      if (n == 1) return;
                  }
              
                  static void allowIfElse(int n) {
                      if (n == 1) return;
                      else return;
                  }
              
                  static void allowIfElseIfElse(int n) {
                      if (n == 1) return;
                      else if (n == 2) return;
                      else return;
                  }
              
                  static void allowWhileWithBody() {
                      while (true) return;
                  }
              
                  static void allowDoWhileWithBody(Object obj) {
                      do obj.notify(); while (true);
                  }
              
                  static void allowForIterativeWithBody(Object obj) {
                      for (int i = 0; ; ) obj.notify();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAllowLoopsWithEmptyBodyWhenSingleLineStatementAreAllowed() {
        rewriteRun(
          needsBraces(style -> style.withAllowSingleLineStatement(true)),
          //language=java
          java(
            """
              class Test {
                  static void doNotAllowWhileWithEmptyBody() {
                      while (true) ;
                  }
              
                  static void doNotAllowDoWhileWithEmptyBody(Object obj) {
                      do ; while (true);
                  }
              
                  static void doNotAllowForIterativeWithEmptyBody(Object obj) {
                      for (int i = 0; ; ) ;
                  }
              }
              """,
            """
              class Test {
                  static void doNotAllowWhileWithEmptyBody() {
                      while (true) {
                      }
                  }
              
                  static void doNotAllowDoWhileWithEmptyBody(Object obj) {
                      do {
                      } while (true);
                  }
              
                  static void doNotAllowForIterativeWithEmptyBody(Object obj) {
                      for (int i = 0; ; ) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowSingleLineStatementInSwitch() {
        rewriteRun(
          needsBraces(style -> style.withAllowSingleLineStatement(true)),
          //language=java
          java(
            """
              class Test {
                  {
                      int counter = 0;
                      int n = 1;
                      switch (n) {
                        case 1: counter++; break;
                        case 6: counter += 10; break;
                        default: counter = 100; break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void initializeStyleWhenOtherwiseNotProvided() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      if (true) {
                          return;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void trailingComment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      if (true) return; // comment
                      if (true) return; // comment 2
                      return;
                  }
                  static void commentWhile(Object obj) {
                      while (true); // while comment
                      while (true) return; // while comment with body
                      do obj.notify(); while (true); // do while comment
                  }
                  static void commentIterative(Object obj) {
                      for (int i = 0; ; ); // iterative comment
                      for (int i = 0; ; ) obj.notify(); // iterative with body comment
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      if (true) {
                          return; // comment
                      }
                      if (true) {
                          return; // comment 2
                      }
                      return;
                  }
                  static void commentWhile(Object obj) {
                      while (true) { // while comment
                      }
                      while (true) {
                          return; // while comment with body
                      }
                      do {
                          obj.notify(); // do while comment
                      } while (true);
                  }
                  static void commentIterative(Object obj) {
                      for (int i = 0; ; ) { // iterative comment
                      }
                      for (int i = 0; ; ) {
                          obj.notify(); // iterative with body comment
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/315")
    @Test
    void commentsBeforeElseBlockWithNoBraces() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      if (true) {
                          return;
                      }
                      /*
                       * comment should be in else block
                       */
                      else return;
                      if(true) {
                          return;
                      } // comment on if
                      else return;
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      if (true) {
                          return;
                      } else {
                          /*
                           * comment should be in else block
                           */
                          return;
                      }
                      if(true) {
                          return;
                      } // comment on if
                      else {
                          return;
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/315")
    @Test
    void commentsBeforeElseBlockWithBraces() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      if (true)
                          return;
                      // if comment
                      else{
                          return;
                      }
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      if (true) {
                          return;
                          // if comment
                      } else {
                          return;
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/315")
    @Test
    void trailingCommentsElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      if (true) return; // if comment
                      else return; // else comment
                      if (true) return; // if comment 2
                      else if (true) return; // else if comment 2
                      else if (true) return; // second else if comment 2
                      else return; // else comment 2
                  }
                  static void methodTwo(){
                      if (true) return; // if comment
                      else return; // else comment
                      return; // return comment
                  }
                  static void methodThreeNested(){
                      if (true){
                          if (true) return; // nested if comment
                          else return; // nested else comment
                      }
                      else return; // else comment
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      if (true) {
                          return; // if comment
                      } else {
                          return; // else comment
                      }
                      if (true) {
                          return; // if comment 2
                      } else if (true) {
                          return; // else if comment 2
                      } else if (true) {
                          return; // second else if comment 2
                      } else {
                          return; // else comment 2
                      }
                  }
                  static void methodTwo(){
                      if (true) {
                          return; // if comment
                      } else {
                          return; // else comment
                      }
                      return; // return comment
                  }
                  static void methodThreeNested(){
                      if (true) {
                          if (true) {
                              return; // nested if comment
                          } else {
                              return; // nested else comment
                          }
                      } else {
                          return; // else comment
                      }
                  }
              }
              """
          )
        );
    }
}
