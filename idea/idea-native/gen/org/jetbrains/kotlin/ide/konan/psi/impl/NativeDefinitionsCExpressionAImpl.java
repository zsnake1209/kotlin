// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlin.ide.konan.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes.*;
import org.jetbrains.kotlin.ide.konan.psi.*;

public class NativeDefinitionsCExpressionAImpl extends NativeDefinitionsCExpressionImpl implements NativeDefinitionsCExpressionA {

  public NativeDefinitionsCExpressionAImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCExpressionA(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public NativeDefinitionsCAssignmentExpression getCAssignmentExpression() {
    return findNotNullChildByClass(NativeDefinitionsCAssignmentExpression.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCExpression getCExpression() {
    return findNotNullChildByClass(NativeDefinitionsCExpression.class);
  }

}
