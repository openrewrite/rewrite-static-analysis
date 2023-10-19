/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class FinalizeMethodArgumentsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FinalizeMethodArguments());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3133")
    @Test
    void twoParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo {
                int test(final int a, int b) {
                    return a + b;
                }
              }
              """,
            """
              class Foo {
                int test(final int a, final int b) {
                    return a + b;
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3135")
    @Test
    void ignoreAbstractMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo {
              abstract void bar(String a);
              }
              """
          )
        );
    }

    @Test
    void doNotAddFinalIfAssigned() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void SubeventUtils(String a) {
                      a = "abc";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAddFinalIfInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface MarketDeleteService {
                   void deleteMarket(Long marketId, String deletionTimestamp);
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void replaceWithFinalModifier() {
        rewriteRun(
          //language=java
          java(
            """
              class TestClass {
                  private void getAccaCouponData(String responsiveRequestConfig, String card) {
                  }
              }
              """,
            """
              class TestClass {
                  private void getAccaCouponData(final String responsiveRequestConfig, final String card) {
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithFinalModifierWhenAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void test(@Override Integer test) {}
              }
              """,
            """
              class Test {
                  public void test(@Override final Integer test) {}
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWithFinalModifier() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package responsive.utils.subevent;
               
               import responsive.enums.subevent.SubeventTypes;
               import responsive.model.dto.card.SubEvent;
               import java.util.List;
               import org.springframework.beans.factory.annotation.Value;
               import org.springframework.stereotype.Component;
               
               import static responsive.enums.matchdata.MatchDataTitleSeparator.AT;
               import static responsive.enums.matchdata.MatchDataTitleSeparator.VS;
               import static java.lang.String.format;
               import static org.apache.commons.lang3.StringUtils.splitByWholeSeparator;
               
               /**
                * Created by mza05 on 13/10/2017.
                */
               @Component
               class SubeventUtils {
               
                   private static final String SUBEVENT_FORMAT = "%s%s%s";
                   private final List<Integer> categoryGroupIdForChangeSubeventName;
               
                   public SubeventUtils(
                           @Value("#{'${responsive.category.group.id.change.subevent.name}'.split(',')}")  final List<Integer> categoryGroupIdForChangeSubeventName) {
                       this.categoryGroupIdForChangeSubeventName = categoryGroupIdForChangeSubeventName;
                   }
               
                   public static boolean isSubeventOfSpecifiedType(final SubEvent subEvent, final List<SubeventTypes> requiredTypes) {
               
                       if (subEvent.getType() == null) {
                           return false;
                       }
                       return requiredTypes.stream()
                               .anyMatch(requiredType -> requiredType.getType().equalsIgnoreCase(subEvent.getType()));
               
                   }
               
                   /**
                    * Change SubeventName by CategoryGroupId and rebub
                    * @param subeventName
                    * @param categoryGroupId
                    * @return
                    */
                   public String getSubeventNameForCategoryGroupId(final String subeventName, final Integer categoryGroupId) {
               
                       if  (subeventName != null && categoryGroupId != null
                               && subeventName.contains(AT.getSeparator())
                               && categoryGroupIdForChangeSubeventName.contains(categoryGroupId)) {
                           final var subeventTeamSplit = splitByWholeSeparator(subeventName, AT.getSeparator());
                           if (subeventTeamSplit.length > 0) {
                               return format(SUBEVENT_FORMAT, subeventTeamSplit[0], VS.getSeparator(), subeventTeamSplit[1]);
                           }
                       }
               
                       return subeventName;
                   }
               }
               """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/176")
    void doNotReplaceIfAssignedThroughUnaryOrAccumulator() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                protected int addFinalToThisVar(int unalteredVariable) {
                  return unalteredVariable;
                }
                            
                protected int increment(int variableToIncrement) {
                  variableToIncrement++;
                  return variableToIncrement;
                }
                            
                protected int preIncrement(int variableToPreIncrement) {
                  return ++variableToPreIncrement;
                }
                            
                protected int decrement(int variableToDecrement) {
                  variableToDecrement--;
                  return variableToDecrement;
                }
                            
                protected int preDecrement(int variableToPreDecrement) {
                  return --variableToPreDecrement;
                }
                            
                protected int accumulate(int add, int accumulator) {
                  accumulator += add;
                  return accumulator;
                }
              }
              """,
            """
              class Test {
                protected int addFinalToThisVar(final int unalteredVariable) {
                  return unalteredVariable;
                }
                            
                protected int increment(int variableToIncrement) {
                  variableToIncrement++;
                  return variableToIncrement;
                }
                            
                protected int preIncrement(int variableToPreIncrement) {
                  return ++variableToPreIncrement;
                }
                            
                protected int decrement(int variableToDecrement) {
                  variableToDecrement--;
                  return variableToDecrement;
                }
                            
                protected int preDecrement(int variableToPreDecrement) {
                  return --variableToPreDecrement;
                }
                            
                protected int accumulate(int add, int accumulator) {
                  accumulator += add;
                  return accumulator;
                }
              }
              """
          )
        );
    }
}
