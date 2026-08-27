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
public class AnonymousFunctionalInterfaceImplementations extends DataTable<AnonymousFunctionalInterfaceImplementations.Row> {

    public AnonymousFunctionalInterfaceImplementations(Recipe recipe) {
        super(recipe,
                "Anonymous functional interface implementations",
                "Every anonymous class that implements a functional interface, whether or not it could be " +
                        "rewritten to a lambda, plus the sites that could not be decided either way because " +
                        "the supertype carries incomplete type attribution. Sites that were not rewritten " +
                        "carry the reason why.");
    }

    @Value
    public static class Row {

        @Column(displayName = "Source path",
                description = "The path to the source file containing the anonymous class.")
        String sourcePath;

        @Column(displayName = "Class",
                description = "The fully qualified name of the class containing the anonymous class.")
        String enclosingClass;

        @Column(displayName = "Functional interface",
                description = "The fully qualified name of the functional interface being implemented, or the " +
                        "supertype as written at the site when it did not resolve.")
        String functionalInterface;

        @Column(displayName = "Method",
                description = "The name of the interface's single abstract method, or empty when type " +
                        "attribution was too incomplete to identify one.")
        String method;

        @Column(displayName = "Convertible to lambda",
                description = "Whether the anonymous class could be rewritten to a lambda automatically.")
        boolean convertible;

        @Column(displayName = "Reason",
                description = "Why the anonymous class was not rewritten, or empty when it was. Reasons naming " +
                        "missing type information mark sites the recipe is blind to rather than sites that are " +
                        "genuinely unconvertible, which usually means the LST was built without the " +
                        "dependencies those types come from.")
        String reason;
    }
}
