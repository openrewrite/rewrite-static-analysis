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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.openrewrite.ruby.Assertions.ruby;

class RubySourcesTest implements RewriteTest {

    static Stream<Arguments> recipes() {
        return Stream.of(
          arguments(new ControlFlowIndentation(), """
            def process(snippets)
              if snippets.empty?
                warn "none"
              else
                puts snippets
              end
              puts "done"
            end
            """),
          arguments(new FinalizeLocalVariables(), """
            class Array
              def abbrev(pattern = nil)
                Abbrev::abbrev(self, pattern)
              end
            end
            """),
          arguments(new FinalizePrivateFields(), """
            begin
              require 'digest/md5'
            rescue LoadError
            end
            """),
          arguments(new MergeIdenticalBranches(), """
            def pick(a, b)
              if a
                x = 1
              elsif b
                x = 1
              end
              x
            end
            """),
          arguments(new PreferEqualityComparisonOverDifferenceCheck(), """
            def previous(lexed, index)
              lexed[index - 2] if index - 2 >= 0
            end
            """),
          arguments(new PreferIncrementOperator(), """
            def next_iteration(iter)
              iter = iter + 1
              iter
            end
            """),
          arguments(new RemoveExtraSemicolons(), """
            class Channel; include Comparable; end
            """),
          arguments(new RemoveSelfAssignment(), """
            def options(library_options)
              library_options = library_options
              library_options
            end
            """),
          arguments(new RemoveUnusedLocalVariables(null, null, null), """
            class OperationBlockMacro
              def read_snippets(snippets)
                snippets.each do |snippet|
                  puts "snippet"
                end
              end
            end
            """),
          arguments(new RenameLocalVariablesToCamelCase(), """
            def check(entry)
              entry.self_types.each do |module_self|
                puts module_self
              end
            end
            """),
          arguments(new SimplifyBooleanExpression(), """
            def newline?
              !!defined?(@newline_flag)
            end
            """),
          arguments(new SimplifyConstantIfBranchExecution(), """
            module English end if false
            """),
          arguments(new SimplifyElseBranch(), """
            def sign(a, b)
              if a
                1
              else
                if b
                  2
                end
              end
            end
            """),
          arguments(new UnwrapElseAfterReturn(), """
            def cdata(text)
              if text
                return text
              else
                raise "Malformed CDATA"
              end
            end
            """),
          arguments(new WriteOctalValuesAsDecimal(), """
            A_PROTECT = 0644
            """)
        );
    }

    @ParameterizedTest
    @MethodSource("recipes")
    void doNotChangeRuby(Recipe recipe, String source) {
        rewriteRun(
          spec -> spec.recipe(recipe),
          ruby(source)
        );
    }
}
