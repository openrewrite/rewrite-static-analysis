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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class IsEmptyCallOnMockReceiversTest implements RewriteTest {
    @Test
    void legacyGroovyModelWithMissingFactoryMethodTypes() {
        String before = """
          package spock.lang
          class Specification { def Mock() { null } }
          class Example extends Specification {
              def check() {
                  given:
                  List list = Mock()
                  Map map = Mock()
                  List alias = list
                  assert list.size() == 0
                  assert alias.size() == 0
                  assert map.size() == 0
                  List ordinary = new ArrayList()
                  assert ordinary.size() == 0
              }
              boolean ordinaryScope(List list) { return list.size() == 0 }
          }
          """;
        assertLegacyModel(before, before.replace("ordinary.size() == 0", "ordinary.isEmpty()")
          .replace("return list.size() == 0", "return list.isEmpty()"));
        String detached = """
          package org.spockframework.mock
          class MockUtil {
              Object createDetachedMock(Object... args) { null }
          }
          class Example {
              List listMock
              MockUtil mockUtil = new MockUtil()
              def setupSpec() { listMock = mockUtil.createDetachedMock("listMock", List) }
              def check() { assert listMock.size() == 0 }
          }
          """;
        assertLegacyModel(detached, detached);
        String shadowed = """
          package spock.lang
          class Specification {}
          class Example extends Specification {
              List Mock() { new ArrayList() }
              def check() { List list = Mock(); assert list.size() == 0 }
          }
          """;
        assertLegacyModel(shadowed, shadowed.replace("list.size() == 0", "list.isEmpty()"));
    }

    private void assertLegacyModel(String before, String expected) {
        var ctx = new org.openrewrite.InMemoryExecutionContext(t -> { throw new AssertionError(t); });
        var parsed = org.openrewrite.groovy.GroovyParser.builder().build().parse(ctx, before).findFirst().orElseThrow();
        // The actual original Spock models have typed size() calls, but null factory
        // method types and a shallow, unqualified MockUtil receiver type.
        var legacy = new org.openrewrite.java.JavaIsoVisitor<Integer>() {
            @Override
            public org.openrewrite.java.tree.J.MethodInvocation visitMethodInvocation(org.openrewrite.java.tree.J.MethodInvocation m, Integer p) {
                var method = super.visitMethodInvocation(m, p);
                if (method.getSimpleName().equals("Mock") || method.getSimpleName().equals("createDetachedMock")) {
                    method = method.withMethodType(null);
                    if (method.getSelect() instanceof org.openrewrite.java.tree.J.Identifier) {
                        method = method.withSelect(((org.openrewrite.java.tree.J.Identifier) method.getSelect())
                          .withType(org.openrewrite.java.tree.JavaType.ShallowClass.build("MockUtil")));
                    }
                }
                return method;
            }
        }.visitNonNull(parsed, 0);
        org.openrewrite.SourceFile after = (org.openrewrite.SourceFile) new IsEmptyCallOnCollections().getVisitor().visitNonNull(legacy, ctx);
        after = (org.openrewrite.SourceFile) new IsEmptyCallOnMaps().getVisitor().visitNonNull(after, ctx);
        org.assertj.core.api.Assertions.assertThat(after.printAll()).isEqualTo(expected);
    }

    @Test
    void spockMocksAndDetachedFieldAssignments() {
        rewriteRun(
          spec -> spec.recipes(new IsEmptyCallOnCollections(), new IsEmptyCallOnMaps()),
          groovy(
            """
              package spock.mock
              class MockingApi { def Mock() { null } }
              class Example extends MockingApi {
                  def check() {
                      List list = Mock()
                      Map ctx = Mock()
                      def alias = list
                      assert list.size() == 0
                      assert alias.size() == 0
                      assert ctx.size() == 0
                      List ordinary = new ArrayList()
                      assert ordinary.size() == 0
                  }
              }
              """,
            """
              package spock.mock
              class MockingApi { def Mock() { null } }
              class Example extends MockingApi {
                  def check() {
                      List list = Mock()
                      Map ctx = Mock()
                      def alias = list
                      assert list.size() == 0
                      assert alias.size() == 0
                      assert ctx.size() == 0
                      List ordinary = new ArrayList()
                      assert ordinary.isEmpty()
                  }
              }
              """
          ),
          groovy(
            """
              package org.spockframework.mock
              class MockUtil {
                  Object createDetachedMock(String name, Class type, Object nature, Object implementation, Map options, ClassLoader loader) { null }
              }
              class DetachedExample {
                  List listMock
                  def setup() {
                      listMock = new MockUtil().createDetachedMock("listMock", List, null, null, [:], getClass().classLoader)
                  }
                  def check() { assert listMock.size() == 0 }
              }
              """
          )
        );
    }

    @Test
    void typedMockitoFactoriesAnnotationsAndAliases() {
        rewriteRun(
          spec -> spec.recipes(new IsEmptyCallOnCollections(), new IsEmptyCallOnMaps())
            .parser(JavaParser.fromJavaVersion().dependsOn(
              "package org.mockito; public class Mockito { public static <T> T mock(Class<T> type) { return null; } }",
              "package org.mockito; public @interface Mock {}"
            )),
          java(
            """
              import java.util.*;
              import static org.mockito.Mockito.mock;
              class Example {
                  static class MapDelegate extends HashMap<String, String> {}
                  @org.mockito.Mock Map<String, String> annotated;
                  boolean check() {
                      List list = mock(List.class);
                      List alias = list;
                      MapDelegate map;
                      map = mock(MapDelegate.class);
                      return list.size() == 0 && alias.size() < 1 && map.size() != 0 && annotated.size() == 0;
                  }
                  boolean direct() { return mock(List.class).size() == 0; }
              }
              """
          )
        );
    }

    @Test
    void unrelatedMockMethodAndSeparateScopesStillTransform() {
        rewriteRun(
          spec -> spec.recipes(new IsEmptyCallOnCollections(), new IsEmptyCallOnMaps()),
          java(
            """
              import java.util.*;
              class Example {
                  List Mock() { return new ArrayList(); }
                  boolean check() { List list = Mock(); return list.size() == 0; }
                  boolean other(List list) { return list.size() == 0; }
              }
              """,
            """
              import java.util.*;
              class Example {
                  List Mock() { return new ArrayList(); }
                  boolean check() { List list = Mock(); return list.isEmpty(); }
                  boolean other(List list) { return list.isEmpty(); }
              }
              """
          )
        );
    }
}
