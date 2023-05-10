/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UnwrapRepeatableAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnwrapRepeatableAnnotations())
            .parser(JavaParser.fromJavaVersion()
                .dependsOn(
                    //language=java
                    """
                      package com.exmaple;
                                                              
                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;
      
                      @Target(ElementType.TYPE)
                      public @interface Annotations {
                          Annotation[] value();
                      }
                      """,
                    //language=java
                    """
                      package com.example;
                                                              
                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Repeatable;
                      import java.lang.annotation.Target;
                                                              
                      @Repeatable(Tests.class)
                      @Target(ElementType.TYPE)
                      public @interface Annotation {
                      }
                      """
                )
            );
    }

    @DocumentExample
    @Test
    void unwrapRepeatable() {
        rewriteRun(
            //language=java
            java(
                """
                  import com.example.*;
    
                  @Annotations({@Annotation, @Annotation})
                  class Test {
                  }
                  """,
                """
                  import com.example.*;
    
                  @Annotation
                  @Annotation
                  class Test {
                  }
                  """
            )
        );
    }
}
