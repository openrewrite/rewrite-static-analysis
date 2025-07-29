/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.staticanalysis.maven;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.Space;

import java.text.Normalizer;

/**
 * Maven's javadoc-plugin configuration does not support non-ASCII characters in Javadoc comments.
 * This can cause build failures with ambiguous error messages that don't clearly indicate the root cause.
 * <p>
 * This recipe removes non-ASCII characters from Javadoc comments by:
 * 1. Normalizing text using Unicode NFKD form
 * 2. Removing any characters that are not in the ASCII character set
 * <p>
 * This is particularly useful when working with international codebases or when comments
 * contain accented characters, special symbols, or other non-ASCII content.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class MavenJavadocNonAsciiRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove non-ASCII characters from Javadoc";
    }

    @Override
    public String getDescription() {
        return "Maven's javadoc-plugin configuration does not support non-ASCII characters. " +
                "What makes it tricky is the error is very ambiguous and doesn't help in any way. " +
                "This recipe removes those non-ASCII characters.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, ExecutionContext ctx) {
                Space sp = super.visitSpace(space, loc, ctx);
                return sp.withComments(ListUtils.map(sp.getComments(), c -> {
                    if (c instanceof Javadoc.DocComment) {
                        Javadoc.DocComment jdc = (Javadoc.DocComment) c;
                        return jdc.withBody(ListUtils.map(jdc.getBody(), jd -> {
                            if (jd instanceof Javadoc.Text) {
                                Javadoc.Text jdText = (Javadoc.Text) jd;
                                String newText = Normalizer.normalize(jdText.getText(), Normalizer.Form.NFKD)
                                        .replaceAll("[^\\p{ASCII}]", "");
                                return jdText.getText().equals(newText) ? jd : jdText.withText(newText);
                            }
                            return jd;
                        }));
                    }
                    return c;
                }));
            }
        };
    }
}
