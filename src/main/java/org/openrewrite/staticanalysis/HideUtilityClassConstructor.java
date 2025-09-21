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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.Set;

import static java.util.Collections.singleton;

@Incubating(since = "7.0.0")
public class HideUtilityClassConstructor extends Recipe {

    @Override
    public String getDisplayName() {
        return "Hide utility class constructor";
    }

    @Override
    public String getDescription() {
        return "Ensures utility classes (classes containing only static methods or fields in their API) do not have a public constructor.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1118");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new HideUtilityClassConstructorVisitor<>();
    }
}
