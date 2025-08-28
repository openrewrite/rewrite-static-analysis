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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;

import static org.openrewrite.java.format.ShiftFormat.indent;
import static org.openrewrite.java.tree.Space.format;

public class SimplifyElseBranch extends Recipe {

    @Override
    public String getDisplayName() {
        // language=markdown
        return "Simplify `else` branch if it only has a single `if`";
    }

    @Override
    public String getDescription() {
        // language=markdown
        return "Simplify `else` branch if it only has a single `if`.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.If.Else visitElse(J.If.Else else_, ExecutionContext ctx) {
                J.If.Else elseStatement = super.visitElse(else_, ctx);
                final Statement body = elseStatement.getBody();
                if (body instanceof J.Block) {
                    final J.Block block = (J.Block) body;
                    if (block.getStatements().size() == 1) {
                        final Statement firstStatement = block.getStatements().get(0);
                        if (firstStatement instanceof J.If) {
                            J.If ifStatement = (J.If) firstStatement;
                            // Combine comments from the block and the if statement
                            final List<Comment> blockComments = block.getComments();
                            final List<Comment> ifComments = ifStatement.getComments();
                            ifStatement = ifStatement.withPrefix(format(" "));
                            if (!ifComments.isEmpty() || !blockComments.isEmpty()) {
                                ifStatement = ifStatement.withComments(ListUtils.concatAll(blockComments, ifComments));
                            }
                            elseStatement = elseStatement.withBody(indent(ifStatement, getCursor(), -1));
                        }
                    }
                }
                return elseStatement;
            }
        };
    }
}
