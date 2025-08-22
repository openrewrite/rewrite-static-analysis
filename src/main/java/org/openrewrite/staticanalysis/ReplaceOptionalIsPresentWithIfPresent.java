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
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toList;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceOptionalIsPresentWithIfPresent extends Recipe {
    private static final MethodMatcher OPTIONAL_IS_PRESENT = new MethodMatcher("java.util.Optional isPresent()");
    private static final MethodMatcher OPTIONAL_GET = new MethodMatcher("java.util.Optional get()");

    @Override
    public String getDisplayName() {
        return "Replace `Optional#isPresent()` with `Optional#ifPresent()`";
    }

    @Override
    public String getDescription() {
        return "Replace `Optional#isPresent()` with `Optional#ifPresent()`. Please note that this recipe is only suitable for if-blocks that lack an Else-block and have a single condition applied.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(OPTIONAL_IS_PRESENT), new ReplaceOptionalIsPresentWithIfPresentVisitor());
    }

    private static class ReplaceOptionalIsPresentWithIfPresentVisitor extends JavaVisitor<ExecutionContext> {
        private final List<J.Identifier> lambdaAccessibleVariables = new ArrayList<>();

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            collectLambdaAccessibleVariables(cu, ctx);
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J visitIf(J.If _if, ExecutionContext ctx) {
            J.If before = _if;
            J.If after = (J.If) super.visitIf(_if, ctx);
            boolean updated = after != before;
            _if = after;
            if (!(_if.getIfCondition().getTree() instanceof J.MethodInvocation) ||
                !OPTIONAL_IS_PRESENT.matches((J.MethodInvocation) _if.getIfCondition().getTree())) {
                return _if;
            }

            /* skip if `if-else` part is present */
            if (_if.getElsePart() != null) {
                return _if;
            }

            /* check if parent is else-if */
            if (getCursor().getParentTreeCursor().getValue() instanceof J.If.Else) {
                return _if;
            }

            /* handle J.If ancestors */
            if (!(_if.getIfCondition().getTree() instanceof J.MethodInvocation) || !OPTIONAL_IS_PRESENT.matches((J.MethodInvocation) _if.getIfCondition().getTree())) {
                return _if;
            }

            Expression select = ((J.MethodInvocation) _if.getIfCondition().getTree()).getSelect();
            if (!(select instanceof J.Identifier) || !isStatementLambdaConvertible(_if.getThenPart())) {
                return _if;
            }

            /* replace if block with Optional#ifPresent and lambda expression */
            J.Identifier optionalVariable = (J.Identifier) select;
            String methodSelector = optionalVariable.getSimpleName();

            Cursor nameScope = getCursor();

            if (updated) {
                nameScope = new Cursor(getCursor().getParentOrThrow(), after);
            }

            String uniqueLambdaParameterName = VariableNameUtils.generateVariableName("obj", nameScope,
                    VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
            String template = String.format("%s.ifPresent(%s -> #{any()})", methodSelector,
                    uniqueLambdaParameterName);
            J ifPresentMi = JavaTemplate.builder(template)
                    .contextSensitive()
                    .build()
                    .apply(getCursor(),
                            _if.getCoordinates().replace(),
                            _if.getThenPart()
                    );

            /* replace Optional#get to lambda parameter */
            J.Identifier lambdaParameterIdentifier =
                    ((J.VariableDeclarations) ((J.Lambda) ((J.MethodInvocation) ifPresentMi).getArguments().get(0))
                            .getParameters().getParameters().get(0)).getVariables().get(0).getName();
            lambdaAccessibleVariables.add(lambdaParameterIdentifier);
            return ReplaceMethodCallWithVariableVisitor.replace(ifPresentMi, ctx, lambdaParameterIdentifier,
                    optionalVariable);
        }

        private boolean isStatementLambdaConvertible(Statement statement) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier id, AtomicBoolean convertible) {
                    if (id.getType() == null || id.getFieldType() == null || id.getFieldType().getOwner() instanceof JavaType.Class) {
                        return id;
                    }

                    if (lambdaAccessibleVariables.stream().noneMatch(v -> id.getFieldType().equals(v.getFieldType()) &&
                                                                          v.getSimpleName().equals(id.getSimpleName()))) {
                        convertible.set(false);
                    }

                    return id;
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean convertible) {
                    return classDecl;
                }

                @Override
                public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean convertible) {
                    return lambda;
                }

                @Override
                public J.Return visitReturn(J.Return _return, AtomicBoolean convertible) {
                    convertible.set(false);
                    return _return;
                }

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean convertible) {
                    if (method.getMethodType() != null && !method.getMethodType().getThrownExceptions().isEmpty()) {
                        convertible.set(false);
                        return method;
                    }
                    return super.visitMethodInvocation(method, convertible);
                }

                @Override
                public J.Throw visitThrow(J.Throw thrown, AtomicBoolean convertible) {
                    convertible.set(false);
                    return thrown;
                }

                @Override
                public J.Continue visitContinue(J.Continue continueStatement, AtomicBoolean convertible) {
                    convertible.set(false);
                    return continueStatement;
                }

                @Override
                public J.Break visitBreak(J.Break breakStatement, AtomicBoolean convertible) {
                    convertible.set(false);
                    return breakStatement;
                }

                @Override
                public J.Block visitBlock(J.Block block, AtomicBoolean convertible) {
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.NewClass) {
                        return block;
                    }
                    return super.visitBlock(block, convertible);
                }
            }.reduce(statement, new AtomicBoolean(true)).get();
        }

        private void collectLambdaAccessibleVariables(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit finalizeLocalVariablesCu = (J.CompilationUnit) new FinalizeLocalVariables().getVisitor().visit(cu, ctx);
            J.CompilationUnit finalizeMethodArgumentsCu = (J.CompilationUnit) new FinalizeMethodArguments().getVisitor().visit(cu, ctx);
            JavaIsoVisitor<List<J.Identifier>> finalVariablesCollector = new JavaIsoVisitor<List<J.Identifier>>() {
                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                        List<J.Identifier> identifiers) {
                    if (multiVariable.hasModifier(J.Modifier.Type.Final)) {
                        identifiers.addAll(multiVariable.getVariables().stream()
                                .map(J.VariableDeclarations.NamedVariable::getName)
                                .collect(toList()));
                    }
                    return super.visitVariableDeclarations(multiVariable, identifiers);
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                                List<J.Identifier> identifiers) {
                    identifiers.addAll(collectFields(classDecl));
                    return super.visitClassDeclaration(classDecl, identifiers);
                }
            };
            finalVariablesCollector.visit(finalizeLocalVariablesCu, lambdaAccessibleVariables);
            finalVariablesCollector.visit(finalizeMethodArgumentsCu, lambdaAccessibleVariables);
        }

        private static List<J.Identifier> collectFields(J.ClassDeclaration classDecl) {
            return classDecl.getBody()
                    .getStatements()
                    .stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(J.VariableDeclarations.class::cast)
                    .map(J.VariableDeclarations::getVariables)
                    .flatMap(Collection::stream)
                    .map(J.VariableDeclarations.NamedVariable::getName)
                    .collect(toList());
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    public static class ReplaceMethodCallWithVariableVisitor extends JavaVisitor<ExecutionContext> {
        J.Identifier lambdaParameterIdentifier;
        J.Identifier methodSelector;

        static J replace(J subtree, ExecutionContext ctx, J.Identifier lambdaParameterIdentifier, J.Identifier methodSelector) {
            return new ReplaceMethodCallWithVariableVisitor(lambdaParameterIdentifier, methodSelector).visitNonNull(subtree, ctx);
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            /* Only replace method invocations that has same method selector as present in if condition */
            if (OPTIONAL_GET.matches(mi) && mi.getSelect() instanceof J.Identifier) {
                J.Identifier selectToBeReplaced = (J.Identifier) mi.getSelect();
                if (methodSelector.getSimpleName().equals(selectToBeReplaced.getSimpleName()) &&
                        methodSelector.getFieldType() != null &&
                        methodSelector.getFieldType().equals(selectToBeReplaced.getFieldType())) {
                    return lambdaParameterIdentifier.withPrefix(method.getPrefix());
                }
            }
            return mi;
        }
    }
}
