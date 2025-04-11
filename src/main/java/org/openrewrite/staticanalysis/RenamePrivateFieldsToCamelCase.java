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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.openrewrite.internal.NameCaseConvention.LOWER_CAMEL;

/**
 * This recipe converts private fields to camel case convention.
 * <p>
 * The first character is set to lower case and existing capital letters are preserved.
 * Special characters that are allowed in java field names `$` and `_` are removed.
 * If a special character is removed the next valid alpha-numeric will be capitalized.
 * <p>
 * Currently, unsupported:
 * - The recipe will not rename fields if the result already exists in a class or the result will be a java reserved keyword.
 */
public class RenamePrivateFieldsToCamelCase extends Recipe {
    private static final AnnotationMatcher LOMBOK_ANNOTATION = new AnnotationMatcher("@lombok.*");

    @Override
    public String getDisplayName() {
        return "Reformat private field names to camelCase";
    }

    @Override
    public String getDescription() {
        return "Reformat private field names to camelCase to comply with Java naming convention. " +
               "The recipe will not rename fields with default, protected or public access modifiers. " +
               "The recipe will not rename private constants. " +
               "The first character is set to lower case and existing capital letters are preserved. " +
               "Special characters that are allowed in java field names `$` and `_` are removed. " +
               "If a special character is removed the next valid alphanumeric will be capitalized. " +
               "The recipe will not rename a field if the result already exists in the class, conflicts with a java reserved keyword, or the result is blank.";
    }

    @Override
    public Set<String> getTags() {
        return new LinkedHashSet<>(Arrays.asList("RSPEC-S116", "RSPEC-S3008"));
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new RenameToCamelCase() {
            @Override
            protected boolean shouldRename(Set<String> hasNameSet, J.VariableDeclarations.NamedVariable variable, String toName) {
                if (toName.isEmpty() || !Character.isAlphabetic(toName.charAt(0))) {
                    return false;
                }
                return hasNameSet.stream().noneMatch(key ->
                        key.equals(toName) ||
                        key.equals(variable.getSimpleName()) ||
                        key.endsWith(" " + toName) ||
                        key.endsWith(" " + variable.getSimpleName())
                );
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                // Skip classes annotated with Lombok annotations, as their fields might be set or exposed by Lombok.
                if (service(AnnotationService.class).matches(getCursor(), LOMBOK_ANNOTATION)) {
                    return classDecl;
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @SuppressWarnings("all")
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                Cursor parentScope = getCursorToParentScope(getCursor());

                // Does support renaming fields in a J.ClassDeclaration.
                // We must have a variable type to make safe changes.
                // Only make changes to private fields that are not constants.
                // Does not apply for instance variables of inner classes
                // Only make a change if the variable does not conform to lower camelcase format.
                JavaType.Variable type = variable.getVariableType();
                if (parentScope.getParent() != null &&
                    parentScope.getParent().getValue() instanceof J.ClassDeclaration &&
                    !(parentScope.getValue() instanceof J.ClassDeclaration) &&
                    type != null &&
                    type.hasFlags(Flag.Private) &&
                    !(type.hasFlags(Flag.Static, Flag.Final)) &&
                    !((J.ClassDeclaration) parentScope.getParent().getValue()).getType().getFullyQualifiedName().contains("$") &&
                    !LOWER_CAMEL.matches(variable.getSimpleName())) {

                    if (variable.getSimpleName().toUpperCase(Locale.getDefault()).equals(variable.getSimpleName()) &&
                        type.hasFlags(Flag.Private, Flag.Final) && !type.hasFlags(Flag.Static) && variable.getInitializer() instanceof J.Literal) {
                        // instead, add a static modifier
                        Set<Flag> flags = new HashSet<>(type.getFlags());
                        flags.add(Flag.Static);
                        getCursor().getParentTreeCursor().putMessage("ADD_STATIC", true);
                        return variable.withVariableType(type.withFlags(flags));
                    }

                    String toName = LOWER_CAMEL.format(variable.getSimpleName());
                    renameVariable(variable, toName);
                } else {
                    hasNameKey(computeKey(variable.getSimpleName(), variable));
                }

                return super.visitVariable(variable, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                if (service(AnnotationService.class).matches(getCursor(), LOMBOK_ANNOTATION)) {
                    return multiVariable;
                }

                J.VariableDeclarations vds = super.visitVariableDeclarations(multiVariable, ctx);
                if (getCursor().getMessage("ADD_STATIC", false)) {
                    return vds.withModifiers(ListUtils.insert(vds.getModifiers(),
                            new J.Modifier(Tree.randomId(), Space.format(" "), Markers.EMPTY,
                                    "static", J.Modifier.Type.Static, emptyList()), 1));
                }
                return vds;
            }

            /**
             * Returns either the current block or a J.Type that may create a reference to a variable.
             * I.E. for(int target = 0; target < N; target++) creates a new name scope for `target`.
             * The name scope in the next J.Block `{}` cannot create new variables with the name `target`.
             * <p>
             * J.* types that may only reference an existing name and do not create a new name scope are excluded.
             */
            private Cursor getCursorToParentScope(Cursor cursor) {
                return cursor.dropParentUntil(is -> is instanceof J.ClassDeclaration || is instanceof J.Block || is instanceof SourceFile);
            }
        });
    }
}
