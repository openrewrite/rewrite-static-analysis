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
package org.openrewrite.staticanalysis.maven;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.text.Normalizer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Maven's javadoc-plugin configuration does not support non-ASCII characters in Javadoc comments.
 * This can cause build failures with ambiguous error messages that don't clearly indicate the root cause.
 * 
 * This recipe removes non-ASCII characters from Javadoc comments by:
 * 1. Normalizing text using Unicode NFKD form
 * 2. Removing any characters that are not in the ASCII character set
 * 
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
        return new MavenJavadocNonAsciiPruner();
    }

    public class MavenJavadocNonAsciiPruner extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            return processComments(super.visitClassDeclaration(classDecl, ctx), classDecl.getComments());
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            return processComments(super.visitMethodDeclaration(method, ctx), method.getComments());
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
            return processComments(super.visitVariable(variable, ctx), variable.getComments());
            return processComments(super.visitMethodDeclaration(method, executionContext), method.getComments());
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
            return processComments(super.visitVariable(variable, executionContext), variable.getComments());
        }

        private <T extends J> T processComments(T jElement, List<Comment> comments) {
            List<CommentChangeResult> prunedComments = pruneComments(comments);
            if (prunedComments.stream().anyMatch(CommentChangeResult::isChanged)) {
                return jElement.withComments(prunedComments.stream().map(CommentChangeResult::getComment).collect(Collectors.toList()));
            }
            return jElement;
        }

        private List<CommentChangeResult> pruneComments(List<Comment> comments) {
            return comments.stream()
                    .map(this::pruneNonAsciiCharacters)
                    .collect(Collectors.toList());
        }

        private CommentChangeResult pruneNonAsciiCharacters(Comment comment) {
            if (comment instanceof Javadoc.DocComment) {
                Javadoc.DocComment jdc = (Javadoc.DocComment) comment;
                AtomicBoolean changed = new AtomicBoolean(false);
                return new CommentChangeResult(jdc.withBody(
                        jdc.getBody().stream().map(jd -> {
                            if (jd instanceof Javadoc.Text) {
                                Javadoc.Text jdText = (Javadoc.Text) jd;
                                String oldText = jdText.getText();
                                String newText = Normalizer.normalize(jdText.getText(), Normalizer.Form.NFKD)
                                        .replaceAll("[^\\p{ASCII}]", "");
                                if (!oldText.equals(newText)) {
                                    changed.set(true);
                                }
                                return jdText.withText(newText);
                            }
                            return jd;
                        }).collect(Collectors.toList())
                ), changed.get());
            }
            return new CommentChangeResult(comment, false);
        }
    }

    public class CommentChangeResult {
        private final Comment comment;
        private final boolean changed;

        public CommentChangeResult(Comment comment, boolean changed) {
            this.comment = comment;
            this.changed = changed;
        }

        public Comment getComment() {
            return comment;
        }

        public boolean isChanged() {
            return changed;
        }
    }
}
