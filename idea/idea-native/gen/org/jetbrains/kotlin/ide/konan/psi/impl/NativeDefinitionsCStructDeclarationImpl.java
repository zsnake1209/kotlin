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

public class NativeDefinitionsCStructDeclarationImpl extends ASTWrapperPsiElement implements NativeDefinitionsCStructDeclaration {

  public NativeDefinitionsCStructDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCStructDeclaration(this);
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
  public NativeDefinitionsCStaticAssertDeclaration getCStaticAssertDeclaration() {
    return findChildByClass(NativeDefinitionsCStaticAssertDeclaration.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCStructDeclaratorList getCStructDeclaratorList() {
    return findChildByClass(NativeDefinitionsCStructDeclaratorList.class);
  }

}
