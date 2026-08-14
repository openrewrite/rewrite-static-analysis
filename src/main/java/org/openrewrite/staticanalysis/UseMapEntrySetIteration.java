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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.table.MapKeySetIterations;

import java.util.*;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseMapEntrySetIteration extends Recipe {

    private static final MethodMatcher KEY_SET = new MethodMatcher("java.util.Map keySet()", true);
    private static final MethodMatcher MAP_GET = new MethodMatcher("java.util.Map get(..)", true);
    private static final MethodMatcher MAP_METHOD = new MethodMatcher("java.util.Map *(..)", true);

    // Any of these can change the entry the loop is currently positioned on, which `Map.Entry` reads through.
    private static final Set<String> MUTATORS = new HashSet<>(Arrays.asList(
            "clear", "compute", "computeIfAbsent", "computeIfPresent", "merge", "put", "putAll", "putIfAbsent",
            "remove", "replace", "replaceAll"));

    transient MapKeySetIterations report = new MapKeySetIterations(this);

    //language=markdown
    String displayName = "Iterate a `Map`'s `entrySet()` rather than its `keySet()`";

    //language=markdown
    String description = "A loop over `map.keySet()` that calls `map.get(key)` hashes and probes the map again for " +
            "every element, which on a `TreeMap` costs an extra `O(log n)` lookup per iteration. Iterating " +
            "`map.entrySet()` instead hands the loop both the key and the value. The loop is only rewritten when:\n" +
            " - The map is a simple reference that is neither modified nor reassigned inside the loop.\n" +
            " - `get` is called only with the loop variable.\n" +
            " - The loop variable is neither reassigned nor captured by a lambda or anonymous class.\n" +
            "\n" +
            "Every candidate loop, converted or not, is recorded in a data table along with the reason it was " +
            "left alone.";

    Set<String> tags = singleton("RSPEC-S2864");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new JavaFileChecker<>(), new UsesMethod<>(KEY_SET)),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, ExecutionContext ctx) {
                        // Innermost loops are converted first so that an enclosing loop sees the names its
                        // nested loops have already taken.
                        J.ForEachLoop loop = super.visitForEachLoop(forEachLoop, ctx);
                        updateCursor(loop);

                        Expression iterable = loop.getControl().getIterable();
                        if (!(iterable instanceof J.MethodInvocation) || !KEY_SET.matches((J.MethodInvocation) iterable)) {
                            return loop;
                        }
                        J.MethodInvocation keySet = (J.MethodInvocation) iterable;
                        Expression map = keySet.getSelect();
                        if (map == null || !(loop.getControl().getVariable() instanceof J.VariableDeclarations)) {
                            return loop;
                        }
                        JavaType.Variable loopVar = ((J.VariableDeclarations) loop.getControl().getVariable())
                                .getVariables().get(0).getVariableType();
                        if (loopVar == null) {
                            return loop;
                        }

                        LoopBodyAnalysis body = new LoopBodyAnalysis(map, loopVar);
                        body.visit(loop.getBody(), 0);
                        if (body.getCalls.isEmpty()) {
                            // The value is never looked up again, so there is nothing to save.
                            return loop;
                        }

                        JavaType.Parameterized keySetType = TypeUtils.asParameterized(keySet.getType());
                        JavaType keyType = keySetType == null || keySetType.getTypeParameters().size() != 1 ?
                                null : keySetType.getTypeParameters().get(0);
                        JavaType valueType = body.getCalls.get(0).getMethodType() == null ?
                                null : requireNonNull(body.getCalls.get(0).getMethodType()).getReturnType();

                        String reason = body.blocker();
                        String keyText = typeText(keyType);
                        String valueText = typeText(valueType);
                        if (reason == null && (keyText == null || valueText == null)) {
                            reason = "the key and value types could not be determined";
                        }
                        if (reason != null) {
                            insertRow(ctx, map, false, reason);
                            return loop;
                        }

                        J.ForEachLoop converted = convert(loop, map, loopVar, keyText, valueText,
                                asWritten(((J.VariableDeclarations) loop.getControl().getVariable()).getTypeExpression(), keyType, getCursor()),
                                asWritten(body.valueTypeExpression, valueType, getCursor()));
                        insertRow(ctx, map, true, "");
                        maybeAddImport("java.util.Map", null, false);
                        doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(converted));
                        return converted;
                    }

                    private J.ForEachLoop convert(J.ForEachLoop loop, Expression map, JavaType.Variable loopVar,
                                                  String keyText, String valueText,
                                                  @Nullable TypeTree keyAsWritten, @Nullable TypeTree valueAsWritten) {
                        String entryName = entryVariableName(loop);
                        // The generated body doubles as a source of correctly typed `getKey()`/`getValue()` calls.
                        J.ForEachLoop generated = JavaTemplate.builder(
                                        "for (Map.Entry<" + keyText + ", " + valueText + "> " + entryName +
                                                " : #{any(java.util.Map)}.entrySet()) {" +
                                                entryName + ".getKey();" +
                                                entryName + ".getValue();" +
                                                "}")
                                .imports("java.util.Map")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), loop.getCoordinates().replace(), map);
                        generated = useTypeNamesFromSource(generated, keyAsWritten, valueAsWritten);

                        List<Statement> prototypes = ((J.Block) generated.getBody()).getStatements();
                        J.MethodInvocation getKey = (J.MethodInvocation) prototypes.get(0);
                        J.MethodInvocation getValue = (J.MethodInvocation) prototypes.get(1);

                        Statement newBody = (Statement) requireNonNull(new JavaVisitor<Integer>() {
                            @Override
                            public J visitMethodInvocation(J.MethodInvocation method, Integer p) {
                                if (isGetOfLoopVariable(method, map, loopVar)) {
                                    return copyWithPrefix(getValue, method.getPrefix());
                                }
                                return super.visitMethodInvocation(method, p);
                            }

                            @Override
                            public J visitIdentifier(J.Identifier identifier, Integer p) {
                                if (references(identifier, loopVar)) {
                                    return copyWithPrefix(getKey, identifier.getPrefix());
                                }
                                return identifier;
                            }
                        }.visit(loop.getBody(), 0));

                        return generated.withPrefix(loop.getPrefix()).withBody(newBody);
                    }

                    /**
                     * A name for the entry variable that collides neither with the enclosing scopes nor with anything
                     * declared or referenced inside the loop, which the enclosing scopes cannot see.
                     */
                    private String entryVariableName(J.ForEachLoop loop) {
                        Set<String> namesInLoop = new HashSet<>();
                        new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                                names.add(identifier.getSimpleName());
                                return identifier;
                            }
                        }.visit(loop.getBody(), namesInLoop);

                        String name = VariableNameUtils.generateVariableName("entry", getCursor(), INCREMENT_NUMBER);
                        for (int i = 1; namesInLoop.contains(name); i++) {
                            name = VariableNameUtils.generateVariableName("entry" + i, getCursor(), INCREMENT_NUMBER);
                        }
                        return name;
                    }

                    private void insertRow(ExecutionContext ctx, Expression map, boolean updated, String reason) {
                        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                        J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        report.insertRow(ctx, new MapKeySetIterations.Row(
                                sourceFile == null ? "" : sourceFile.getSourcePath().toString(),
                                enclosing == null || enclosing.getType() == null ? "" :
                                        enclosing.getType().getFullyQualifiedName(),
                                map.printTrimmed(getCursor()),
                                updated,
                                reason));
                    }
                });
    }

    /**
     * Swap the fully qualified type arguments the template had to be built from back to the trees the source
     * already had, which keeps their (correct) types while printing the name the rest of the file uses.
     */
    private static J.ForEachLoop useTypeNamesFromSource(J.ForEachLoop loop, @Nullable TypeTree key,
                                                        @Nullable TypeTree value) {
        J.VariableDeclarations entry = (J.VariableDeclarations) loop.getControl().getVariable();
        if ((key == null && value == null) || !(entry.getTypeExpression() instanceof J.ParameterizedType)) {
            return loop;
        }
        J.ParameterizedType entryType = (J.ParameterizedType) entry.getTypeExpression();
        List<Expression> arguments = requireNonNull(entryType.getTypeParameters());
        return loop.withControl(loop.getControl().withVariable(entry.withTypeExpression(
                entryType.withTypeParameters(ListUtils.map(arguments, (i, argument) -> {
                    TypeTree written = i == 0 ? key : value;
                    return written == null ? argument :
                            requireNonNull((Expression) new RandomizeIdVisitor<Integer>().visit(written, 0))
                                    .withPrefix(argument.getPrefix());
                })))));
    }

    // Each replacement site needs its own copy; sharing one prototype would put duplicate ids in the tree.
    private static J.MethodInvocation copyWithPrefix(J.MethodInvocation prototype, Space prefix) {
        return requireNonNull(new RandomizeIdVisitor<Integer>().visit(prototype, 0)).withPrefix(prefix);
    }

    private static boolean isGetOfLoopVariable(J.MethodInvocation method, Expression map, JavaType.Variable loopVar) {
        return MAP_GET.matches(method) &&
                method.getSelect() != null &&
                SemanticallyEqual.areEqual(map, method.getSelect()) &&
                method.getArguments().size() == 1 &&
                references(method.getArguments().get(0), loopVar);
    }

    private static boolean references(@Nullable Expression expression, JavaType.Variable variable) {
        return expression instanceof J.Identifier && Objects.equals(variable, ((J.Identifier) expression).getFieldType());
    }

    /**
     * A fully qualified name for the type, which is what the template has to be given: a simple name that only
     * resolves through the file's own package or a wildcard import would come back without type attribution.
     * Null when the type cannot be written as a type argument at all, which includes a raw map.
     */
    private static @Nullable String typeText(@Nullable JavaType type) {
        if (type == null || type instanceof JavaType.Primitive || !TypeUtils.isWellFormedType(type)) {
            return null;
        }
        String text = TypeUtils.toString(type).replace('$', '.');
        return text.contains("Unknown") ? null : text;
    }

    /**
     * The type tree the source already uses for this type, which reads better than the fully qualified name the
     * template is built from, and which a same-package or wildcard-imported type never gets shortened to.
     * The declaration inside the loop is preferred; otherwise any naming of the type elsewhere in the same method
     * will do, since whatever resolves there resolves at the loop too. Null when the source never names the type
     * exactly, as `var` and a supertype declaration do not.
     */
    private static @Nullable TypeTree asWritten(@Nullable TypeTree tree, @Nullable JavaType type, Cursor cursor) {
        if (type == null) {
            return null;
        }
        if (names(tree, type)) {
            return tree;
        }
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (method == null) {
            return null;
        }
        TypeTree[] found = new TypeTree[1];
        new JavaIsoVisitor<TypeTree[]>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, TypeTree[] t) {
                return classDecl; // a name that only resolves inside a nested class is of no use out here
            }

            @SuppressWarnings("ConstantValue")
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, TypeTree[] t) {
                if (t[0] == null && identifier.getFieldType() == null && names(identifier, type)) {
                    t[0] = identifier;
                }
                return identifier;
            }

            @SuppressWarnings("ConstantValue")
            @Override
            public J.ParameterizedType visitParameterizedType(J.ParameterizedType parameterized, TypeTree[] t) {
                if (t[0] == null && names(parameterized, type)) {
                    t[0] = parameterized;
                    return parameterized;
                }
                return super.visitParameterizedType(parameterized, t);
            }
        }.visit(method, found);
        return found[0];
    }

    private static boolean names(@Nullable TypeTree tree, JavaType type) {
        return tree instanceof Expression && TypeUtils.isOfType(tree.getType(), type) &&
                !(tree instanceof J.Identifier && "var".equals(((J.Identifier) tree).getSimpleName()));
    }

    /**
     * Everything about the loop body that decides whether the rewrite is safe, gathered in one pass.
     */
    private static class LoopBodyAnalysis extends JavaIsoVisitor<Integer> {
        private final Expression map;
        private final JavaType.Variable loopVar;

        final List<J.MethodInvocation> getCalls = new ArrayList<>();
        @Nullable TypeTree valueTypeExpression;
        boolean getWithOtherKey;
        boolean mapModified;
        boolean loopVarReassigned;
        boolean capturedInClosure;

        private int closureDepth;

        LoopBodyAnalysis(Expression map, JavaType.Variable loopVar) {
            this.map = map;
            this.loopVar = loopVar;
        }

        @Nullable String blocker() {
            if (!isStableReference(map)) {
                return "the map expression may have side effects";
            }
            if (TypeUtils.isAssignableTo("java.util.concurrent.ConcurrentMap", map.getType())) {
                return "an entry of a concurrent map may not reflect concurrent updates the way `get` does";
            }
            if (mapModified) {
                return "the map is modified inside the loop";
            }
            if (loopVarReassigned) {
                return "the loop variable is reassigned inside the loop";
            }
            if (capturedInClosure) {
                return "the loop variable is captured by a lambda or anonymous class";
            }
            if (getWithOtherKey) {
                return "`get` is called with a key other than the loop variable";
            }
            return null;
        }

        private static boolean isStableReference(Expression expression) {
            if (expression instanceof J.FieldAccess) {
                return isStableReference(((J.FieldAccess) expression).getTarget());
            }
            return expression instanceof J.Identifier;
        }

        private boolean referencesLoopVar(@Nullable Expression expression) {
            return references(expression, loopVar);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
            J.MethodInvocation m = super.visitMethodInvocation(method, p);
            if (m.getSelect() != null && SemanticallyEqual.areEqual(map, m.getSelect())) {
                if (MAP_GET.matches(m)) {
                    if (m.getArguments().size() == 1 && referencesLoopVar(m.getArguments().get(0))) {
                        getCalls.add(m);
                    } else {
                        getWithOtherKey = true;
                    }
                } else if (MUTATORS.contains(m.getSimpleName()) && MAP_METHOD.matches(m)) {
                    mapModified = true;
                }
            }
            return m;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, Integer p) {
            J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, p);
            for (J.VariableDeclarations.NamedVariable variable : v.getVariables()) {
                if (variable.getInitializer() instanceof J.MethodInvocation &&
                        getCalls.contains(variable.getInitializer())) {
                    valueTypeExpression = v.getTypeExpression();
                    break;
                }
            }
            return v;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
            if (closureDepth > 0 && referencesLoopVar(identifier)) {
                capturedInClosure = true;
            }
            return identifier;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, Integer p) {
            recordAssignment(assignment.getVariable());
            return super.visitAssignment(assignment, p);
        }

        @Override
        public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, Integer p) {
            recordAssignment(assignOp.getVariable());
            return super.visitAssignmentOperation(assignOp, p);
        }

        @Override
        public J.Unary visitUnary(J.Unary unary, Integer p) {
            if (unary.getOperator().isModifying()) {
                recordAssignment(unary.getExpression());
            }
            return super.visitUnary(unary, p);
        }

        private void recordAssignment(Expression target) {
            if (referencesLoopVar(target)) {
                loopVarReassigned = true;
            } else if (SemanticallyEqual.areEqual(map, target)) {
                mapModified = true;
            }
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, Integer p) {
            closureDepth++;
            J.Lambda l = super.visitLambda(lambda, p);
            closureDepth--;
            return l;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, Integer p) {
            if (newClass.getBody() == null) {
                return super.visitNewClass(newClass, p);
            }
            closureDepth++;
            J.NewClass n = super.visitNewClass(newClass, p);
            closureDepth--;
            return n;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
            closureDepth++;
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, p);
            closureDepth--;
            return c;
        }
    }
}
