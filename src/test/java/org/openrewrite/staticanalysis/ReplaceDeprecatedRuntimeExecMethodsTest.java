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
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.kotlin.Assertions.kotlin;

class ReplaceDeprecatedRuntimeExecMethodsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDeprecatedRuntimeExecMethods());
    }

    @DocumentExample
    @Test
    void rawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        File dir = new File("/tmp");

                        Process process1 = runtime.exec("  ls  -a\\t-l\\n\\r\\f ");
                        Process process2 = runtime.exec("ls\\t-a  -l", envp);
                        Process process3 = runtime.exec("ls\\f-a\\r-l\\n", envp, dir);
                    }
                }
                """,
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        File dir = new File("/tmp");

                        Process process1 = runtime.exec(new String[]{"ls", "-a", "-l"});
                        Process process2 = runtime.exec(new String[]{"ls", "-a", "-l"}, envp);
                        Process process3 = runtime.exec(new String[]{"ls", "-a", "-l"}, envp, dir);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void escapesLiteralTokensAndLeavesEmptyCommandUnchanged() {
        rewriteRun(
          version(
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("cmd \\"quoted\\" path\\\\name alpha\\013beta");
                        runtime.exec("");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"cmd", "\\"quoted\\"", "path\\\\name", "alpha\\u000Bbeta"});
                        runtime.exec("");
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void preservesDynamicCommandsAcrossAllOverloads() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class B {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String command = "  ls  -a\\t-l\\n\\r\\f ";
                        String[] envp = { "E1=1", "E2=2"};
                        File dir = new File("/tmp");
                        Process process1 = runtime.exec(command);
                        Process process2 = runtime.exec(command, envp);
                        Process process3 = runtime.exec(command, envp, dir);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void preservesMethodInvocationAsInput() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class B {
                    String command() {
                        return "ls -al";
                    }
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        Process process = runtime.exec(command());
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void concatenatedRawStringsAsInput() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        Process process = runtime.exec("ls" + " " + "-a" + " " + "-l");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        Process process = runtime.exec(new String[]{"ls", "-a", "-l"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void preservesConcatenationsWithDynamicParts() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class B {
                    String options = "-a -l";
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        Process process = runtime.exec("ls" + " " + options);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void deprecatedMethod2() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        Process process = runtime.exec("ls -a -l", envp);
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        Process process = runtime.exec(new String[]{"ls", "-a", "-l"}, envp);
                    }
                }
                """
            ), 18)
        );
    }


    @Test
    void doNotChangeIfUsingNewMethods() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        File dir = new File("/tmp");

                        Process process1 = runtime.exec(new String[]{"ls", "-al"});
                        Process process2 = runtime.exec(new String[]{"ls", "-al"}, envp);
                        Process process3 = runtime.exec(new String[]{"ls", "-a", "-l"}, envp, dir);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void doNotChangeIfUnderJava18() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method() throws IOException {
                        Runtime runtime = Runtime.getRuntime();
                        String[] envp = { "E1=1", "E2=2"};
                        File dir = new File("/tmp");

                        Process process1 = runtime.exec("ls -al");
                        Process process2 = runtime.exec("ls -al", envp);
                        Process process3 = runtime.exec("ls -al", envp, dir);
                    }
                }
                """
            ), 17)
        );
    }

    @Test
    void doNotChangeKotlinFiles() {
        rewriteRun(
          version(
            //language=kotlin
            kotlin(
              """
                class A {
                    fun method() {
                        val process = Runtime.getRuntime().exec("ls -a")
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void javadoc() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                /**
                 * @see java.lang.Runtime#exec(String, String[], java.io.File)
                 */
                class B {
                }
                """
           ), 18)
        );
    }

    @Test
    void multipleInvocationsOfSameOverload() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime, String command) throws IOException {
                        runtime.exec("ls -a");
                        runtime.exec("ls -l");
                        runtime.exec(command);
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime, String command) throws IOException {
                        runtime.exec(new String[]{"ls", "-a"});
                        runtime.exec(new String[]{"ls", "-l"});
                        runtime.exec(command);
                    }
                }
                """
            ), 18)
        );
    }
}
