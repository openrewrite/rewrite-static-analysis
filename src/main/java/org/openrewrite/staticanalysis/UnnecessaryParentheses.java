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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.cleanup.UnnecessaryParenthesesVisitor;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnnecessaryParentheses extends Recipe {

    @Getter
    final String displayName = "Remove unnecessary parentheses";

    @Getter
    final String description = "Removes unnecessary parentheses from code where extra parentheses pairs are redundant.";

    @Getter
    final Set<String> tags = new LinkedHashSet<>(Arrays.asList("RSPEC-S1110", "RSPEC-S1611"));

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UnnecessaryParenthesesVisitor<>();
    }
}
