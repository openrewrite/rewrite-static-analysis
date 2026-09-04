/*
 * Copyright 2025 the original author or authors.
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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.ReflectionUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.python.tree.Py;

import java.util.List;

import static org.openrewrite.java.format.ShiftFormat.indent;

public class SimplifyElseBranch extends Recipe {

    private static final boolean IS_PYTHON_AVAILABLE = ReflectionUtils.isClassAvailable("org.openrewrite.python.tree.Py");

    @Getter
    final String displayName = "Simplify `else` branch if it only has a single `if`";

    @Getter
    final String description = "Simplify `else` branch if it only has a single `if`.";

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.If.Else visitElse(J.If.Else else_, ExecutionContext ctx) {
                J.If.Else elseStatement = super.visitElse(else_, ctx);
                Statement body = elseStatement.getBody();
                if (body instanceof J.Block) {
                    J.Block block = (J.Block) body;
                    if (block.getStatements().size() == 1) {
                        Statement firstStatement = block.getStatements().get(0);
                        if (firstStatement instanceof J.If) {
                            List<Comment> comments = ListUtils.concatAll(block.getComments(), firstStatement.getComments());
                            if (IS_PYTHON_AVAILABLE && getCursor().firstEnclosing(Py.CompilationUnit.class) != null) {
                                // Python renders `else` + `if` as a single `elif` keyword, so the `if` keeps an empty
                                // prefix and any comments move ahead of it, onto the `elif` line
                                Space elsePrefix = elseStatement.getPrefix();
                                Space withComments = elsePrefix.withComments(ListUtils.concatAll(elsePrefix.getComments(),
                                        ListUtils.map(comments, c -> c.withSuffix(elsePrefix.getWhitespace()))));
                                J.If ifStatement = firstStatement.withPrefix(Space.EMPTY);
                                return elseStatement.withPrefix(withComments).withBody(indent(ifStatement, getCursor(), -1));
                            }
                            // Combine comments from the block and the if statement
                            J.If ifStatement = firstStatement
                                    .withPrefix(Space.SINGLE_SPACE)
                                    .withComments(comments);
                            return elseStatement.withBody(indent(ifStatement, getCursor(), -1));
                        }
                    }
                }
                return elseStatement;
            }
        };
    }
}
