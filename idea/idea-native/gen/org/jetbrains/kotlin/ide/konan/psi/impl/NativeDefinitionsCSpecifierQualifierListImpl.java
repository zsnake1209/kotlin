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

public class NativeDefinitionsCSpecifierQualifierListImpl extends ASTWrapperPsiElement implements NativeDefinitionsCSpecifierQualifierList {

  public NativeDefinitionsCSpecifierQualifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCSpecifierQualifierList(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCSpecifierQualifierList getCSpecifierQualifierList() {
    return findChildByClass(NativeDefinitionsCSpecifierQualifierList.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCTypeQualifier getCTypeQualifier() {
    return findChildByClass(NativeDefinitionsCTypeQualifier.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCTypeSpecifier getCTypeSpecifier() {
    return findChildByClass(NativeDefinitionsCTypeSpecifier.class);
  }

}
