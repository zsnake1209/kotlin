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

public class NativeDefinitionsCDeclarationSpecifiersImpl extends ASTWrapperPsiElement implements NativeDefinitionsCDeclarationSpecifiers {

  public NativeDefinitionsCDeclarationSpecifiersImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCDeclarationSpecifiers(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCAlignmentSpecifier getCAlignmentSpecifier() {
    return findChildByClass(NativeDefinitionsCAlignmentSpecifier.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCDeclarationSpecifiers getCDeclarationSpecifiers() {
    return findChildByClass(NativeDefinitionsCDeclarationSpecifiers.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCFunctionSpecifier getCFunctionSpecifier() {
    return findChildByClass(NativeDefinitionsCFunctionSpecifier.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCStorageClassSpecifier getCStorageClassSpecifier() {
    return findChildByClass(NativeDefinitionsCStorageClassSpecifier.class);
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
