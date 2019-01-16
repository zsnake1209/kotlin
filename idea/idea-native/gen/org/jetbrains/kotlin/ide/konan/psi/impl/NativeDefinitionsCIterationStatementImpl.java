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

public class NativeDefinitionsCIterationStatementImpl extends ASTWrapperPsiElement implements NativeDefinitionsCIterationStatement {

  public NativeDefinitionsCIterationStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCIterationStatement(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCDeclaration getCDeclaration() {
    return findChildByClass(NativeDefinitionsCDeclaration.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCExpression getCExpression() {
    return findChildByClass(NativeDefinitionsCExpression.class);
  }

  @Override
  @NotNull
  public List<NativeDefinitionsCExpressionStatement> getCExpressionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, NativeDefinitionsCExpressionStatement.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCStatement getCStatement() {
    return findNotNullChildByClass(NativeDefinitionsCStatement.class);
  }

}
