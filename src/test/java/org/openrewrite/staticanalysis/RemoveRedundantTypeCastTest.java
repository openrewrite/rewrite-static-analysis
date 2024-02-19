/*
 * Copyright 2022 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ALL")
class RemoveRedundantTypeCastTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantTypeCast());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1784")
    @Test
    void objectToObjectArray() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method(Object array) {
                      Object[] o = (Object[]) array;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1783")
    @Test
    void parametersDoNotMatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;
                            
              class Test {
                  Class<? extends Collection<String>> test = (Class<? extends Collection<String>>) get();
              
                  Class<?> get() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveCast() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.DataOutputStream;
                            
              class Test {
                  void m(DataOutputStream out) {
                      out.writeByte((byte) 0xff);
                      out.writeDouble((double) 42);
                  }
              }
              """
          )
        );
    }


    @Test
    void genericTypeVariableCast() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;
              
              class GenericNumberIterable<T extends Number> implements Iterable<T> {
              
                  private final Iterable<Number> wrappedIterable;
              
                  GenericNumberIterable(Iterable<Number> wrap) {
                      this.wrappedIterable = wrap;
                  }
              
                  @Override
                  public Iterator<T> iterator() {
                      final Iterator<Number> iter = wrappedIterable.iterator();
              
                      return new Iterator<T>() {
                          @Override
                          public boolean hasNext() {
                              return iter.hasNext();
                          }
              
                          @Override
                          @SuppressWarnings("unchecked")
                          public T next() {
                              return (T) iter.next();
                          }
              
                          @Override
                          public void remove() {
                              throw new UnsupportedOperationException();
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1739")
    @Test
    void changeTypeCastInReturn() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;
              
              class Test {
                  public <T extends Collection<String>> T test() {
                      return (T) get();
                  }
                  public List<String> get() {
                      return List.of("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void nonSamParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;
              
              class Test {
                  public boolean foo() {
                      return Objects.equals("x", (Comparable<String>) (s) -> 1);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    void redundantTypeCast() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String s = (String) "";
                  String s2 = (String) method();

                  String method() {
                      return null;
                  }
              }
              """,
            """
              class Test {
                  String s = "";
                  String s2 = method();

                  String method() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void wildcardGenericsInTargetType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              
              class Test {
                  Object o = null;
                  List<?> l = (List<?>) o;
                  List<?> l2 = (List) o;
              }
              """
          )
        );
    }

    @Test
    void keepCastWithMethodOverloads() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void visit(Integer i) {
                      visit((Number) i);
                  }
                  void visit(Number n) {
                  }
                  void visitAll(Integer... i) {
                      visitAll((Number[]) i);
                  }
                  void visitAll(Number... n) {
                  }
              }
              """
          )
        );
    }

    @Test
    void varargsCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void m(String... s) {
                  }
                  void foo() {
                      m("1", (String) "2");
                  }
              }
              """,
            """
              class Test {
                  void m(String... s) {
                  }
                  void foo() {
                      m("1", "2");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    void downCast() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Object o = (String) "";
                  Object o2 = (String) method();

                  String method() {
                      return null;
                  }
              }
              """,
            """
              class Test {
                  Object o = "";
                  Object o2 = method();

                  String method() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void downCastParameterizedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
                            
              class Test {
                  Object o = (List<String>) method();
                  Object o2 = (List<? extends String>) method();
                  Object o3 = (List<? super String>) method();

                  List<String> method() {
                      return null;
                  }
              }
              """,
            """
              import java.util.List;
                            
              class Test {
                  Object o = method();
                  Object o2 = method();
                  Object o3 = method();

                  List<String> method() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    void downCastExtendedObject() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
              }
              """
          ),
          //language=java
          java(
            """
              class ExtendTest extends Test {
                  Test extendTest = (ExtendTest) new ExtendTest();
              }
              """,
            """
              class ExtendTest extends Test {
                  Test extendTest = new ExtendTest();
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    void downCastExtendedObjectArray() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
              }
              """
          ),
          //language=java
          java(
            """
              class ExtendTest extends Test {
                  Test[][] extendTestArray = (ExtendTest[][]) new ExtendTest[0][0];
              }
              """,
            """
              class ExtendTest extends Test {
                  Test[][] extendTestArray = new ExtendTest[0][0];
              }
              """
          )
        );
    }


    @Test
    void lambdaWithComplexTypeInference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.LinkedHashMap;
              import java.util.Map;
              import java.util.function.Supplier;
              import java.util.stream.Collectors;
              
              class Test {
                  void method() {
                      Object o2 = new MapDropdownChoice<String, Integer>(
                              (Supplier<Map<String, Integer>>) () -> {
                                  Map<String, Integer> choices = Map.of("id1", 2);
                                  return choices.entrySet().stream()
                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                                  (e1, e2) -> e1, LinkedHashMap::new));
                              });
                  }
              }
              
              class MapDropdownChoice<K, V> {
                  public MapDropdownChoice(Supplier<? extends Map<K, ? extends V>> choiceMap) {
                  }
              }
              """
          )
        );
    }

    @Test
    void returnPrimitiveIntToWrapperLong() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Long method() {
                      return (long) 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void castWildcard() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
              
              class Test {
                  void method() {
                      List<? extends Number> list = new ArrayList<>();
                      List<Number> n = (List<Number>) list;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeImport() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
              
              class Test {
                  List method(List list) {
                      return (ArrayList) list;
                  }
              }
              """,
            """
              import java.util.List;
              
              class Test {
                  List method(List list) {
                      return list;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCastInMarshaller() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.glassfish.jaxb.core.marshaller;
                import java.io.IOException;
                import java.io.Writer;
                              
                public interface CharacterEscapeHandler {
                    void escape( char[] ch, int start, int length, boolean isAttVal, Writer out ) throws IOException;\s
                }
                """,
              """
                package javax.xml.bind;

                public interface Marshaller {
                    void setProperty(String var1, Object var2);
                }
                """
            )
          ),
          //language=java
          java(
            """
              import javax.xml.bind.Marshaller;
              import org.glassfish.jaxb.core.marshaller.CharacterEscapeHandler;
                
              class Foo {
                void bar(Marshaller marshaller) {
                  marshaller.setProperty("org.glassfish.jaxb.characterEscapeHandler", (CharacterEscapeHandler) (ch, start, length, isAttVal, out) -> {
                  });
                }
              }
              """
          )
        );
    }
}
