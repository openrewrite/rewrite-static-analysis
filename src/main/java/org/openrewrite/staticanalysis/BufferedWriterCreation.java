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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.Semantics;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Set;

import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.SHORTEN_NAMES;

public class BufferedWriterCreation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Modernize BufferedWriter creation & prevent file descriptor leak";
    }

    @Override
    public String getDescription() {
        return "The code `new BufferedWriter(new FileWriter(f))` creates a `BufferedWriter` that does not close the underlying `FileWriter` when it is closed. " +
               "This can lead to file descriptor leaks. " +
               "Use `Files.newBufferedWriter` to create a `BufferedWriter` that closes the underlying file descriptor when it is closed.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("CWE-755");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {
            final JavaTemplate beforeFile = Semantics.expression(
                    this,
                    "beforeFile",
                    (java.io.File f) -> new java.io.BufferedWriter(new java.io.FileWriter(f))
            ).build();
            final JavaTemplate afterFile = Semantics.expression(
                    this,
                    "afterFile",
                    (java.io.File f) -> java.nio.file.Files.newBufferedWriter(f.toPath())
            ).build();

            final JavaTemplate beforeString = Semantics.expression(
                    this,
                    "beforeString",
                    (String f) -> new java.io.BufferedWriter(new java.io.FileWriter(f))
            ).build();
            final JavaTemplate afterString = Semantics.expression(
                    this,
                    "afterString",
                    (String f) -> java.nio.file.Files.newBufferedWriter(new java.io.File(f).toPath())
            ).build();

            final JavaTemplate beforeFileBoolean = Semantics.expression(
                    this,
                    "beforeFileBoolean",
                    (java.io.File f, Boolean b) -> new java.io.BufferedWriter(new java.io.FileWriter(f, b))
            ).build();
            final JavaTemplate afterFileBoolean = Semantics.expression(
                    this,
                    "afterFileBoolean",
                    (java.io.File f, Boolean b) -> java.nio.file.Files.newBufferedWriter(f.toPath(), b ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE)
            ).build();

            final JavaTemplate beforeStringBoolean = Semantics.expression(
                    this,
                    "beforeStringBoolean",
                    (String f, Boolean b) -> new java.io.BufferedWriter(new java.io.FileWriter(f, b))
            ).build();
            final JavaTemplate afterStringBoolean = Semantics.expression(
                    this,
                    "afterStringBoolean",
                    (String f, Boolean b) -> java.nio.file.Files.newBufferedWriter(new java.io.File(f).toPath(), b ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE)
            ).build();

            @Override
            public J visitNewClass(J.NewClass elem, ExecutionContext ctx) {
                J j1 = replaceOneArg(elem, ctx, beforeFile, afterFile);
                if (j1 != null) {
                    return j1;
                }
                J j2 = replaceOneArg(elem, ctx, beforeString, afterString);
                if (j2 != null) {
                    return j2;
                }
                J j3 = replaceTwoArg(elem, ctx, beforeFileBoolean, afterFileBoolean);
                if (j3 != null) {
                    return j3;
                }
                J j4 = replaceTwoArg(elem, ctx, beforeStringBoolean, afterStringBoolean);
                if (j4 != null) {
                    return j4;
                }
                return super.visitNewClass(elem, ctx);
            }

            private @Nullable J replaceOneArg(J.NewClass elem, ExecutionContext ctx, JavaTemplate before, JavaTemplate after) {
                JavaTemplate.Matcher matcher;
                if ((matcher = before.matcher(getCursor())).find()) {
                    maybeRemoveImport("java.io.FileWriter");
                    return embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                return null;
            }

            private @Nullable J replaceTwoArg(J.NewClass elem, ExecutionContext ctx, JavaTemplate before, JavaTemplate after) {
                JavaTemplate.Matcher matcher;
                if ((matcher = before.matcher(getCursor())).find()) {
                    maybeRemoveImport("java.io.FileWriter");
                    J j = embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0), matcher.parameter(1)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                    return (J) new SimplifyConstantTernaryExecution().getVisitor().visitNonNull(j, ctx, getCursor().getParentOrThrow());
                }
                return null;
            }

        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("java.io.BufferedWriter", true),
                        new UsesType<>("java.io.FileWriter", true),
                        new UsesMethod<>("java.io.BufferedWriter <constructor>(..)"),
                        new UsesMethod<>("java.io.FileWriter <constructor>(..)")
                ),
                javaVisitor
        );
    }
}
