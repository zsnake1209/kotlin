// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlin.ide.konan.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.jetbrains.kotlin.ide.konan.psi.*;

public class NativeDefinitionsCStatementImpl extends ASTWrapperPsiElement implements NativeDefinitionsCStatement {

  public NativeDefinitionsCStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCStatement(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCCompoundStatement getCCompoundStatement() {
    return findChildByClass(NativeDefinitionsCCompoundStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCExpressionStatement getCExpressionStatement() {
    return findChildByClass(NativeDefinitionsCExpressionStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCIterationStatement getCIterationStatement() {
    return findChildByClass(NativeDefinitionsCIterationStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCJumpStatement getCJumpStatement() {
    return findChildByClass(NativeDefinitionsCJumpStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCLabeledStatement getCLabeledStatement() {
    return findChildByClass(NativeDefinitionsCLabeledStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCSelectionStatement getCSelectionStatement() {
    return findChildByClass(NativeDefinitionsCSelectionStatement.class);
  }

}
