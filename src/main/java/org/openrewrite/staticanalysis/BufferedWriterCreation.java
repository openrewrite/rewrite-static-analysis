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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.Primitive;
import org.openrewrite.java.template.RecipeDescriptor;

import java.io.BufferedWriter;
import java.io.IOException;

@RecipeDescriptor(
        name = "Modernize `BufferedWriter` creation & prevent file descriptor leaks",
        description = "The code `new BufferedWriter(new FileWriter(f))` creates a `BufferedWriter` that does not close the underlying `FileWriter` when it is closed. " +
                      "This can lead to file descriptor leaks as per [CWE-755](https://cwe.mitre.org/data/definitions/755.html). " +
                      "Use `Files.newBufferedWriter` to create a `BufferedWriter` that closes the underlying file descriptor when it is closed."
)
public class BufferedWriterCreation {

    @RecipeDescriptor(
            name = "Convert `new BufferedWriter(new FileWriter(File))` to `Files.newBufferedWriter(Path)`",
            description = "Convert `new BufferedWriter(new FileWriter(f))` to `Files.newBufferedWriter(f.toPath())`."
    )
    static class BufferedWriterFromNewFileWriterWithFileArgument {
        @BeforeTemplate
        BufferedWriter before(java.io.File f) throws IOException {
            return new BufferedWriter(new java.io.FileWriter(f));
        }

        @AfterTemplate
        BufferedWriter after(java.io.File f) throws IOException {
            return java.nio.file.Files.newBufferedWriter(f.toPath());
        }
    }

    @RecipeDescriptor(
            name = "Convert `new BufferedWriter(new FileWriter(String))` to `Files.newBufferedWriter(Path)`",
            description = "Convert `new BufferedWriter(new FileWriter(s))` to `Files.newBufferedWriter(new java.io.File(s).toPath())`."
    )
    static class BufferedWriterFromNewFileWriterWithStringArgument {
        @BeforeTemplate
        BufferedWriter before(String s) throws IOException {
            return new BufferedWriter(new java.io.FileWriter(s));
        }

        @AfterTemplate
        BufferedWriter after(String s) throws IOException {
            return java.nio.file.Files.newBufferedWriter(new java.io.File(s).toPath());
        }
    }

    @RecipeDescriptor(
            name = "Convert `new BufferedWriter(new FileWriter(File, boolean))` to `Files.newBufferedWriter(Path, StandardOpenOption)`",
            description = "Convert `new BufferedWriter(new FileWriter(f, b))` to `Files.newBufferedWriter(f.toPath(), b ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)`."
    )
    static class BufferedWriterFromNewFileWriterWithFileAndBooleanArguments {
        @BeforeTemplate
        BufferedWriter before(java.io.File f, @Primitive Boolean b) throws IOException {
            return new BufferedWriter(new java.io.FileWriter(f, b));
        }

        @AfterTemplate
        BufferedWriter after(java.io.File f, @Primitive Boolean b) throws IOException {
            return java.nio.file.Files.newBufferedWriter(f.toPath(), b ?
                    java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        }
    }

    @RecipeDescriptor(
            name = "Convert `new BufferedWriter(new FileWriter(String, boolean))` to `Files.newBufferedWriter(Path, StandardOpenOption)`",
            description = "Convert `new BufferedWriter(new FileWriter(s, b))` to `Files.newBufferedWriter(new java.io.File(s).toPath(), b ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)`."
    )
    static class BufferedWriterFromNewFileWriterWithStringAndBooleanArguments {
        @BeforeTemplate
        BufferedWriter before(String s, @Primitive Boolean b) throws IOException {
            return new BufferedWriter(new java.io.FileWriter(s, b));
        }

        @AfterTemplate
        BufferedWriter after(String s, @Primitive Boolean b) throws IOException {
            return java.nio.file.Files.newBufferedWriter(new java.io.File(s).toPath(), b ?
                    java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        }
    }

}
