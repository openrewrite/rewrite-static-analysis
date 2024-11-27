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
package org.openrewrite.staticanalysis.nameconvention;

import org.openrewrite.internal.NameCaseConvention;

import java.util.regex.Pattern;

class CsharpNameConvention implements NameConvention {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-zA-Z0-9]+_\\w+$");

    @Override
    public String applyNameConvention(String normalizedName) {
        StringBuilder result = new StringBuilder();
        if (SNAKE_CASE.matcher(normalizedName).matches()) {
            result.append(NameCaseConvention.format(NameCaseConvention.UPPER_CAMEL, normalizedName));
        } else {
            int nameLength = normalizedName.length();
            for (int i = 0; i < nameLength; i++) {
                char c = normalizedName.charAt(i);

                if (i == 0) {
                    // the java specification requires identifiers to start with [a-zA-Z$_]
                    if (c != '$' && c != '_') {
                        result.append(Character.toUpperCase(c));
                    }
                } else {
                    if (!Character.isLetterOrDigit(c)) {
                        while (i < nameLength && (!Character.isLetterOrDigit(c) || c > 'z')) {
                            c = normalizedName.charAt(i++);
                        }
                        if (i < nameLength) {
                            result.append(Character.toUpperCase(c));
                        }
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }
}
