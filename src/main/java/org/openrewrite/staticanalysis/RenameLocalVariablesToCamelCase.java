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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.internal.NameCaseConvention.LOWER_CAMEL;

/**
 * This recipe converts local variables and method parameters to camel case convention.
 * The recipe will not rename variables declared in for loop controls or catches with a single character.
 * <br/>
 * The first character is set to lower case and existing capital letters are preserved.
 * Special characters that are allowed in java field names `$` and `_` are removed.
 * If a special character is removed the next valid alphanumeric will be capitalized.
 * <br/>
 * Currently, unsupported:
 * - The recipe will not rename variables declared in a class.
 * - The recipe will not rename variables if the result already exists in a class or the result will be a java reserved keyword.
 */
public class RenameLocalVariablesToCamelCase extends Recipe {

    @Getter
    final String displayName = "Reformat local variable names to camelCase";

    @Getter
    final String description = "Reformat local variable and method parameter names to camelCase to comply with Java naming convention. " +
            "The recipe will not rename variables declared in for loop controls or catches with a single character. " +
            "The first character is set to lower case and existing capital letters are preserved. " +
            "Special characters that are allowed in java field names `$` and `_` are removed (unless the name starts with one). " +
            "If a special character is removed the next valid alphanumeric will be capitalized. " +
            "Currently, does not support renaming members of classes. " +
            "The recipe will not rename a variable if the result already exists in the class, conflicts with a java reserved keyword, or the result is blank.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S117");
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
                return isAvailableIdentifier(toName, variable, hasNameSet);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                // the meaning of a local variable is “is contained in a method declaration body”.
                if (!isLocalVariable(mv)) {
                    return mv;
                }

                List<J.VariableDeclarations.NamedVariable> variables = mv.getVariables();
                for (J.VariableDeclarations.NamedVariable v : variables) {
                    String name = v.getSimpleName();
                    if (!LOWER_CAMEL.matches(name) && name.length() > 1) {
                        renameVariable(v, LOWER_CAMEL.format(name));
                    } else {
                        hasNameKey(computeKey(name, v));
                    }
                }
                return mv;
            }

            private boolean isLocalVariable(J.VariableDeclarations mv) {
                // The recipe will not rename variables declared in for loop controls or catches.
                if (!isInMethodDeclarationBody() || isDeclaredInForLoopControl() || isDeclaredInCatch() || isMethodArgument()) {
                    return false;
                }

                // Ignore fields (aka "instance variable" or "class variable")
                for (J.VariableDeclarations.NamedVariable v : mv.getVariables()) {
                    if (v.isField(getCursor())) {
                        return false;
                    }
                }

                return true;
            }

            private boolean isMethodArgument() {
                return getCursor().getParentTreeCursor()
                        .getValue() instanceof J.MethodDeclaration;
            }

            private boolean isInMethodDeclarationBody() {
                return getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration ||
                                                        p instanceof J.ClassDeclaration ||
                                                        p instanceof J.NewClass ||
                                                        p == Cursor.ROOT_VALUE).getValue() instanceof J.MethodDeclaration;
            }

            private boolean isDeclaredInForLoopControl() {
                return getCursor().getParentTreeCursor()
                    .getValue() instanceof J.ForLoop.Control;
            }

            private boolean isDeclaredInCatch() {
                Cursor parentScope = getCursorToParentScope(getCursor());
                return parentScope.getValue() instanceof J.Try.Catch || parentScope.getValue() instanceof J.MultiCatch;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                hasNameKey(computeKey(identifier.getSimpleName(), identifier));
                return identifier;
            }

            /**
             * Returns either the current block or a J.Type that may create a reference to a variable.
             * I.E. for(int target = 0; target < N; target++) creates a new name scope for `target`.
             * The name scope in the next J.Block `{}` cannot create new variables with the name `target`.
             * <p>
             * J.* types that may only reference an existing name and do not create a new name scope are excluded.
             */
            private Cursor getCursorToParentScope(Cursor cursor) {
                return cursor.dropParentUntil(is ->
                        is instanceof J.ClassDeclaration ||
                                is instanceof J.Block ||
                                is instanceof J.MethodDeclaration ||
                                is instanceof J.ForLoop ||
                                is instanceof J.ForEachLoop ||
                                is instanceof J.ForLoop.Control ||
                                is instanceof J.Case ||
                                is instanceof J.Try ||
                                is instanceof J.Try.Catch ||
                                is instanceof J.MultiCatch ||
                                is instanceof J.Lambda ||
                                is instanceof JavaSourceFile
                );
            }

            private boolean isAvailableIdentifier(String identifier, J context, Set<String> hasNameSet) {
                if (hasNameSet.contains(identifier)) {
                    return false;
                }
                JavaType.Variable fieldType = getFieldType(context);
                if (fieldType != null && fieldType.getOwner() != null) {
                    if (hasNameSet.contains(fieldType.getOwner() + " " + identifier)) {
                        return false;
                    }
                    if (fieldType.getOwner() instanceof JavaType.Method) {
                        // Add all enclosing classes
                        JavaType.FullyQualified declaringType = ((JavaType.Method) fieldType.getOwner()).getDeclaringType();
                        while (declaringType != null) {
                            if (hasNameSet.contains(declaringType + " " + identifier)) {
                                return false;
                            }
                            declaringType = declaringType.getOwningClass();
                        }
                    }
                }
                return true;
            }
        });
    }
}
