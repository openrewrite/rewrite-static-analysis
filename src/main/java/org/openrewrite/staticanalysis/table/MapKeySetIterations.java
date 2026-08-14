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
public class MapKeySetIterations extends DataTable<MapKeySetIterations.Row> {

    public MapKeySetIterations(Recipe recipe) {
        super(recipe,
                "Map `keySet()` iterations",
                "Loops that iterate a map's `keySet()` and look the value up again with `get(key)`, and whether " +
                        "they were converted to `entrySet()` iteration.");
    }

    @Value
    public static class Row {

        @Column(displayName = "Source path",
                description = "The path to the source file containing the loop.")
        String sourcePath;

        @Column(displayName = "Class",
                description = "The fully qualified name of the class containing the loop.")
        String enclosingClass;

        @Column(displayName = "Map expression",
                description = "The expression the `keySet()` and `get(key)` calls are made on.")
        String mapExpression;

        @Column(displayName = "Updated",
                description = "Whether the loop was rewritten to iterate `entrySet()`.")
        boolean updated;

        @Column(displayName = "Reason",
                description = "Why the loop was left unchanged. Empty when the loop was updated.")
        String reason;
    }
}
