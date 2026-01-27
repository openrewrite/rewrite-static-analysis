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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;

import static java.util.Collections.*;
import static org.openrewrite.staticanalysis.csharp.CSharpFileChecker.isCSharpTree;

public class CatchClauseOnlyRethrows extends Recipe {

    @Getter
    final String displayName = "Catch clause should do more than just rethrow";

    @Getter
    final String description = "A `catch` clause that only rethrows the caught exception is unnecessary. " +
            "Letting the exception bubble up as normal achieves the same result with less code.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2737");

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
                        // if a subsequent catch is a wider exception type and doesn't rethrow, we should keep this one
                        for (int j = i + 1; j < tryable.getCatches().size(); j++) {
                            J.Try.Catch next = tryable.getCatches().get(j);
                            if (isAnyAssignableTo(getJavaTypes(next), getJavaTypes(aCatch)) && !onlyRethrows(next)) {
                                return aCatch;
                            }
                        }
                        return null;
                    }
                    return aCatch;
                }));
            }

            private List<JavaType> getJavaTypes(J.Try.Catch next) {
                if (next.getParameter().getType() instanceof JavaType.MultiCatch) {
                    JavaType.MultiCatch multiCatch = (JavaType.MultiCatch) next.getParameter().getType();
                    return multiCatch.getThrowableTypes();
                }
                return next.getParameter().getType() == null ? emptyList() : singletonList(next.getParameter().getType());
            }

            private boolean isAnyAssignableTo(List<JavaType> nextTypes, List<JavaType> aCatchTypes) {
                for (JavaType aCatchType : aCatchTypes) {
                    for (JavaType nextType : nextTypes) {
                        if (TypeUtils.isAssignableTo(nextType, aCatchType)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean onlyRethrows(J.Try.Catch aCatch) {
                if (aCatch.getBody().getStatements().size() != 1 ||
                    !(aCatch.getBody().getStatements().get(0) instanceof J.Throw)) {
                    return false;
                }

                Expression exception = ((J.Throw) aCatch.getBody().getStatements().get(0)).getException();

                // In C# an implicit rethrow is possible, which means a `throw` statement without a variable
                if (isCSharpTree(getCursor()) && exception instanceof J.Empty) {
                    return true;
                }

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
