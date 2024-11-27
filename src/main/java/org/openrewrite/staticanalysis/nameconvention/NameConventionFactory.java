package org.openrewrite.staticanalysis.nameconvention;

import fj.data.Java;
import org.openrewrite.SourceFile;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class NameConventionFactory {

    static Map<String, NameConvention> conventionMap = new HashMap<>();

    public static NameConvention getNameConvention(SourceFile sourceFile) {
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
