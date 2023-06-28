/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.Set;

public class ReplaceWeekYearWithYear extends Recipe {
    @Override
    public String getDisplayName() {
        return "Week Year (YYYY) should not be used for date formatting";
    }

    @Override
    public String getDescription() {
        return "For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of" +
               " December and first week of January Week Year could produce unexpected results.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3986");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("java.util.Date", false),
                        new UsesType<>("java.time.format.DateTimeFormatter", false),
                        new UsesType<>("java.text.SimpleDateFormat", false)
                ),
                new ReplaceWeekYearVisitor()
        );
    }

    private static class ReplaceWeekYearVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.Literal visitLiteral(J.Literal li, ExecutionContext ctx) {
            if (li.getValue() instanceof String) {
                String value = li.getValueSource();
                if (value != null && value.contains("YY")) {
                    String newValue = value.replace('Y', 'y');
                    return li.withValueSource(newValue).withValue(newValue);
                }
            }

            return li;
        }
    }
}
