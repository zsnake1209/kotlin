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

public class NativeDefinitionsCSelectionStatementImpl extends ASTWrapperPsiElement implements NativeDefinitionsCSelectionStatement {

  public NativeDefinitionsCSelectionStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCSelectionStatement(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public NativeDefinitionsCExpression getCExpression() {
    return findNotNullChildByClass(NativeDefinitionsCExpression.class);
  }

  @Override
  @NotNull
  public List<NativeDefinitionsCStatement> getCStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, NativeDefinitionsCStatement.class);
  }

}
