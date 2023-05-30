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

import static org.openrewrite.java.Assertions.java;

import java.util.function.Consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.Issue;
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

class TernaryOperatorsShouldNotBeNestedTest implements RewriteTest {

    private static final Consumer<SourceSpec<J.CompilationUnit>> JAVA_17 = (spec) -> spec.markers(new JavaVersion(
      Tree.randomId(),
      "createdBy",
      "vmVendor",
      "17",
      "17"
    ));

    private static final Consumer<SourceSpec<J.CompilationUnit>> JAVA_11 = (spec) -> spec.markers(new JavaVersion(
      Tree.randomId(),
      "createdBy",
      "vmVendor",
      "11",
      "11"
    ));

    TernaryOperatorsShouldNotBeNested recipe = new TernaryOperatorsShouldNotBeNested();

    @Override
    public void defaults(RecipeSpec spec) {
        recipe = new TernaryOperatorsShouldNotBeNested();
        spec.recipe(recipe);
    }

    @Nested
    class SwitchExpressionNotSupported {

        @Test
        void doReplaceNestedOrTernaryWithIfFollowedByTernary() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        if ("a".equals(a)) {
                            return "a";
                        }
                        return "b".equals(b) ? "b" : "nope";
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryRecursive() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String a, String b, String c) {
                          return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "c".equals(b) ? "c" :"nope";
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String a, String b, String c) {
                          if ("a".equals(a)) {
                              return "a";
                          }
                          if ("b".equals(b)) {
                              return "b";
                          }
                          return "c".equals(b) ? "c" : "nope";
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedAndTernaryWithIfThenTernary() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String a, String b) {
                          return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "nope";
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String a, String b) {
                          if ("a".equals(a)) {
                              return "b".equals(b) ? "b" : "a";
                          }
                          return "nope";
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedAndOrTernaryWithIfThenTernaryElseTernary() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String a, String b, String c) {
                          return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "c".equals(c) ? "c" : "nope";
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String a, String b, String c) {
                          if ("a".equals(a)) {
                              return "b".equals(b) ? "b" : "a";
                          }
                          return "c".equals(c) ? "c" : "nope";
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceMultiLevelTernaries() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String letter) {
                          return "a".equals(letter) ? "a" :
                          "b".equals(letter) ? "b" :
                          "c".equals(letter) ? "c" :
                          letter.contains("d") ? letter.startsWith("d") ? letter.equals("d") ? "equals" : "startsWith" : "contains" :
                          "e".equals(letter) ? "e" :
                          "f".equals(letter) ? "f" :
                          "g".equals(letter) ? "g" : "nope";
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String letter) {
                          if ("a".equals(letter)) {
                              return "a";
                          }
                          if ("b".equals(letter)) {
                              return "b";
                          }
                          if ("c".equals(letter)) {
                              return "c";
                          }
                          if (letter.contains("d")) {
                              if (letter.startsWith("d")) {
                                  return letter.equals("d") ? "equals" : "startsWith";
                              }
                              return "contains";
                          }
                          if ("e".equals(letter)) {
                              return "e";
                          }
                          if ("f".equals(letter)) {
                              return "f";
                          }
                          return "g".equals(letter) ? "g" : "nope";
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @ExpectedToFail(
          "Comment `dont forget about c` is dropped as it is part of a `before` in leftPad, not sure how to extract that")
        @Issue("todo")
        @Test
        void doReplaceMultiLevelTernariesWithComments() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String letter) {
                          return "a".equals(letter) ? "a" : //look its a
                          "b".equals(letter) ? "b" : //b is also here
                          "c".equals(letter) ? "c" /* dont forget about c */ :
                          // d is important too
                          letter.contains("d") ? letter.startsWith("d") ? letter.equals("d") ? "equals" : "startsWith" : "contains" :
                          "e".equals(letter) ? "e" : //e
                          "f".equals(letter) ? "f" : //f
                          "g".equals(letter) ? "g" : "nope"; //and nope if nope
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String letter) {
                          if ("a".equals(letter)) {
                              return "a";
                          }//look its a
                          if ("b".equals(letter)) {
                              return "b";
                          }//b is also here
                          if ("c".equals(letter)) {
                              return "c"; /* dont forget about c */
                          }// d is important too
                          if (letter.contains("d")) {
                              if (letter.startsWith("d")) {
                                  return letter.equals("d") ? "equals" : "startsWith";
                              }
                              return "contains";
                          }
                          if ("e".equals(letter)) {
                              return "e";
                          }//e
                          if ("f".equals(letter)) {
                              return "f";
                          }//f
                          return "g".equals(letter) ? "g" : "nope"; //and nope if nope
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/112")
        @ExpectedToFail("only directly returned ternaries are taken into account")
        @Test
        void doReplaceNestedOrAssignmentTernaryWithIfElse() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public void doThing(String a, String b) {
                          String result = "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";
                          System.out.println(result);
                      }
                  }
                  """,
                """
                  class Test {
                      public void doThing(String a, String b) {
                          String result;
                          if ("a".equals(a)) {
                            result = "a";
                          }
                          else {
                            result = "b".equals(b) ? "b" : "nope";
                          }
                          System.out.println(result);
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doNotReplaceNonNestedOrTernaryInStream() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                      public Set<String> makeASet() {
                         List<String> s = Arrays.asList("a","b","c","nope");
                         return s.stream().map(item -> item.startsWith("a") ? "a" : "nope").collect(Collectors.toSet());
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryInStreamWithIfInBlock() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map(item -> item.startsWith("a") ? "a" : item.startsWith("b") ? "b" : "nope").collect(Collectors.toSet());
                    }
                  }
                  """,
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map(item -> {
                            if (item.startsWith("a")) {
                                return "a";
                            }
                            return item.startsWith("b") ? "b" : "nope";
                        }).collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryInStreamContainingComments() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map( /* look a lambda */ item ->
                             //look a ternary
                             item.startsWith("a") ? "a" : item.startsWith("b") ? "b" : "nope"
                             ).collect(Collectors.toSet());
                    }
                  }
                  """,
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map( /* look a lambda */ item ->
                                //look a ternary
                                {
                                    if (item.startsWith("a")) {
                                        return "a";
                                    }
                                    return item.startsWith("b") ? "b" : "nope";
                                }
                             ).collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }


