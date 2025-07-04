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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AtomicPrimitiveEqualsUsesGetTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AtomicPrimitiveEqualsUsesGet());
    }

    @Test
    @DocumentExample
    void usesEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicLong;
              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean areEqual(AtomicInteger i1, AtomicInteger i2) {
                      return i1.equals(i2);
                  }
                  boolean areEqual(AtomicLong l1, AtomicLong l2) {
                      return l1.equals(l2);
                  }
                  boolean areEqual(AtomicBoolean b1, AtomicBoolean b2) {
                      return b1.equals(b2);
                  }
              }
              """,
            """
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicLong;
              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean areEqual(AtomicInteger i1, AtomicInteger i2) {
                      return i1.get() == i2.get();
                  }
                  boolean areEqual(AtomicLong l1, AtomicLong l2) {
                      return l1.get() == l2.get();
                  }
                  boolean areEqual(AtomicBoolean b1, AtomicBoolean b2) {
                      return b1.get() == b2.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void usesGet() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicLong;
              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean areEqual(AtomicInteger a1, AtomicInteger a2) {
                      return a1.get() == a2.get();
                  }
                  boolean areEqual(AtomicLong a1, AtomicLong a2) {
                      return a1.get() == a2.get();
                  }
                  boolean areEqual(AtomicBoolean a1, AtomicBoolean a2) {
                      return a1.get() == a2.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void equalsArgNotAtomicType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.atomic.AtomicInteger;
              import java.util.concurrent.atomic.AtomicLong;
              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean areEqual(Integer a1, Integer a2) {
                      return a1.equals(a2);
                  }
              }
              """
          )
        );
    }

    @Test
    void typeExtendsAtomic() {
        rewriteRun(
          //language=java
          java(
            """
            package abc;
            import java.util.concurrent.atomic.AtomicLong;

            public class AtomicLongWithEquals extends AtomicLong {
                public AtomicLongWithEquals(long i) {
                    super(i);
                }
            }
            """
          ),
          //language=java
          java(
            """
              package abc;

              class A {
                  boolean doSomething(AtomicLongWithEquals i1, AtomicLongWithEquals i2) {
                      return i1.equals(i2);
                  }
              }
              """
          )
        );
    }
}
