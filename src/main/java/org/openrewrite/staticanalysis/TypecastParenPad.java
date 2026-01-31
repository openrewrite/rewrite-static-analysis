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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.SpacesVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.SpacesStyle;
import org.openrewrite.java.style.TypecastParenPadStyle;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.style.Style;

public class TypecastParenPad extends Recipe {
    @Getter
    final String displayName = "Typecast parenthesis padding";

    @Getter
    final String description = "Fixes whitespace padding between a typecast type identifier and the enclosing left and right parentheses. " +
            "For example, when configured to remove spacing, `( int ) 0L;` becomes `(int) 0L;`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        //noinspection NotNullFieldNotInitialized
        return Preconditions.check(
                Preconditions.not(new GroovyFileChecker<>()),
                new JavaIsoVisitor<ExecutionContext>() {
                    SpacesStyle spacesStyle;
                    TypecastParenPadStyle typecastParenPadStyle;

                    @Override
                    public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                        if (tree instanceof JavaSourceFile) {
                            SourceFile cu = (SourceFile) tree;
                            spacesStyle = Style.from(SpacesStyle.class, cu, IntelliJ::spaces);
                            typecastParenPadStyle = Style.from(TypecastParenPadStyle.class, cu, Checkstyle::typecastParenPadStyle);

                            spacesStyle = spacesStyle.withWithin(spacesStyle.getWithin().withTypeCastParentheses(typecastParenPadStyle.getSpace()));
                        }
                        return super.visit(tree, ctx);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                        J.TypeCast tc = super.visitTypeCast(typeCast, ctx);
                        // Only format the clazz part (type inside parentheses), not the entire typecast
                        // which would incorrectly apply spacing rules to nested expressions
                        J.ControlParentheses<TypeTree> clazz = tc.getClazz();
                        J.ControlParentheses<TypeTree> formattedClazz = (J.ControlParentheses<TypeTree>) new SpacesVisitor<>(spacesStyle, null, null, clazz)
                                .visitNonNull(clazz, ctx, getCursor().fork());
                        tc = tc.withClazz(formattedClazz);

                        // Handle afterTypeCast spacing (space between closing paren and expression)
                        boolean afterTypeCast = spacesStyle.getOther().getAfterTypeCast();
                        String expectedPrefix = afterTypeCast ? " " : "";
                        Expression expr = tc.getExpression();
                        String currentPrefix = expr.getPrefix().getWhitespace();
                        if (!currentPrefix.equals(expectedPrefix) && !currentPrefix.contains("\n")) {
                            tc = tc.withExpression(expr.withPrefix(expr.getPrefix().withWhitespace(expectedPrefix)));
                        }
                        return tc;
                    }
                }
        );
    }

}
