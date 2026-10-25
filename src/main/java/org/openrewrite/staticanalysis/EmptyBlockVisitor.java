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

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.FindReferencedTypes;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.EmptyBlockStyle;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@NoArgsConstructor
@AllArgsConstructor
public class EmptyBlockVisitor<P> extends JavaIsoVisitor<P> {
    private EmptyBlockStyle emptyBlockStyle;

    @Override
    public @Nullable J visit(@Nullable Tree tree, P p) {
        //noinspection ConstantValue
        if (emptyBlockStyle == null && tree instanceof SourceFile) {
            emptyBlockStyle = Style.from(EmptyBlockStyle.class, (SourceFile)tree, Checkstyle::emptyBlock);
        }
        return super.visit(tree, p);
    }

    @Override
    public J.Block visitBlock(J.Block block, P p) {
        J.Block b = super.visitBlock(block, p);

        AtomicBoolean filtered = new AtomicBoolean(false);
        List<Statement> statements = ListUtils.map(b.getStatements(), s -> {
            if (!(s instanceof J.Block)) {
                return s;
            }

            J.Block nestedBlock = (J.Block) s;
            if (isEmptyBlock(nestedBlock) && ((Boolean.TRUE.equals(emptyBlockStyle.getStaticInit()) && nestedBlock.isStatic()) ||
                    (Boolean.TRUE.equals(emptyBlockStyle.getInstanceInit()) && !nestedBlock.isStatic()))) {
                filtered.set(true);
                //noinspection DataFlowIssue
                return null;
            }

            return nestedBlock;
        });

        return filtered.get() ? b.withStatements(statements) : b;
    }

    @Override
    public J.Try visitTry(J.Try tryable, P p) {
        J.Try t = super.visitTry(tryable, p);

        if (Boolean.TRUE.equals(emptyBlockStyle.getLiteralTry()) &&
            isEmptyBlock(t.getBody()) &&
            isEmptyResources(t.getResources())) {
            J.Block finallyBlock = t.getFinally();
            if (finallyBlock == null || finallyBlock.getStatements().isEmpty()) {
                replaceStatement(tryable, emptyList());
            } else if (finallyBlock.getStatements().stream()
                    .anyMatch(s -> s instanceof J.VariableDeclarations || s instanceof J.ClassDeclaration)) {
                replaceStatement(tryable, singletonList(finallyBlock));
            } else {
                replaceStatement(tryable, finallyBlock.getStatements());
            }
        } else if (Boolean.TRUE.equals(emptyBlockStyle.getLiteralFinally()) && t.getFinally() != null &&
                   !t.getCatches().isEmpty() && isEmptyBlock(t.getFinally())) {
            t = t.withFinally(null);
        }

        return t;
    }

