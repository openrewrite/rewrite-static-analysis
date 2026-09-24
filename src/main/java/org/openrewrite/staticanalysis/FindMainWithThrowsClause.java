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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindMainWithThrowsClause extends Recipe {

    String displayName = "Find `main` methods with a `throws` clause";

    String description = "Marks JVM entry-point `main` methods (`public static void main(String[])` " +
            "or the varargs equivalent) that declare a `throws` clause. Uncaught exceptions from " +
            "`main` propagate to the JVM's default handler, which just prints the stack trace and " +
            "returns exit code 1. Handle exceptions explicitly and exit with a meaningful code.";

    Set<String> tags = singleton("RSPEC-S2096");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    private static final MethodMatcher MAIN = new MethodMatcher("*..* main(java.lang.String[])");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (!MAIN.matches(m, enclosingClass)) {
                    return m;
                }
                if (!m.hasModifier(J.Modifier.Type.Public) || !m.hasModifier(J.Modifier.Type.Static)) {
                    return m;
                }
                if (!(m.getReturnTypeExpression() instanceof J.Primitive) ||
                        ((J.Primitive) m.getReturnTypeExpression()).getType() != JavaType.Primitive.Void) {
                    return m;
                }
                if (m.getThrows() == null || m.getThrows().isEmpty()) {
                    return m;
                }
                return SearchResult.found(m,
                        "`main` declares `throws`; uncaught exceptions propagate to the JVM's " +
                                "default handler. Handle them explicitly and exit with a meaningful code.");
            }
        };
    }
}
