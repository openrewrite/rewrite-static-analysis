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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.InvertCondition;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Loop;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class NoRedundantJumpStatements extends Recipe {

    @Override
    public String getDisplayName() {
        return "Jump statements should not be redundant";
    }

    @Override
    public String getDescription() {
        return "Jump statements such as return and continue let you change the default flow of program execution, but jump statements that direct the control flow to the original direction are just a waste of keystrokes.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3626");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.If visitIf(J.If iff, ExecutionContext ctx) {
                J.If i = super.visitIf(iff, ctx);

                boolean thenIsOnlyContinue = i.getThenPart() instanceof J.Continue;
                if (i.getThenPart() instanceof J.Block) {
                    J.Block then = (J.Block) i.getThenPart();
                    thenIsOnlyContinue = then.getStatements().size() == 1 && then.getStatements().get(0) instanceof J.Continue;
                }

                if (getCursor().getParentOrThrow().getParentOrThrow().getValue() instanceof J.Block) {
                    J enclosing = getCursor().dropParentUntil(J.Block.class::isInstance)
                            .getParentTreeCursor()
                            .getValue();

                    if (enclosing instanceof Loop) {
                        if (thenIsOnlyContinue &&
                            i.getElsePart() != null && !(i.getElsePart().getBody() instanceof J.If)) {
                            Loop loop = (Loop) enclosing;
                            if (loop.getBody() instanceof J.Block) {
                                J.Block loopBlock = (J.Block) loop.getBody();

                                // last statement in loop
                                if (loopBlock.getStatements().get(loopBlock.getStatements().size() - 1) == i) {
                                    i = i.withIfCondition(InvertCondition.invert(i.getIfCondition(), getCursor()))
                                            .withThenPart(i.getElsePart().getBody())
                                            .withElsePart(null);
                                }
                            }
                        }
                    }
                }

                return i;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (m.getBody() != null && methodType != null && JavaType.Primitive.Void == methodType.getReturnType()) {
                    return m.withBody(m.getBody().withStatements(ListUtils.mapLast(m.getBody().getStatements(), s -> s instanceof J.Return ? null : s)));
                }

                return m;
            }
        };
    }
}
