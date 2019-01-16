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

public class NativeDefinitionsCStructOrUnionSpecifierImpl extends ASTWrapperPsiElement implements NativeDefinitionsCStructOrUnionSpecifier {

  public NativeDefinitionsCStructOrUnionSpecifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCStructOrUnionSpecifier(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCStructDeclarationList getCStructDeclarationList() {
    return findChildByClass(NativeDefinitionsCStructDeclarationList.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCStructOrUnion getCStructOrUnion() {
    return findNotNullChildByClass(NativeDefinitionsCStructOrUnion.class);
  }

}