    @Override
    public J.If visitIf(J.If iff, P p) {
        J.If i = super.visitIf(iff, p);

        if (Boolean.TRUE.equals(emptyBlockStyle.getLiteralElse()) &&
            i.getElsePart() != null &&
            isEmptyBlock(i.getElsePart().getBody())) {
            i = i.withElsePart(null);
        }

        if (Boolean.FALSE.equals(emptyBlockStyle.getLiteralIf()) || !isEmptyBlock(i.getThenPart())) {
            return i;
        }

        if (i.getElsePart() == null) {
            replaceWithSideEffects(i, i.getIfCondition().getTree());
            return i;
        }

        // invert top-level if
        // Ideally should also add support for other types of expression
        J.ControlParentheses<Expression> cond = i.getIfCondition();
        if (!(cond.getTree() instanceof J.Binary)) {
            return i;
        }
        J.Binary binary = (J.Binary) cond.getTree();
        if (binary.getOperator() != J.Binary.Type.Equal && binary.getOperator() != J.Binary.Type.NotEqual &&
            (mayBeNaN(binary.getLeft()) || mayBeNaN(binary.getRight()))) {
            // `!(d > 0)` holds for NaN, but `d <= 0` does not
            return i;
        }

        // only some operators are supported
        switch (binary.getOperator()) {
            case Equal:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.NotEqual));
                break;
            case NotEqual:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.Equal));
                break;
            case LessThan:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.GreaterThanOrEqual));
                break;
            case LessThanOrEqual:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.GreaterThan));
                break;
            case GreaterThan:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.LessThanOrEqual));
                break;
            case GreaterThanOrEqual:
                cond = cond.withTree(binary.withOperator(J.Binary.Type.LessThan));
                break;
            default:
                return i;
        }
        i = i.withIfCondition(cond);

        if (i.getElsePart() == null) {
            return i.withThenPart(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))
                    .withElsePart(null);
        }

        // NOTE: then part MUST be a J.Block, because otherwise impossible to have an empty if condition followed by else-if/else chain
        J.Block thenPart = (J.Block) i.getThenPart();

        Statement elseStatement = i.getElsePart().getBody();
        List<Statement> elseStatementBody;
        if (elseStatement instanceof J.Block) {
            // any else statements should already be at the correct indentation level
            elseStatementBody = ((J.Block) elseStatement).getStatements();
        } else if (elseStatement instanceof J.If) {
            // J.If will typically just have a format of one space (the space between "else" and "if" in "else if")
            // we want this to be on its own line now inside its containing if block
            elseStatementBody = singletonList(ShiftFormat.indent(
                    elseStatement.withPrefix(Space.format("\n" + i.getPrefix().getIndent())),
                    getCursor(), 1));
        } else {
            elseStatementBody = singletonList(ShiftFormat.indent(elseStatement, getCursor(), 1));
        }

        return i.withThenPart(thenPart.withStatements(elseStatementBody))
                .withElsePart(null);
    }

    @Override
    public J.Synchronized visitSynchronized(J.Synchronized synch, P p) {
        if (Boolean.TRUE.equals(emptyBlockStyle.getLiteralSynchronized()) && isEmptyBlock(synch.getBody())) {
            replaceWithSideEffects(synch, synch.getLock().getTree());
        }

        return super.visitSynchronized(synch, p);
    }

    @Override
    public J.Switch visitSwitch(J.Switch switch_, P p) {
        if (Boolean.TRUE.equals(emptyBlockStyle.getLiteralSwitch()) && isEmptyBlock(switch_.getCases())) {
            replaceWithSideEffects(switch_, switch_.getSelector().getTree());
        }

        return super.visitSwitch(switch_, p);
    }

    private void replaceWithSideEffects(Statement statement, Expression expression) {
        if (sideEffectsExtractable(expression)) {
            replaceStatement(statement, expression.getSideEffects());
        }
    }

    private void replaceStatement(Statement statement, List<? extends J> replacements) {
        if (replacements.isEmpty()) {
            doAfterVisit(new DeleteStatement<>(statement));
            return;
        }
        J.Block enclosingBlock = getCursor().firstEnclosing(J.Block.class);
        if (enclosingBlock != null) {
            doAfterVisit(new ReplaceStatement<>(enclosingBlock, statement, new ArrayList<>(replacements)));
        }
    }

    // `getSideEffects()` misses side effects inside ternaries, array accesses and the like,
    // and would make those on the right of `&&` and `||` unconditional
    private static boolean sideEffectsExtractable(Expression e) {
        if (!SideEffects.mayHaveSideEffects(e) ||
            e instanceof J.MethodInvocation || e instanceof J.NewClass ||
            e instanceof J.Assignment || e instanceof J.AssignmentOperation) {
            return true;
        }
        if (e instanceof J.Unary) {
            J.Unary unary = (J.Unary) e;
            return unary.getOperator().isModifying() || sideEffectsExtractable(unary.getExpression());
        }
        if (e instanceof J.Binary) {
            J.Binary binary = (J.Binary) e;
            if (binary.getOperator() == J.Binary.Type.And || binary.getOperator() == J.Binary.Type.Or) {
                return sideEffectsExtractable(binary.getLeft()) && !SideEffects.mayHaveSideEffects(binary.getRight());
            }
            return sideEffectsExtractable(binary.getLeft()) && sideEffectsExtractable(binary.getRight());
        }
        if (e instanceof J.Parentheses) {
            J inner = ((J.Parentheses<?>) e).getTree();
            return inner instanceof Expression && sideEffectsExtractable((Expression) inner);
        }
        if (e instanceof J.TypeCast) {
            return sideEffectsExtractable(((J.TypeCast) e).getExpression());
        }
        if (e instanceof J.InstanceOf) {
            return sideEffectsExtractable(((J.InstanceOf) e).getExpression());
        }
        if (e instanceof J.FieldAccess) {
            return sideEffectsExtractable(((J.FieldAccess) e).getTarget());
        }
        return false;
    }

    private static boolean mayBeNaN(Expression e) {
        JavaType type = e.getType();
        return type == null || type instanceof JavaType.Unknown ||
               type == JavaType.Primitive.Double || type == JavaType.Primitive.Float ||
               TypeUtils.isOfClassType(type, "java.lang.Double") || TypeUtils.isOfClassType(type, "java.lang.Float");
    }

    private boolean isEmptyBlock(Statement blockNode) {
        if (blockNode instanceof J.Block) {
            J.Block block = (J.Block) blockNode;
            if (EmptyBlockStyle.BlockPolicy.STATEMENT == emptyBlockStyle.getBlockPolicy()) {
                return block.getStatements().isEmpty();
            }
            if (EmptyBlockStyle.BlockPolicy.TEXT == emptyBlockStyle.getBlockPolicy()) {
                return block.getStatements().isEmpty() && block.getEnd().getComments().isEmpty();
            }
        }
        return false;
    }

    private boolean isEmptyResources(@Nullable List<J.Try.Resource> resources) {
        return resources == null || resources.isEmpty();
    }

    @RequiredArgsConstructor
    private static class ReplaceStatement<P> extends JavaVisitor<P> {
        private final J.Block enclosingBlock;
        private final Statement toReplace;
        private final List<J> replacements;

        @Override
        public J visitBlock(J.Block block, P p) {
            J.Block b = (J.Block) super.visitBlock(block, p);
            if (enclosingBlock.isScope(block)) {
                b = b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (!toReplace.isScope(statement)) {
                        return statement;
                    }
                    for (JavaType.FullyQualified referenced : FindReferencedTypes.find(toReplace)) {
                        maybeRemoveImport(referenced);
                    }
                    return ListUtils.mapFirst(
                            ListUtils.map(replacements, replacement -> autoFormat(replacement, p, getCursor())),
                            first -> first.withPrefix(toReplace.getPrefix()));
                }));
            }
            return b;
        }
    }
}
