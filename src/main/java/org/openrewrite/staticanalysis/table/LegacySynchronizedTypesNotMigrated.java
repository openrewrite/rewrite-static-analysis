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
public class LegacySynchronizedTypesNotMigrated extends DataTable<LegacySynchronizedTypesNotMigrated.Row> {

    public LegacySynchronizedTypesNotMigrated(Recipe recipe) {
        super(recipe,
                "Legacy synchronized types not migrated",
                "Instances of a legacy synchronized type (`Hashtable`, `Vector`, `Stack`, `StringBuffer`) that " +
                        "were found but left unchanged because they could not be proven safe to modernize.");
    }

    @Value
    public static class Row {

        @Column(displayName = "Source path",
                description = "The path to the source file containing the unmigrated reference.")
        String sourcePath;

        @Column(displayName = "Class",
                description = "The fully qualified name of the class containing the reference.")
        String enclosingClass;

        @Column(displayName = "Unmigrated type",
                description = "The fully qualified name of the legacy synchronized type that was found but not migrated.")
        String unmigratedType;

        @Column(displayName = "Reason",
                description = "Why the instance was left unchanged.")
        String reason;
    }
}
