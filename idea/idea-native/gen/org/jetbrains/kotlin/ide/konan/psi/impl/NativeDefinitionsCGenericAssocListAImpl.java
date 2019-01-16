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

public class NativeDefinitionsCGenericAssocListAImpl extends NativeDefinitionsCGenericAssocListImpl implements NativeDefinitionsCGenericAssocListA {

  public NativeDefinitionsCGenericAssocListAImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCGenericAssocListA(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public NativeDefinitionsCGenericAssociation getCGenericAssociation() {
    return findNotNullChildByClass(NativeDefinitionsCGenericAssociation.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCGenericAssocList getCGenericAssocList() {
    return findNotNullChildByClass(NativeDefinitionsCGenericAssocList.class);
  }

}
