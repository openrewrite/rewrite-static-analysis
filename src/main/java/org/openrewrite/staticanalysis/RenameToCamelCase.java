/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;

public abstract class RenameToCamelCase extends JavaIsoVisitor<ExecutionContext> {

    @Nullable
    private Cursor sourceFileCursor;

    private Cursor getSourceFileCursor() {
        if (sourceFileCursor == null) {
            sourceFileCursor = getCursor().getPathAsCursors(c -> c.getValue() instanceof JavaSourceFile).next();
        }
        return sourceFileCursor;
    }

    @Override
    public @Nullable J postVisit(J tree, ExecutionContext ctx) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) tree;
            Map<J.VariableDeclarations.NamedVariable, String> renameVariablesMap = getCursor().getMessage("RENAME_VARIABLES_KEY", emptyMap());
            Set<String> hasNameSet = getCursor().computeMessageIfAbsent("HAS_NAME_KEY", k -> new HashSet<>());
            for (Map.Entry<J.VariableDeclarations.NamedVariable, String> entry : renameVariablesMap.entrySet()) {
                J.VariableDeclarations.NamedVariable variable = entry.getKey();
                String toName = entry.getValue();
                if (shouldRename(hasNameSet, variable, toName)) {
                    cu = (JavaSourceFile) new RenameVariable<>(variable, toName).visitNonNull(cu, ctx);
                    hasNameSet.add(computeKey(toName, variable));
                }
            }
            return cu;
        }
        return super.postVisit(tree, ctx);
    }

    protected abstract boolean shouldRename(Set<String> hasNameSet, J.VariableDeclarations.NamedVariable variable,
                                            String toName);

    protected void renameVariable(J.VariableDeclarations.NamedVariable variable, String toName) {
        getSourceFileCursor()
                .computeMessageIfAbsent("RENAME_VARIABLES_KEY", k -> new LinkedHashMap<>())
                .put(variable, toName);
    }

    protected void hasNameKey(String variableName) {
        getSourceFileCursor()
                .computeMessageIfAbsent("HAS_NAME_KEY", k -> new HashSet<>())
                .add(variableName);
    }

    protected String computeKey(String identifier, J context) {
        JavaType.Variable fieldType = getFieldType(context);
        if (fieldType != null && fieldType.getOwner() != null) {
            return fieldType.getOwner() + " " + identifier;
        }
        return identifier;
    }

    protected JavaType.@Nullable Variable getFieldType(J tree) {
        if (tree instanceof J.Identifier) {
            return ((J.Identifier) tree).getFieldType();
        }
        if (tree instanceof J.VariableDeclarations.NamedVariable) {
            return ((J.VariableDeclarations.NamedVariable) tree).getVariableType();
        }
        return null;
    }
}
