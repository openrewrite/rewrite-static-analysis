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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.HideUtilityClassConstructorStyle;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.test.RewriteTest.toRecipe;

class HideUtilityClassConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HideUtilityClassConstructor());
    }

    @DocumentExample
    @Test
    void changePublicConstructorToPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }

                  public static void utility() {
                  }
              }
              """,
            """
              public class A {
                  private A() {
                  }

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1780")
    @SuppressWarnings("UnnecessaryModifier")
    @Test
    void doNotAddConstructorToInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface A {
                  public static final String utility = "";
              }
              """
          )
        );
    }

    @Test
    void changePackagePrivateConstructorToPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  A() {
                  }

                  public static void utility() {
                  }
              }
              """,
            """
              public class A {
                  private A() {
                  }

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyUtilityClassesWithProtectedConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  protected A() {
                  }

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInapplicableNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  private A() {}
                  public static String foo() { return "foo"; }
              }
              """
          )
        );
    }

    @Test
    void changeApplicableNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {}
                  public static String foo() { return "foo"; }
                  private static class Builder {
                      public Builder() {}
                      public static String foo() { return "foo"; }
                  }
              }
              """,
            """
              public class A {
                  private A() {}
                  public static String foo() { return "foo"; }
                  private static class Builder {
                      private Builder() {}
                      public static String foo() { return "foo"; }
                  }
              }
              """
          )
        );
    }

    @Test
    void changeUtilityClassesWithMixedExposedConstructors() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  protected A() {
                  }

                  public A(String a) {
                  }

                  A(String a, String b) {
                  }

                  private A(String a, String b, String c) {
                  }

                  public static void utility() {
                  }
              }
              """,
            """
              public class A {
                  protected A() {
                  }

                  private A(String a) {
                  }

                  private A(String a, String b) {
                  }

                  private A(String a, String b, String c) {
                  }

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Test
    void addPrivateConstructorWhenOnlyDefaultConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class Math {
                  public static final int TWO = 2;

                  public static int addTwo(int a) {
                      return a + TWO;
                  }
              }
              """,
            """
              public class Math {
                  public static final int TWO = 2;

                  public static int addTwo(int a) {
                      return a + TWO;
                  }

                  private Math() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/538")
    @Test
    void ignoreClassesWithMainMethod() {
        rewriteRun(
          //language=java
          java(
            """
              package a;

              public class A {
                  public static void main(String[] args) {
                      // SpringApplication.run(A.class, args);
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyUtilityClassesOnlyStaticFields() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }

                  public static int a;
              }
              """,
            """
              public class A {
                  private A() {
                  }

                  public static int a;
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesWhenImplementsInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface B {
                  static void utility() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              public class A implements B {
                  public A() {
                  }

                  public static void utility() {
                      B.utility();
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesWhenExtendsClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class B {
                  public static void utility() {}
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              public class A extends B {
                  public A() {
                  }

                  public static void doSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesMixedFields() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }

                  public int a;

                  public static int b;
              }
              """
          )
        );
    }

    @Test
    void identifyUtilityClassesOnlyStaticMethods() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }

                  public static void utility() {
                  }

                  public static void utility(String[] args) {
                      utility();
                  }
              }
              """,
            """
              public class A {
                  private A() {
                  }

                  public static void utility() {
                  }

                  public static void utility(String[] args) {
                      utility();
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyUtilityClassesInnerStaticClasses() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {

                  static class Inner {
                      public Inner() {
                      }

                      public static void utility() {
                      }
                  }

                  public void utility() {
                  }
              }
              """,
            """
              public class A {

                  static class Inner {
                      private Inner() {
                      }

                      public static void utility() {
                      }
                  }

                  public void utility() {
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesMixedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }

                  public static void someStatic() {
                  }

                  public void notStatic() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1060")
    @Test
    void identifyAbstractClass() {
        rewriteRun(
          //language=java
          java(
            """
              public abstract class A {
                  public A() {
                  }

                  public static void someStatic1() {
                  }

                  public static void someStatic2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesOnlyPublicConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void identifyNonUtilityClassesTotallyEmpty() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/729")
    @Test
    void doNotChangeSpringConfigurationClass() {
        rewriteRun(
          //language=java
          java(
            """
              package org.springframework.context.annotation;
              public @interface Configuration {}
              """
          ),
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;

              @Configuration
              class MyConfig {
                  public static String someBean() {
                      return "bean";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/729")
    @Test
    void doNotChangeSpringTestConfigurationClass() {
        rewriteRun(
          //language=java
          java(
            """
              package org.springframework.context.annotation;
              public @interface Configuration {}
              """
          ),
          //language=java
          java(
            """
              package org.springframework.boot.test.context;
              import org.springframework.context.annotation.Configuration;
              @Configuration
              public @interface TestConfiguration {}
              """
          ),
          //language=java
          java(
            """
              import org.springframework.boot.test.context.TestConfiguration;

              @TestConfiguration
              class MyTestConfig {
                  public static String someBean() {
                      return "bean";
                  }
              }
              """
          )
        );
    }

    private static Consumer<RecipeSpec> hideUtilityClassConstructor(String... ignoreIfAnnotatedBy) {
        return spec -> spec.parser(JavaParser.fromJavaVersion().styles(
          List.of(
            new NamedStyles(
              randomId(), "test", "test", "test", Set.of(), List.of(
              new HideUtilityClassConstructorStyle(List.of(ignoreIfAnnotatedBy))
            )
            )
          )
        ));
    }

    @Test
    void dontChangeSuppressedUtilityClasses() {
        rewriteRun(
          hideUtilityClassConstructor(
            "@lombok.experimental.UtilityClass",
            "@java.lang.SuppressWarnings(\"checkstyle:HideUtilityClassConstructor\")"
          ),
          //language=java
          java(
            """
              package lombok.experimental;
              public @interface UtilityClass {}
              """
          ),
          //language=java
          java(
            """
              import lombok.experimental.UtilityClass;

              @UtilityClass
              public class DoNotChangeMeA {
                  public static void utility() {
                  }
              }

              @SuppressWarnings("checkstyle:HideUtilityClassConstructor")
              class DoNotChangeMeB {
                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Test
    void readStyleOfEachSourceFileWhenReusingTheVisitor() {
        HideUtilityClassConstructorVisitor<ExecutionContext> visitor = new HideUtilityClassConstructorVisitor<>();
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> visitor)),
          //language=java
          java(
            """
              class ChangeMe {
                  public static void utility() {
                  }
              }
              """,
            """
              class ChangeMe {
                  public static void utility() {
                  }

                  private ChangeMe() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              @Deprecated
              class DoNotChangeMe {
                  public static void utility() {
                  }
              }
              """,
            spec -> spec.markers(new NamedStyles(randomId(), "test", "test", "test", Set.of(), List.of(
              new HideUtilityClassConstructorStyle(List.of("@java.lang.Deprecated"))
            )))
          )
        );
    }

    @Test
    void suppressedUtilityClassWithDifferentArgument() {
        rewriteRun(
          hideUtilityClassConstructor("@java.lang.SuppressWarnings(\"checkstyle:HideUtilityClassConstructor\")"),
          //language=java
          java(
            """
              class ChangeMeA {
                  public static void utility() {
                  }
              }
              """,
            """
              class ChangeMeA {
                  public static void utility() {
                  }

                  private ChangeMeA() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1058")
    @Test
    void doesNotChangePackagePrivateEnumConstructorToPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              public enum SomeEnum {
                  A,B,C;

                  SomeEnum() {
                  }

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1058")
    @Test
    void enumClass() {
        rewriteRun(
          //language=java
          java(
            """
              public enum SomeEnum {
                  A,B,C;

                  public static void utility() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1058")
    @Test
    void emptyRecord() {
        rewriteRun(
          version(java(
            """
              public record SomeRecord(String component) {
                  static void utility() {
                  }
              }
              """
          ), 17)
        );
    }

    @Test
    void hideConstructorUsedOnlyWithinItsTopLevelClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public static String foo() { return "foo"; }
                  static Object instance() {
                      return new A.Util();
                  }
                  static class Util {
                      public static String bar() { return "bar"; }
                  }
                  static class Sub extends A {
                  }
              }
              """,
            """
              public class A {
                  public static String foo() { return "foo"; }
                  static Object instance() {
                      return new A.Util();
                  }
                  static class Util {
                      public static String bar() { return "bar"; }

                      private Util() {
                      }
                  }
                  static class Sub extends A {
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void indentAddedConstructorLikeTheFileNotLikeAttachedStyle() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().styles(
            List.of(
              new NamedStyles(
                randomId(), "repository", "repository", "repository", Set.of(), List.of(
                IntelliJ.tabsAndIndents().withIndentSize(2).withContinuationIndent(4)
              )
              )
            )
          )),
          //language=java
          java(
            """
              public class Constants {

                  public static final String NONE = "NONE";

                  public static String none() {
                      return NONE;
                  }
              }
              """,
            """
              public class Constants {

                  public static final String NONE = "NONE";

                  public static String none() {
                      return NONE;
                  }

                  private Constants() {
                  }
              }
              """
          )
        );
    }

    @Test
    void indentAddedConstructorWithTabs() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
              \tpublic static void utility() {
              \t}
              }
              """,
            """
              public class A {
              \tpublic static void utility() {
              \t}

              \tprivate A() {
              \t}
              }
              """
          )
        );
    }

    @Test
    void indentAddedConstructorOfNestedClassWithTabs() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
              \tpublic void instance() {
              \t}

              \tstatic class Util {
              \t\tstatic void utility() {
              \t\t}
              \t}
              }
              """,
            """
              public class A {
              \tpublic void instance() {
              \t}

              \tstatic class Util {
              \t\tstatic void utility() {
              \t\t}

              \t\tprivate Util() {
              \t\t}
              \t}
              }
              """
          )
        );
    }

    @Test
    void indentAddedConstructorWithTwoSpaces() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                static void utility() {
                }
              }
              """,
            """
              public class A {
                static void utility() {
                }

                private A() {
                }
              }
              """
          )
        );
    }

    @Test
    void hideExplicitConstructorUsedOnlyWithinItsTopLevelClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public A() {
                  }
                  public static void utility() {
                  }
                  static class Nested {
                      Object a = new A();
                  }
              }
              """,
            """
              public class A {
                  private A() {
                  }
                  public static void utility() {
                  }
                  static class Nested {
                      Object a = new A();
                  }
              }
              """
          )
        );
    }

    @Test
    void hideConstructorOfClassWhoseClassLiteralIsOnlyNamedElsewhere() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }
              }
              """,
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }

                  private A() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              import java.util.logging.Logger;
              class B {
                  Logger logger = Logger.getLogger(A.class.getName());
                  String name = A.class.getSimpleName();
                  Object location = A.class.getProtectionDomain().getCodeSource();
                  Object method() throws Exception {
                      return A.class.getMethod("utility");
                  }
              }
              """
          )
        );
    }

    @Test
    void hideConstructorOfClassWhoseClassLiteralIsUsedWithoutConstructingIt() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }
              }
              """,
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }

                  private A() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              import java.io.InputStream;
              class B {
                  InputStream in = A.class.getResourceAsStream("x");
                  boolean is(Object o) {
                      return A.class.isInstance(o);
                  }
              }
              """
          )
        );
    }

    @Test
    void hideConstructorOfClassWhoseClassLiteralIsALoggerNameOrLock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.logging.Logger;
              public class A {
                  private static final Logger LOGGER = Logger.getLogger(A.class.getName());
                  public static void utility() {
                      synchronized (A.class) {
                          LOGGER.info("x");
                      }
                  }
              }
              """,
            """
              import java.util.logging.Logger;
              public class A {
                  private static final Logger LOGGER = Logger.getLogger(A.class.getName());
                  public static void utility() {
                      synchronized (A.class) {
                          LOGGER.info("x");
                      }
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void hideConstructorOfClassWhoseClassLiteralNamesALogger() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              class LoggerFactory {
                  int level;
                  static Object getLogger(Class<?> c) {
                      return null;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }
              }
              """,
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }

                  private A() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              class B {
                  Object log = LoggerFactory.getLogger(A.class);
              }
              """
          )
        );
    }

    @Test
    void keepConstructorReachedReflectivelyWithinItsTopLevelClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class Outer {
                  public static void f() {
                  }
                  static Object make() throws Exception {
                      Class<?> c = Util.class;
                      return c.getConstructor().newInstance();
                  }
                  public static class Util {
                      public static void g() {
                      }
                  }
              }
              """,
            """
              public class Outer {
                  public static void f() {
                  }
                  static Object make() throws Exception {
                      Class<?> c = Util.class;
                      return c.getConstructor().newInstance();
                  }
                  public static class Util {
                      public static void g() {
                      }
                  }

                  private Outer() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepConstructorsUsedQualifiedOrGenericWithoutTypeAttribution() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package com.example;
              public class Outer {
                  public static void f() {
                  }
                  public static class Util {
                      public static void g() {
                      }
                  }
              }
              """,
            """
              package com.example;
              public class Outer {
                  public static void f() {
                  }
                  public static class Util {
                      public static void g() {
                      }
                  }

                  private Outer() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;
              public class A {
                  public static void utility() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.other;
              class B extends A<String> {
                  Object u = new Outer.Util();
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void doNotChangeExtendedClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class Base {
                      public static Object[] combine(Object[] a, Object[] b) {
                          return a;
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class Sub extends Base {
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassInstantiatedElsewhere() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class EntryFactory {
                      public static String create() {
                          return "";
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class EntryFactoryTest {
                      void test() {
                          new EntryFactory();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeExplicitConstructorUsedElsewhere() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public A() {
                      }
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  import java.util.function.Supplier;
                  class B {
                      Supplier<Object> supplier = A::new;
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeAnonymouslySubclassedClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B {
                      Object a = new A() {};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorCalledThroughSuper() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      A(String s) {
                      }
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B extends A {
                      B() {
                          super("b");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassExtendedInKotlin() {
            rewriteRun(
              // the Kotlin parser does not see the Java sources, so `A` is unresolved there
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  class B : A()
                  """
              )
            );
        }

        @Test
        void doNotChangeClassInstantiatedInGroovy() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=groovy
              groovy(
                """
                  package com.example
                  class G {
                      def a() { new A() }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassInstantiatedWithoutTypeAttribution() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.other;
                  class B {
                      Object a = new A();
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeExplicitPublicConstructorOfExtendedClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public A() {
                      }
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B extends A {
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorUsedByAnotherTopLevelClassOfTheSameFile() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  class B {
                      Object a = new A();
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassWhoseConstructorLombokGenerates() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
              //language=java
              java(
                """
                  import lombok.AllArgsConstructor;

                  @AllArgsConstructor
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorUsedReflectively() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                      static Object create() throws Exception {
                          return A.class.getDeclaredConstructor().newInstance();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorCalledFromKotlin() {
            rewriteRun(
              // the Kotlin parser does not see the Java sources, so `A` is unresolved there
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  fun create(): Any = A()
                  """
              )
            );
        }

        @Test
        void doNotChangeClassWhoseClassLiteralIsUsedElsewhere() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B {
                      Object create() throws Exception {
                          Class<?> c = A.class;
                          return c.getDeclaredConstructor().newInstance();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorReferencedFromKotlin() {
            rewriteRun(
              // the Kotlin parser does not see the Java sources, so `A` is unresolved there
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  val f = ::A
                  """
              )
            );
        }

        @Test
        void doNotChangeClassWhoseLiteralIsInAnnotation() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public @interface Uses {
                      Class<?> value();
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  @Uses(A.class)
                  class B {
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassWhoseLiteralIsUsedInKotlin() {
            rewriteRun(
              // the Kotlin parser does not see the Java sources, so `A` is unresolved there
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  fun create(): Any = A::class.java.getDeclaredConstructor().newInstance()
                  """
              )
            );
        }

        @Test
        void doNotChangeNestedClassUsedQualifiedFromOtherLanguages() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package a;
                  public class Outer {
                      public static void f() {
                      }
                      public static class Util {
                          public static void g() {
                          }
                      }
                      public static class Ref {
                          public static void g() {
                          }
                      }
                      public static class Gr {
                          public static void g() {
                          }
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package b
                  import a.Outer
                  fun x() = Outer.Util()
                  val f = Outer::Ref
                  """
              ),
              //language=groovy
              groovy(
                """
                  package b
                  import a.Outer
                  def x = new Outer.Gr()
                  def y = new Outer()
                  """
              )
            );
        }

        @Test
        void doNotChangeClassNamedInAString() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B {
                      Object create() throws Exception {
                          return Class.forName("com.example.A").getDeclaredConstructor().newInstance();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassLoadedByItsName() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class C {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class B {
                      Object a() throws Exception {
                          return Class.forName(A.class.getName()).getDeclaredConstructor().newInstance();
                      }
                      Class<?> c(ClassLoader loader) throws Exception {
                          return loader.loadClass(C.class.getName());
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeClassExtendedByKotlinObject() {
            rewriteRun(
              // the Kotlin parser does not see the Java sources, so `A` is unresolved there
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  val a = object : A() {}
                  """
              )
            );
        }

        @Test
        void doNotChangeClassWhoseInstancesAreHeldElsewhere() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  import java.util.function.Supplier;
                  class B {
                      Supplier<A> injected;
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class C {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class D {
                      C[] injected;
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class E {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  class F {
                      E produce() {
                          return null;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeConstructorUsedFromTypeAttributedKotlin() {
            rewriteRun(
              spec -> spec.parser(KotlinParser.builder().dependsOn("package com.example\nopen class A\nopen class C\nopen class D")),
              //language=java
              java(
                """
                  package com.example;
                  public class A {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class C {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package com.example;
                  public class D {
                      public static void utility() {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package com.example
                  class B : A()
                  fun c(): Any = C()
                  val d = ::D
                  """
              )
            );
        }

        @Test
        void doNotChangeClassInstantiatedInTestSources() {
            rewriteRun(
              srcMainJava(
                //language=java
                java(
                  """
                    package com.example;
                    public class A {
                        public static void utility() {
                        }
                    }
                    """
                )
              ),
              srcTestJava(
                //language=java
                java(
                  """
                    package com.example;
                    class ATest {
                        Object a = new A();
                    }
                    """
                )
              )
            );
        }

        @Test
        void doNotChangeClassInstantiatedInAnotherModule() {
            rewriteRun(
              mavenProject("a",
                //language=java
                java(
                  """
                    package com.example;
                    public class A {
                        public static void utility() {
                        }
                    }
                    """
                )
              ),
              mavenProject("b",
                //language=java
                java(
                  """
                    package com.other;
                    import com.example.A;
                    class B {
                        Object a = new A();
                    }
                    """
                )
              )
            );
        }
    }
}
