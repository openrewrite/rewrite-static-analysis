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
package org.openrewrite.staticanalysis.kotlin;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class ReplaceLambdaWithCallableReferenceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceLambdaWithCallableReference());
    }

    @DocumentExample
    @Test
    void toQualifiedMethodReference() {
        rewriteRun(
          kotlin(
            """
              interface Pet {
                  fun move() {
                  }
              }

              class Cat : Pet {
                  override fun move() {
                      println("Cat is moving")
                  }
              }

              class Dog : Pet {
                  override fun move() {
                      println("Dog is moving")
                  }
              }
              """
          ),
          kotlin(
            """
              fun main() {
                  val pets = listOf(Cat(), Dog())
                  pets.forEach { it.move() }
              }
              """,
            """
              fun main() {
                  val pets = listOf(Cat(), Dog())
                  pets.forEach(Pet::move)
              }
              """
          )
        );
    }

    @Test
    void toUnqualifiedMethodReference() {
        rewriteRun(
          kotlin(
            """
              fun isEven(number: Int): Boolean {
                  return number % 2 == 0
              }
              """
          ),
          kotlin(
            """
              val numbers = listOf(1, 2, 3, 4, 5)
              val evenNumbers = numbers.filter{isEven(it)}
              """
            ,
            """
              val numbers = listOf(1, 2, 3, 4, 5)
              val evenNumbers = numbers.filter(::isEven)
              """
          )
        );
    }

    @Test
    void memberPropertyAndFunction() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class Person(val name: String) {
                  fun greet(): String = "Hi " + name
              }

              fun names(people: List<Person>, words: List<String>) {
                  people.map { it.name }
                  people.map { p -> p.greet() }
                  words.map { it.length }
              }
              """,
            """
              class Person(val name: String) {
                  fun greet(): String = "Hi " + name
              }

              fun names(people: List<Person>, words: List<String>) {
                  people.map(Person::name)
                  people.map(Person::greet)
                  words.map(String::length)
              }
              """
          )
        );
    }

    @Test
    void topLevelAndMemberFunctions() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              object Formatter {
                  fun format(s: String) = "[" + s + "]"
              }

              class Box(val value: String)

              class Printer {
                  fun print(s: String) {
                  }

                  fun printAll(words: List<String>) {
                      words.forEach { println(it) }
                      words.forEach { print(it) }
                      words.map { Formatter.format(it) }
                      words.map { Box(it) }
                  }
              }
              """,
            """
              object Formatter {
                  fun format(s: String) = "[" + s + "]"
              }

              class Box(val value: String)

              class Printer {
                  fun print(s: String) {
                  }

                  fun printAll(words: List<String>) {
                      words.forEach(::println)
                      words.forEach(this::print)
                      words.map(Formatter::format)
                      words.map(::Box)
                  }
              }
              """
          )
        );
    }

    @Test
    void memberOfClassNamedLikeFileFacade() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class UtilKt {
                  fun f(s: String) {
                  }

                  fun g(words: List<String>) {
                      words.forEach { f(it) }
                  }
              }
              """,
            """
              class UtilKt {
                  fun f(s: String) {
                  }

                  fun g(words: List<String>) {
                      words.forEach(this::f)
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaPositions() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class Counter {
                  fun add(total: Int, s: String): Int = total + s.length

                  fun count(words: List<String>, nested: List<List<String>>) {
                      val length = { s: String -> s.length }
                      words.map({ it.length })
                      words.fold(0) { total, s -> add(total, s) }
                      words.forEach() { println(it) }
                      nested.forEach { it.map { s -> s.length } }
                  }
              }
              """,
            """
              class Counter {
                  fun add(total: Int, s: String): Int = total + s.length

                  fun count(words: List<String>, nested: List<List<String>>) {
                      val length = String::length
                      words.map(String::length)
                      words.fold(0, this::add)
                      words.forEach(::println)
                      nested.forEach { it.map(String::length) }
                  }
              }
              """
          )
        );
    }

    @Test
    void addImportForReceiverType() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              package com.example.model

              class Person(val name: String)

              fun people(): List<Person> = listOf(Person("Alice"))
              """
          ),
          //language=kotlin
          kotlin(
            """
              import com.example.model.people

              fun names() = people().map { it.name }
              """,
            """
              import com.example.model.Person
              import com.example.model.people

              fun names() = people().map(Person::name)
              """
          )
        );
    }

    @Test
    void unchangedWhenReferenceWouldNotBeEquivalent() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import java.io.File

              class Holder {
                  companion object {
                      fun create(s: String) = s
                  }
              }

              class Logger {
                  fun log(s: String) {
                  }
              }

              class Tag(val label: String)

              class Example(var logger: Logger) {
                  fun f(s: String) {
                  }

                  fun overloaded(s: String) {
                  }

                  fun overloaded(i: Int) {
                  }

                  fun <T> wrap(s: String): List<T> = emptyList()

                  fun String.extension(words: List<String>) {
                      words.forEach { f(it) }
                  }

                  fun test(words: List<String?>, nested: List<List<String>>, any: List<Any?>,
                           pairs: Map<String, Int>, files: List<File>, strings: List<String>, tags: List<Tag>) {
                      strings.map { wrap<Int>(it) }
                      files.map { it.name }
                      tags.joinToString { it.label }
                      tags.joinToString(", ") { it.label }
                      tags.joinToString(separator = ", ") { it.label }
                      pairs.forEach { (key, _) -> f(key) }
                      pairs.map { (key, _) -> key.length }
                      words.map { it.isNullOrEmpty() }
                      words.map { it?.length }
                      words.map { it!!.substring(1) }
                      words.forEach { logger.log(it!!) }
                      nested.map { it.size }
                      nested.forEach { inner -> inner.forEach { s -> f(s) } }
                      nested.forEach { run { it.forEach { s -> f(s) } } }
                      any.filter { it != null }
                      any.filter { it is String }
                      any.map { it.toString() }
                      any.map { Holder.create(it as String) }
                      any.forEach { overloaded(it as String) }
                      run { f("") }
                      val print = { s: String -> println(s) }
                      nested.map { it.map { s -> s.uppercase() } }
                      nested.forEach(fun(it) { println(it) })
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedPropertyAccessWhereUnitIsExpected() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class Tag(val label: String)

              fun test(words: List<String>, tags: List<Tag>, s: String) {
                  words.forEach { it.length }
                  tags.forEach { it.label }
                  s.also { it.length }
                  words.forEach(action = { it.length })
                  words.stream().forEach { it.length }
                  val unit: (String) -> Unit = { it.length }
              }
              """
          )
        );
    }
}
