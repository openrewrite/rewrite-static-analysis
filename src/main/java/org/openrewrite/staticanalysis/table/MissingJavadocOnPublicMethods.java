/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.staticanalysis.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class MissingJavadocOnPublicMethods extends DataTable<MissingJavadocOnPublicMethods.Row> {

    public MissingJavadocOnPublicMethods(Recipe recipe) {
        super(recipe,
                "Public methods missing Javadoc",
                "Public method declarations that are not documented with a Javadoc comment.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the undocumented method.")
        String sourcePath;

        @Column(displayName = "Class",
                description = "The fully qualified name of the class declaring the method.")
        String className;

        @Column(displayName = "Method name",
                description = "The name of the undocumented method.")
        String methodName;

        @Column(displayName = "Parameter types",
                description = "A comma-separated list of the method's parameter types, empty for no-arg methods.")
        String parameterTypes;
    }
}
