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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

public class LowercasePackage extends ScanningRecipe<Map<String, String>> {

    @Getter
    final String displayName = "Rename packages to lowercase";

    @Getter
    final String description = "By convention all Java package names should contain only lowercase letters, numbers, and dashes. " +
            "This recipe converts any uppercase letters in package names to be lowercase.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S120");

    @Override
    public Map<String, String> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, String> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J preVisit(J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                    J.Package pkg = cu.getPackageDeclaration();
                    if (pkg != null) {
                        String packageText = getPackageText(getCursor(), pkg);
                        String lowerCase = packageText.toLowerCase();
                        if (!packageText.equals(lowerCase)) {
                            acc.put(packageText, lowerCase);
                        }
                    }
                    stopAfterPreVisit();
                }
                return super.preVisit(tree, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, String> acc) {
        return Preconditions.check(!acc.isEmpty(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                    J.Package pkg = cu.getPackageDeclaration();
                    if (pkg != null) {
                        String packageText = getPackageText(getCursor(), pkg);
                        if (acc.containsKey(packageText)) {
                            return (JavaSourceFile) new ChangePackage(packageText, acc.get(packageText), true)
                                    .getVisitor().visitNonNull(cu, ctx);
                        }
                    }
                    stopAfterPreVisit();
                }
                return tree;
            }
        });
    }

    private String getPackageText(Cursor cursor, J.Package pkg) {
        return pkg.getExpression().print(cursor).replaceAll("\\s", "");
    }
}
