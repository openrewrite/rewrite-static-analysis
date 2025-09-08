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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class FinalClass extends Recipe {

    @Option(displayName = "Include never-extended classes",
            description = "Finalize classes that are never extended anywhere in the codebase",
            required = false)
    @Nullable
    Boolean includeNeverExtended;

    @Option(displayName = "Exclude packages",
            description = "Package patterns to exclude from never-extended finalization (e.g., com.example.api.*)",
            example = "com.example.api.*",
            required = false)
    @Nullable
    List<String> excludePackages;

    @Option(displayName = "Exclude annotations",
            description = "Classes with these annotations won't be finalized when using never-extended mode",
            example = "@ExtensionPoint",
            required = false)
    @Nullable
    List<String> excludeAnnotations;

    @Override
    public String getDisplayName() {
        return "Finalize classes with private constructors";
    }

    @Override
    public String getDescription() {
        return "Adds the `final` modifier to classes that expose no public or package-private constructors." +
                "Optionally, can also finalize classes that are never extended anywhere in the codebase.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2974");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        boolean includeNeverExtendedFlag = Boolean.TRUE.equals(includeNeverExtended);
        List<String> excludePackagesList = excludePackages != null ? excludePackages : Collections.emptyList();
        List<String> excludeAnnotationsList = excludeAnnotations != null ? excludeAnnotations : Collections.emptyList();
        FinalClassVisitor visitor = new FinalClassVisitor(includeNeverExtendedFlag, excludePackagesList, excludeAnnotationsList);
        return Preconditions.check(new JavaFileChecker<>(), visitor);
    }
}
