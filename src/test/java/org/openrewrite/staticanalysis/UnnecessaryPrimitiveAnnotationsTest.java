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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UnnecessaryPrimitiveAnnotationsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryPrimitiveAnnotations())
          .parser(JavaParser.fromJavaVersion().classpath("jsr305"));
    }

    @Test
    void nullableOnNonPrimitive() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.annotation.CheckForNull;
              import javax.annotation.Nullable;
              class A {
                  @Nullable
                  private long[] partitionLengths;
                  
                  @CheckForNull
                  public Object getCount(@Nullable Object val) {
                      return val;
                  }
                  
                  @Nullable
                  public byte[] getBytes() {
                      return null;
                  }
                  
                  public void doSomething(long requestId, long stageId, String component, String host,
                                            String type, boolean skipFailure) {
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void unnecessaryNullable() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.annotation.CheckForNull;
              import javax.annotation.Nullable;
              class A {
                  @CheckForNull
                  public int getCount(@Nullable int val) {
                      return val;
                  }
              }
              """,
            """
              class A {
              
                  public int getCount(int val) {
                      return val;
                  }
              }
              """
          )
        );
    }
}
