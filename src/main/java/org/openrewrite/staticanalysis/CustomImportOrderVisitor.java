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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.CustomImportOrderStyle;
import org.openrewrite.java.style.CustomImportOrderStyle.GroupWithDepth;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;

import java.util.*;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

@EqualsAndHashCode(callSuper = false)
@Value
public class CustomImportOrderVisitor<P> extends JavaIsoVisitor<P> {
    CustomImportOrderStyle style;

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        String pkgName = cu.getPackageDeclaration() != null ?
                cu.getPackageDeclaration().getExpression().printTrimmed(getCursor()) :
                "";

        List<JRightPadded<J.Import>> originalImports = cu.getPadding().getImports();
        if (originalImports.isEmpty() || style.getImportOrder().isEmpty()) {
            return cu;
        }

        // Prepare group order and containers
        Map<CustomImportOrderStyle.GroupWithDepth, List<JRightPadded<J.Import>>> groupedImports = new LinkedHashMap<>();
        for (CustomImportOrderStyle.GroupWithDepth groupDef : style.getImportOrder()) {
            groupedImports.put(groupDef, new ArrayList<>());
        }

        // Group each import
        List<JRightPadded<J.Import>> unmatchedImports = new ArrayList<>();
        for (JRightPadded<J.Import> impPad : originalImports) {
            CustomImportOrderStyle.GroupWithDepth group = null;
            for (CustomImportOrderStyle.GroupWithDepth groupDef : style.getImportOrder()) {
                if (belongsToGroup(groupDef, impPad, pkgName)) {
                    group = groupDef;
                    break;
                }
            }
            if (group != null) {
                groupedImports.get(group).add(impPad);
            } else {
                unmatchedImports.add(impPad);
            }
        }

        // Sort alphabetically in group
        for (List<JRightPadded<J.Import>> groupImports : groupedImports.values()) {
            if (style.getSortImportsInGroupAlphabetically() && groupImports.size() > 1) {
                groupImports.sort(Comparator.comparing(
                        (JRightPadded<J.Import> irp) -> irp.getElement().getQualid().printTrimmed(getCursor())
                ));
            }
        }

        List<JRightPadded<J.Import>> finalOrderedImports = new ArrayList<>();
        Collection<List<JRightPadded<J.Import>>> allImportGroups = new ArrayList<>(groupedImports.values());
        if (!unmatchedImports.isEmpty()) {
            allImportGroups.add(unmatchedImports);
        }

        boolean firstGroupEmitted = false;
        boolean previousGroupWasStatic = false;
        boolean hasInsertedStaticNonStaticSeparator = false;
        int currentGroupIndex = 0;

        for (List<JRightPadded<J.Import>> importGroup : allImportGroups) {
            if (importGroup.isEmpty()) continue;
            boolean groupIsStatic = importGroup.get(0).getElement().isStatic();

            if (style.getSeparateLineBetweenGroups() || !firstGroupEmitted) {
                // Blank line before every group if separated, or before the very first group
                normalizeGroupWhitespace(importGroup, true);
                firstGroupEmitted = true;
            }
            currentGroupIndex++;

            if (!style.getSeparateLineBetweenGroups() && currentGroupIndex > 1) {
                // No separation: We need to handle the special static->non-static transition only
                // Remove extra blank lines, normalize prefix
                normalizeGroupWhitespace(importGroup, false);

                if (groupIsStatic) {
                    // Add blank line before static group (convention)
                    normalizeGroupWhitespace(importGroup, true);
                }

                // If prior group was static, current is non-static, handle the only allowed separator
                if (previousGroupWasStatic && !hasInsertedStaticNonStaticSeparator && !groupIsStatic) {
                    normalizeGroupWhitespace(importGroup, true);
                    hasInsertedStaticNonStaticSeparator = true;
                }
            }

            previousGroupWasStatic = groupIsStatic;
            finalOrderedImports.addAll(importGroup);
        }

        for (int i = 0; i < finalOrderedImports.size(); i++) {
            String orderedImport = finalOrderedImports.get(i).getElement().print(getCursor());
            String originalImport = cu.getPadding().getImports().get(i).getElement().print(getCursor());
            if (!orderedImport.equals(originalImport)) {
                cu = cu.getPadding().withImports(finalOrderedImports);
                break;
            }
        }

        return cu;
    }

    private boolean belongsToGroup(CustomImportOrderStyle.GroupWithDepth groupDef, JRightPadded<J.Import> anImport, String pkgName) {
        J.Import imp = anImport.getElement();
        String importFqcn = imp.getQualid().printTrimmed(getCursor());
        Pattern specialPattern = compile(style.getSpecialImportsRegExp());
        Pattern standardPattern = compile(style.getStandardPackageRegExp());
        Pattern thirdPartyPattern = compile(style.getThirdPartyPackageRegExp());
        switch (groupDef.getGroup()) {
            case STATIC:
                return imp.isStatic();
            case SAME_PACKAGE:
                Integer depth = groupDef.getDepth();
                return depth != null && isSamePackage(importFqcn, pkgName, depth);
            case STANDARD_JAVA_PACKAGE:
                return !imp.isStatic() && standardPattern.matcher(importFqcn).find();
            case SPECIAL_IMPORTS:
                return !imp.isStatic() && specialPattern.matcher(importFqcn).find();
            case THIRD_PARTY_PACKAGE:
                boolean notOther =
                        !imp.isStatic() && !specialPattern.matcher(importFqcn).find() &&
                                !standardPattern.matcher(importFqcn).find() &&
                                (groupDef.getDepth() == null ||
                                !isSamePackage(importFqcn, pkgName, groupDef.getDepth()));
                boolean matchesThird = thirdPartyPattern.matcher(importFqcn).find();
                return notOther && matchesThird;
            default:
                return false;
        }
    }

    private boolean isSamePackage(String importFqcn, String pkgName, int depth) {
        if (depth <= 0 || pkgName.isEmpty()) return false;
        String[] importParts = importFqcn.split("\\.");
        String[] pkgParts = pkgName.split("\\.");
        if (importParts.length < depth || pkgParts.length < depth) {
            return false;
        }
        for (int i = 0; i < depth; i++) {
            if (!importParts[i].equals(pkgParts[i])) return false;
        }
        return true;
    }

    private void normalizeGroupWhitespace(List<JRightPadded<J.Import>> group, boolean useDoubleNewline) {
        if (group.isEmpty()) return;
        JRightPadded<J.Import> first = group.get(0);
        String newWs = first.getElement().getPrefix().getWhitespace().replaceFirst("^\\n+", useDoubleNewline ? "\n\n" : "\n");
        group.set(0, first.withElement(first.getElement().withPrefix(Space.format(newWs))));
        for (int i = 1; i < group.size(); i++) {
            JRightPadded<J.Import> imp = group.get(i);
            J.Import orig = imp.getElement();
            String ws = orig.getPrefix().getWhitespace().replaceFirst("^\\n+", "\n");
            group.set(i, imp.withElement(orig.withPrefix(Space.format(ws))));
        }
    }
}
