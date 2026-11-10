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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class RemoveInstanceOfPatternMatch extends Recipe {

    @Getter
    final String displayName = "Remove Java 14's `instanceof` pattern matching";

    @Getter
    final String description = "Adds an explicit variable declaration at the beginning of the `if` statement instead of `instanceof` pattern matching. A record pattern, or a pattern whose variable is used outside the `if` statement or ternary that tests it, is left unchanged.";

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
            // this recipe declares only a plain pattern variable, so a record pattern stays
            if (!(instanceOf.getPattern() instanceof J.Identifier) || variableUsage.kept.contains(instanceOf)) {
                return super.visitInstanceOf(instanceOf, ctx);
            }
            J.InstanceOf i = instanceOf.withPattern(null);
            // before Java 16 the type in an `instanceof` must be reifiable, which `List<?>` is and `List<T>` is not
            if (i.getClazz() instanceof J.ParameterizedType) {
                J.ParameterizedType type = (J.ParameterizedType) i.getClazz();
                JavaType.Parameterized parameterized = TypeUtils.asParameterized(type.getType());
                i = i.withClazz(type
                        .withTypeParameters(ListUtils.map(type.getTypeParameters(), t ->
                                new J.Wildcard(Tree.randomId(), t.getPrefix(), Markers.EMPTY, null, null)))
                        .withType(parameterized == null ? type.getType() :
                                parameterized.withTypeParameters(ListUtils.map(parameterized.getTypeParameters(), t ->
                                        new JavaType.GenericTypeVariable(null, "?", JavaType.GenericTypeVariable.Variance.INVARIANT, emptyList())))));
            }
            return super.visitInstanceOf(i, ctx);
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
                            elsePart = elsePart.withBody(addVariableDeclaration(blockCursor, instanceOf, ctx));
                            result = result.withElsePart(elsePart);
                            updateCursor(result);
                        }
                    }
                }
            }
            return result;
        }

        /**
         * Reuses the type tree and pattern variable of the {@code instanceof}, since types such as method type
         * variables and nested classes resolve only in the scope the parser saw.
         */
        private J.Block addVariableDeclaration(Cursor blockCursor, J.InstanceOf instanceOf, ExecutionContext ctx) {
            J.Block block = blockCursor.getValue();
            J.Identifier pattern = (J.Identifier) Objects.requireNonNull(instanceOf.getPattern());
            Expression expression = (Expression) visitNonNull(instanceOf.getExpression(), ctx);
            J.VariableDeclarations declaration = new J.VariableDeclarations(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    emptyList(),
                    emptyList(),
                    copyOfType(instanceOf),
                    null,
                    singletonList(padRight(new J.VariableDeclarations.NamedVariable(
                            Tree.randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            pattern.withId(Tree.randomId()).withPrefix(Space.EMPTY),
                            emptyList(),
                            new JLeftPadded<>(
                                    Space.SINGLE_SPACE,
                                    typeCast(copyOfType(instanceOf), expression.withPrefix(Space.SINGLE_SPACE)),
                                    Markers.EMPTY),
                            pattern.getFieldType()))));
            return block.withStatements(ListUtils.insert(block.getStatements(),
                    autoFormat(declaration, ctx, blockCursor), 0));
        }

        /**
         * Flattens comments to whitespace so that a comment inside the type appears once, on the {@code instanceof}.
         */
        private static TypeTree copyOfType(J.InstanceOf instanceOf) {
            return new RandomizeIdVisitor<Integer>() {
                @Override
                public Space visitSpace(@Nullable Space space, Space.Location loc, Integer p) {
                    if (space == null || space.getComments().isEmpty()) {
                        return space;
                    }
                    return Space.format(space.getWhitespace().isEmpty() ? " " : space.getWhitespace());
                }
            }.visitNonNull((TypeTree) instanceOf.getClazz(), 0).withPrefix(Space.EMPTY);
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

        // patterns whose variable has a use this recipe can't rewrite, left as they are
        public Set<J.InstanceOf> kept = new HashSet<>();

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
            VariableUsage usage = collector.variableUsage;
            usage.conditions.values().removeAll(usage.kept);
            for (Map<?, Set<J.InstanceOf>> branchUses : Arrays.asList(usage.thenParts, usage.elseParts)) {
                branchUses.values().removeIf(instanceOfs -> {
                    instanceOfs.removeAll(usage.kept);
                    return instanceOfs.isEmpty();
                });
            }
            return usage;
        }

        @Override
        public J.If visitIf(J.If iff, J contextTree) {
            // only a pattern in the condition gets its variable declared at the top of a branch
            J.If.Else elsePart = iff.getElsePart();
            return iff.withIfCondition(visitAndCast(iff.getIfCondition(), iff))
                    .withThenPart(Objects.requireNonNull(visitAndCast(iff.getThenPart(), iff.getThenPart())))
                    .withElsePart(elsePart == null ? null : visitAndCast(elsePart, elsePart));
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
            if (instanceOf != null && TypeUtils.isOfType(identifier.getFieldType(),
                    ((J.Identifier) instanceOf.getPattern()).getFieldType())) {
                J parentTree = parentTrees.get(instanceOf);
                switch (getUsageContext(parentTree)) {
                    case NONE:
                        variableUsage.kept.add(instanceOf);
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
