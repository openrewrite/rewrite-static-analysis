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

import java.math.BigDecimal;

@RecipeDescriptor(
        name = "`new BigDecimal(double)` should not be used",
        description = "Use of `new BigDecimal(double)` constructor can lead to loss of precision. Use `BigDecimal.valueOf(double)` instead.\n" +
                      "For example writing `new BigDecimal(0.1)` does not create a `BigDecimal` which is exactly equal to `0.1`, " +
                      "but it is equal to `0.1000000000000000055511151231257827021181583404541015625`. " +
                      "This is because `0.1` cannot be represented exactly as a double (or, for that matter, as a binary fraction of any finite length).",
        tags = {"RSPEC-2111"}
)
public class BigDecimalDoubleConstructor {

    @BeforeTemplate
    BigDecimal bigDecimalDoubleConstructor(double d) {
        return new BigDecimal(d);
    }

    @AfterTemplate
    BigDecimal bigDecimalValueOf(double d) {
        return BigDecimal.valueOf(d);
    }
}
