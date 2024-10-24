/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

@SuppressWarnings({"ClassInitializerMayBeStatic", "StatementWithEmptyBody", "ConstantConditions"})
class EqualsAvoidsNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EqualsAvoidsNull());
    }

    @DocumentExample
    @Test
    void invertConditional() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;
              public class A {
                  public static final String EXTERNAL_KEY = "EXTERNAL_KEY";
                  {
                      String s = null;
                      if (s.equals(EXTERNAL_KEY)) {}
                      if (s.equalsIgnoreCase(EXTERNAL_KEY)) {}
                      System.out.println(s.compareTo(EXTERNAL_KEY));
                      System.out.println(s.compareToIgnoreCase(EXTERNAL_KEY));
                      System.out.println(s.contentEquals(EXTERNAL_KEY));
                      System.out.println(Stream.of(EXTERNAL_KEY)
                                               .filter(item -> item.contentEquals(EXTERNAL_KEY))
                                               .findFirst());
                  }

                  boolean isFoo(final String test) {
                      return new B().getBar(EXTERNAL_KEY).contentEquals(EXTERNAL_KEY)
                             || B.getBar2(EXTERNAL_KEY).contentEquals(EXTERNAL_KEY)
                             || test.contentEquals(EXTERNAL_KEY);
                  }
              }

              public static class B {
                  String getBar(final String test) {
                      return null;
                  }

                  static String getBar2(final String test) {
                      return null;
                  }
              }
              """,
            """
              import java.util.stream.Stream;
              public class A {
                  public static final String EXTERNAL_KEY = "EXTERNAL_KEY";
                  {
                      String s = null;
                      if (EXTERNAL_KEY.equals(s)) {}
                      if (EXTERNAL_KEY.equalsIgnoreCase(s)) {}
                      System.out.println(EXTERNAL_KEY.compareTo(s));
                      System.out.println(EXTERNAL_KEY.compareToIgnoreCase(s));
                      System.out.println(EXTERNAL_KEY.contentEquals(s));
                      System.out.println(Stream.of(EXTERNAL_KEY)
                                               .filter(item -> EXTERNAL_KEY.contentEquals(item))
                                               .findFirst());
                  }

                  boolean isFoo(final String test) {
                      return EXTERNAL_KEY.contentEquals(new B().getBar(EXTERNAL_KEY))
                             || EXTERNAL_KEY.contentEquals(B.getBar2(EXTERNAL_KEY))
                             || EXTERNAL_KEY.contentEquals(test);
                  }
              }

              public static class B {
                  String getBar(final String test) {
                      return null;
                  }

                  static String getBar2(final String test) {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnnecessaryNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      String s = null;
                      if (s != null && s.equals("test")) {}
                      if (null != s && s.equals("test")) {}
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = null;
                      if ("test".equals(s)) {}
                      if ("test".equals(s)) {}
                  }
              }
              """
          )
        );
    }

    @Test
    void nullLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void foo(String s) {
                      if (s.equals(null)) {
                      }
                  }
              }
              """,
            """
              public class A {
                  void foo(String s) {
                      if (s == null) {
                      }
                  }
              }
              """
          )
        );
    }
}
