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
package org.openrewrite.staticanalysis.pmd;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePmdRule extends Recipe {

    @Option(displayName = "Old rule",
            description = "The rule to replace, either a fully qualified reference such as " +
                          "`category/java/errorprone.xml/MissingBreakInSwitch` or just the rule name, in which case the " +
                          "rule is replaced regardless of which ruleset file it is referenced from.",
            example = "category/java/errorprone.xml/MissingBreakInSwitch")
    String oldRule;

    @Option(displayName = "New rule",
            description = "The rule to replace it with. Give a fully qualified reference when the replacement lives in a " +
                          "different ruleset file; a bare rule name keeps the existing ruleset file.",
            example = "ImplicitSwitchFallThrough")
    String newRule;

    @Override
    public String getDisplayName() {
        return "Replace a PMD rule in a ruleset";
    }

    @Override
    public String getDescription() {
        return "Updates `<rule ref=\"...\"/>` references and `<exclude name=\"...\"/>` elements in PMD ruleset XML files to " +
               "name a rule's replacement. An `<exclude>` is only renamed when the replacement lives in the same ruleset " +
               "file, because an exclusion can only name a rule from the ruleset its enclosing `<rule>` refers to; when " +
               "the replacement moved to another ruleset file the exclusion no longer names a rule PMD knows, so it is " +
               "removed instead.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                // Only PMD rulesets have the `<rule>` and `<exclude>` elements this recipe operates on
                return "ruleset".equals(document.getRoot().getName()) ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ("rule".equals(t.getName())) {
                    String ref = PmdRuleRef.attributeValue(t, "ref");
                    if (ref != null && PmdRuleRef.matches(ref, oldRule)) {
                        String rulesetFile = PmdRuleRef.rulesetFile(ref);
                        String newRef = newRule.indexOf('/') != -1 || rulesetFile == null ?
                                newRule : rulesetFile + '/' + newRule;
                        return replaceAttributeValue(t, "ref", ref, newRef);
                    }
                } else if ("exclude".equals(t.getName())) {
                    // An `<exclude>` names a rule within the ruleset file its enclosing `<rule>` refers to, so it can
                    // only be renamed when the replacement rule lives in that same file. Once the replacement moves
                    // elsewhere the exclusion names a rule that ruleset no longer has, and is dropped.
                    Object parent = getCursor().getParentTreeCursor().getValue();
                    String name = PmdRuleRef.attributeValue(t, "name");
                    if (name != null && parent instanceof Xml.Tag && "rule".equals(((Xml.Tag) parent).getName())) {
                        String rulesetFile = PmdRuleRef.attributeValue((Xml.Tag) parent, "ref");
                        String newRulesetFile = PmdRuleRef.rulesetFile(newRule);
                        if (rulesetFile != null && PmdRuleRef.matches(rulesetFile + '/' + name, oldRule)) {
                            if (newRulesetFile == null || newRulesetFile.equals(rulesetFile)) {
                                return replaceAttributeValue(t, "name", name, PmdRuleRef.name(newRule));
                            }
                            doAfterVisit(new RemoveRulesetContentVisitor<>(t));
                        }
                    }
                }
                return t;
            }

            private Xml.Tag replaceAttributeValue(Xml.Tag tag, String key, String oldValue, String newValue) {
                if (oldValue.equals(newValue)) {
                    return tag;
                }
                return tag.withAttributes(ListUtils.map(tag.getAttributes(), attribute ->
                        key.equals(attribute.getKeyAsString()) ?
                                attribute.withValue(attribute.getValue().withValue(newValue)) : attribute));
            }
        };
    }
}
