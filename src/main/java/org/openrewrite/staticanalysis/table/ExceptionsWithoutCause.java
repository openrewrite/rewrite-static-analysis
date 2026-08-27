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
public class ExceptionsWithoutCause extends DataTable<ExceptionsWithoutCause.Row> {

    public ExceptionsWithoutCause(Recipe recipe) {
        super(recipe,
                "Exceptions thrown without the caught cause",
                "New exceptions thrown from a `catch` block that do not reference the caught exception.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the offending `throw`.")
        String sourcePath;

        @Column(displayName = "Caught exception type",
                description = "The declared type of the exception caught by the enclosing `catch` clause.")
        String caughtType;

        @Column(displayName = "Thrown exception type",
                description = "The type of the new exception thrown without referencing the caught exception.")
        String thrownType;
    }
}
