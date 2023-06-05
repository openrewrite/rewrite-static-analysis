/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.marker.SearchResult;

import java.util.Objects;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceIsPresentWithIfPresent extends Recipe {
    private static final MethodMatcher OPTIONAL_IS_PRESENT = new MethodMatcher("java.util.Optional isPresent()");
    private static final MethodMatcher OPTIONAL_GET = new MethodMatcher("java.util.Optional get()");

    @Override
    public String getDisplayName() {
        return "Replace `Optional#isPresent` with `Optional#IfPresent`";
    }

    @Override
    public String getDescription() {
        System.out.println();
        return "Replace `Optional#isPresent` with `Optional#IfPresent`. Please note that this recipe is only suitable for if-blocks that lack an Else-block and have a single condition applied.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(OPTIONAL_IS_PRESENT), new ReplaceIsPresentWithIfPresentVisitor());
    }

    private static class ReplaceIsPresentWithIfPresentVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitIf(J.If _if, ExecutionContext context) {
            J.If before = _if;
            J.If after  = (J.If) super.visitIf(_if, context);
            boolean updated = after != before;
            _if = after;
            if (!(_if.getIfCondition().getTree() instanceof J.MethodInvocation) ||
                !OPTIONAL_IS_PRESENT.matches( (J.MethodInvocation) _if.getIfCondition().getTree()) ) {
                return _if;
            }

            /* skip if `if-else` part is present */
            if (Objects.nonNull(_if.getElsePart())) {
                return _if;
            }

            /* check if parent is else-if */
            if (getCursor().getParent() == null ||
                getCursor().getParent().getParent() == null ||
                getCursor().getParent().getParent().getValue() instanceof J.If.Else) {
                return _if;
            }

            /* handle J.If ancestors */
            if (!(_if.getIfCondition().getTree() instanceof J.MethodInvocation) || !OPTIONAL_IS_PRESENT.matches((J.MethodInvocation) _if.getIfCondition().getTree())) {
                return _if;
            }

            J.Identifier optionalVariable =
                (J.Identifier) ((J.MethodInvocation) _if.getIfCondition().getTree()).getSelect();
            if (optionalVariable == null ||
                !IsBlockLambdaConvertibleVisitor.isBlockLambdaConvertible((J.Block) _if.getThenPart(),
                    getCursor(), optionalVariable).get()) {
                return _if;
            }

            /* replace if block with Optional#ifPresent and lambda expression */
            String methodSelector = optionalVariable.getSimpleName();

            Cursor nameScope = getCursor();

            if (updated) {
                nameScope = getUpdatedNameScope(getCursor(), before, after);
            }

            String uniqueLambdaParameterName = VariableNameUtils.generateVariableName("obj", nameScope,
                VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
            String template = String.format("%s.ifPresent((%s) -> #{any()})", methodSelector,
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
            return ReplaceMethodCallWithStringVisitor.replace(ifPresentMi, context, lambdaParameterIdentifier,
                optionalVariable);
        }
    }

    private static Cursor getUpdatedNameScope(Cursor cursor, J.If before, J.If after) {
        J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (cu == null || before == after) {
            return cursor;
        }
        cu = (J.CompilationUnit) new JavaIsoVisitor<J.If>(){
            @Override
            public J.If visitIf(J.If iff, J.If targetIf) {
                if (iff == targetIf) {
                    return after;
                }
                return super.visitIf(iff, targetIf);
            }
        }.visitNonNull(cu, before);
        return new Cursor(null, cu);
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public static class ReplaceMethodCallWithStringVisitor extends JavaVisitor<ExecutionContext> {
        J.Identifier lambdaParameterIdentifier;
        J.Identifier methodSelector;

        static J replace(J subtree, ExecutionContext p, J.Identifier lambdaParameterIdentifier, J.Identifier methodSelector) {
            return new ReplaceMethodCallWithStringVisitor(lambdaParameterIdentifier, methodSelector).visitNonNull(subtree, p);
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, p);
            /* Only replace method invocations that has same method selector as present in if condition */
            if (OPTIONAL_GET.matches(mi) && mi.getSelect() instanceof J.Identifier) {
                J.Identifier selectToBeReplaced = (J.Identifier) mi.getSelect();
                if (methodSelector.getSimpleName().equals(mi.getSelect().toString()) &&
                    methodSelector.getFieldType() != null &&
                    methodSelector.getFieldType().equals(selectToBeReplaced.getFieldType()))
                    return lambdaParameterIdentifier;
            }
            return mi;
        }
    }
}