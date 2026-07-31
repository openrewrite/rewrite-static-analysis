/*
 * Copyright 2026 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.TaintFlowSpec;
import org.openrewrite.analysis.dataflow.analysis.FlowGraph;
import org.openrewrite.analysis.dataflow.analysis.ForwardFlow;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;
import org.openrewrite.staticanalysis.table.ExceptionsWithoutCause;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindNewExceptionWithoutCause extends Recipe {

    private static final String TAINTED_KEY = "caughtExceptionTaintedExpressions";
    private static final String CAUGHT_KEY = "caughtExceptionVariable";

    transient ExceptionsWithoutCause report = new ExceptionsWithoutCause(this);

    @Override
    public String getDisplayName() {
        return "Find new exceptions thrown without the caught exception";
    }

    @Override
    public String getDescription() {
        return "Finds `catch` blocks that throw a newly created exception without referencing the caught exception, " +
               "which discards the original exception's stack trace and message. Data flow (taint) tracking is used " +
               "to establish whether the caught exception—or any value derived from it—reaches the thrown exception, " +
               "so indirect references through local variables and string concatenation are not falsely reported. " +
               "This mirrors PMD's `PreserveStackTrace` rule.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new JavaFileChecker<>(),
                new GroovyFileChecker<>(),
                new KotlinFileChecker<>()
        ), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Try.Catch visitCatch(J.Try.Catch aCatch, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable caughtVar = aCatch.getParameter().getTree().getVariables().get(0);
                JavaType.Variable caughtType = caughtVar.getVariableType();
                String caughtName = caughtVar.getSimpleName();

                // Seed a taint analysis from every reference to the caught exception (and every value read off of it,
                // e.g. `e.getMessage()`) and collect all expressions the caught exception flows into.
                Set<Expression> tainted = new HashSet<>();
                ExceptionTaintSpec spec = new ExceptionTaintSpec(caughtType, caughtName);
                new JavaIsoVisitor<Set<Expression>>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<Expression> set) {
                        if (referencesCaught(identifier, caughtType, caughtName)) {
                            seedFlow(getCursor(), spec, set);
                        }
                        return identifier;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<Expression> set) {
                        if (rootReferencesCaught(method.getSelect(), caughtType, caughtName)) {
                            seedFlow(getCursor(), spec, set);
                        }
                        return super.visitMethodInvocation(method, set);
                    }
                }.visit(aCatch.getBody(), tainted, getCursor());

                getCursor().putMessage(TAINTED_KEY, tainted);
                getCursor().putMessage(CAUGHT_KEY, caughtVar);
                return super.visitCatch(aCatch, ctx);
            }

            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                J.Throw t = super.visitThrow(thrown, ctx);
                if (!(t.getException() instanceof J.NewClass)) {
                    return t;
                }
                J.NewClass newException = (J.NewClass) t.getException();

                // Find the `catch` clause that directly governs this `throw`, bailing out if a `try` body, lambda,
                // or other execution boundary sits between them.
                Cursor governing = null;
                for (Cursor c = getCursor().getParent(); c != null; c = c.getParent()) {
                    Object v = c.getValue();
                    if (v instanceof J.Try.Catch) {
                        governing = c;
                        break;
                    }
                    if (v instanceof J.Try || v instanceof J.Lambda || v instanceof J.MethodDeclaration ||
                        v instanceof J.ClassDeclaration ||
                        (v instanceof J.NewClass && ((J.NewClass) v).getBody() != null)) {
                        break;
                    }
                }
                if (governing == null) {
                    return t;
                }

                J.VariableDeclarations.NamedVariable caughtVar = governing.getMessage(CAUGHT_KEY);
                Set<Expression> tainted = governing.getMessage(TAINTED_KEY);
                if (caughtVar == null || tainted == null) {
                    return t;
                }

                JavaType.Variable caughtType = caughtVar.getVariableType();
                String caughtName = caughtVar.getSimpleName();
                if (referencesCaughtException(newException, caughtType, caughtName, tainted)) {
                    return t;
                }

                JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                report.insertRow(ctx, new ExceptionsWithoutCause.Row(
                        sourceFile == null ? "" : sourceFile.getSourcePath().toString(),
                        String.valueOf(caughtType == null ? caughtVar.getType() : caughtType.getType()),
                        String.valueOf(newException.getType())
                ));
                return t.withException(SearchResult.found(newException));
            }

            private void seedFlow(Cursor cursor, ExceptionTaintSpec spec, Set<Expression> tainted) {
                // Dataflow#findSinks gates on control-flow reachability, but a `catch` block is only reached via
                // an exceptional edge that the control-flow graph does not model, so its expressions are considered
                // unreachable and pruned. Drive ForwardFlow directly to obtain the taint graph without that gate.
                DataFlowNode.of(cursor).forEach(node -> {
                    FlowGraph graph = ForwardFlow.findAllFlows(node, spec, FlowGraph.Factory.defaultFactory());
                    Deque<FlowGraph> worklist = new ArrayDeque<>();
                    worklist.add(graph);
                    while (!worklist.isEmpty()) {
                        FlowGraph current = worklist.poll();
                        Object value = current.getNode().getCursor().getValue();
                        if (value instanceof Expression) {
                            tainted.add((Expression) value);
                        }
                        worklist.addAll(current.getEdges());
                    }
                });
            }

            private boolean referencesCaughtException(J newException, JavaType.@Nullable Variable caughtType,
                                                      String caughtName, Set<Expression> tainted) {
                AtomicBoolean referenced = new AtomicBoolean(false);
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public Expression visitExpression(Expression expression, AtomicBoolean found) {
                        if (found.get()) {
                            return expression;
                        }
                        if (tainted.contains(expression) ||
                            (expression instanceof J.Identifier &&
                             referencesCaught((J.Identifier) expression, caughtType, caughtName))) {
                            found.set(true);
                            return expression;
                        }
                        return super.visitExpression(expression, found);
                    }
                }.visit(newException, referenced);
                return referenced.get();
            }
        });
    }

    private static boolean referencesCaught(J.Identifier identifier, JavaType.@Nullable Variable caughtType,
                                            String caughtName) {
        JavaType.Variable fieldType = identifier.getFieldType();
        if (caughtType != null && fieldType != null) {
            return caughtType.equals(fieldType);
        }
        return caughtName.equals(identifier.getSimpleName());
    }

    private static boolean rootReferencesCaught(@Nullable Expression select, JavaType.@Nullable Variable caughtType,
                                                String caughtName) {
        Expression e = select;
        while (e != null) {
            if (e instanceof J.Identifier) {
                return referencesCaught((J.Identifier) e, caughtType, caughtName);
            }
            if (e instanceof J.MethodInvocation) {
                e = ((J.MethodInvocation) e).getSelect();
            } else if (e instanceof J.FieldAccess) {
                e = ((J.FieldAccess) e).getTarget();
            } else {
                return false;
            }
        }
        return false;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class ExceptionTaintSpec extends TaintFlowSpec {
        JavaType.@Nullable Variable caughtType;
        String caughtName;

        @Override
        public boolean isSource(DataFlowNode srcNode) {
            Object v = srcNode.getCursor().getValue();
            if (v instanceof J.Identifier) {
                return referencesCaught((J.Identifier) v, caughtType, caughtName);
            }
            if (v instanceof J.MethodInvocation) {
                return rootReferencesCaught(((J.MethodInvocation) v).getSelect(), caughtType, caughtName);
            }
            return false;
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return true;
        }
    }
}
