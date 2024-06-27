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
package org.openrewrite.staticanalysis;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class MaskCreditCardNumbers extends Recipe {

    @Override
    public String getDisplayName() {
        return "Mask credit card numbers";
    }

    @Override
    public String getDescription() {
        return "When encountering string literals which appear to be credit card numbers, " +
               "mask the last eight digits with the letter 'X'.";
    }

    private static final Pattern CC_PATTERN = Pattern.compile("([0-9]{4} ?[0-9]{4} ?)([0-9]{4} ?[0-9]{4} ?)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal l = super.visitLiteral(literal, executionContext);
                if(l.getValue() instanceof String) {
                    String value = (String) l.getValue();
                    Matcher m = CC_PATTERN.matcher(value);
                    if(m.matches()) {
                        String masked = m.group(1) +maskDigits(m.group(2));
                        l = l.withValue(masked)
                                .withValueSource("\"" + masked + "\"");
                    }
                }
                return l;
            }
        };
    }

    private static String maskDigits(String digits) {
        return digits.replaceAll("[0-9]", "X");
    }
}
