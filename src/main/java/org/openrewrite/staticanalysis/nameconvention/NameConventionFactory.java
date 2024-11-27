/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jspecify.annotations.Nullable;

import fj.data.Java;
import org.openrewrite.SourceFile;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class NameConventionFactory {

    static Map<String, NameConvention> conventionMap = new HashMap<>();

    public static @Nullable NameConvention getNameConvention(SourceFile sourceFile) {
        if (sourceFile instanceof Cs){
            conventionMap.computeIfAbsent("csharp", k -> new CsharpNameConvention());
            return conventionMap.get("csharp");
        } else if (sourceFile instanceof J) {
            conventionMap.computeIfAbsent("java", k -> new JavaNameConvention());
            return conventionMap.get("java");
        }
        return null;
    }

}
