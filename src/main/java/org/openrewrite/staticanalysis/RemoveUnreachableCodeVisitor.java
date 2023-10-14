package org.openrewrite.staticanalysis;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.Break;
import org.openrewrite.java.tree.J.Continue;
import org.openrewrite.java.tree.J.Return;
import org.openrewrite.java.tree.J.Throw;
import org.openrewrite.java.tree.Statement;

@AllArgsConstructor
public class RemoveUnreachableCodeVisitor extends JavaVisitor<ExecutionContext> {

  @Override
  public J visitBlock(Block block, ExecutionContext executionContext) {
    block = (Block) super.visitBlock(block, executionContext);

    List<Statement> statements = block.getStatements();
    Optional<Integer> maybeFirstJumpIndex = findFirstJump(statements);
    if (!maybeFirstJumpIndex.isPresent()) {
      return block;
    }
    int firstJumpIndex = maybeFirstJumpIndex.get();

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
          statement instanceof Return ||
          statement instanceof Throw ||
          statement instanceof Break ||
          statement instanceof Continue
      ) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }
}
