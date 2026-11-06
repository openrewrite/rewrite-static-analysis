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

import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/** Public or protected class declarations whose binary API must be preserved. */
public final class PublicApi {
    private PublicApi() {
    }

    public static boolean isExternallyVisible(J.ClassDeclaration declaration) {
        JavaType.FullyQualified type = declaration.getType();
        // A public nested class can be inherited through a public subclass even when
        // its declaring outer class is inaccessible. Do not narrow it based on its owner.
        return declaration.hasModifier(J.Modifier.Type.Public) ||
               declaration.hasModifier(J.Modifier.Type.Protected) ||
               type != null && (type.hasFlags(Flag.Public) || type.hasFlags(Flag.Protected));
    }
}
