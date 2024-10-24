/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class CatchClauseOnlyRethrows extends Recipe {

    @Override
    public String getDisplayName() {
        return "Catch clause should do more than just rethrow";
    }

    @Override
    public String getDescription() {
        return "A `catch` clause that only rethrows the caught exception is unnecessary. " +
               "Letting the exception bubble up as normal achieves the same result with less code.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2737");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (statement instanceof J.Try) {
                        // if a try has no catches, no finally, and no resources get rid of it and merge its statements into the current block
                        J.Try aTry = (J.Try) statement;
                        if (aTry.getCatches().isEmpty() && aTry.getResources() == null && aTry.getFinally() == null) {
                            return ListUtils.map(aTry.getBody().getStatements(), tryStat -> autoFormat(tryStat, ctx, getCursor()));
                        }
                    }
                    return statement;
                }));
            }

            @Override
            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                J.Try t = super.visitTry(tryable, ctx);
                return t.withCatches(ListUtils.map(t.getCatches(), (i, aCatch) -> {
                    if (onlyRethrows(aCatch)) {
                        // if a subsequent catch is a wider exception type and doesn't rethrow, we should
                        // keep this one
                        for (int j = i + 1; j < tryable.getCatches().size(); j++) {
                            J.Try.Catch next = tryable.getCatches().get(j);
                            if (hasWiderExceptionType(aCatch, next)) {
                                if (onlyRethrows(next)) {
                                    return null;
                                }
                                return aCatch;
                            }
                        }
                        return null;
                    }
                    return aCatch;
                }));
            }

            private boolean hasWiderExceptionType(J.Try.Catch aCatch, J.Try.Catch next) {
                if (next.getParameter().getType() instanceof JavaType.MultiCatch) {
                    JavaType.MultiCatch multiCatch = (JavaType.MultiCatch) next.getParameter().getType();
                    for (JavaType throwableType : multiCatch.getThrowableTypes()) {
                        if (TypeUtils.isAssignableTo(throwableType, aCatch.getParameter().getType())) {
                            return true;
                        }
                    }
                    return false;
                }
                return TypeUtils.isAssignableTo(next.getParameter().getType(), aCatch.getParameter().getType());
            }

            private boolean onlyRethrows(J.Try.Catch aCatch) {
                if (aCatch.getBody().getStatements().size() != 1 ||
                    !(aCatch.getBody().getStatements().get(0) instanceof J.Throw)) {
                    return false;
                }

                Expression exception = ((J.Throw) aCatch.getBody().getStatements().get(0)).getException();
                JavaType catchParameterType = aCatch.getParameter().getType();
                if (!(catchParameterType instanceof JavaType.MultiCatch)) {
                    JavaType.FullyQualified catchType = TypeUtils.asFullyQualified(catchParameterType);
                    if (catchType == null || !catchType.equals(exception.getType())) {
                        return false;
                    }
                }
                if (exception instanceof J.Identifier) {
                    return ((J.Identifier) exception).getSimpleName().equals(aCatch.getParameter().getTree().getVariables().get(0).getSimpleName());
                }

                return false;
            }
        };
    }
}
