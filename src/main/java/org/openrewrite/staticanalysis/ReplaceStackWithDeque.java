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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.FindLocalFlowPaths;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

public class ReplaceStackWithDeque extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace `java.util.Stack` with `java.util.Deque`";
    }

    @Override
    public String getDescription() {
        return "From the Javadoc of `Stack`:\n" +
               "> A more complete and consistent set of LIFO stack operations is provided by the Deque interface and its implementations, which should be used in preference to this class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.util.Stack", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);

                DataFlowSpec returned = new DataFlowSpec() {
                    @Override
                    public boolean isSource(DataFlowNode srcNode) {
                        return variable.getInitializer() == srcNode.getCursor().getValue();
                    }

                    @Override
                    public boolean isSink(DataFlowNode sinkNode) {
                        return sinkNode.getCursor().firstEnclosing(J.Return.class) != null;
                    }
                };

                if (v.getInitializer() != null && FindLocalFlowPaths.noneMatch(getCursor(), returned)) {
                    v = v.withInitializer((Expression) new ChangeType("java.util.Stack", "java.util.ArrayDeque", false)
                            .getVisitor().visitNonNull(v.getInitializer(), ctx, getCursor().getParentOrThrow()));
                    getCursor().putMessageOnFirstEnclosing(J.VariableDeclarations.class, "replace", true);
                }

                return v;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                if (getCursor().getMessage("replace", false)) {
                    v = v.withTypeExpression((TypeTree) new ChangeType("java.util.Stack", "java.util.Deque", false)
                            .getVisitor().visit(v.getTypeExpression(), ctx, getCursor().getParentOrThrow()));
                    maybeAddImport("java.util.ArrayDeque");
                    maybeAddImport("java.util.Deque");
                }
                return v;
            }
        });
    }
}
