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

public class NativeDefinitionsCFunctionDefinitionImpl extends ASTWrapperPsiElement implements NativeDefinitionsCFunctionDefinition {

  public NativeDefinitionsCFunctionDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull NativeDefinitionsVisitor visitor) {
    visitor.visitCFunctionDefinition(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof NativeDefinitionsVisitor) accept((NativeDefinitionsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public NativeDefinitionsCCompoundStatement getCCompoundStatement() {
    return findNotNullChildByClass(NativeDefinitionsCCompoundStatement.class);
  }

  @Override
  @Nullable
  public NativeDefinitionsCDeclarationList getCDeclarationList() {
    return findChildByClass(NativeDefinitionsCDeclarationList.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCDeclarationSpecifiers getCDeclarationSpecifiers() {
    return findNotNullChildByClass(NativeDefinitionsCDeclarationSpecifiers.class);
  }

  @Override
  @NotNull
  public NativeDefinitionsCDeclarator getCDeclarator() {
    return findNotNullChildByClass(NativeDefinitionsCDeclarator.class);
  }

}
