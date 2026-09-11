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

class FindWaitWithMultipleLocksHeldTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindWaitWithMultipleLocksHeld());
    }

    @DocumentExample
    @Test
    void findsWaitInsideTwoNestedSynchronizedBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object outer = new Object();
                  private final Object inner = new Object();

                  void await() throws InterruptedException {
                      synchronized (outer) {
                          synchronized (inner) {
                              inner.wait();
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  private final Object outer = new Object();
                  private final Object inner = new Object();

                  void await() throws InterruptedException {
                      synchronized (outer) {
                          synchronized (inner) {
                              /*~~(`wait()` called while holding multiple monitors. Only the receiver's monitor is released; other held monitors continue to block their waiters and can deadlock.)~~>*/inner.wait();
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void findsWaitInSynchronizedMethodWithNestedSynchronizedBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object other = new Object();

                  synchronized void await() throws InterruptedException {
                      synchronized (other) {
                          other.wait();
                      }
                  }
              }
              """,
            """
              class A {
                  private final Object other = new Object();

                  synchronized void await() throws InterruptedException {
                      synchronized (other) {
                          /*~~(`wait()` called while holding multiple monitors. Only the receiver's monitor is released; other held monitors continue to block their waiters and can deadlock.)~~>*/other.wait();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void findsBareWaitInsideTwoLocks() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object other = new Object();

                  synchronized void await() throws InterruptedException {
                      synchronized (other) {
                          wait();
                      }
                  }
              }
              """,
            """
              class A {
                  private final Object other = new Object();

                  synchronized void await() throws InterruptedException {
                      synchronized (other) {
                          /*~~(`wait()` called while holding multiple monitors. Only the receiver's monitor is released; other held monitors continue to block their waiters and can deadlock.)~~>*/wait();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowsWaitWithSingleMonitor() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object lock = new Object();

                  void await() throws InterruptedException {
                      synchronized (lock) {
                          lock.wait();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowsWaitInSynchronizedMethodOnly() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  synchronized void await() throws InterruptedException {
                      wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void allowsTimedWaitWithMultipleLocks() {
        // Timed waits are explicitly out of scope for S3046 — they self-release, so the failure
        // mode is less severe. Sonar-java's `TwoLocksWaitCheck` matches only `wait()` (0-arg).
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object outer = new Object();
                  private final Object inner = new Object();

                  void await() throws InterruptedException {
                      synchronized (outer) {
                          synchronized (inner) {
                              inner.wait(1000);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowsWaitInLambdaEscapingOuterLocks() {
        // Lambda bodies execute deferred, outside the outer sync frames — do not flag.
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object outer = new Object();
                  private final Object inner = new Object();

                  Runnable make() {
                      synchronized (outer) {
                          synchronized (inner) {
                              return () -> {
                                  try {
                                      inner.wait();
                                  } catch (InterruptedException e) {
                                      Thread.currentThread().interrupt();
                                  }
                              };
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowsWaitInAnonymousInnerClassNonSynchronizedMethod() {
        // Anonymous-class methods reset the lock context — its `run()` doesn't inherit the outer
        // sync frames.
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object outer = new Object();
                  private final Object inner = new Object();

                  Runnable make() {
                      synchronized (outer) {
                          synchronized (inner) {
                              return new Runnable() {
                                  @Override
                                  public void run() {
                                      try {
                                          inner.wait();
                                      } catch (InterruptedException e) {
                                          Thread.currentThread().interrupt();
                                      }
                                  }
                              };
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void findsWaitInsideTripleNesting() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final Object a = new Object();
                  private final Object b = new Object();
                  private final Object c = new Object();

                  void await() throws InterruptedException {
                      synchronized (a) {
                          synchronized (b) {
                              synchronized (c) {
                                  c.wait();
                              }
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  private final Object a = new Object();
                  private final Object b = new Object();
                  private final Object c = new Object();

                  void await() throws InterruptedException {
                      synchronized (a) {
                          synchronized (b) {
                              synchronized (c) {
                                  /*~~(`wait()` called while holding multiple monitors. Only the receiver's monitor is released; other held monitors continue to block their waiters and can deadlock.)~~>*/c.wait();
                              }
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
