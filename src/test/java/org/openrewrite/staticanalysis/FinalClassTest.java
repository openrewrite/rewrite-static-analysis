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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Arrays;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

class FinalClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FinalClass(false, null, null));
    }

    @DocumentExample
    @Test
    void finalizeClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  private A(String s) {
                  }

                  private A() {
                  }
              }
              """,
            """
              public final class A {
                  private A(String s) {
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2954")
    @Test
    void nestedClassWithSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private A() {
                  }

                  private static class C extends B {
                      private C() {
                      }

                      private static class D extends C {
                      }
                  }

                  private static class B extends A {
                      private B() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void hasPublicConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  private A(String s) {
                  }

                  public A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void hasImplicitConstructor() {
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

    @Test
    void innerClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {

                  class B {
                      private B() {}
                  }
              }
              """,
            """
              class A {

                  final class B {
                      private B() {}
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1061")
    @Test
    void abstractClass() {
        rewriteRun(
          //language=java
          java(
            """
              public abstract class A {
                  public static void foo() {
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2339")
    @Test
    void classWithAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {

                  @Deprecated
                  class B {
                      private B() {}
                  }
              }
              """,
            """
              class A {

                  @Deprecated
                  final class B {
                      private B() {}
                  }
              }
              """
          )
        );
    }

    @Test
    void neverExtendedClassesWithIncludeNeverExtended() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              class Person {
                  private String name;

                  public Person(String name) {
                      this.name = name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }
              }

              class Address {
                  private String street;

                  public Address(String street) {
                      this.street = street;
                  }
              }
              """,
            """
              final class Person {
                  private String name;

                  public Person(String name) {
                      this.name = name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }
              }

              final class Address {
                  private String street;

                  public Address(String street) {
                      this.street = street;
                  }
              }
              """
          )
        );
    }

    @Test
    void extendedClassesNotFinalized() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              class Vehicle {
                  protected String brand;

                  public Vehicle(String brand) {
                      this.brand = brand;
                  }
              }

              class Car extends Vehicle {
                  private int doors;

                  public Car(String brand, int doors) {
                      super(brand);
                      this.doors = doors;
                  }
              }
              """,
            """
              class Vehicle {
                  protected String brand;

                  public Vehicle(String brand) {
                      this.brand = brand;
                  }
              }

              final class Car extends Vehicle {
                  private int doors;

                  public Car(String brand, int doors) {
                      super(brand);
                      this.doors = doors;
                  }
              }
              """
          )
        );
    }

    @Test
    void abstractClassesNotFinalized() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              abstract class Animal {
                  protected String name;

                  public Animal(String name) {
                      this.name = name;
                  }

                  public abstract void makeSound();
              }

              class Dog extends Animal {
                  public Dog(String name) {
                      super(name);
                  }

                  @Override
                  public void makeSound() {
                      System.out.println("Woof!");
                  }
              }
              """,
            """
              abstract class Animal {
                  protected String name;

                  public Animal(String name) {
                      this.name = name;
                  }

                  public abstract void makeSound();
              }

              final class Dog extends Animal {
                  public Dog(String name) {
                      super(name);
                  }

                  @Override
                  public void makeSound() {
                      System.out.println("Woof!");
                  }
              }
              """
          )
        );
    }

    @Test
    void excludePackagePatterns() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, Arrays.asList("com.example.api.*"), null)),
          java(
            """
              package com.example.api;

              class PublicApi {
                  public void doSomething() {
                      // This should not be finalized due to package exclusion
                  }
              }
              """
          )
        );
    }

    @Test
    void excludeAnnotatedClasses() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, Arrays.asList("@ExtensionPoint"))),
          java(
            """
              @ExtensionPoint
              class PluginBase {
                  public void initialize() {
                      // This should not be finalized due to annotation exclusion
                  }
              }

              @interface ExtensionPoint {}
              """
          )
        );
    }

    @Test
    void privateConstructorClassesStillFinalized() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              class UtilityClass {
                  private UtilityClass() {
                      // Private constructor - should be finalized by original logic
                  }

                  public static void doSomething() {
                      System.out.println("Utility method");
                  }
              }
              """,
            """
              final class UtilityClass {
                  private UtilityClass() {
                      // Private constructor - should be finalized by original logic
                  }

                  public static void doSomething() {
                      System.out.println("Utility method");
                  }
              }
              """
          )
        );
    }

    @Test
    void mixedInheritanceChain() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              class GrandParent {
                  public GrandParent() {}
              }

              class Parent extends GrandParent {
                  public Parent() {}
              }

              class Child extends Parent {
                  public Child() {}
              }

              class StandaloneClass {
                  public StandaloneClass() {}
              }
              """,
            """
              class GrandParent {
                  public GrandParent() {}
              }

              class Parent extends GrandParent {
                  public Parent() {}
              }

              final class Child extends Parent {
                  public Child() {}
              }

              final class StandaloneClass {
                  public StandaloneClass() {}
              }
              """
          )
        );
    }

    @Test
    void nestedClassInheritance() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              class Outer {
                  private Outer() {}

                  static class Inner1 {
                      public Inner1() {}
                  }

                  static class Inner2 extends Inner1 {
                      public Inner2() {}
                  }
              }
              """,
            """
              final class Outer {
                  private Outer() {}

                  static class Inner1 {
                      public Inner1() {}
                  }

                  static final class Inner2 extends Inner1 {
                      public Inner2() {}
                  }
              }
              """
          )
        );
    }

    @Test
    void interfacesAndEnumsNotFinalized() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              interface MyInterface {
                  void method();
              }

              enum MyEnum {
                  VALUE1, VALUE2
              }
              """
          )
        );
    }

    @Test
    void excludeMultiplePackagePatterns() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, Arrays.asList("com.example.api.*", "com.example.spi.*"), null)),
          java(
            """
              package com.example.api;

              class ApiClass {
                  public void api() {}
              }
              """
          ),
          java(
            """
              package com.example.spi;

              class SpiClass {
                  public void spi() {}
              }
              """
          ),
          java(
            """
              package com.example.internal;

              class InternalClass {
                  public void internal() {}
              }
              """,
            """
              package com.example.internal;

              final class InternalClass {
                  public void internal() {}
              }
              """
          )
        );
    }

    @Test
    void excludeMultipleAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, Arrays.asList("@ExtensionPoint", "@ApiClass"))),
          java(
            """
              @interface ExtensionPoint {}
              @interface ApiClass {}

              @ExtensionPoint
              class PluginBase {
                  public void plugin() {}
              }

              @ApiClass
              class PublicApi {
                  public void api() {}
              }

              class RegularClass {
                  public void regular() {}
              }
              """,
            """
              @interface ExtensionPoint {}
              @interface ApiClass {}

              @ExtensionPoint
              class PluginBase {
                  public void plugin() {}
              }

              @ApiClass
              class PublicApi {
                  public void api() {}
              }

              final class RegularClass {
                  public void regular() {}
              }
              """
          )
        );
    }

    @Test
    void alreadyFinalClassesUnchanged() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              final class AlreadyFinal {
                  public AlreadyFinal() {}
              }
              """
          )
        );
    }

    @Test
    void shouldNotFinalizeRecords() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              public record Point(int a, int b) {
              }
              """
          )
        );
    }

    @Test
    void shouldNotFinalizeSealedClasses() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(true, null, null)),
          java(
            """
              sealed class S permits X, Y {
                  S() {}
              }
              final class X extends S {
                  X() {
                      super();
                  }
              }
              non-sealed class Y extends S {
                  Y() {
                      super();
                  }
              }
              """
          )
        );
    }

    @Test
    void excludeAnnotationWithoutAt() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(false, null, List.of("Deprecated"))),
          java(
            """
              package com.example.config;

              @Deprecated
              public class DeprecatedClass {
                  public DeprecatedClass() {}
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/729")
    void excludeAnnotationWithFullyQualifiedName() {
        rewriteRun(
          spec -> spec.recipe(new FinalClass(false, null, List.of("@java.lang.Deprecated"))),
          java(
            """
              package com.example.config;

              @java.lang.Deprecated
              public class AnotherDeprecatedClass {
                  public AnotherDeprecatedClass() {}
              }
              """
          )
        );
    }
}
