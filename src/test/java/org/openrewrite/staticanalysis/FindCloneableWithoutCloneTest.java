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

class FindCloneableWithoutCloneTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCloneableWithoutClone());
    }

    @DocumentExample
    @Test
    void cloneableWithoutClone() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo implements Cloneable {
                  public int value;
              }
              """,
            """
              class /*~~(`Cloneable` is a marker interface that promises a working `clone()`, but this class inherits `Object.clone()`, which copies fields one by one. Override `clone()` and call `super.clone()` from it.)~~>*/Foo implements Cloneable {
                  public int value;
              }
              """
          )
        );
    }

    @Test
    void cloneableWithACovariantClone() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo implements Cloneable {
                  public int value;

                  @Override
                  public Foo clone() {
                      try {
                          return (Foo) super.clone();
                      } catch (CloneNotSupportedException e) {
                          throw new AssertionError();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void cloneableWithAnObjectReturningClone() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo implements Cloneable {
                  @Override
                  protected Object clone() throws CloneNotSupportedException {
                      return super.clone();
                  }
              }
              """
          )
        );
    }

    @Test
    void cloneableInheritedFromASupertype() {
        rewriteRun(
          //language=java
          java(
            """
              class Base implements Cloneable {
                  @Override
                  public Base clone() {
                      try {
                          return (Base) super.clone();
                      } catch (CloneNotSupportedException e) {
                          throw new AssertionError();
                      }
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Foo extends Base {
                  public int value;
              }
              """
          )
        );
    }

    @Test
    void notCloneable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;

              class Foo implements Serializable {
                  public int value;
              }
              """
          )
        );
    }

    @Test
    void anInterfaceExtendingCloneableIsNotAClass() {
        rewriteRun(
          //language=java
          java(
            """
              interface Copyable extends Cloneable {
              }
              """
          )
        );
    }

    @Test
    void cloneableThroughAnInterface() {
        rewriteRun(
          //language=java
          java(
            """
              interface Copyable extends Cloneable {
              }
              """
          ),
          //language=java
          java(
            """
              class Foo implements Copyable {
                  public int value;
              }
              """,
            """
              class /*~~(`Cloneable` is a marker interface that promises a working `clone()`, but this class inherits `Object.clone()`, which copies fields one by one. Override `clone()` and call `super.clone()` from it.)~~>*/Foo implements Copyable {
                  public int value;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAnAncestorDeclaresCloneAndCloneableIsRestated() {
        rewriteRun(
          //language=java
          java(
            """
              class Base {
                  @Override
                  public Base clone() {
                      try {
                          return (Base) super.clone();
                      } catch (CloneNotSupportedException e) {
                          throw new AssertionError();
                      }
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Foo extends Base implements Cloneable {
                  public int value;
              }
              """
          )
        );
    }
}
