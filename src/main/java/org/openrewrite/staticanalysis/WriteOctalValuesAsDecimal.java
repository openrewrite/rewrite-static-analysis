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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.singleton;

public class WriteOctalValuesAsDecimal extends Recipe {
    @Getter
    final String displayName = "Write octal values as decimal";

    @Getter
    final String description = "Developers may not recognize octal values as such, " +
            "mistaking them instead for decimal values. Because a leading zero " +
            "silently switches the literal to base-8, what looks like `010` " +
            "actually represents `8`, which is a common source of subtle " +
            "numeric bugs.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1314");

    private static final Pattern OCTAL_LITERAL = Pattern.compile("0[0-7_]*[0-7][lL]?");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
                String src = literal.getValueSource();
                if (src != null && literal.getValue() != null && OCTAL_LITERAL.matcher(src).matches() &&
                        !isGoFileMode(literal, getCursor())) {
                    String suffix = src.endsWith("l") || src.endsWith("L") ? src.substring(src.length() - 1) : "";
                    return literal.withValueSource(literal.getValue() + suffix);
                }
                return super.visitLiteral(literal, ctx);
            }
        });
    }

    private static boolean isGoFileMode(J.Literal literal, Cursor cursor) {
        Object parent = cursor.getParentTreeCursor().getValue();
        if (parent instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) parent;
            int i = mi.getArguments().indexOf(literal);
            if (i < 0) {
                return false;
            }
            // os.FileMode(0600) conversion, which carries no attributed method type
            if ("FileMode".equals(mi.getSimpleName())) {
                return true;
            }
            if (mi.getMethodType() != null) {
                List<JavaType> params = mi.getMethodType().getParameterTypes();
                if (!params.isEmpty()) {
                    return isGoFileModeType(i < params.size() ? params.get(i) : params.get(params.size() - 1));
                }
            }
        } else if (parent instanceof J.VariableDeclarations.NamedVariable) {
            Object grandparent = cursor.getParentTreeCursor().getParentTreeCursor().getValue();
            if (grandparent instanceof J.VariableDeclarations) {
                TypeTree typeExpression = ((J.VariableDeclarations) grandparent).getTypeExpression();
                return typeExpression != null && isGoFileModeType(typeExpression.getType());
            }
        }
        return false;
    }

    private static boolean isGoFileModeType(@Nullable JavaType type) {
        // os.FileMode is an alias resolved to io/fs.FileMode
        return TypeUtils.isOfClassType(type, "io/fs.FileMode") || TypeUtils.isOfClassType(type, "os.FileMode");
    }
}
