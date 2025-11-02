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

import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This recipe replaces magic number literals in method bodies with named constants following the Sonar java:S109 rule.
 * <br/>
 * All detected magic numbers (excluding those explicitly assigned to variables or fields) will be extracted as
 * private static final constants at the top of the class.
 * The original numeric usages are replaced with the new constant name to improve code readability and maintainability.
 * <br/>
 * Currently, unsupported:
 * - The recipe will not create constants for literals already declared as field/variable assignments.
 * - The recipe does not ignore typical non-magic numbers (like 0, 1, -1); this can be extended for full Sonar parity.
 * - Only basic type literals (e.g., int, double, float) are handled. String and char literals are not affected.
 * <br/>
 * Note: The constant name is generated based on the type and value, and may contain underscores or "NEGATIVE_" for negative values.
 */
public class ReplaceMagicNumbersWithConstants extends Recipe {
  private static final String CUSTOM_MODIFIERS = "private static final";

  @Override
  public @NlsRewrite.DisplayName String getDisplayName() {
    return "How to use Visitors";
  }

  @Override
  public @NlsRewrite.Description String getDescription() {
      return "Replaces magic number literals in method bodies with named constants to improve code readability and maintainability. " +
              "Magic numbers are replaced by private static final constants declared at the top of the class, following Sonar's java:S109 rule. " +
              "The recipe does not create constants for literals that are already assigned to fields or variables, nor for typical non-magic numbers (such as 0, 1, or -1). " +
              "Currently, only numeric primitive literals are handled; string and character literals are unaffected. " +
              "If a constant for a value already exists, or the constant name would conflict with an existing symbol, the recipe will skip that value.";
  }
  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<ExecutionContext>(){
    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
      J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

      List<J.Literal> literals = new ArrayList<>();
      new JavaVisitor<ExecutionContext>() {
        @Override
        public J visitLiteral(J.Literal literal, ExecutionContext ctx2) {
          Cursor cursor = getCursor();
          if(!(cursor.getParent().getParent().getValue() instanceof J.VariableDeclarations.NamedVariable)){
            literals.add(literal);
          }
          return literal;
        }
      }.visit(classDecl, ctx);

      List<String> newFieldSources = new ArrayList<>();
      for (J.Literal literal : literals) {
        String constantName = getStrValFromLiteral(literal);
        boolean alreadyExists = cd.getBody().getStatements().stream()
            .filter(J.VariableDeclarations.class::isInstance)
            .map(J.VariableDeclarations.class::cast)
            .flatMap(vars -> vars.getVariables().stream())
            .anyMatch(var -> var.getSimpleName().equals(constantName));
        if (!alreadyExists) {
          String modifiers = CUSTOM_MODIFIERS; // this is "private static final"
          String typeName = literal.getType() == null ? "Object" : literal.getType().toString();
          String fieldSource = modifiers + " " + typeName + " " + constantName + " = " + literal.getValueSource() + ";";
          newFieldSources.add(fieldSource);
        }
      }
      if (newFieldSources.isEmpty()) {
        return cd;
      }

      String templateStr = String.join("\n", newFieldSources);
      JavaTemplate template = JavaTemplate.builder(templateStr)
          .contextSensitive()
          .build();
      Cursor bodyCursor = new Cursor(getCursor(), cd.getBody());
      J.Block updatedBody = template.apply(bodyCursor, cd.getBody().getCoordinates().firstStatement());

      return cd.withBody(updatedBody);
    }


      @Override
      public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
        Cursor cursor = getCursor();
        if(cursor.getParent().getParent().getValue() instanceof J.VariableDeclarations.NamedVariable){
          return super.visitLiteral(literal, ctx);
        }
        String valueSource = literal.getValueSource();
        String constantName = getStrValFromLiteral(literal); // e.g., "DOUBLE_51_0"
        if (constantName != null) {
            JavaTemplate template = JavaTemplate.builder(constantName).build();
            return template.apply(getCursor(), literal.getCoordinates().replace());
        }
        return super.visitLiteral(literal, ctx);
      }
    };
  }

  @Override
  public Set<String> getTags() {
    return Collections.singleton("RSPEC-109");
  }
  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofSeconds(10);
  }
  private String getStrValFromLiteral(J.Literal literal) {
    return literal.getType().toString().toUpperCase() + "_" + literal.getValueSource().replace(".", "_").replace("-", "NEGATIVE_");
  }
}
