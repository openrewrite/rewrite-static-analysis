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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class ReplaceDeprecatedRuntimeExecMethods extends Recipe {
    private static final MethodMatcher RUNTIME_EXEC_CMD = new MethodMatcher("java.lang.Runtime exec(String)");
    private static final MethodMatcher RUNTIME_EXEC_CMD_ENVP = new MethodMatcher("java.lang.Runtime exec(String, String[])");
    private static final MethodMatcher RUNTIME_EXEC_CMD_ENVP_FILE = new MethodMatcher("java.lang.Runtime exec(String, String[], java.io.File)");

    @Getter
    final String displayName = "Replace deprecated `Runtime#exec()` methods";

    @Getter
    final String description = "Replace `Runtime#exec(String)` methods to use `exec(String[])` instead because the former is deprecated " +
            "after Java 18 and is no longer recommended for use by the Java documentation. Only commands made up entirely of " +
            "string literals are replaced, because only then is it known at compile time which arguments " +
            "`Runtime#exec(String)` would build; any other command is left unchanged rather than launched with " +
            "different arguments.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(3);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesJavaVersion<>(18), new JavaIsoVisitor<ExecutionContext>() {
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
                    List<Expression> commands = new ArrayList<>();
                    boolean flattenAble = ChainStringBuilderAppendCalls.flatAdditiveExpressions(command, commands);

                    StringBuilder sb = new StringBuilder();
                    if (flattenAble) {
                        for (Expression e : commands) {
                            if (e instanceof J.Literal && ((J.Literal) e).getType() == JavaType.Primitive.String &&
                                    isDecoded((J.Literal) e)) {
                                sb.append(((J.Literal) e).getValue());
                            } else {
                                flattenAble = false;
                                break;
                            }
                        }
                    }

                    // Only a command made up entirely of string literals can be converted, because `String#split(" ")`
                    // does not reproduce `Runtime#exec(String)`, which tokenizes on any of ' ', '\t', '\n', '\r' and
                    // '\f' and collapses runs of them. Anything else is left alone rather than launching a different
                    // process.
                    if (!flattenAble) {
                        return m;
                    }

                    List<String> cmds = new ArrayList<>();
                    for (StringTokenizer tokenizer = new StringTokenizer(sb.toString()); tokenizer.hasMoreTokens(); ) {
                        cmds.add(tokenizer.nextToken());
                    }
                    // `exec("")` throws `IllegalArgumentException("Empty command")` where `exec(new String[]{})`
                    // throws `IndexOutOfBoundsException`, so a command that tokenizes to nothing is left alone.
                    JavaType.Method methodType = m.getMethodType();
                    if (cmds.isEmpty() || methodType == null) {
                        return m;
                    }

                    updateCursor(m);
                    JavaTemplate template = JavaTemplate.builder(String.format("new String[] {%s}", toStringArguments(cmds))).build();

                    List<Expression> args = m.getArguments();
                    Cursor cursor = new Cursor(getCursor(), args.get(0));
                    args.set(0, template.apply(cursor, args.get(0).getCoordinates().replace()));

                    // `getParameterTypes()` is a write through view of the `JavaType.Method`, which is interned and
                    // therefore shared by every other call of the same overload, so copy it before replacing.
                    List<JavaType> parameterTypes = new ArrayList<>(methodType.getParameterTypes());
                    parameterTypes.set(0, new JavaType.Array(null, JavaType.ShallowClass.build("java.lang.String"), null));
                    return m.withArguments(args)
                            .withMethodType(methodType.withParameterTypes(parameterTypes));
                }

                return m;
            }
        });
    }

    /**
     * The source of a string literal is its value plus at least the two quotes around it. Where that does not hold the
     * parser did not decode the literal, which happens for a unicode escape of a supplementary character, and the value
     * read back is not the command that would be executed.
     */
    private static boolean isDecoded(J.Literal literal) {
        Object value = literal.getValue();
        String valueSource = literal.getValueSource();
        return value != null && valueSource != null && String.valueOf(value).length() + 2 <= valueSource.length();
    }

    private static String toStringArguments(List<String> cmds) {
        StringBuilder sb = new StringBuilder();
        for (String token : cmds) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append('"');
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c < ' ' || (c >= 0x7f && c <= 0x9f) ||
                        (c == '#' && i + 1 < token.length() && token.charAt(i + 1) == '{')) {
                    // Control characters are escaped so that they stay legible rather than becoming invisible
                    // bytes. `#{` has to be escaped because this text is also `JavaTemplate` source, where `#{`
                    // opens a parameter placeholder.
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        }
        return sb.toString();
    }
}
