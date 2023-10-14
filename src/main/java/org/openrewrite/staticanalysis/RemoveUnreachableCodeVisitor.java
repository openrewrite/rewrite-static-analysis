package org.openrewrite.staticanalysis;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.Return;
import org.openrewrite.java.tree.J.Throw;
import org.openrewrite.java.tree.Statement;

@AllArgsConstructor
public class RemoveUnreachableCodeVisitor extends JavaVisitor<ExecutionContext> {
  @Override
  public J visitMethodDeclaration(MethodDeclaration method, ExecutionContext executionContext) {
    method = (MethodDeclaration) super.visitMethodDeclaration(method, executionContext);
    if (method.getBody() == null) {
      return method;
    }

    List<Statement> statements = method.getBody().getStatements();
    Optional<Integer> maybeFirstJumpIndex = findFirstJump(statements);
    if (!maybeFirstJumpIndex.isPresent()) {
      return method;
    }
    int firstJumpIndex = maybeFirstJumpIndex.get();

    List<Statement> newStatements =
        ListUtils.flatMap(
            method.getBody().getStatements(),
            (index, statement) -> {
              if (index <= firstJumpIndex) {
                return statement;
              }
              return Collections.emptyList();
            }
        );

    return method.withBody(method.getBody().withStatements(newStatements));
  }

  private Optional<Integer> findFirstJump(List<Statement> statements) {
    for (int i = 0; i < statements.size(); i++) {
      Statement statement = statements.get(i);
      if (statement instanceof Return || statement instanceof Throw) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }
}
