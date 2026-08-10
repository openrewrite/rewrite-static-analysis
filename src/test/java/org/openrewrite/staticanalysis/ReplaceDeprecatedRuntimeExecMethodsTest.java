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

                        Process process1 = runtime.exec("ls -a -l");
                        Process process2 = runtime.exec("ls -a -l", envp);
                        Process process3 = runtime.exec("ls -a -l", envp, dir);
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
    void repeatedDelimitersInRawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method(Runtime runtime, String[] envp, File dir) throws IOException {
                        runtime.exec("printf  '%s'  value");
                        runtime.exec("printf  '%s'  value", envp);
                        runtime.exec("printf  '%s'  value", envp, dir);
                    }
                }
                """,
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    void method(Runtime runtime, String[] envp, File dir) throws IOException {
                        runtime.exec(new String[]{"printf", "'%s'", "value"});
                        runtime.exec(new String[]{"printf", "'%s'", "value"}, envp);
                        runtime.exec(new String[]{"printf", "'%s'", "value"}, envp, dir);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void allTokenizerDelimitersInRawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(" \\tls\\n-a \\r\\r -l\\f-h ");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"ls", "-a", "-l", "-h"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void quotesAndBackslashesInRawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("echo \\"a b\\" C:\\\\dir");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"echo", "\\"a", "b\\"", "C:\\\\dir"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void controlCharactersInRawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("echo a\\u000bb \\u007f");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"echo", "a\\u000bb", "\\u007f"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void templatePlaceholderInRawString() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("sed -e s/#{a}/b/ f.txt");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"sed", "-e", "s/\\u0023{a}/b/", "f.txt"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void everyCallOfTheSameOverloadIsReplaced() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("ls -a");
                        runtime.exec("ps -e");
                    }
                }
                """,
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"ls", "-a"});
                        runtime.exec(new String[]{"ps", "-e"});
                    }
                }
                """
            ), 18),
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class B {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("df -h");
                    }
                }
                """,
              """
                import java.io.IOException;

                class B {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"df", "-h"});
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void rawStringWithSideEffectingEnvironmentAndDirectory() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    String[] envp() {
                        return new String[]{"E1=1"};
                    }
                    File dir() {
                        return new File("/tmp");
                    }
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("ls -a", envp(), dir());
                    }
                }
                """,
              """
                import java.io.File;
                import java.io.IOException;

                class A {
                    String[] envp() {
                        return new String[]{"E1=1"};
                    }
                    File dir() {
                        return new File("/tmp");
                    }
                    void method(Runtime runtime) throws IOException {
                        runtime.exec(new String[]{"ls", "-a"}, envp(), dir());
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void doNotChangeStringVariableAsInput() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.File;
                import java.io.IOException;

                class B {
                    void method(Runtime runtime, String command, String[] envp, File dir) throws IOException {
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
    void doNotChangeMethodInvocationAsInput() {
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
                    void method(Runtime runtime) throws IOException {
                        Process process = runtime.exec(command());
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void doNotChangeCommandsThatFailAtRuntime() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("");
                        runtime.exec("   ");
                        runtime.exec("\\t\\n\\r\\f");
                        runtime.exec((String) null);
                    }
                }
                """
            ), 18)
        );
    }

    @Test
    void doNotChangeCommandWithSupplementaryCharacterEscape() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class A {
                    void method(Runtime runtime) throws IOException {
                        runtime.exec("echo \\ud83d\\ude00x");
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
    void doNotChangeConcatenatedObjectsAsInput() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                import java.io.IOException;

                class B {
                    String options = "-a -l";
                    void method(Runtime runtime) throws IOException {
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

}
