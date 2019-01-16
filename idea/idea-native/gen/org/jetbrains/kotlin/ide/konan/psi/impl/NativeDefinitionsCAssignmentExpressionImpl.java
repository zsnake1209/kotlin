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

public class NativeDefinitionsCAssignmentExpressionImpl extends ASTWrapperPsiElement implements NativeDefinitionsCAssignmentExpression {

  public NativeDefinitionsCAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCAssignmentExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCAssignmentExpression getCAssignmentExpression() {
    return findChildByClass(NativeDefinitionsCAssignmentExpression.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCAssignmentOperator getCAssignmentOperator() {
    return findChildByClass(NativeDefinitionsCAssignmentOperator.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCConditionalExpression getCConditionalExpression() {
    return findChildByClass(NativeDefinitionsCConditionalExpression.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCUnaryExpression getCUnaryExpression() {
    return findChildByClass(NativeDefinitionsCUnaryExpression.class);
  }

}
