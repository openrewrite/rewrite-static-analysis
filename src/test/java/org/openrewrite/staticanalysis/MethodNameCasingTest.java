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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.MethodNameCasing.MethodNameChange;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.test.RewriteTest.toRecipe;

@Issue("https://github.com/openrewrite/rewrite/issues/466")
class MethodNameCasingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MethodNameCasing(false, false));
    }

    @DocumentExample
    @Test
    void changeMethodDeclaration() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """,
              """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2571")
    @Test
    void noChangesOnMethodsBeginningWithUnderscore() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void _1() {}
                  void _finally() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2557")
    @Test
    void interfaceMethods() {
        rewriteRun(
          //language=java
          java(
            """
              interface Test {
                  void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2557")
    @Test
    void annotationMethods() {
        rewriteRun(
          //language=java
          java(
            """
              @interface Test {
                  String getFoo_bar();
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void correctMethodNameCasing() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private String getFoo_bar() {
                        return "foobar";
                    }
                }
                """,
              """
                class Test {
                    private String getFooBar() {
                        return "foobar";
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void doNotRenamePublicMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void doNotRenamePublicMethodsNullOptions() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(null, null)),
          //language=java
          java(
            """
              class Test {
                  public void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void okToRenamePublicMethods() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(true, true)),
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    public void getFoo_bar(){}
                }
                """,
              """
                class Test {
                    public void getFooBar(){}
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1741")
    @Test
    void doNotApplyToTest() {
        rewriteRun(
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1741")
    @Test
    void applyChangeToTest() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(true, false)),
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """,
              """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void changeCamelCaseMethodWithFirstLetterUpperCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod() {
                    }
                }
                """,
              """
                class Test {
                    void myMethod() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void changeMethodInvocations() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """, """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            ),
            //language=java
            java(
              """
                class A {
                    void test() {
                        new Test().MyMethod_with_über();
                    }
                }
                """,
              """
                class A {
                    void test() {
                        new Test().myMethodWithUber();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void dontChangeCorrectlyCasedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void dontChange() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeMethodNameWhenOverride() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class ParentClass {
                    void Method() {
                    }
                }
                """,
              """
                class ParentClass {
                    void method() {
                    }
                }
                """
            ),
            //language=java
            java(
              """
                class Test extends ParentClass {
                    @Override
                    void Method() {
                    }
                }
                """,
              """
                class Test extends ParentClass {
                    @Override
                    void method() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void newNameExists() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void Method() {
                  }
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void nameExistsInInnerClass() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class T {
                    void Method(){}

                    private static class M {
                        void Method(){}
                    }
                }
                """,
              """
                class T {
                    void method(){}

                    private static class M {
                        void method(){}
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2103")
    @SuppressWarnings("UnusedAssignment")
    @Test
    void snakeCaseToCamelCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class T {
                    private static int SOME_METHOD() {
                      return 1;
                    }
                    private static int some_method_2() {
                      return 1;
                    }
                    private static int some_über_method() {
                      return 1;
                    }
                    public static void anotherMethod() {
                      int i = SOME_METHOD();
                      i = some_method_2();
                      i = some_über_method();
                    }
                }
                """,
              """
                class T {
                    private static int someMethod() {
                      return 1;
                    }
                    private static int someMethod2() {
                      return 1;
                    }
                    private static int someUberMethod() {
                      return 1;
                    }
                    public static void anotherMethod() {
                      int i = someMethod();
                      i = someMethod2();
                      i = someUberMethod();
                    }
                }
                """
            )
          )
        );
    }

    // This test uses a recipe remove ClassDeclaration types information prior to running the MethodNameCasing recipe.
    // This results in a change with an empty diff, thus before and after sources are identical
    @Issue("https://github.com/openrewrite/rewrite/issues/2103")
    @Test
    void doesNotRenameMethodInvocationsWhenTheMethodDeclarationsClassTypeIsNull() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none())
            .recipes(
              toRecipe(() -> new JavaIsoVisitor<>() {
                  @Override
                  public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                      return super.visitClassDeclaration(classDecl, ctx).withType(null);
                  }
              }),
              new MethodNameCasing(true, false)
            ),
          //language=java
          java(
            """
              package abc;
              class T {
                  public static int MyMethod() {return -1;}
                  public static void anotherMethod() {
                      int i = MyMethod();
                  }
              }
              """,
            """
              package abc;
              class T {
                  public static int MyMethod() {return -1;}
                  public static void anotherMethod() {
                      int i = MyMethod();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCamelCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private void Method() {

                    }
                }
                """,
              """
                class Test {
                    private void method() {

                    }
                }
                """
            )
          )
        );
    }

    @Test
    void keepCamelCase2() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                import java.util.*;

                class Test {
                    private List<String> GetNames() {
                        List<String> result = new ArrayList<>();
                        result.add("Alice");
                        result.add("Bob");
                        result.add("Carol");
                        return result;
                    }

                    public void run() {
                        for (String n: GetNames()) {
                            System.out.println(n);
                        }
                    }
                }
                """,
              """
                import java.util.*;

                class Test {
                    private List<String> getNames() {
                        List<String> result = new ArrayList<>();
                        result.add("Alice");
                        result.add("Bob");
                        result.add("Carol");
                        return result;
                    }

                    public void run() {
                        for (String n: getNames()) {
                            System.out.println(n);
                        }
                    }
                }
                """
            )
          )
        );
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked", "rawtypes"})
    @Test
    void changeNameOfMethodWithArrayArgument() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                import java.util.*;

                class Test {
                    private List<String> GetNames(String[] names) {
                        List<String> result = new ArrayList<>(Arrays.asList(names));
                        return result;
                    }
                }
                """,
              """
                import java.util.*;

                class Test {
                    private List<String> getNames(String[] names) {
                        List<String> result = new ArrayList<>(Arrays.asList(names));
                        return result;
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2261")
    @Test
    void unknownParameterTypes() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private void Foo(Unknown u) {
                    }
                }
                """,
              """
                class Test {
                    private void foo(Unknown u) {
                    }
                }
                """
            )
          )
        );
    }

    private static final int FILES = 4;
    private static final int METHODS_PER_FILE = 30;

    /**
     * Proves the edit phase performs a single LST traversal per source file, independent of how many
     * renames ({@code M}) were collected across the whole codebase. The previous implementation ran one
     * {@code ChangeMethodName} pass per collected change, so every node was visited {@code M} times per
     * file (an {@code M * N} blow-up).
     */
    @Test
    void editPhaseVisitsEachNodeExactlyOncePerFileRegardlessOfChangeCount() {
        // given
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = parseSources();
        List<MethodNameChange> changes = scan(sources, ctx);

        int[] nodes = countMethodNodes(sources);
        int totalDeclarations = nodes[0];
        int totalInvocations = nodes[1];

        // every bad_name_* is package-private, so each of the M changes is applicable to every file
        assertThat(changes).hasSize(FILES * METHODS_PER_FILE);

        // when: run the real edit-phase visitor once per file (mirroring the recipe's outer loop)
        int declVisits = 0;
        int invVisits = 0;
        for (SourceFile sf : sources) {
            CountingRename counter = new CountingRename(mapFor(sf, changes));
            counter.visitNonNull(sf, ctx);
            declVisits += counter.declVisits;
            invVisits += counter.invVisits;
        }

        // then: exactly one visit per node across the whole edit phase -> O(N), not O(M * N)
        assertThat(declVisits).isEqualTo(totalDeclarations);
        assertThat(invVisits).isEqualTo(totalInvocations);
        assertThat((long) declVisits).isLessThan((long) changes.size() * totalDeclarations);
    }

    @Test
    void visitCountIsIndependentOfM() {
        // given
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = parseSources();
        List<MethodNameChange> changes = scan(sources, ctx);
        SourceFile file0 = sources.get(0);

        // when: the same file is edited with the full change set vs. a single change
        CountingRename withAllChanges = new CountingRename(mapFor(file0, changes));
        withAllChanges.visitNonNull(file0, ctx);

        CountingRename withOneChange = new CountingRename(mapFor(file0, singletonList(changes.get(0))));
        withOneChange.visitNonNull(file0, ctx);

        // then: the traversal cost does not grow with the number of collected changes
        assertThat(changes.size()).isGreaterThan(1);
        assertThat(withAllChanges.declVisits).isEqualTo(withOneChange.declVisits);
        assertThat(withAllChanges.invVisits).isEqualTo(withOneChange.invVisits);
    }

    @Test
    void singlePassProducesSameResultAsTheRecipe() {
        // given
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = parseSources();
        List<MethodNameChange> changes = scan(sources, ctx);
        SourceFile file0 = sources.get(0);

        // when
        MethodNameCasing recipe = new MethodNameCasing(true, false);
        SourceFile viaRecipe = (SourceFile) recipe.getVisitor(changes).visit(file0, ctx);
        SourceFile viaRenameMethods = (SourceFile) new MethodNameCasing.RenameMethods(mapFor(file0, changes))
                .visitNonNull(file0, ctx);

        // then: renaming happened and both paths agree
        assertThat(viaRecipe).isNotNull();
        assertThat(viaRecipe.printAll()).doesNotContain("bad_name_");
        assertThat(viaRecipe.printAll()).isEqualTo(viaRenameMethods.printAll());
    }

    @Test
    void theOldStrategyWouldVisitEachNodeMTimes() {
        // given
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = parseSources();
        List<MethodNameChange> changes = scan(sources, ctx);
        int totalDeclarations = countMethodNodes(sources)[0];

        // when: reproduce the previous design - one rename pass per collected change, per file
        long oldDeclVisits = 0;
        for (SourceFile sf : sources) {
            for (MethodNameChange c : changes) {
                if (!c.isPrivateMethod() || sf.getId().equals(c.getScope())) {
                    CountingRename onePass = new CountingRename(mapFor(sf, singletonList(c)));
                    onePass.visitNonNull(sf, ctx);
                    oldDeclVisits += onePass.declVisits;
                }
            }
        }

        // then: it visited every declaration once per applicable change (M * N), which is exactly
        // what editPhaseVisitsEachNodeExactlyOncePerFileRegardlessOfChangeCount() now forbids
        assertThat(oldDeclVisits).isEqualTo((long) changes.size() * totalDeclarations);
        assertThat(oldDeclVisits).isGreaterThan(totalDeclarations);
    }

    private static List<SourceFile> parseSources() {
        List<String> sources = new ArrayList<>();
        for (int f = 0; f < FILES; f++) {
            StringBuilder sb = new StringBuilder("class C" + f + " {\n");
            for (int d = 0; d < METHODS_PER_FILE; d++) {
                sb.append("    void bad_name_").append(f).append('_').append(d).append("() {}\n");
            }
            sb.append("    void driver() {\n");
            for (int d = 0; d < METHODS_PER_FILE; d++) {
                sb.append("        bad_name_").append(f).append('_').append(d).append("();\n");
            }
            sb.append("    }\n}\n");
            sources.add(sb.toString());
        }
        return JavaParser.fromJavaVersion().build()
                .parse(new InMemoryExecutionContext(), sources.toArray(new String[0]))
                .map(cu -> (SourceFile) cu.withMarkers(cu.getMarkers()
                        .addIfAbsent(new JavaSourceSet(UUID.randomUUID(), "main", List.of(), null))))
                .collect(Collectors.toList());
    }

    private static List<MethodNameChange> scan(List<SourceFile> sources, InMemoryExecutionContext ctx) {
        MethodNameCasing recipe = new MethodNameCasing(true, false);
        List<MethodNameChange> changes = recipe.getInitialValue(ctx);
        for (SourceFile sf : sources) {
            recipe.getScanner(changes).visit(sf, ctx);
        }
        return changes;
    }

    private static Map<String, List<MethodNameChange>> mapFor(SourceFile sf, List<MethodNameChange> changes) {
        Map<String, List<MethodNameChange>> byName = new HashMap<>();
        for (MethodNameChange c : changes) {
            if (!c.isPrivateMethod() || sf.getId().equals(c.getScope())) {
                byName.computeIfAbsent(c.getFromName(), k -> new ArrayList<>()).add(c);
            }
        }
        return byName;
    }

    private static int[] countMethodNodes(List<SourceFile> sources) {
        int[] counts = new int[2];
        JavaIsoVisitor<int[]> counter = new JavaIsoVisitor<>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, int[] c) {
                c[0]++;
                return super.visitMethodDeclaration(method, c);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, int[] c) {
                c[1]++;
                return super.visitMethodInvocation(method, c);
            }
        };
        for (SourceFile sf : sources) {
            counter.visit(sf, counts);
        }
        return counts;
    }

    static class CountingRename extends MethodNameCasing.RenameMethods {
        int declVisits;
        int invVisits;

        CountingRename(Map<String, List<MethodNameChange>> byName) {
            super(byName);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            declVisits++;
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            invVisits++;
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
