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

import static org.openrewrite.java.Assertions.java;

class AvoidRepeatedPatternCompileTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AvoidRepeatedPatternCompile());
    }

    /**
     * Main happy path:
     * <p>
     * Pattern compiled inside method from literal regex
     * becomes static final field.
     */
    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/622")
    void extractLiteralPatternToConstant() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class EmailValidator {

                  boolean valid(String email) {
                      Pattern emailPattern = Pattern.compile("[a-z]+");
                      return emailPattern.matcher(email).matches();
                  }
              }
              """,
            """
              import java.util.regex.Pattern;

              class EmailValidator {

                  private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-z]+");

                  boolean valid(String email) {
                      return EMAIL_PATTERN.matcher(email).matches();
                  }
              }
              """
          )
        );
    }

    /**
     * Dynamic regex must NOT be moved.
     * <p>
     * We don't know its value until runtime.
     */
    @Test
    void doNotChangeDynamicRegex() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class Validator {

                  boolean valid(String value, String regex) {
                      Pattern pattern = Pattern.compile(regex);
                      return pattern.matcher(value).matches();
                  }
              }
              """
          )
        );
    }

    /**
     * Regex returned from another method is runtime-generated,
     * so it must NOT be moved.
     */
    @Test
    void doNotChangeMethodGeneratedRegex() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class Validator {

                  boolean valid(String value) {
                      Pattern pattern = Pattern.compile(createRegex());
                      return pattern.matcher(value).matches();
                  }

                  private String createRegex() {
                      return "[a-z]+";
                  }
              }
              """
          )
        );
    }

    /**
     * Pattern.compile(String, int) should work when the flags
     * are compile-time constants.
     */
    @Test
    void extractPatternWithConstantFlags() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class EmailValidator {

                  boolean valid(String email) {
                      Pattern emailPattern = Pattern.compile("[a-z]+", Pattern.CASE_INSENSITIVE);
                      return emailPattern.matcher(email).matches();
                  }
              }
              """,
            """
              import java.util.regex.Pattern;

              class EmailValidator {

                  private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-z]+", Pattern.CASE_INSENSITIVE);

                  boolean valid(String email) {
                      return EMAIL_PATTERN.matcher(email).matches();
                  }
              }
              """
          )
        );
    }

    /**
     * Runtime flags must NOT be moved to a static field.
     */
    @Test
    void doNotChangeDynamicFlags() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class Validator {

                  boolean valid(String value, int flags) {
                      Pattern pattern = Pattern.compile("[a-z]+", flags);
                      return pattern.matcher(value).matches();
                  }
              }
              """
          )
        );
    }

    /**
     * This verifies that we use JavaType.Variable rather
     * than blindly replacing everything named "pattern".
     */
    @Test
    void doesNotReplaceUnrelatedVariableWithSameName() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class Validator {

                  boolean first(String value) {
                      Pattern pattern = Pattern.compile("[a-z]+");
                      return pattern.matcher(value).matches();
                  }

                  boolean second() {
                      String pattern = "hello";
                      return pattern.isEmpty();
                  }
              }
              """,
            """
              import java.util.regex.Pattern;

              class Validator {

                  private static final Pattern FIRST_PATTERN = Pattern.compile("[a-z]+");

                  boolean first(String value) {
                      return FIRST_PATTERN.matcher(value).matches();
                  }

                  boolean second() {
                      String pattern = "hello";
                      return pattern.isEmpty();
                  }
              }
              """
          )
        );
    }

    /**
     * More than one local Pattern should result in
     * more than one static final field.
     */
    @Test
    void multiplePatternsInSameClass() {
        rewriteRun(
          java(
            """
              import java.util.regex.Pattern;

              class Validator {

                  boolean valid(String email, String phone) {
                      Pattern emailPattern = Pattern.compile("[a-z]+");
                      Pattern phonePattern = Pattern.compile("[0-9]+");
                      return emailPattern.matcher(email).matches() &&
                             phonePattern.matcher(phone).matches();
                  }
              }
              """,
            """
              import java.util.regex.Pattern;

              class Validator {

                  private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-z]+");
                  private static final Pattern PHONE_PATTERN = Pattern.compile("[0-9]+");

                  boolean valid(String email, String phone) {
                      return EMAIL_PATTERN.matcher(email).matches() &&
                             PHONE_PATTERN.matcher(phone).matches();
                  }
              }
              """
          )
        );
    }
}
