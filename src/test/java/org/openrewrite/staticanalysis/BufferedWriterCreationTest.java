/*
 * Copyright 2024 the original author or authors.
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

@SuppressWarnings("EmptyTryBlock")
public class BufferedWriterCreationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BufferedWriterCreation());
    }

    @Test
    @DocumentExample
    void bufferedReaderCreation() {
        rewriteRun(
          java(
            """
              import java.io.BufferedWriter;
              import java.io.FileWriter;
              import java.io.File;
              import java.io.IOException;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(File f) throws IOException {
                      try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
              
                      }
                  }
              }
              """,
            """
              import java.io.BufferedWriter;
              import java.io.File;
              import java.io.IOException;
              import java.nio.file.Files;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(File f) throws IOException {
                      try (BufferedWriter writer = Files.newBufferedWriter(f.toPath())) {
              
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void bufferedReaderCreationAppend() {
        rewriteRun(
          java(
            """
              import java.io.BufferedWriter;
              import java.io.FileWriter;
              import java.io.File;
              import java.io.IOException;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(File f) throws IOException {
                      try (BufferedWriter writer = new BufferedWriter(new FileWriter(f, true))) {
              
                      }
                  }
              }
              """,
            """
              import java.io.BufferedWriter;
              import java.io.File;
              import java.io.IOException;
              import java.nio.file.Files;
              import java.nio.file.StandardOpenOption;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(File f) throws IOException {
                      try (BufferedWriter writer = Files.newBufferedWriter(f.toPath(), StandardOpenOption.APPEND)) {
              
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void bufferedReaderStringCreation() {
        rewriteRun(
          java(
            """
              import java.io.BufferedWriter;
              import java.io.FileWriter;
              import java.io.File;
              import java.io.IOException;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(String f) throws IOException {
                      try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
              
                      }
                  }
              }
              """,
            """
              import java.io.BufferedWriter;
              import java.io.File;
              import java.io.IOException;
              import java.nio.file.Files;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(String f) throws IOException {
                      try (BufferedWriter writer = Files.newBufferedWriter(new File(f).toPath())) {
              
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void bufferedReaderStringCreationAppend() {
        rewriteRun(
          java(
            """
              import java.io.BufferedWriter;
              import java.io.FileWriter;
              import java.io.File;
              import java.io.IOException;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(String f) throws IOException {
                      try (BufferedWriter writer = new BufferedWriter(new FileWriter(f, false))) {
              
                      }
                  }
              }
              """,
            """
              import java.io.BufferedWriter;
              import java.io.File;
              import java.io.IOException;
              import java.nio.file.Files;
              import java.nio.file.StandardOpenOption;
              
              public class BufferedWriterCreationTest {
                  public void createBufferedWriter(String f) throws IOException {
                      try (BufferedWriter writer = Files.newBufferedWriter(new File(f).toPath(), StandardOpenOption.CREATE)) {
              
                      }
                  }
              }
              """
          )
        );
    }


}
