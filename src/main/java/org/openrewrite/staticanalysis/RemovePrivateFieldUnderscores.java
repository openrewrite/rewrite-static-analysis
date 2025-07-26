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
package org.openrewrite.staticanalysis;

import org.openrewrite.Recipe;

public class RemovePrivateFieldUnderscores extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove underscores from private class field names";
    }

    @Override
    public String getDescription() {
        return "Removes prefix or suffix underscores from private class field names and adds `this.` to all field accesses for clarity and consistency.";
    }
}
