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

public class NativeDefinitionsCAlignmentSpecifierImpl extends ASTWrapperPsiElement implements NativeDefinitionsCAlignmentSpecifier {

  public NativeDefinitionsCAlignmentSpecifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCAlignmentSpecifier(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCConstantExpression getCConstantExpression() {
    return findChildByClass(NativeDefinitionsCConstantExpression.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCTypeName getCTypeName() {
    return findChildByClass(NativeDefinitionsCTypeName.class);
  }

}
