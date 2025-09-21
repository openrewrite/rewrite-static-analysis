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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class ExplicitInitialization extends Recipe {

    @Override
    public String getDisplayName() {
        return "Explicit initialization";
    }

    @Override
    public String getDescription() {
        return "Checks if any class or object member is explicitly initialized to default for its type value:\n" +
               " - `null` for object references\n" +
               " - zero for numeric types and `char`\n" +
               " - and `false` for `boolean`\n" +
               "Removes explicit initializations where they aren't necessary.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S3052");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new ExplicitInitializationVisitor<>());
    }
}
