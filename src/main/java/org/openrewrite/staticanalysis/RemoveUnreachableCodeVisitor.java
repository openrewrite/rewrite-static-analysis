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
import org.openrewrite.java.service.ScopeService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class RemoveUnreachableCodeVisitor extends JavaIsoVisitor<ExecutionContext> {

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);
        List<Statement> statements = b.getStatements();
        ScopeService scopes = service(ScopeService.class);
        for (int i = 0; i < statements.size() - 1; i++) {
            if (!completesNormally(scopes, statements.get(i))) {
                return b.withStatements(statements.subList(0, i + 1));
            }
        }
        return b;
    }

    /**
     * Whether the statement can complete normally (JLS 14.22). {@link ScopeService#completesNormally(Statement)} does
     * not count a continue as a way out of a do-while, so a statement with a continue inside a do-while is taken to
     * complete.
     */
    static boolean completesNormally(ScopeService scopes, Statement statement) {
        return scopes.completesNormally(statement) || new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Continue visitContinue(J.Continue continueStatement, AtomicBoolean inDoWhile) {
                if (getCursor().firstEnclosing(J.DoWhileLoop.class) != null) {
                    inDoWhile.set(true);
                }
                return continueStatement;
            }
        }.reduce(statement, new AtomicBoolean()).get();
    }
}
