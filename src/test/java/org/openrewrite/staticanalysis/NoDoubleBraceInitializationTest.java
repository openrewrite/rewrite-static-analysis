/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"Convert2Diamond", "ResultOfMethodCallIgnored", "StringOperationCanBeSimplified", "rawtypes"})
class NoDoubleBraceInitializationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoDoubleBraceInitialization());
    }

    @DocumentExample
    @SuppressWarnings("WrapperTypeMayBePrimitive")
    @Test
    void addStatementInForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;
              import java.util.LinkedHashSet;
              class A {
                  void a() {
                      Integer CNT = 10;
                      final Set<Integer> keys = new LinkedHashSet<>(){{
                          for (int i = 0; i < CNT; i++) {
                              add(i);
                          }
                      }};
                  }
              }
              """,
            """
              import java.util.Set;
              import java.util.LinkedHashSet;
              class A {
                  void a() {
                      Integer CNT = 10;
                      final Set<Integer> keys = new LinkedHashSet<>();
                      for (int i = 0; i < CNT; i++) {
                          keys.add(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropsConstructorCollectionParameterInMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;

              class A {
                  void foo() {
                      Map<String, String> a = Map.of("foo", "bar");
                      Map<String, String> b = new HashMap<>(a) {{
                          put("irrelevantKey", "irrelevantValue");
                      }};
                  }
              }
              """,
            """
              import java.util.*;

              class A {
                  void foo() {
                      Map<String, String> a = Map.of("foo", "bar");
                      Map<String, String> b = new HashMap<>(a);
                      b.put("irrelevantKey", "irrelevantValue");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/356")
    @Test
    void dropsConstructorCollectionParameterInClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;

              class A {
                  Map<String, String> a = Map.of("foo", "bar");
                  Map<String, String> b = new HashMap<>(a) {{
                      put("irrelevantKey", "irrelevantValue");
                  }};
              }
              """,
            """
              import java.util.*;

              class A {
                  Map<String, String> a = Map.of("foo", "bar");
                  Map<String, String> b;
                  {
                      b = new HashMap<>(a);
                      b.put("irrelevantKey", "irrelevantValue");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2674")
    @Test
    void possibleMistakenlyMissedAddingToCollection() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              class A {
                  void example() {
                      OTList otList = new OTList() {{ new OTElement();}};
                  }
              }
              """,
            """
              import java.util.List;
              class A {
                  void example() {
                      OTList otList = new OTList() {{ /*~~(Did you mean to invoke add() method to the collection?)~~>*/new OTElement();}};
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class OTElement {
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.ArrayList;
              class OTList extends ArrayList {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2674")
    @Test
    void possibleMistakenlyMissedAddingToCollectionWithDifferentMethodName() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.*;

              class A {
                  private final Map<String, String> map = new HashMap<String, String>() {{
                      new AbstractMap.SimpleEntry<>("key", "value");
                  }};
                  private final List<String> list = new ArrayList<String>() {{
                      new String("foo");
                      new String("bar");
                  }};
                  private final Set<String> set = new HashSet<String>() {{
                      new String("foo");
                      new String("bar");
                  }};
              }
              """,
            """
              import java.util.List;
              import java.util.*;

              class A {
                  private final Map<String, String> map = new HashMap<String, String>() {{
                      /*~~(Did you mean to invoke put() method to the collection?)~~>*/new AbstractMap.SimpleEntry<>("key", "value");
                  }};
                  private final List<String> list = new ArrayList<String>() {{
                      /*~~(Did you mean to invoke add() method to the collection?)~~>*/new String("foo");
                      /*~~(Did you mean to invoke add() method to the collection?)~~>*/new String("bar");
                  }};
                  private final Set<String> set = new HashSet<String>() {{
                      /*~~(Did you mean to invoke add() method to the collection?)~~>*/new String("foo");
                      /*~~(Did you mean to invoke add() method to the collection?)~~>*/new String("bar");
                  }};
              }
              """
          )
        );
    }

    @Test
    void doubleBraceInitializationForFieldVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.HashSet;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  private static final Map<String, String> map = new HashMap<>() {{put("a", "a");}};
                  private final List<String> lst = new ArrayList<>() {{add("x");add("y");}};
                  private final Set<String> mySet = new HashSet<>(){{add("q");}};
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.HashSet;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  private static final Map<String, String> map;
                  static {
                      map = new HashMap<>();
                      map.put("a", "a");
                  }
                  private final List<String> lst;
                  {
                      lst = new ArrayList<>();
                      lst.add("x");
                      lst.add("y");
                  }
                  private final Set<String> mySet;
                  {
                      mySet = new HashSet<>();
                      mySet.add("q");
                  }
              }
              """
          )
        );
    }

    @Test
    void memberVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              class A {
                  void example() {
                      Map<String, String> aMap = new HashMap<>();
                      aMap.put("c", "c");
                      String s = "x";
                      Map<String, String> bMap = new HashMap<>(){{
                          s.concat("z");
                          put("a", "A");
                          put("b", "B");
                      }};
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              class A {
                  void example() {
                      Map<String, String> aMap = new HashMap<>();
                      aMap.put("c", "c");
                      String s = "x";
                      Map<String, String> bMap = new HashMap<>();
                      s.concat("z");
                      bMap.put("a", "A");
                      bMap.put("b", "B");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/352")
    @Test
    void nestedDoubleBraceInitializationKeepsItsOwnReceiver() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.LinkedHashMap;

              class A {
                  void example() {
                      LinkedHashMap<String, Object> expectedMap = new LinkedHashMap<>() {{
                          put("a", 1);
                          put("testArrayList", new ArrayList<>() {{
                              add(1);
                          }});
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.LinkedHashMap;

              class A {
                  void example() {
                      LinkedHashMap<String, Object> expectedMap = new LinkedHashMap<>();
                      expectedMap.put("a", 1);
                      expectedMap.put("testArrayList", new ArrayList<>() {
                          {
                              add(1);
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/352")
    @Test
    void enclosingMethodCallInsideAnonymousClassKeepsNoSelect() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class A {
                  String helper() {
                      return "H";
                  }

                  void example() {
                      Map<String, String> map = new HashMap<String, String>() {{
                          put("a", new Object() {
                              @Override
                              public String toString() {
                                  return helper();
                              }
                          }.toString());
                      }};
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class A {
                  String helper() {
                      return "H";
                  }

                  void example() {
                      Map<String, String> map = new HashMap<String, String>();
                      map.put("a", new Object() {
                          @Override
                          public String toString() {
                              return helper();
                          }
                      }.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void implicitReceiver() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class A {
                  void example() {
                      var m = new HashMap<String, String>() {
                          {
                              put("a", "A");
                              put("b", "B");
                          }
                      };
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class A {
                  void example() {
                      var m = new HashMap<String, String>();
                      m.put("a", "A");
                      m.put("b", "B");
                  }
              }
              """
          )
        );
    }

    @Test
    void selectIsThis() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              class A {
                  void example() {
                      Map<String, String> bMap = new HashMap<>(){{
                          this.put("a", "A");
                          this.put("b", "B");
                      }};
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              class A {
                  void example() {
                      Map<String, String> bMap = new HashMap<>();
                      bMap.put("a", "A");
                      bMap.put("b", "B");
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosingClassMethodStaysUnqualified() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  private StringBuilder newBuilder() {
                      return new StringBuilder();
                  }

                  void m() {
                      List<String> input = new ArrayList<String>() {{
                          add(newBuilder().toString());
                          add(newBuilder().append("x").toString());
                      }};
                      System.out.println(input);
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  private StringBuilder newBuilder() {
                      return new StringBuilder();
                  }

                  void m() {
                      List<String> input = new ArrayList<String>();
                      input.add(newBuilder().toString());
                      input.add(newBuilder().append("x").toString());
                      System.out.println(input);
                  }
              }
              """
          )
        );
    }

    @Test
    void enclosingClassMethodStaysUnqualifiedForField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashSet;
              import java.util.Set;

              class Test {
                  private static String name(int i) {
                      return "n" + i;
                  }

                  private String other() {
                      return "o";
                  }

                  private static final Set<String> NAMES = new HashSet<String>() {{
                      add(name(1));
                  }};
                  private final Set<String> others = new HashSet<String>() {{
                      add(other());
                      this.add(name(2));
                  }};
              }
              """,
            """
              import java.util.HashSet;
              import java.util.Set;

              class Test {
                  private static String name(int i) {
                      return "n" + i;
                  }

                  private String other() {
                      return "o";
                  }

                  private static final Set<String> NAMES;
                  static {
                      NAMES = new HashSet<String>();
                      NAMES.add(name(1));
                  }
                  private final Set<String> others;
                  {
                      others = new HashSet<String>();
                      others.add(other());
                      others.add(name(2));
                  }
              }
              """
          )
        );
    }

    @Test
    void collectionMethodWinsOverEnclosingMethodOfTheSameName() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void add(String s) {
                  }

                  void m() {
                      List<String> l = new ArrayList<String>() {{
                          add("a");
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void add(String s) {
                  }

                  void m() {
                      List<String> l = new ArrayList<String>();
                      l.add("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void qualifyCallInLambdaOnlyWhenVariableIsEffectivelyFinal() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = new ArrayList<String>() {{
                          Arrays.asList("a", "b").forEach(s -> add(s));
                      }};
                      System.out.println(l);
                  }

                  void n() {
                      List<String> l = new ArrayList<String>() {{
                          Arrays.asList("a", "b").forEach(s -> add(s));
                      }};
                      l = new ArrayList<>();
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = new ArrayList<String>();
                      Arrays.asList("a", "b").forEach(s -> l.add(s));
                      System.out.println(l);
                  }

                  void n() {
                      List<String> l = new ArrayList<String>() {{
                          Arrays.asList("a", "b").forEach(s -> add(s));
                      }};
                      l = new ArrayList<>();
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldInitializerReadingAnEarlierField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  static final String EARLIER = String.valueOf(1);
                  static final List<String> L = new ArrayList<String>() {{
                      add(EARLIER);
                      Arrays.asList("a").forEach(s -> add(s));
                  }};
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  static final String EARLIER = String.valueOf(1);
                  static final List<String> L;
                  static {
                      L = new ArrayList<String>();
                      L.add(EARLIER);
                      Arrays.asList("a").forEach(s -> L.add(s));
                  }
              }
              """
          )
        );
    }

    @Test
    void accessOfTheCalledOverloadDecides() {
        rewriteRun(
          //language=java
          java(
            """
              package q;
              import java.util.ArrayList;
              public class MyList extends ArrayList<String> {
                  protected void put(String a, String b) {
                  }
                  public void put(String a) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package p;
              import java.util.List;
              import q.MyList;
              class Test {
                  void m() {
                      MyList l = new MyList() {{
                          add("x");
                          put("b", "c");
                      }};
                      MyList k = new MyList() {{
                          add("x");
                          put("b");
                      }};
                  }
              }
              """,
            """
              package p;
              import java.util.List;
              import q.MyList;
              class Test {
                  void m() {
                      MyList l = new MyList() {
                          {
                              add("x");
                              put("b", "c");
                          }
                      };
                      MyList k = new MyList();
                      k.add("x");
                      k.put("b");
                  }
              }
              """
          )
        );
    }

    @Test
    void protectedMethodOfTheSamePackage() {
        rewriteRun(
          //language=java
          java(
            """
              package p;
              import java.util.ArrayList;
              public class MyList extends ArrayList<String> {
                  protected void addTwice(String s) {
                      add(s);
                      add(s);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package p;
              class Test {
                  void m() {
                      MyList l = new MyList() {{
                          add("a");
                          addTwice("b");
                      }};
                  }
              }
              """,
            """
              package p;
              class Test {
                  void m() {
                      MyList l = new MyList();
                      l.add("a");
                      l.addTwice("b");
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceFieldReadingLaterStaticField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  List<String> l = new ArrayList<String>() {{
                      add(LATER);
                  }};
                  static final String LATER = String.valueOf(1);
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  List<String> l;
                  {
                      l = new ArrayList<String>();
                      l.add(LATER);
                  }
                  static final String LATER = String.valueOf(1);
              }
              """
          )
        );
    }

    @Test
    void staticFieldOfClassWithLombokAnnotation() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              import lombok.RequiredArgsConstructor;

              @RequiredArgsConstructor
              class A {
                  static final Map<String, String> M = new HashMap<String, String>() {{
                      put("a", "b");
                  }};
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;
              import lombok.RequiredArgsConstructor;

              @RequiredArgsConstructor
              class A {
                  static final Map<String, String> M;
                  static {
                      M = new HashMap<String, String>();
                      M.put("a", "b");
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedWildcardInDeclaredType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  static final Map<String, Class<?>> TYPES = new HashMap<String, Class<?>>() {{
                      put("s", String.class);
                  }};
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class Test {
                  static final Map<String, Class<?>> TYPES;
                  static {
                      TYPES = new HashMap<String, Class<?>>();
                      TYPES.put("s", String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldInitializerReadingInheritedField() {
        rewriteRun(
          //language=java
          java(
            """
              class Base {
                  protected String name = "x";
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test extends Base {
                  List<String> l = new ArrayList<String>() {{
                      add(name);
                  }};
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test extends Base {
                  List<String> l;
                  {
                      l = new ArrayList<String>();
                      l.add(name);
                  }
              }
              """
          )
        );
    }

    @Test
    void parenthesizedInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = (new ArrayList<String>() {{
                          add("a");
                      }});
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = (new ArrayList<String>());
                      l.add("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void callMethodTheDeclaredTypeInheritsFromAnInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = new ArrayList<String>() {{
                          add("a");
                          removeIf(s -> s.isEmpty());
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  void m() {
                      List<String> l = new ArrayList<String>();
                      l.add("a");
                      l.removeIf(s -> s.isEmpty());
                  }
              }
              """
          )
        );
    }

    @Test
    void callMethodTheDeclaredTypeInheritsFromASuperclass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.AbstractList;
              import java.util.ArrayList;
              import java.util.Arrays;

              class Test {
                  void m() {
                      AbstractList<String> l = new ArrayList<String>() {{
                          add("a");
                          removeAll(Arrays.asList("b"));
                      }};
                  }
              }
              """,
            """
              import java.util.AbstractList;
              import java.util.ArrayList;
              import java.util.Arrays;

              class Test {
                  void m() {
                      AbstractList<String> l = new ArrayList<String>();
                      l.add("a");
                      l.removeAll(Arrays.asList("b"));
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldInitializerWhoseNestedClassReadsALaterField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  final List<Runnable> l = new ArrayList<Runnable>() {{
                      add(new Runnable() {
                          public void run() {
                              System.out.println(later);
                          }
                      });
                  }};
                  String later = "x";
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test {
                  final List<Runnable> l;
                  {
                      l = new ArrayList<Runnable>();
                      l.add(new Runnable() {
                          public void run() {
                              System.out.println(later);
                          }
                      });
                  }
                  String later = "x";
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Issue("https://github.com/openrewrite/rewrite/issues/2674")
        @Test
        void noCollectionInitializedInDoubleBraceIgnored() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashMap;
                  import java.util.Map;
                  class A<T> {
                      void example() {
                          Map<String, String> map = new HashMap<String, String>() {
                              {
                                func1();
                              }
                           };
                      }
                      void func1() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doubleBranchInitializationForArgIgnored() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashMap;
                  import java.util.Map;
                  class A {
                      void m1(Map<String, String> map) {
                      }
                      void m2() {
                          m1(new HashMap<String, String>(){{put("a", "a");}});
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doubleBranchInitializationForNewClassArgIgnored() {
            rewriteRun(
              //language=java
              java(
                """
                  package abc;
                  import java.util.List;

                  public class Thing {
                      private final List<String> stuff;
                      public Thing(List<String> stuff) {
                          this.stuff = stuff;
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package abc;
                  import java.util.ArrayList;
                  import java.util.List;

                  class A {
                      Thing t = new Thing(new ArrayList<String>(){{add("abc"); add("def");}});
                  }
                  """
              )
            );
        }

        @Test
        void doubleBraceInitWithinConstructorArgIgnored() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.List;
                  import java.util.Collections;
                  import java.util.ArrayList;
                  class A {
                      private final List<String> expectedCommand =
                        Collections.unmodifiableList(
                            new ArrayList<String>() {
                              {
                                add("a");
                                add("b");
                              }
                            });
                  }
                  """
              )
            );
        }

        @Test
        void anonymousSubClassMethodInvoked() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashMap;
                  import java.util.Map;

                  class A {
                      void example() {
                          Map<String, String> bMap = new HashMap<String, String>() {
                              {
                                  subClassMethod();
                                  put("a", "A");
                                  put("b", "B");
                              }
                              void subClassMethod() {
                              }
                          };
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/407")
        @Test
        void skipDoubleBraceInInterface() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.LinkedHashSet;
                  interface A {
                      Set<Integer> keys = new LinkedHashSet<>(){{
                          add(1);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void keepInitializerWhoseLocalALaterDeclarationRepeats() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              String s = "a";
                              add(s);
                          }};
                          String s = "b";
                          System.out.println(s);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void keepInitializerWhoseLocalShadowsAVariableInScope() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m(String s) {
                          List<String> l = new ArrayList<String>() {{
                              String s = "a";
                              add(s);
                          }};
                          System.out.println(l);
                      }

                      void n() {
                          List<String> l = new ArrayList<String>() {{
                              String l = "a";
                              add(l);
                          }};
                          System.out.println(l);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerCallingProtectedMethodOfTheCollection() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add("a");
                              add("b");
                              removeRange(0, 1);
                          }};
                      }

                      List<String> f = new ArrayList<String>() {{
                          add("a");
                          removeRange(0, 1);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerReadingFieldOfTheCollection() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add("a" + modCount);
                          }};
                      }

                      List<String> f = new ArrayList<String>() {{
                          add("a" + modCount);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerNamingThisOrSuper() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.Collections;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add("a");
                              Collections.sort(this);
                          }};
                          List<String> k = new ArrayList<String>() {{
                              super.add("a");
                          }};
                      }

                      List<String> f = new ArrayList<String>() {{
                          add("a");
                          Collections.sort(this);
                      }};
                      List<String> g = new ArrayList<String>() {{
                          super.add("a");
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerWhoseNestedClassCallsTheCollection() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add("a");
                              new Runnable() {
                                  @Override
                                  public void run() {
                                      add("b");
                                  }
                              }.run();
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldInitializerWhoseLocalShadowsTheField() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      List<String> l = new ArrayList<String>() {{
                          String l = "a";
                          add(l);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldInitializerNamingMemberTypeOfTheCollection() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashMap;
                  import java.util.Map;

                  class Test {
                      Map<String, String> other = new HashMap<>();
                      Map<String, String> m = new HashMap<String, String>() {{
                          for (Entry<String, String> e : other.entrySet()) {
                              put(e.getKey(), e.getValue());
                          }
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerCallingMethodTheDeclaredTypeLacks() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.LinkedList;
                  import java.util.List;
                  import java.util.Queue;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              ensureCapacity(10);
                              add("a");
                          }};
                      }

                      Queue<String> q = new LinkedList<String>() {{
                          add("a");
                          push("b");
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldInitializerReadingThisOrALaterField() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      static final List<String> L = new ArrayList<String>() {{
                          add(LATER);
                      }};
                      static final String LATER = String.valueOf(1);

                      List<String> self = new ArrayList<String>() {{
                          add("a");
                          System.out.println(self);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeDoubleBraceThatIsOnlyPartOfTheInitializer() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.Collections;
                  import java.util.List;
                  import java.util.Optional;

                  class Test {
                      static boolean FLAG;
                      List<String> f = FLAG ? new ArrayList<String>() {{
                          add("a");
                      }} : Collections.<String>emptyList();

                      void m(boolean flag, List<String> other, Optional<List<String>> existing) {
                          List<String> l = flag ? new ArrayList<String>() {{
                              add("a");
                          }} : other;
                          List<String> k = existing.orElseGet(() -> new ArrayList<String>() {{
                              add("a");
                          }});
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeDeclarationOfSeveralVariables() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      List<String> a = new ArrayList<String>() {{
                          add("x");
                      }}, b = new ArrayList<>(a);

                      void m() {
                          List<String> c = new ArrayList<String>() {{
                              add("x");
                          }}, d = new ArrayList<String>() {{
                              add("y");
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerThroughWildcard() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.HashMap;
                  import java.util.List;
                  import java.util.Map;

                  class Test {
                      Map<String, ? extends Object> f = new HashMap<String, Object>() {{
                          put("a", 1);
                      }};

                      void m() {
                          Map<String, ?> m = new HashMap<String, Object>() {{
                              put("a", 1);
                          }};
                          List<? extends Number> l = new ArrayList<Integer>() {{
                              add(1);
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerWhoseOverloadTheDeclaredTypeLacks() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.Collection;

                  class Test {
                      void m() {
                          Collection<Integer> c = new ArrayList<Integer>() {{
                              add(5);
                              add(0);
                              remove(0);
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeEnumInstanceFieldReadingStaticField() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  enum Color {
                      RED, GREEN;
                      private static final String PREFIX = String.valueOf("c");
                      private final List<String> names = new ArrayList<String>() {{
                          add(PREFIX);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerCallingGetClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add(getClass().getName());
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeEnumInstanceFieldReadingEnumConstant() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashSet;
                  import java.util.Set;

                  enum Color {
                      RED, GREEN;
                      private final Set<Color> others = new HashSet<Color>() {{
                          add(RED);
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldOfAnnotationType() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  @interface A {
                      List<String> L = new ArrayList<String>() {{
                          add("a");
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldOfClassWithLombokAnnotation() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;
                  import lombok.RequiredArgsConstructor;

                  @RequiredArgsConstructor
                  class A {
                      private final String name;
                      private final List<String> l = new ArrayList<String>() {{
                          add("a");
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeCallOfOverloadOnlyTheSubclassHas() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.HashMap;
                  public class Config extends HashMap<String, Object> {
                      public Object put(String k, String v) {
                          return super.put(k, "str:" + v);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import java.util.Map;
                  class Test {
                      void m() {
                          Map<String, Object> m = new Config() {{
                              put("a", "b");
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerCallingStaticMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  import static java.util.List.of;

                  class Test {
                      void m() {
                          List<List<String>> l = new ArrayList<List<String>>() {{
                              add(of("a"));
                          }};
                      }

                      List<List<String>> f = new ArrayList<List<String>>() {{
                          add(of("a"));
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldInitializerReadingTheFieldByQualifiedName() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class T {
                      static final List<String> L = new ArrayList<String>() {{
                          add(String.valueOf(T.L));
                      }};
                      final List<String> l = new ArrayList<String>() {{
                          add(String.valueOf(T.this.l));
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenTheSuperclassCannotBeInstantiatedHere() {
            rewriteRun(
              //language=java
              java(
                """
                  package p;
                  import java.util.ArrayList;
                  public abstract class BaseList<T> extends ArrayList<T> {
                  }
                  """
              ),
              //language=java
              java(
                """
                  package p;
                  import java.util.ArrayList;
                  public class PList<T> extends ArrayList<T> {
                      protected PList() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package q;
                  import java.util.List;
                  import p.BaseList;
                  import p.PList;
                  class T {
                      List<String> f = new BaseList<String>() {{
                          add("a");
                      }};

                      void m() {
                          List<String> l = new BaseList<String>() {{
                              add("a");
                          }};
                          List<String> k = new PList<String>() {{
                              add("a");
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldWithLombokAnnotationAfterModifiers() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;
                  import lombok.Getter;

                  class A {
                      private @Getter(lazy = true) final List<String> l = new ArrayList<String>() {{
                          add("a");
                      }};
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenTheVarargsConstructorIsProtected() {
            rewriteRun(
              //language=java
              java(
                """
                  package p;
                  import java.util.ArrayList;
                  public class VList extends ArrayList<String> {
                      protected VList(String... xs) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package q;
                  import java.util.List;
                  import p.VList;
                  class T {
                      void m() {
                          List<String> l = new VList("a", "b") {{
                              add("c");
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeRawAnonymousClassAssignedToGenericVariable() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList() {{
                              add(1);
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeLambdaOfNonFinalFieldInitializer() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      List<Runnable> tasks = new ArrayList<>();
                      List<String> names = new ArrayList<String>() {{
                          tasks.add(() -> add("x"));
                      }};

                      void reset() {
                          names = new ArrayList<>();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeInitializerCallingUnresolvedMethod() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      void m() {
                          List<String> l = new ArrayList<String>() {{
                              add("a");
                              add(foo());
                          }};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFieldWithUnresolvedAnnotation() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  import java.util.ArrayList;
                  import java.util.List;

                  class Test {
                      @Foo
                      final List<String> l = new ArrayList<String>() {{
                          add("a");
                      }};
                  }
                  """
              )
            );
        }
    }
}
