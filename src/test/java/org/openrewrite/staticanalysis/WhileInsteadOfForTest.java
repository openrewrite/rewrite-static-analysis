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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.Semicolon;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Paths;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.javascript;

class WhileInsteadOfForTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WhileInsteadOfFor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.js", "example.ts", "example.java"})
    void retainJavaScriptLoopAndNativeSemicolon(String path) {
        // Construct the shared loop nodes without requiring an external JavaScript RPC install.
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { int f(int i) { for(;i<1;)i++;return i; } }")
          .findFirst().orElseThrow();
        J.MethodDeclaration method = (J.MethodDeclaration) javaSource.getClasses().get(0).getBody().getStatements().get(0);
        J.ForLoop loop = (J.ForLoop) method.getBody().getStatements().get(0);
        loop = loop.withPrefix(Space.EMPTY);
        loop = loop.getPadding().withBody(loop.getPadding().getBody()
          .withMarkers(Markers.EMPTY.add(new Semicolon(randomId()))));
        JS.CompilationUnit source = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get(path), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build((Statement) loop)), Space.EMPTY);
        assertThat(source.printAll()).isEqualTo("for(;i<1;)i++;");
        assertThat(new WhileInsteadOfFor().getVisitor().visit(source, ctx)).isSameAs(source);
        JS.CompilationUnit marked = SearchResult.found(source);
        assertThat(new WhileInsteadOfFor().getVisitor().visit(marked, ctx)).isSameAs(marked);
    }

    @Test
    void retainMinifiedJavaScriptLoopBeforeReturn() {
        rewriteRun(javascript("function f(){var i=0;for(;i<1;)i++;return i}"));
    }

    @DocumentExample
    @SuppressWarnings("ALL")
    @Test
    void replaceWithWhile() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      for (; 1 == 2 ;) {
                          System.out.println("i'm going to say hi a lot");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      while (1 == 2) {
                          System.out.println("i'm going to say hi a lot");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForEmptyControlCondition() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  int m() {
                      for (;;) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithWhileInLanguagesThatHaveWhile() {
        rewriteRun(
          groovy(
            """
              def test() {
                  for (; 1 == 2 ;) {
                      println("i'm going to say hi a lot")
                  }
              }
              """,
            """
              def test() {
                  while (1 == 2) {
                      println("i'm going to say hi a lot")
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeGo() {
        assumeTrue(GoEngineTestListener.isAvailable(), "rewrite-go-rpc engine unavailable");
        rewriteRun(
          go(
            """
              package main

              func test() {
                  for ; 1 == 2; {
                      println("i'm going to say hi a lot")
                  }
              }
              """
          )
        );
    }
}
