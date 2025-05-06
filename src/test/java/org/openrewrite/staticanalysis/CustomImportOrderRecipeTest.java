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

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.CustomImportOrderStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.style.CustomImportOrderStyle.parseImportOrder;

class CustomImportOrderRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CustomImportOrder());
    }

    private static Consumer<RecipeSpec> customImportOrder(UnaryOperator<CustomImportOrderStyle> with) {
        return spec -> spec.parser(JavaParser.fromJavaVersion().styles(
          singletonList(
            new NamedStyles(
              Tree.randomId(), "test", "test", "test", emptySet(),
              singletonList(with.apply(new CustomImportOrderStyle(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, THIRD_PARTY_PACKAGE"),
                true,
                false,
                "^$", "^(java|javax)\\.", ".*"))))))
        );
    }

    @Test
    void ordersStaticThenStandardThenThirdPartyWithNoGroupBlankLine() {
        rewriteRun(
          customImportOrder(style -> style.withSeparateLineBetweenGroups(false)),
          //language=java
          java(
            """
                    package com.example;

                    import org.apache.commons.lang3.StringUtils;
                    import java.util.Collections;
                    import static java.util.Collections.*;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;

                    import java.util.Collections;
                    import org.apache.commons.lang3.StringUtils;

                    class Test {}
                    """));
    }

    @Test
    void ordersStaticThenStandardThenThirdPartyMultipleStaticImports() {
        rewriteRun(
          customImportOrder(style -> style),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;

                    import java.time.*;
                    import javax.net.*;
                    import static java.io.File.separator;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """));
    }

    @Test
    void includesJavaPackagesInStandardJavaGroup() {
        rewriteRun(
          customImportOrder(style -> style.withStandardPackageRegExp("^java\\.")),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;

                    import javax.net.*;
                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """));
    }

    @Test
    void includesComPackagesInThirdPartyGroup() {
        rewriteRun(
          customImportOrder(style -> style.withThirdPartyPackageRegExp("^com\\.")
            .withImportOrder(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, SPECIAL_IMPORTS, THIRD_PARTY_PACKAGE"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;
                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """));
    }

    @Test
    void includesOrgPackagesInSpecialGroup() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^org\\.")
            .withImportOrder(parseImportOrder("STATIC, SPECIAL_IMPORTS, STANDARD_JAVA_PACKAGE"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import org.apache.commons.lang3.StringUtils;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import org.apache.commons.lang3.StringUtils;
                    import org.apache.commons.io.FileUtils;

                    import java.time.*;
                    import javax.net.*;

                    class Test {}
                    """));
    }

    @Test
    void addsBlankLineBetweenGroupsWhenEnabled() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^org\\.").withThirdPartyPackageRegExp("^com\\.")
            .withImportOrder(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, SPECIAL_IMPORTS, THIRD_PARTY_PACKAGE"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;
                    import org.apache.commons.io.FileUtils;
                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """));
    }

    @Test
    void sortsImportsAlphabeticallyWithinGroupsNonSeparated() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^org\\.").withThirdPartyPackageRegExp("^com\\.")
            .withSortImportsInGroupAlphabetically(true).withSeparateLineBetweenGroups(false)
            .withImportOrder(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, SPECIAL_IMPORTS, THIRD_PARTY_PACKAGE"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;
                    import org.apache.commons.io.FileUtils;
                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.io.File.separator;
                    import static java.util.Collections.*;

                    import java.time.*;
                    import javax.net.*;
                    import org.apache.commons.io.FileUtils;
                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """));
    }

    @Test
    void sortsImportsAlphabeticallyWithinGroupsWithGroupSeparation() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^org\\.").withThirdPartyPackageRegExp("^com\\.")
            .withSortImportsInGroupAlphabetically(true).withSeparateLineBetweenGroups(true)
            .withImportOrder(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, SPECIAL_IMPORTS, THIRD_PARTY_PACKAGE"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import javax.net.*;
                    import java.time.*;

                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;

                    import org.apache.commons.io.FileUtils;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.io.File.separator;
                    import static java.util.Collections.*;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """));
    }

    @Test
    void sortsAndSeparatesAlphabeticallyWithConfiguredGroups() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^org\\.").withSortImportsInGroupAlphabetically(true)
            .withImportOrder(parseImportOrder("STATIC, STANDARD_JAVA_PACKAGE, SPECIAL_IMPORTS"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.util.Collections.*;
                    import static java.io.File.separator;

                    import java.time.*;
                    import javax.net.*;
                    import org.apache.commons.io.FileUtils;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """,
            """
                    package com.example;

                    import static java.io.File.separator;
                    import static java.util.Collections.*;

                    import java.time.*;
                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """));
    }

    @Test
    void sortsImportsInASCIIOrderInsideStandardGroup() {
        rewriteRun(
          customImportOrder(style -> style.withSortImportsInGroupAlphabetically(true)),
          //language=java
          java(
            """
                    package com.example;

                    import java.awt.Dialog;
                    import java.awt.Window;
                    import java.awt.color.ColorSpace;
                    import java.awt.Frame;

                    class Test {}
                    """,
            """
                    package com.example;

                    import java.awt.Dialog;
                    import java.awt.Frame;
                    import java.awt.Window;
                    import java.awt.color.ColorSpace;

                    class Test {}
                    """));
    }


    @Test
    void ordersImportsIDEAStyleSortedGroupsNoBlankLinesExceptStatic() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^javax\\.").withStandardPackageRegExp("^java\\.")
            .withSortImportsInGroupAlphabetically(true).withSeparateLineBetweenGroups(false)
            .withImportOrder(parseImportOrder("THIRD_PARTY_PACKAGE, SPECIAL_IMPORTS, STANDARD_JAVA_PACKAGE, STATIC"))),
          //language=java
          java(
            """
                    package com.example;

                    import static java.io.File.separator;
                    import static java.util.Collections.*;

                    import java.time.*;

                    import javax.net.*;

                    import org.apache.commons.io.FileUtils;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;

                    class Test {}
                    """,
            """
                    package com.example;

                    import com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck;
                    import com.puppycrawl.tools.checkstyle.checks.imports.ImportOrderCheck;
                    import org.apache.commons.io.FileUtils;
                    import javax.net.*;
                    import java.time.*;

                    import static java.io.File.separator;
                    import static java.util.Collections.*;

                    class Test {}
                    """));
    }

    @Test
    void sortsStaticImportsAlphabeticallyWhenOnlyStaticsPresent() {
        rewriteRun(
          customImportOrder(style -> style.withSortImportsInGroupAlphabetically(true)),
          //language=java
          java(
            """
            package com.example;

            import static java.util.Collections.emptyList;
            import static java.lang.Math.max;

            class Test {}
            """,
            """
            package com.example;

            import static java.lang.Math.max;
            import static java.util.Collections.emptyList;

            class Test {}
            """
          )
        );
    }

    @Test
    void sortsThirdPartyImportsAlphabeticallyWhenOnlyThirdPartyPresent() {
        rewriteRun(
          customImportOrder(style -> style.withSortImportsInGroupAlphabetically(true)),
          //language=java
          java(
            """
            package com.example;

            import org.apache.commons.io.FileUtils;
            import com.google.common.collect.Lists;

            class Test {}
            """,
            """
            package com.example;

            import com.google.common.collect.Lists;
            import org.apache.commons.io.FileUtils;

            class Test {}
            """
          )
        );
    }


    @Test
    void ordersImportsThirdPartyThenStaticThenSpecialImportsBasedOnRegex() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^java\\.lang\\.String$")
            .withImportOrder(parseImportOrder("SAME_PACKAGE(3), THIRD_PARTY_PACKAGE, STATIC, SPECIAL_IMPORTS"))),
          //language=java
          java(
            """
                    package com.example;

                    import com.google.common.annotations.Beta;
                    import com.google.common.annotations.VisibleForTesting;
                    import org.apache.commons.io.FileUtils;

                    import static java.util.Collections.emptyList;

                    import com.google.common.annotations.GwtCompatible;

                    import java.lang.String;

                    class Test {}
                    """,
            """
                    package com.example;

                    import com.google.common.annotations.Beta;
                    import com.google.common.annotations.VisibleForTesting;
                    import org.apache.commons.io.FileUtils;
                    import com.google.common.annotations.GwtCompatible;

                    import static java.util.Collections.emptyList;

                    import java.lang.String;

                    class Test {}
                    """));
    }

    @Test
    void ordersImportsSamePackageDepth3FirstThenThirdPartyThenStatic() {
        rewriteRun(
          customImportOrder(style -> style.withImportOrder(parseImportOrder("SAME_PACKAGE(3), THIRD_PARTY_PACKAGE, STATIC"))),
          // language=java
          java(
            """
            package com.mycompany.library.util;

            import com.mycompany.other.Helper;
            import static java.util.Collections.emptyList;
            import org.junit.Assert;
            import com.mycompany.library.util.Bar;
            import com.mycompany.library.Foo;
            import com.mycompany.library.util.baz.Baz;

            class Example {}
            """,
            """
            package com.mycompany.library.util;

            import com.mycompany.library.util.Bar;
            import com.mycompany.library.Foo;
            import com.mycompany.library.util.baz.Baz;

            import com.mycompany.other.Helper;
            import org.junit.Assert;

            import static java.util.Collections.emptyList;

            class Example {}
            """
          )
        );
    }

    @Test
    void handlesNoImports() {
        rewriteRun(
          customImportOrder(style -> style),
          // language=java
          java(
            """
            package com.example;

            class Example {}
            """
          )
        );
    }

    @Test
    void handlesOnlyUnmatchedImportsWhenNoGroupMatches() {
        rewriteRun(
          customImportOrder(style -> style.withSpecialImportsRegExp("^thiswillnotmatch$")
            .withStandardPackageRegExp("^willnotmatch\\.")
            .withThirdPartyPackageRegExp("^alsowillnotmatch\\.")
            .withImportOrder(parseImportOrder("STATIC, SPECIAL_IMPORTS, STANDARD_JAVA_PACKAGE, THIRD_PARTY_PACKAGE"))
          ),
          // language=java
          java(
            """
            package com.example;

            import foo.A;
            import bar.B;

            class Example {}
            """
          )
        );
    }

    @Test
    void doNothingIfAlreadyStandard() {
        rewriteRun(
          customImportOrder(style -> style.withSeparateLineBetweenGroups(false)),
          //language=java
          java(
            """
            package com.example;

            import static java.util.Collections.*;

            import java.util.Collections;
            import org.apache.commons.lang3.StringUtils;

            class Test {}
            """));
    }
}
