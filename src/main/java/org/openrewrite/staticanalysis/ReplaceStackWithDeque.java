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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.FindLocalFlowPaths;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceStackWithDeque extends Recipe {
    String displayName = "Replace `java.util.Stack` with `java.util.Deque`";

    String description = "From the Javadoc of `Stack`:\n" +
            "> A more complete and consistent set of LIFO stack operations is provided by " +
            "the Deque interface and its implementations, which should be used in preference " +
            "to this class.\n\n" +
            "`Stack` inherits from `Vector`, which carries unnecessary synchronization overhead " +
            "in single-threaded contexts and exposes non-stack operations like random index access.";

    Set<String> tags = singleton("RSPEC-S1149");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(20);

    /**
     * Source file cursor message mapping each variable redeclared as a {@code Deque} to its retyped form,
     * which the references visited after the declaration take on.
     */
    private static final String RETYPED_VARIABLES = "retypedVariables";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.util.Stack", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
                if (v.getInitializer() == null) {
                    return v;
                }

                Expression initializer = (Expression) new ChangeType("java.util.Stack", "java.util.ArrayDeque", false)
                        .getVisitor().visitNonNull(v.getInitializer(), ctx, getCursor().getParentOrThrow());
                if (initializer == v.getInitializer()) {
                    // Not a `Stack`, so skip the data flow analysis below, which is costly on every variable in the file
                    return v;
                }

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

                boolean returnedSomewhere;
                try {
                    returnedSomewhere = !FindLocalFlowPaths.noneMatch(getCursor(), returned);
                } catch (RuntimeException e) {
                    // rewrite-analysis 2.39.0, its last release, cannot follow some flows, as through an anonymous
                    // class with several returning methods: a Stack whose flow it cannot follow is left as it is.
                    returnedSomewhere = true;
                }
                if (!returnedSomewhere) {
                    v = v.withInitializer(initializer);
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
                    JavaType deque = v.getTypeExpression() == null ? null : v.getTypeExpression().getType();
                    if (deque != null) {
                        Map<JavaType.Variable, JavaType.Variable> retyped = getCursor()
                                .dropParentUntil(JavaSourceFile.class::isInstance)
                                .computeMessageIfAbsent(RETYPED_VARIABLES, k -> new IdentityHashMap<>());
                        v = v.withVariables(ListUtils.map(v.getVariables(), nv -> {
                            JavaType.Variable stack = nv.getVariableType();
                            if (stack == null) {
                                return nv;
                            }
                            JavaType.Variable variable = stack.withType(deque);
                            retyped.put(stack, variable);
                            return nv.withVariableType(variable)
                                    .withName(nv.getName().withType(deque).withFieldType(variable));
                        }));
                    }
                    maybeAddImport("java.util.ArrayDeque");
                    maybeAddImport("java.util.Deque");
                    maybeRemoveImport("java.util.Stack");
                }
                return v;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                Map<JavaType.Variable, JavaType.Variable> retyped = getCursor().getNearestMessage(RETYPED_VARIABLES);
                JavaType.Variable variable = retyped == null || i.getFieldType() == null ? null : retyped.get(i.getFieldType());
                return variable == null ? i : i.withType(variable.getType()).withFieldType(variable);
            }
        });
    }
}
