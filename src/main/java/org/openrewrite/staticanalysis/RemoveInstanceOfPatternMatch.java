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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.singletonList;

public class RemoveInstanceOfPatternMatch extends Recipe {

    @Getter
    final String displayName = "Remove Java 14's `instanceof` pattern matching";

    @Getter
    final String description = "Adds an explicit variable declaration at the beginning of the `if` statement instead of `instanceof` pattern matching.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.not(new GroovyFileChecker<>()),
                        new UsesJavaVersion<>(14)
                ),
                new RemoveInstanceOfPatternMatchVisitor());
    }

    private static class RemoveInstanceOfPatternMatchVisitor extends JavaVisitor<ExecutionContext> {

        private VariableUsage variableUsage;

        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            // Only J.CompilationUnit carries `instanceof` pattern matching; other
            // source files (e.g. Kotlin's K.CompilationUnit) start from an empty
            // usage so the visitor never dereferences a null field.
            if (tree instanceof JavaSourceFile) {
                variableUsage = tree instanceof J.CompilationUnit ?
                        VariableUsageAnalyzer.analyze((J.CompilationUnit) tree) :
                        new VariableUsage();
                J result = super.visit(tree, ctx);
                variableUsage = null;
                return result;
            }
            return super.visit(tree, ctx);
        }

        @Override
        public J visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
            return super.visitInstanceOf(instanceOf.withPattern(null), ctx);
        }

        @Override
        public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getName() == identifier) {
                return identifier;
            }
            J.InstanceOf instanceOf = variableUsage.conditions.get(identifier);
            if (instanceOf != null) {
                J result = autoFormat(
                        typeCast((TypeTree) instanceOf.getClazz(), instanceOf.getExpression()),
                        ctx);
                if (parent instanceof J.MethodInvocation) {
                    result = parentheses(result);
                }
                return result.withPrefix(identifier.getPrefix());
            }
            return identifier;
        }

        @Override
        public J.If visitIf(J.If iff, ExecutionContext ctx) {
            J.If result = (J.If) super.visitIf(iff, ctx);
            updateCursor(result);

            Set<J.InstanceOf> thenInstanceOfs = variableUsage.thenParts.get(iff);
            if (thenInstanceOfs != null) {
                if (!(result.getThenPart() instanceof J.Block)) {
                    result = autoFormat(result.withThenPart(J.Block.createEmptyBlock()
                            .withStatements(singletonList(result.getThenPart()))), ctx);
                    updateCursor(result);
                }
                // each declaration goes in first, so add them in reverse to keep the `instanceof` order
                Iterator<J.InstanceOf> iter = variableUsage.declarations.get(iff).descendingIterator();
                while (iter.hasNext()) {
                    J.InstanceOf instanceOf = iter.next();
                    if (thenInstanceOfs.contains(instanceOf)) {
                        Cursor blockCursor = new Cursor(getCursor(), result.getThenPart());
                        result = result.withThenPart(addVariableDeclaration(blockCursor, instanceOf, ctx));
                        updateCursor(result);
                    }
                }
            }

            Set<J.InstanceOf> elseInstanceOfs = variableUsage.elseParts.get(iff.getElsePart());
            J.If.Else elsePart = result.getElsePart();
            if (elsePart != null && elseInstanceOfs != null) {
                if (!(elsePart.getBody() instanceof J.Block)) {
                    result = autoFormat(result.withElsePart(elsePart.withBody(
                                    J.Block.createEmptyBlock().withStatements(
                                            singletonList(elsePart.getBody())))),
                            ctx);
                    updateCursor(result);
                    elsePart = result.getElsePart();
                }
                if (elsePart != null) {
                    Iterator<J.InstanceOf> iter = variableUsage.declarations.get(iff).descendingIterator();
                    while (iter.hasNext()) {
                        J.InstanceOf instanceOf = iter.next();
                        if (elseInstanceOfs.contains(instanceOf)) {
                            Cursor blockCursor = new Cursor(new Cursor(getCursor(), elsePart), elsePart.getBody());
                            result = result.withElsePart(elsePart.withBody(
                                    addVariableDeclaration(blockCursor, instanceOf, ctx)));
                        }
                    }
                }
            }
            return result;
        }

        private J.Block addVariableDeclaration(Cursor blockCursor, J.InstanceOf instanceOf, ExecutionContext ctx) {
            J.Block block = blockCursor.getValue();
            JavaTemplate template = JavaTemplate
                    .builder("#{} #{} = (#{}) #{any()};")
                    .contextSensitive()
                    .imports(typesNamedBy(instanceOf.getClazz()))
                    .build();
            return template.apply(blockCursor, block.getCoordinates().firstStatement(), instanceOf.getClazz().toString(),
                    ((J.Identifier) Objects.requireNonNull(instanceOf.getPattern())).getSimpleName(),
                    instanceOf.getClazz().toString(),
                    visit(instanceOf.getExpression(), ctx));
        }

        private static String[] typesNamedBy(J typeTree) {
            return new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Set<String> t) {
                    // a qualified name is found through its first part
                    visit(fieldAccess.getTarget(), t);
                    return fieldAccess;
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> t) {
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
                    if (type != null && !type.getPackageName().isEmpty() &&
                            identifier.getSimpleName().equals(type.getClassName())) {
                        t.add(type.getFullyQualifiedName());
                    }
                    return identifier;
                }
            }.reduce(typeTree, new LinkedHashSet<>()).toArray(new String[0]);
        }

        private J.TypeCast typeCast(TypeTree typeTree, Expression expression) {
            return new J.TypeCast(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    new J.ControlParentheses<>(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            padRight(typeTree)),
                    expression);
        }

        private <T extends J> J.Parentheses<T> parentheses(T tree) {
            return new J.Parentheses<>(Tree.randomId(), Space.EMPTY, Markers.EMPTY, padRight(tree));
        }

        private <T> JRightPadded<T> padRight(T tree) {
            return new JRightPadded<>(tree, Space.EMPTY, Markers.EMPTY);
        }

    }

    private enum UsageContext {
        NONE,
        CONDITION,
        THEN_PART,
        ELSE_PART
    }

    private static class VariableUsage {

        public Map<J.If, Deque<J.InstanceOf>> declarations = new HashMap<>();

        // also holds every use inside a ternary, which has no block to declare a variable in
        public Map<J.Identifier, J.InstanceOf> conditions = new HashMap<>();

        public Map<J.If, Set<J.InstanceOf>> thenParts = new HashMap<>();

        public Map<J.If.Else, Set<J.InstanceOf>> elseParts = new HashMap<>();

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private static class VariableUsageAnalyzer extends JavaIsoVisitor<J> {

        private final Map<String, J.InstanceOf> currentScope = new HashMap<>();

        private final Map<J.InstanceOf, J> parentTrees = new HashMap<>();

        private final VariableUsage variableUsage = new VariableUsage();

        public static VariableUsage analyze(J tree) {
            VariableUsageAnalyzer collector = new VariableUsageAnalyzer();
            collector.visit(tree, tree);
            collector.currentScope.clear();
            return collector.variableUsage;
        }

        @Override
        public J.If visitIf(J.If iff, J contextTree) {
            return iff.withIfCondition(visitAndCast(iff.getIfCondition(), iff))
                    .withThenPart(Objects.requireNonNull(visitAndCast(iff.getThenPart(), iff)))
                    .withElsePart(visitAndCast(iff.getElsePart(), iff));
        }

        @Override
        public J.Ternary visitTernary(J.Ternary ternary, J contextTree) {
            return super.visitTernary(ternary, ternary);
        }

        @Override
        public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, J contextTree) {
            if (instanceOf.getPattern() instanceof J.Identifier) {
                String variableName = ((J.Identifier) instanceOf.getPattern()).getSimpleName();
                currentScope.put(variableName, instanceOf);
                parentTrees.put(instanceOf, contextTree);
                if (contextTree instanceof J.If) {
                    variableUsage.declarations
                            .computeIfAbsent((J.If) contextTree, k -> new LinkedList<>())
                            .add(instanceOf);
                }
            }
            visit(instanceOf.getExpression(), contextTree);
            return instanceOf;
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(
                J.VariableDeclarations.NamedVariable variable, J contextTree) {
            // an explicit declaration shadows a same-named pattern variable
            currentScope.remove(variable.getSimpleName());
            visit(variable.getInitializer(), contextTree);
            return variable;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, J contextTree) {
            J.InstanceOf instanceOf = currentScope.get(identifier.getSimpleName());
            if (instanceOf != null) {
                J parentTree = parentTrees.get(instanceOf);
                switch (getUsageContext(parentTree)) {
                    case NONE:
                        break;
                    case CONDITION:
                        variableUsage.conditions.put(identifier, instanceOf);
                        break;
                    case THEN_PART:
                        variableUsage.thenParts
                                .computeIfAbsent(
                                        (J.If) parentTree,
                                        k -> new HashSet<>())
                                .add(instanceOf);
                        break;
                    case ELSE_PART:
                        currentScope.get(identifier.getSimpleName());
                        variableUsage.elseParts
                                .computeIfAbsent(
                                        ((J.If) parentTree).getElsePart(),
                                        k -> new HashSet<>())
                                .add(instanceOf);
                        break;
                }
            }
            return identifier;
        }

        private UsageContext getUsageContext(J parentTree) {
            if (parentTree instanceof J.If) {
                J.If iff = (J.If) parentTree;
                Iterator<Object> iter = getCursor().getPath();
                while (iter.hasNext()) {
                    Object tree = iter.next();
                    if (tree.equals(iff.getIfCondition())) {
                        return UsageContext.CONDITION;
                    }
                    if (tree.equals(iff.getThenPart())) {
                        return UsageContext.THEN_PART;
                    }
                    if (tree.equals(iff.getElsePart())) {
                        return UsageContext.ELSE_PART;
                    }
                }
            } else if (parentTree instanceof J.Ternary) {
                return UsageContext.CONDITION;
            }
            return UsageContext.NONE;
        }

    }

}
