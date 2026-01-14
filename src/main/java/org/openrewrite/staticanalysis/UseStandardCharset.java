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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;

public class UseStandardCharset extends Recipe {

    @Getter
    final String displayName = "Use `StandardCharset` constants";

    @Getter
    final String description = "Replaces `Charset.forName(java.lang.String)` with the equivalent `StandardCharset` constant.";

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.nio.charset.Charset", false), new JavaVisitor<ExecutionContext>() {
            final MethodMatcher CHARSET_FOR_NAME = new MethodMatcher("java.nio.charset.Charset forName(java.lang.String)");

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (CHARSET_FOR_NAME.matches(m)) {
                    Expression charsetName = m.getArguments().get(0);
                    if (!(charsetName instanceof J.Literal)) {
                        return m;
                    }
                    String maybeReplace = (String) ((J.Literal) charsetName).getValue();
                    if (maybeReplace != null) {
                        maybeAddImport("java.nio.charset.StandardCharsets");

                        Charset charset;
                        try {
                            charset = Charset.forName(maybeReplace);
                        } catch (UnsupportedCharsetException ex) {
                            // This should never happen in practice.
                            return method;
                        }

                        String standardName = "";
                        if (charset == StandardCharsets.ISO_8859_1) {
                            standardName = "ISO_8859_1";
                        } else if (charset == StandardCharsets.US_ASCII) {
                            standardName = "US_ASCII";
                        } else if (charset == StandardCharsets.UTF_8) {
                            standardName = "UTF_8";
                        } else if (charset == StandardCharsets.UTF_16) {
                            standardName = "UTF_16";
                        } else if (charset == StandardCharsets.UTF_16BE) {
                            standardName = "UTF_16BE";
                        } else if (charset == StandardCharsets.UTF_16LE) {
                            standardName = "UTF_16LE";
                        }

                        if (!StringUtils.isBlank(standardName)) {
                            return JavaTemplate.builder("StandardCharsets." + standardName)
                                    .imports("java.nio.charset.StandardCharsets")
                                    .build()
                                    .apply(updateCursor(m), m.getCoordinates().replace());
                        }
                    }
                }
                return m;
            }
        });
    }
}