        @Test
        void doReplaceNestedOrTernaryContainingNull() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      public String determineSomething(String a, String b) {
                          return "a".equals(a) ? null : "b".equals(b) ? "b" : null;
                      }
                  }
                  """,
                """
                  class Test {
                      public String determineSomething(String a, String b) {
                          if ("a".equals(a)) {
                              return null;
                          }
                          return "b".equals(b) ? "b" : null;
                      }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingExpression() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return "a".equals(a) ? "foo" + "bar" : "b".equals(b) ? a + b : b + a;
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        if ("a".equals(a)) {
                            return "foo" + "bar";
                        }
                        return "b".equals(b) ? a + b : b + a;
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingComments() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      //this should be before the if and followed by a new line
                     
                      return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope"; //this should be behind the ternary
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        //this should be before the if and followed by a new line
                        
                        if ("a".equals(a)) {
                            return "a";
                        }
                        return "b".equals(b) ? "b" : "nope"; //this should be behind the ternary
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingMethodCall() {
            //language=java
            rewriteRun(
              java("""
                class M{
                  static String a(){return "a";}
                  static String b(){return "b";}
                  static String c(){return "c";}
                  static String nope(){return "nope";}
                }
                """),
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return "a".equals(a) ? M.a() : "b".equals(b) ? M.b() : M.nope();
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        if ("a".equals(a)) {
                            return M.a();
                        }
                        return "b".equals(b) ? M.b() : M.nope();
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingNewlines() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return "a".equals(a)
                      ? null
                      : "b".equals(b)
                      ? "b"
                      : null;
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        if ("a".equals(a)) {
                            return null;
                        }
                        return "b".equals(b)
                                ? "b"
                                : null;
                    }
                  }
                  """,
                JAVA_11
              )
            );
        }
    }

    @Nested
    class SwitchExpressionSupported {

        @Test
        void doReplaceNestedOrTernaryWithSwitchExpression() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return "a".equals(a) ? "a" : "b".equals(a) ? "b" : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return switch (a) {
                          case "a" -> "a";
                          case "b" -> "b";
                          default -> "nope";
                      };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryWithSwitchExpressionInvertedEquals() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return a.equals("a") ? "a" : a.equals("b") ? b : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return switch (a) {
                          case "a" -> "a";
                          case "b" -> b;
                          default -> "nope";
                      };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryWithSwitchExpressionNullSafeEquals() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Objects;
                  class Test {
                    public String determineSomething(String a, String b) {
                      return Objects.equals(a, "a") ? "a" : Objects.equals(a, "b") ? b : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return switch (a) {
                          case "a" -> "a";
                          case "b" -> b;
                          default -> "nope";
                      };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryWithSwitchExpressionNullSafeEqualsInverted() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Objects;
                  class Test {
                    public String determineSomething(String a, String b) {
                      return Objects.equals("a", a) ? "a" : Objects.equals("b", a) ? b : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return switch (a) {
                          case "a" -> "a";
                          case "b" -> b;
                          default -> "nope";
                      };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryRecursive() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String aString, String bString, String cString) {
                        return "a".equals(aString) ? "a" : "b".equals(bString) ? "b" : "c".equals(cString) ? "c" :"nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String aString, String bString, String cString) {
                        if ("a".equals(aString)) {
                            return "a";
                        }
                        if ("b".equals(bString)) {
                            return "b";
                        }
                        return "c".equals(cString) ? "c" : "nope";
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedAndTernaryWithIfThenTernary() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                      return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        if ("a".equals(a)) {
                            return "b".equals(b) ? "b" : "a";
                        }
                        return "nope";
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedAndOrTernaryWithIfThenTernaryElseTernary() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b, String c) {
                      return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "c".equals(c) ? "c" : "nope";
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b, String c) {
                        if ("a".equals(a)) {
                            return "b".equals(b) ? "b" : "a";
                        }
                        return "c".equals(c) ? "c" : "nope";
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceMultiLevelTernariesWithSwitchExpression() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String letter) {
                      return "a".equals(letter) ? "a" : //look its a
                      "b".equals(letter) ? "b" : //b is also here
                      "c".equals(letter) ? "c" : /* dont forget about c */
                      // d is important too
                      "d".equals(letter) ? "d" :
                      "e".equals(letter) ? "e" : //e
                      "f".equals(letter) ? "f" : //f
                      "g".equals(letter) ? "g" : "nope"; //and nope if nope
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String letter) {
                      return switch (letter) {
                          case "a" -> "a"; //look its a
                          case "b" -> "b"; //b is also here
                          case "c" -> "c"; /* dont forget about c */
                          // d is important too
                          case "d" -> "d";
                          case "e" -> "e"; //e
                          case "f" -> "f"; //f
                          case "g" -> "g";
                          default -> "nope";
                      }; //and nope if nope
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrAssignmentTernaryWithSwitch() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public void doThing(String a, String b) {
                        String result = "a".equals(a) ? "a" : "b".equals(a) ? b : "nope";
                        System.out.println(result);
                    }
                  }
                  """,
                """
                  class Test {
                    public void doThing(String a, String b) {
                        String result = switch (a) {
                            case "a" -> "a";
                            case "b" -> b;
                            default -> "nope";
                        };
                        System.out.println(result);
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doNotReplaceNonNestedOrTernaryInStreamWithSwitch() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                       List<String> s = Arrays.asList("a","b","c","nope");
                       return s.stream().map(item -> item.startsWith("a") ? "a" : "nope").collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryInStreamWithSwitch() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map(item -> item.equals("a") ? "a" : item.equals("b") ? "b" : "nope").collect(Collectors.toSet());
                    }
                  }
                  """,
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                        List<String> s = Arrays.asList("a","b","c","nope");
                        return s.stream().map(item -> switch (item) {
                            case "a" -> "a";
                            case "b" -> "b";
                            default -> "nope";
                        }).collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @ExpectedToFail("Pattern matching not yet implemented")
        @Issue("todo")
        @Test
        void doReplaceNestedOrTernaryInStreamWithPatternMatchingSwitch() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                       List<String> s = Arrays.asList("a","b","c","nope");
                       return s.stream().map(item -> item.startsWith("a") ? "a" : item.startsWith("b") ? "b" : "nope").collect(Collectors.toSet());
                    }
                  }
                  """,
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                       List<String> s = Arrays.asList("a","b","c","nope");
                       return s.stream().map(item ->
                        switch (item) {
                          case String st && st.startsWith("a") -> "a";
                          case String st && st.startsWith("b") -> "b";
                          default -> "nope";
                        }
                       ).collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryInStreamContainingComments() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                       List<String> s = Arrays.asList("a","b","c","nope");
                       return s.stream().map( /* look a lambda */ item ->
                            //look a ternary
                            item.equals("a") ? "a" : item.equals("b") ? "b" : "nope"
                            ).collect(Collectors.toSet());
                    }
                  }
                  """,
                """
                  import java.util.Set;
                  import java.util.Arrays;
                  import java.util.List;
                  import java.util.stream.Collectors;
                  class Test {
                    public Set<String> makeASet() {
                       List<String> s = Arrays.asList("a","b","c","nope");
                       return s.stream().map( /* look a lambda */ item -> switch (item) { //look a ternary
                                case "a" -> "a";
                                case "b" -> "b";
                                default -> "nope";
                            }
                            ).collect(Collectors.toSet());
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingNull() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a) {
                      return "a".equals(a) ? null : "b".equals(a) ? "b" : null;
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a) {
                      return switch (a) {
                          case "a" -> null;
                          case "b" -> "b";
                          default -> null;
                      };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @ExpectedToFail("not yet implemented collapsing cases")
        @Issue("todo")
        @Test
        void doReplaceNestedOrTernaryContainingNullCollapsingSameCases() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a) {
                      return "a".equals(a) ? null : "b".equals(a) ? "b" : "c".equals(a) ? "c" : null;
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return switch (a) {
                            case "b" -> "b";
                            case "c" -> "c";
                            default -> null;
                        };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingExpression() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return "a".equals(a) ? "foo" + "bar" : "b".equals(a) ? a + b : b + a;
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return switch (a) {
                            case "a" -> "foo" + "bar";
                            case "b" -> a + b;
                            default -> b + a;
                        };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingComments() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        //this should be before the if and followed by a new line
                    
                        return "a".equals(a) ? "a" : "b".equals(a) ? "b" : "nope"; //this should be behind the ternary
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        //this should be before the if and followed by a new line
                        
                        return switch (a) {
                            case "a" -> "a";
                            case "b" -> "b";
                            default -> "nope";
                        }; //this should be behind the ternary
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

        @Test
        void doReplaceNestedOrTernaryContainingMethodCall() {
            //language=java
            rewriteRun(
              java("""
                class M{
                  static String a(){return "a";}
                  static String b(){return "b";}
                  static String c(){return "c";}
                  static String nope(){return "nope";}
                }
                """),
              java(
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return "a".equals(a) ? M.a() : "b".equals(a) ? M.b() : M.nope();
                    }
                  }
                  """,
                """
                  class Test {
                    public String determineSomething(String a, String b) {
                        return switch (a) {
                            case "a" -> M.a();
                            case "b" -> M.b();
                            default -> M.nope();
                        };
                    }
                  }
                  """,
                JAVA_17
              )
            );
        }

    }


}