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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class FallThrough extends Recipe {
    @Override
    public String getDisplayName() {
        return "Fall through";
    }

    @Override
    public String getDescription() {
        return "Checks for fall-through in switch statements, adding `break` statements in locations where a case contains Java code but does not have a `break`, `return`, `throw`, or `continue` statement.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S128");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FallThroughVisitor<>();
    }
}
