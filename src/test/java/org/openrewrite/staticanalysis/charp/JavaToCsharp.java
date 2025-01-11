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
package org.openrewrite.staticanalysis.charp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.test.AdHocRecipe;

import java.util.List;

import static org.openrewrite.test.RewriteTest.toRecipe;

public class JavaToCsharp {

    public static AdHocRecipe toCsRecipe(Recipe recipe) {
        return toRecipe(() -> new JavaVisitor<>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                Cs.CompilationUnit cscu = compilationUnit(cu);
                // Exercise the regular recipe with the now modified CSharp compilation unit
                return (J) recipe.getVisitor().visit(cscu, ctx);
            }
        });
    }

    private static Cs.CompilationUnit compilationUnit(J.CompilationUnit cu) {
        return new Cs.CompilationUnit(
          cu.getId(),
          cu.getPrefix(),
          cu.getMarkers(),
          cu.getSourcePath(),
          cu.getFileAttributes(),
          cu.getCharset().name(),
          cu.isCharsetBomMarked(),
          cu.getChecksum(),
          List.of(),
          List.of(),
          List.of(),
          cu.getClasses().stream()
            .map(Statement.class::cast)
            .map(JRightPadded::build)
            .toList(),
          cu.getEof());
    }
}
