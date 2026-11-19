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
import org.apache.commons.text.StringEscapeUtils;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceDeprecatedRuntimeExecMethods extends Recipe {
    private static final MethodMatcher RUNTIME_EXEC_CMD = new MethodMatcher("java.lang.Runtime exec(String)");
    private static final MethodMatcher RUNTIME_EXEC_CMD_ENVP = new MethodMatcher("java.lang.Runtime exec(String, String[])");
    private static final MethodMatcher RUNTIME_EXEC_CMD_ENVP_FILE = new MethodMatcher("java.lang.Runtime exec(String, String[], java.io.File)");

    String displayName = "Replace deprecated `Runtime#exec()` methods";

    String description = "Replace literal commands passed to deprecated Runtime.exec(String) methods with argument arrays. " +
            "Dynamic commands are preserved because the original overloads still tokenize these commands at runtime.";

    Set<String> tags = singleton("RSPEC-S1874");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesJavaVersion<>(18), new JavaFileChecker<>()),
                new JavaIsoVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitReference(Javadoc.Reference reference, ExecutionContext ctx) {
                        return reference;
                    }
                };
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                if (RUNTIME_EXEC_CMD.matches(m) || RUNTIME_EXEC_CMD_ENVP.matches(m) || RUNTIME_EXEC_CMD_ENVP_FILE.matches(m)) {
                    Expression command = m.getArguments().get(0);
                    List<Expression> parts = new ArrayList<>();
                    if (!ChainStringBuilderAppendCalls.flatAdditiveExpressions(command, parts)) {
                        return m;
                    }

                    StringBuilder literalCommand = new StringBuilder();
                    for (Expression part : parts) {
                        if (!(part instanceof J.Literal) || ((J.Literal) part).getType() != JavaType.Primitive.String) {
                            return m;
                        }
                        literalCommand.append(((J.Literal) part).getValue());
                    }

                    if (literalCommand.length() == 0 || m.getMethodType() == null) {
                        return m;
                    }

                    updateCursor(m);
                    Cursor cursor = new Cursor(getCursor(), command);
                    String templateCode = "new String[] {" + toStringArguments(new StringTokenizer(literalCommand.toString())) + "}";
                    Expression arguments = JavaTemplate.apply(templateCode, cursor, command.getCoordinates().replace());
                    return m.withArguments(ListUtils.mapFirst(m.getArguments(), a -> arguments))
                            .withMethodType(m.getMethodType().withParameterTypes(ListUtils.mapFirst(
                                    m.getMethodType().getParameterTypes(), t -> arguments.getType())));
                }

                return m;
            }
        });
    }

    private static String toStringArguments(StringTokenizer tokens) {
        StringBuilder sb = new StringBuilder();
        while (tokens.hasMoreTokens()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('"').append(StringEscapeUtils.escapeJava(tokens.nextToken())).append('"');
        }
        return sb.toString();
    }
}
