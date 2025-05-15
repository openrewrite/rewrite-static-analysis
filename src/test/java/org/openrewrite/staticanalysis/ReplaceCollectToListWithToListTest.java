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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class InvertReplaceCollectToListWithToListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceCollectToListWithToList());
    }

    @Test
    void replacesCollectToList() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                package com.example;

                import java.util.stream.Collectors;
                import java.util.stream.Stream;

                class Example {
                    public void test() {
                        Stream.of().collect(Collectors.toList());
                    }
                }
                """,
              """
                package com.example;

                import java.util.stream.Stream;

                class Example {
                    public void test() {
                        Stream.of().toList();
                    }
                }
                """),
            17));
    }

    @Test
    void replacesCollectToListKeepImport() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                package com.example;

                import java.util.List;
                import java.util.stream.Collector;
                import java.util.stream.Collectors;
                import java.util.stream.Stream;

                class Example {
                    public void test() {
                        Collector<Object,?,List<Object>> list = Collectors.toList();
                        Stream.of().collect(list);
                        Stream.of().collect(Collectors.toList());
                    }
                }
                """,
              """
                package com.example;

                import java.util.List;
                import java.util.stream.Collector;
                import java.util.stream.Collectors;
                import java.util.stream.Stream;

                class Example {
                    public void test() {
                        Collector<Object,?,List<Object>> list = Collectors.toList();
                        Stream.of().collect(list);
                        Stream.of().toList();
                    }
                }
                """),
            17));
    }
}
