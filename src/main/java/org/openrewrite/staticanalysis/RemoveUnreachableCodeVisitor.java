package org.openrewrite.staticanalysis;

import lombok.AllArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class RemoveUnreachableCodeVisitor extends JavaVisitor<ExecutionContext> {

  @Override
  public J visitBlock(J.Block block, ExecutionContext executionContext) {
    block = (J.Block) super.visitBlock(block, executionContext);

    List<Statement> statements = block.getStatements();
    Optional<Integer> maybeFirstJumpIndex = findFirstJump(statements);
    if (!maybeFirstJumpIndex.isPresent()) {
      return block;
    }
    int firstJumpIndex = maybeFirstJumpIndex.get();

    if (firstJumpIndex == statements.size() - 1) {
      // Jump is at the end of the block, so nothing to do
      return block;
    }

    List<Statement> newStatements =
        ListUtils.flatMap(
            block.getStatements(),
            (index, statement) -> {
              if (index <= firstJumpIndex) {
                return statement;
              }
              return Collections.emptyList();
            }
        );

    return block.withStatements(newStatements);
  }

  private Optional<Integer> findFirstJump(List<Statement> statements) {
    for (int i = 0; i < statements.size(); i++) {
      Statement statement = statements.get(i);
      if (
          statement instanceof J.Return ||
          statement instanceof J.Throw ||
          statement instanceof J.Break ||
          statement instanceof J.Continue
      ) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }
}
