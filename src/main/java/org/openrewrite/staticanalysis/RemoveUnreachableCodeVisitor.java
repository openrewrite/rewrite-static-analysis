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

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;

class RemoveUnreachableCodeVisitor extends JavaIsoVisitor<ExecutionContext> {

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);
        List<Statement> statements = b.getStatements();
        int firstJumpIndex = findFirstJump(statements);
        if (firstJumpIndex == -1 || firstJumpIndex == statements.size() - 1) {
            return b;
        }
        return b.withStatements(statements.subList(0, firstJumpIndex + 1));
    }

    private int findFirstJump(List<Statement> statements) {
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (statement instanceof J.Return ||
                statement instanceof J.Throw ||
                statement instanceof J.Break ||
                statement instanceof J.Continue) {
                return i;
            }
        }
        return -1;
    }
}
