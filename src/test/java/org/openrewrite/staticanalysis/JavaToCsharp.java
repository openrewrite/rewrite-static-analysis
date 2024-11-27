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

import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.stream.Collectors;

public class JavaToCsharp {

    public static Cs.CompilationUnit compilationUnit(J.CompilationUnit cu) {
        return new Cs.CompilationUnit(cu.getId(), cu.getPrefix(), cu.getMarkers(), cu.getSourcePath(),
          cu.getFileAttributes(), cu.getCharset().name(), cu.isCharsetBomMarked(), cu.getChecksum(), List.of(), List.of(), List.of(), cu.getClasses().stream().map(Statement.class::cast).map(cd -> JRightPadded.build(cd)).collect(Collectors.toList()), cu.getEof());
    }
}
