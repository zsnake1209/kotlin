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

public class NativeDefinitionsCConditionalExpressionImpl extends ASTWrapperPsiElement implements NativeDefinitionsCConditionalExpression {

  public NativeDefinitionsCConditionalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCConditionalExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCConditionalExpression getCConditionalExpression() {
    return findChildByClass(NativeDefinitionsCConditionalExpression.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCExpression getCExpression() {
    return findChildByClass(NativeDefinitionsCExpression.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCLogicalOrExpression getCLogicalOrExpression() {
    return findNotNullChildByClass(NativeDefinitionsCLogicalOrExpression.class);
  }

}
