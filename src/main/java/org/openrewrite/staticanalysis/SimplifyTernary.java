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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Simplify ternary expressions",
        description = "Simplifies various types of ternary expressions to improve code readability."
)
public class SimplifyTernary {

    @RecipeDescriptor(
            name = "Replace `booleanExpression ? true : false` with `booleanExpression`",
            description = "Replace ternary expressions like `booleanExpression ? true : false` with `booleanExpression`."
    )
    public static class SimplifyTernaryTrueFalse {

        @BeforeTemplate
        boolean before(boolean expr) {
            return expr ? true : false;
        }

        @AfterTemplate
        boolean after(boolean expr) {
            return expr;
        }
    }

    @RecipeDescriptor(
            name = "Replace `booleanExpression ? false : true` with `!booleanExpression`",
            description = "Replace ternary expressions like `booleanExpression ? false : true` with `!booleanExpression`."
    )
    public static class SimplifyTernaryFalseTrue {

        @BeforeTemplate
        boolean before(boolean expr) {
            return expr ? false : true;
        }

        @AfterTemplate
        boolean after(boolean expr) {
            return !(expr);
        }
    }
}
