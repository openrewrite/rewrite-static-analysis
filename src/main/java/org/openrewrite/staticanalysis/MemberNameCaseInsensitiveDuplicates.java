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
package org.openrewrite.staticanalysis;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class MemberNameCaseInsensitiveDuplicates extends Recipe {

    String displayName = "Members should not have names differing only by capitalization";
    String description = "Looking at the set of methods and fields in a class and all of its parents, " +
            "no two members should have names that differ only in capitalization. " +
            "This rule will not report if a method overrides a parent method.";
    Set<String> tags = singleton("RSPEC-S1845");
    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    private static final String MESSAGE = "Rename this member to not match other members differing only by capitalization";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                // Collect member names from parent classes and interfaces, keyed by lowercase
                Set<String> inheritedNames = new HashSet<>();
                Map<String, Set<String>> inheritedByLowerCase = new HashMap<>();
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(cd.getType());
                if (type != null) {
                    Set<String> visited = new HashSet<>();
                    collectInheritedNames(type.getSupertype(), inheritedNames, visited);
                    for (JavaType.FullyQualified iface : type.getInterfaces()) {
                        collectInheritedNames(iface, inheritedNames, visited);
                    }
                    for (String name : inheritedNames) {
                        inheritedByLowerCase.computeIfAbsent(name.toLowerCase(), k -> new HashSet<>()).add(name);
                    }
                }

                // Collect current class member names
                List<MemberInfo> currentMembers = new ArrayList<>();
                for (Object stmt : cd.getBody().getStatements()) {
                    if (stmt instanceof J.VariableDeclarations) {
                        for (J.VariableDeclarations.NamedVariable var : ((J.VariableDeclarations) stmt).getVariables()) {
                            currentMembers.add(new MemberInfo(var.getSimpleName(), var));
                        }
                    } else if (stmt instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) stmt;
                        if (method.isConstructor()) {
                            continue;
                        }
                        currentMembers.add(new MemberInfo(method.getSimpleName(), method));
                    }
                }

                // Check each current member against inherited names and other current members
                Set<J> marked = new HashSet<>();
                for (int i = 0; i < currentMembers.size(); i++) {
                    MemberInfo member = currentMembers.get(i);

                    // Check against inherited names via O(1) lookup
                    Set<String> sameKey = inheritedByLowerCase.get(member.name.toLowerCase());
                    if (sameKey != null && !sameKey.contains(member.name)) {
                        marked.add(member.tree);
                    }

                    // Check against other members in the same class
                    for (int j = i + 1; j < currentMembers.size(); j++) {
                        MemberInfo other = currentMembers.get(j);
                        if (member.name.equalsIgnoreCase(other.name) && !member.name.equals(other.name)) {
                            if (!inheritedNames.contains(member.name)) {
                                marked.add(member.tree);
                            }
                            if (!inheritedNames.contains(other.name)) {
                                marked.add(other.tree);
                            }
                        }
                    }
                }

                if (marked.isEmpty()) {
                    return cd;
                }

                // Apply SearchResult markers
                return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), stmt -> {
                    if (stmt instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                        return vd.withVariables(ListUtils.map(vd.getVariables(), var ->
                                marked.contains(var) ? SearchResult.found(var, MESSAGE) : var));
                    } else if (stmt instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) stmt;
                        if (marked.contains(method)) {
                            return method.withName(SearchResult.found(method.getName(), MESSAGE));
                        }
                    }
                    return stmt;
                })));
            }

            private void collectInheritedNames(JavaType.@Nullable FullyQualified type, Set<String> names, Set<String> visited) {
                if (type == null || !visited.add(type.getFullyQualifiedName())) {
                    return;
                }
                for (JavaType.Variable member : type.getMembers()) {
                    if (!member.hasFlags(Flag.Private)) {
                        names.add(member.getName());
                    }
                }
                for (JavaType.Method method : type.getMethods()) {
                    if (!method.isConstructor() && !method.hasFlags(Flag.Private)) {
                        names.add(method.getName());
                    }
                }
                collectInheritedNames(type.getSupertype(), names, visited);
                for (JavaType.FullyQualified iface : type.getInterfaces()) {
                    collectInheritedNames(iface, names, visited);
                }
            }
        });
    }

    @RequiredArgsConstructor
    private static class MemberInfo {
        final String name;
        final J tree;
    }
}
