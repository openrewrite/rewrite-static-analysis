/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FluentSetterRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FluentSetterRecipe(false, null, null));
    }

    @Test
    void convertSimpleSetter() {
        rewriteRun(
          java(
            """
              public class Person {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }
              }
              """,
            """
              public class Person {
                  private String name;

                  public Person setName(String name) {
                      this.name = name;
                      return this;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertSetterWithExtraIdent() {
        rewriteRun(
          java(
            """
              public class Person {
                  private String name;

                  public void setName(String name) {
                                this.name = name;
                  }
              }
              """,
            """
              public class Person {
                  private String name;

                  public Person setName(String name) {
                                this.name = name;
                                return this;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertMultipleSetters() {
        rewriteRun(
          java(
            """
              public class Person {
                  private String name;
                  private int age;
                  private String email;

                  public void setName(String name) {
                      System.out.println(name);
                      this.name = name;
                  }

                  public void setAge(int age) {
                      this.age = age;
                  }

                  public void setEmail(String email) {
                      this.email = email;
                  }
              }
              """,
            """
              public class Person {
                  private String name;
                  private int age;
                  private String email;

                  public Person setName(String name) {
                      System.out.println(name);
                      this.name = name;
                      return this;
                  }

                  public Person setAge(int age) {
                      this.age = age;
                      return this;
                  }

                  public Person setEmail(String email) {
                      this.email = email;
                      return this;
                  }
              }
              """
          )
        );
    }

    @Test
    void skipNonSetterVoidMethods() {
        rewriteRun(
          java(
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }

                  public void doSomething() {
                      System.out.println("doing something");
                  }

                  public void run() {
                      // some logic
                  }
              }
              """,
            """
              public class Example {
                  private String name;

                  public Example setName(String name) {
                      this.name = name;
                      return this;
                  }

                  public void doSomething() {
                      System.out.println("doing something");
                  }

                  public void run() {
                      // some logic
                  }
              }
              """
          )
        );
    }

    @Test
    void skipStaticMethods() {
        rewriteRun(
          java(
            """
              public class Example {
                  private static String globalName;
                  private String name;

                  public static void setGlobalName(String name) {
                      globalName = name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }
              }
              """,
            """
              public class Example {
                  private static String globalName;
                  private String name;

                  public static void setGlobalName(String name) {
                      globalName = name;
                  }

                  public Example setName(String name) {
                      this.name = name;
                      return this;
                  }
              }
              """
          )
        );
    }

    @Test
    void skipMethodsWithExistingReturnStatement() {
        rewriteRun(
          java(
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      if (name == null) {
                          return;
                      }
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void skipNonVoidMethods() {
        rewriteRun(
          java(
            """
              public class Example {
                  private String name;

                  public String setName(String name) {
                      this.name = name;
                      return "success";
                  }

                  public String getName() {
                      return name;
                  }
              }
              """
          )
        );
    }

    @Test
    void skipAbstractMethods() {
        rewriteRun(
          java(
            """
              public abstract class Example {
                  private String name;

                  public abstract void setName(String name);
              }
              """
          )
        );
    }

    @Test
    void skipInvalidSetterPatterns() {
        rewriteRun(
          java(
            """
              public class Example {
                  private String name;

                  // Not a setter - doesn't start with 'set'
                  public void updateName(String name) {
                      this.name = name;
                  }

                  // Not a setter - 'set' but lowercase next char
                  public void setup() {
                      // setup logic
                  }

                  // Not a setter - too many parameters
                  public void setNameAndAge(String name, int age) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void includeAllVoidMethods() {
        rewriteRun(
          spec -> spec.recipe(new FluentSetterRecipe(true, null, null)),
          java(
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }

                  public void doSomething() {
                      System.out.println("doing something");
                  }

                  public void process(String data) {
                      System.out.println("processing something");
                  }
              }
              """,
            """
              public class Example {
                  private String name;

                  public Example setName(String name) {
                      this.name = name;
                      return this;
                  }

                  public Example doSomething() {
                      System.out.println("doing something");
                      return this;
                  }

                  public Example process(String data) {
                      System.out.println("processing something");
                      return this;
                  }
              }
              """
          )
        );
    }

    @Test
    void customMethodNamePattern() {
        rewriteRun(
          spec -> spec.recipe(new FluentSetterRecipe(false, "add.*|remove.*", null)),
          java(
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }

                  public void addItem(String item) {
                      System.out.println("adding item");
                  }

                  public void removeItem(String item) {
                      System.out.println("removing item");
                  }

                  public void doSomething() {
                  }
              }
              """,
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }

                  public Example addItem(String item) {
                      System.out.println("adding item");
                      return this;
                  }

                  public Example removeItem(String item) {
                      System.out.println("removing item");
                      return this;
                  }

                  public void doSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void excludeMethodPattern() {
        rewriteRun(
          spec -> spec.recipe(new FluentSetterRecipe(true, null, "main|run|execute")),
          java(
            """
              public class Example {
                  private String name;

                  public void setName(String name) {
                      this.name = name;
                  }

                  public void doSomething() {
                      System.out.println("doing something");
                  }

                  // should be excluded
                  public void run() {
                  }

                  // should be excluded
                  public void execute() {
                  }

                  // should be excluded (also static)
                  public static void main(String[] args) {
                  }
              }
              """,
            """
              public class Example {
                  private String name;

                  public Example setName(String name) {
                      this.name = name;
                      return this;
                  }

                  public Example doSomething() {
                      System.out.println("doing something");
                      return this;
                  }

                  // should be excluded
                  public void run() {
                  }

                  // should be excluded
                  public void execute() {
                  }

                  // should be excluded (also static)
                  public static void main(String[] args) {
                  }
              }
              """
          )
        );
    }

    @Test
    void worksWithInnerClasses() {
        rewriteRun(
          java(
            """
              public class Outer {
                  private String outerName;

                  public void setOuterName(String name) {
                      this.outerName = name;
                  }

                  public static class Inner {
                      private String innerName;

                      public void setInnerName(String name) {
                          this.innerName = name;
                      }
                  }
              }
              """,
            """
              public class Outer {
                  private String outerName;

                  public Outer setOuterName(String name) {
                      this.outerName = name;
                      return this;
                  }

                  public static class Inner {
                      private String innerName;

                      public Inner setInnerName(String name) {
                          this.innerName = name;
                          return this;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesMethodModifiers() {
        rewriteRun(
          java(
            """
              public class Example {
                  private String name;

                  protected void setName(String name) {
                      this.name = name;
                  }

                  private void setPrivateName(String name) {
                      this.name = name;
                  }
              }
              """,
            """
              public class Example {
                  private String name;

                  protected Example setName(String name) {
                      this.name = name;
                      return this;
                  }

                  private Example setPrivateName(String name) {
                      this.name = name;
                      return this;
                  }
              }
              """
          )
        );
    }
}
