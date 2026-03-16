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

@SuppressWarnings("unused")
class MemberNameCaseInsensitiveDuplicatesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MemberNameCaseInsensitiveDuplicates());
    }

    @DocumentExample
    @Test
    void fieldInChildDiffersFromFieldInParentByCase() {
        rewriteRun(
            //language=java
            java(
                """
                class Parent {
                    int name;
                }
                """
            ),
            //language=java
            java(
                """
                class Child extends Parent {
                    int Name;
                }
                """,
                """
                class Child extends Parent {
                    int /*~~(Rename this member to not match other members differing only by capitalization)~~>*/Name;
                }
                """
            )
        );
    }

    @Test
    void fieldVsFieldInSameClass() {
        rewriteRun(
            //language=java
            java(
                """
                class A {
                    int count;
                    int Count;
                }
                """,
                """
                class A {
                    int /*~~(Rename this member to not match other members differing only by capitalization)~~>*/count;
                    int /*~~(Rename this member to not match other members differing only by capitalization)~~>*/Count;
                }
                """
            )
        );
    }

    @Test
    void methodVsMethodInSameClass() {
        rewriteRun(
            //language=java
            java(
                """
                class A {
                    void doSomething() {}
                    void doSOMETHING() {}
                }
                """,
                """
                class A {
                    void /*~~(Rename this member to not match other members differing only by capitalization)~~>*/doSomething() {}
                    void /*~~(Rename this member to not match other members differing only by capitalization)~~>*/doSOMETHING() {}
                }
                """
            )
        );
    }

    @Test
    void fieldInClassVsMethodInParent() {
        rewriteRun(
            //language=java
            java(
                """
                class Parent {
                    void process() {}
                }
                """
            ),
            //language=java
            java(
                """
                class Child extends Parent {
                    int Process;
                }
                """,
                """
                class Child extends Parent {
                    int /*~~(Rename this member to not match other members differing only by capitalization)~~>*/Process;
                }
                """
            )
        );
    }

    @Test
    void methodInClassVsMethodInInterface() {
        rewriteRun(
            //language=java
            java(
                """
                interface MyInterface {
                    void run();
                }
                """
            ),
            //language=java
            java(
                """
                class A implements MyInterface {
                    public void run() {}
                    void Run() {}
                }
                """,
                """
                class A implements MyInterface {
                    public void run() {}
                    void /*~~(Rename this member to not match other members differing only by capitalization)~~>*/Run() {}
                }
                """
            )
        );
    }

    @Test
    void doNotChangeCompletelyDifferentNames() {
        rewriteRun(
            //language=java
            java(
                """
                class A {
                    int foo;
                    int bar;
                    void baz() {}
                }
                """
            )
        );
    }

    @Test
    void doNotChangeIdenticalNames() {
        rewriteRun(
            //language=java
            java(
                """
                class Parent {
                    int name;
                }
                """
            ),
            //language=java
            java(
                """
                class Child extends Parent {
                    int name;
                }
                """
            )
        );
    }

    @Test
    void doNotChangeOverriddenMethod() {
        rewriteRun(
            //language=java
            java(
                """
                class Parent {
                    void process() {}
                }
                """
            ),
            //language=java
            java(
                """
                class Child extends Parent {
                    @Override
                    void process() {}
                }
                """
            )
        );
    }
}
