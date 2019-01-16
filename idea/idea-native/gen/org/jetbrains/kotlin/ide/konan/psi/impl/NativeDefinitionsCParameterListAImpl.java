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

public class NativeDefinitionsCParameterListAImpl extends NativeDefinitionsCParameterListImpl implements NativeDefinitionsCParameterListA {

  public NativeDefinitionsCParameterListAImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCParameterListA(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public NativeDefinitionsCParameterDeclaration getCParameterDeclaration() {
    return findNotNullChildByClass(NativeDefinitionsCParameterDeclaration.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCParameterList getCParameterList() {
    return findNotNullChildByClass(NativeDefinitionsCParameterList.class);
  }

}
