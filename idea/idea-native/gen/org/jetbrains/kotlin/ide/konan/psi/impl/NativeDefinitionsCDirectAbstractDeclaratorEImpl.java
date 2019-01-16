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

public class NativeDefinitionsCDirectAbstractDeclaratorEImpl extends NativeDefinitionsCDirectAbstractDeclaratorImpl implements NativeDefinitionsCDirectAbstractDeclaratorE {

  public NativeDefinitionsCDirectAbstractDeclaratorEImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCDirectAbstractDeclaratorE(this);
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
  public NativeDefinitionsCDirectAbstractDeclarator getCDirectAbstractDeclarator() {
    return findNotNullChildByClass(NativeDefinitionsCDirectAbstractDeclarator.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCTypeQualifierList getCTypeQualifierList() {
    return findNotNullChildByClass(NativeDefinitionsCTypeQualifierList.class);
  }

}
