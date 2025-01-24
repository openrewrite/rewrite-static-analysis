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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Set;

import static org.openrewrite.java.tree.J.Modifier.Type.*;

public class AbstractClassPublicConstructor extends Recipe {
    @Override
    public String getDisplayName() {
        return "Constructors of an `abstract` class should not be declared `public`";
    }

    @Override
    public String getDescription() {
        return "Constructors of `abstract` classes can only be called in constructors of their subclasses. " +
                "Therefore the visibility of `public` constructors are reduced to `protected`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S5993");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.hasModifier(Abstract)) {
                    return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), st -> {
                        if (st instanceof J.MethodDeclaration && ((J.MethodDeclaration) st).isConstructor()) {
                            return ((J.MethodDeclaration) st).withModifiers(ListUtils.map(((J.MethodDeclaration) st).getModifiers(),
                                    mod -> mod.getType() == Public ? mod.withType(Protected) : mod));
                        }
                        return st;
                    })));
                }
                return cd;
            }
        };
    }
}
