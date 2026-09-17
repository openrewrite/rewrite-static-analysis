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
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;
import org.openrewrite.staticanalysis.table.ExceptionsWithoutCause;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindNewExceptionWithoutCause extends Recipe {

    private static final String TAINTED_KEY = "caughtExceptionTaint";
    private static final String POSITIONS_KEY = "throwPositions";
    private static final int SNIPPET_LENGTH = 120;

    transient ExceptionsWithoutCause report = new ExceptionsWithoutCause(this);

    String displayName = "Find new exceptions thrown without the caught exception";

    String description = "Finds `catch` blocks that throw a newly created exception without referencing the caught exception, " +
            "which discards the original exception's stack trace and message. Taint tracking over the local variables of " +
            "the `catch` block establishes whether the caught exception—or any value derived from it—reaches the thrown " +
            "exception, so indirect references through local variables, helper calls and string concatenation are not " +
            "falsely reported. This mirrors PMD's `PreserveStackTrace` rule.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new JavaFileChecker<>(),
                new GroovyFileChecker<>(),
                new KotlinFileChecker<>()
        ), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Try.Catch visitCatch(J.Try.Catch aCatch, ExecutionContext ctx) {
                // A nested `catch` inherits the enclosing taint, because a `throw` inside it can still preserve an
                // exception caught further out.
                Taint taint = new Taint(getCursor().getNearestMessage(TAINTED_KEY));
                J.VariableDeclarations.NamedVariable caught = aCatch.getParameter().getTree().getVariables().get(0);
                taint.add(caught.getVariableType(), caught.getSimpleName());
                propagate(aCatch.getBody(), taint);

                getCursor().putMessage(TAINTED_KEY, taint);
                return super.visitCatch(aCatch, ctx);
            }

            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                J.Throw t = super.visitThrow(thrown, ctx);

                // A builder chain such as `new ServiceError().withMessage(...)` throws the exception at the chain root.
                Expression exception = t.getException();
                Expression root = exception;
                while (root instanceof J.MethodInvocation) {
                    root = ((J.MethodInvocation) root).getSelect();
                }
                if (!(root instanceof J.NewClass)) {
                    return t;
                }
                J.NewClass newException = (J.NewClass) root;

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

                Taint taint = governing.getMessage(TAINTED_KEY);
                if (taint == null || containsTainted(exception, taint)) {
                    return t;
                }

                J.VariableDeclarations parameter = ((J.Try.Catch) governing.getValue()).getParameter().getTree();
                String caughtType = caughtTypeName(parameter);
                String thrownType = fullyQualifiedName(exception.getType());
                boolean typeResolved = caughtType != null && thrownType != null;
                if (caughtType == null) {
                    caughtType = parameter.getTypeExpression() == null ? "" :
                            parameter.getTypeExpression().printTrimmed(getCursor());
                }
                if (thrownType == null) {
                    thrownType = newException.getClazz() == null ? "" :
                            newException.getClazz().printTrimmed(getCursor());
                }

                JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                int[] position = {0, 0};
                if (sourceFile != null) {
                    // The root cursor is shared by every source file in a cycle, so the cache hangs off this file's.
                    position = getCursor().dropParentUntil(JavaSourceFile.class::isInstance)
                            .<Map<UUID, int[]>>computeMessageIfAbsent(POSITIONS_KEY, k -> throwPositions(sourceFile))
                            .getOrDefault(thrown.getId(), position);
                }
                String snippet = thrown.printTrimmed(getCursor()).replaceAll("\\s+", " ");
                if (snippet.length() > SNIPPET_LENGTH) {
                    snippet = snippet.substring(0, SNIPPET_LENGTH - 3) + "...";
                }

                report.insertRow(ctx, new ExceptionsWithoutCause.Row(
                        sourceFile == null ? "" : sourceFile.getSourcePath().toString(),
                        caughtType,
                        thrownType,
                        typeResolved,
                        position[0],
                        position[1],
                        snippet
                ));
                return t.withException(SearchResult.found(exception));
            }
        });
    }

    /**
     * Taints every local derived from an already tainted one, to a fixpoint, so that a chain such as
     * `r = e.getLastAttempt(); cause = r.getFailureCause()` taints both `r` and `cause`.
     */
    private static void propagate(J.Block body, Taint taint) {
        List<Flow> flows = new ArrayList<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                if (variable.getInitializer() != null) {
                    flows.add(new Flow(variable.getVariableType(), variable.getSimpleName(), variable.getInitializer()));
                }
                return super.visitVariable(variable, p);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, Integer p) {
                if (assignment.getVariable() instanceof J.Identifier) {
                    J.Identifier target = (J.Identifier) assignment.getVariable();
                    flows.add(new Flow(target.getFieldType(), target.getSimpleName(), assignment.getAssignment()));
                }
                return super.visitAssignment(assignment, p);
            }

            @Override
            public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, Integer p) {
                if (instanceOf.getPattern() instanceof J.Identifier) {
                    J.Identifier binding = (J.Identifier) instanceOf.getPattern();
                    flows.add(new Flow(binding.getFieldType(), binding.getSimpleName(), instanceOf.getExpression()));
                }
                return super.visitInstanceOf(instanceOf, p);
            }
        }.visit(body, 0);

        for (boolean grew = true; grew; ) {
            grew = false;
            for (Flow flow : flows) {
                if (containsTainted(flow.getValue(), taint)) {
                    grew |= taint.add(flow.getType(), flow.getName());
                }
            }
        }
    }

    private static boolean containsTainted(Expression value, Taint taint) {
        AtomicBoolean found = new AtomicBoolean(false);
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean f) {
                // An accessor named like a tainted local, `cfg.message()`, reads nothing from that local.
                visit(method.getSelect(), f);
                for (Expression argument : method.getArguments()) {
                    visit(argument, f);
                }
                return method;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                if (taint.contains(identifier)) {
                    f.set(true);
                }
                return identifier;
            }
        }.visit(value, found);
        return found.get();
    }

    /**
     * A multi-catch parameter is typed as the least upper bound of its alternatives, which names none of the types
     * actually caught, so the alternatives are joined as written.
     */
    private static @Nullable String caughtTypeName(J.VariableDeclarations parameter) {
        if (parameter.getTypeExpression() instanceof J.MultiCatch) {
            StringJoiner alternatives = new StringJoiner(" | ");
            for (NameTree alternative : ((J.MultiCatch) parameter.getTypeExpression()).getAlternatives()) {
                String name = fullyQualifiedName(alternative.getType());
                if (name == null) {
                    return null;
                }
                alternatives.add(name);
            }
            return alternatives.toString();
        }
        return fullyQualifiedName(parameter.getVariables().get(0).getType());
    }

    private static @Nullable String fullyQualifiedName(@Nullable JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    /**
     * A marker on each `J.Throw` is what gives the capture its hook: a printer consults the marker printer only for
     * markers a node actually carries.
     */
    private static Map<UUID, int[]> throwPositions(JavaSourceFile sourceFile) {
        JavaSourceFile marked = (JavaSourceFile) requireNonNull(new JavaIsoVisitor<Integer>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, Integer p) {
                return super.visitThrow(thrown, p).withMarkers(thrown.getMarkers().add(new ThrowPosition(randomId())));
            }
        }.visit(sourceFile, 0));

        PositionCapture capture = new PositionCapture();
        marked.<Integer>printer(new Cursor(null, Cursor.ROOT_VALUE)).visit(marked, capture);
        return capture.positions;
    }

    /**
     * The locals a caught exception has reached. Types identify a variable precisely; names are the fallback for source
     * whose types did not resolve, and cost only an occasional unreported `throw`.
     */
    private static class Taint {
        private final List<JavaType.Variable> variables = new ArrayList<>();
        private final Set<String> names = new HashSet<>();

        Taint(@Nullable Taint enclosing) {
            if (enclosing != null) {
                variables.addAll(enclosing.variables);
                names.addAll(enclosing.names);
            }
        }

        boolean add(JavaType.@Nullable Variable type, String name) {
            boolean grew = names.add(name);
            if (type != null && !variables.contains(type)) {
                variables.add(type);
                grew = true;
            }
            return grew;
        }

        boolean contains(J.Identifier identifier) {
            JavaType.Variable fieldType = identifier.getFieldType();
            return fieldType == null ? names.contains(identifier.getSimpleName()) : variables.contains(fieldType);
        }
    }

    @Value
    private static class Flow {
        JavaType.@Nullable Variable type;
        String name;
        Expression value;
    }

    @Value
    private static class ThrowPosition implements Marker {
        UUID id;

        @Override
        public ThrowPosition withId(UUID id) {
            return new ThrowPosition(id);
        }
    }

    /**
     * Counts lines and columns of what the printer emits. The marker printer emits nothing, so the counts are
     * positions in the source as written.
     */
    private static class PositionCapture extends PrintOutputCapture<Integer> {
        final Map<UUID, int[]> positions = new HashMap<>();

        private final MarkerPrinter positionRecorder = new MarkerPrinter() {
            @Override
            public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> commentWrapper) {
                if (marker instanceof ThrowPosition) {
                    positions.put(((J) cursor.getParentTreeCursor().getValue()).getId(), new int[]{line, column});
                }
                return "";
            }
        };

        private int line = 1;
        private int column;

        PositionCapture() {
            super(0);
        }

        @Override
        public MarkerPrinter getMarkerPrinter() {
            return positionRecorder;
        }

        @Override
        public PrintOutputCapture<Integer> append(@Nullable String text) {
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    append(text.charAt(i));
                }
            }
            return this;
        }

        @Override
        public PrintOutputCapture<Integer> append(char c) {
            if (c == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
            return this;
        }
    }
}
