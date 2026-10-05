/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindThreadGroupUsagesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindThreadGroupUsages());
    }

    @DocumentExample
    @Test
    void flagsThreadGroupConstruction() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void go() {
                      ThreadGroup group = new ThreadGroup("workers");
                      Thread t = new Thread(group, () -> {});
                      t.start();
                  }
              }
              """,
            """
              class A {
                  void go() {
                      ThreadGroup group = /*~~(`ThreadGroup` is superseded by `java.util.concurrent.ExecutorService`.)~~>*/new ThreadGroup("workers");
                      Thread t = new Thread(group, () -> {});
                      t.start();
                  }
              }
              """
          )
        );
    }

    @Test
    void flagsThreadGetThreadGroupCall() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  ThreadGroup current() {
                      return Thread.currentThread().getThreadGroup();
                  }
              }
              """,
            """
              class A {
                  ThreadGroup current() {
                      return /*~~(`Thread.getThreadGroup()` exposes the discouraged `ThreadGroup` API.)~~>*/Thread.currentThread().getThreadGroup();
                  }
              }
              """
          )
        );
    }

    @Test
    void flagsMethodCallsOnThreadGroupReceiver() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  int count(ThreadGroup group) {
                      return group.activeCount();
                  }
              }
              """,
            """
              class A {
                  int count(ThreadGroup group) {
                      return /*~~(Method call on a `ThreadGroup` receiver; use an `ExecutorService` instead.)~~>*/group.activeCount();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagUnrelatedThreadApi() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void go() {
                      Thread t = new Thread(() -> {});
                      t.setName("worker");
                      t.start();
                      t.interrupt();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagUserClassNamedThreadGroup() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              public class ThreadGroup {
                  public int count() { return 0; }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;

              class A {
                  int use() {
                      ThreadGroup tg = new ThreadGroup();
                      return tg.count();
                  }
              }
              """
          )
        );
    }
}
