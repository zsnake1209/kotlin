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

public class NativeDefinitionsCTypeSpecifierImpl extends ASTWrapperPsiElement implements NativeDefinitionsCTypeSpecifier {

  public NativeDefinitionsCTypeSpecifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCTypeSpecifier(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public NativeDefinitionsCAtomicTypeSpecifier getCAtomicTypeSpecifier() {
    return findChildByClass(NativeDefinitionsCAtomicTypeSpecifier.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCEnumSpecifier getCEnumSpecifier() {
    return findChildByClass(NativeDefinitionsCEnumSpecifier.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCStructOrUnionSpecifier getCStructOrUnionSpecifier() {
    return findChildByClass(NativeDefinitionsCStructOrUnionSpecifier.class);
  }

}
