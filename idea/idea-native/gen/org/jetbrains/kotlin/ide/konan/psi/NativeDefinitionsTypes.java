// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlin.ide.konan.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;

public interface NativeDefinitionsTypes {

  IElementType CODE = new NativeDefinitionsElementType("CODE");

  IElementType CODE_CHARS = new NativeDefinitionsTokenType("CODE_CHARS");
  IElementType COMMENT = new NativeDefinitionsTokenType("COMMENT");
  IElementType DELIM = new NativeDefinitionsTokenType("DELIM");
  IElementType KEY_KNOWN = new NativeDefinitionsTokenType("KEY_KNOWN");
  IElementType KEY_UNKNOWN = new NativeDefinitionsTokenType("KEY_UNKNOWN");
  IElementType PLATFORM = new NativeDefinitionsTokenType("PLATFORM");
  IElementType SEPARATOR = new NativeDefinitionsTokenType("SEPARATOR");
  IElementType VALUE = new NativeDefinitionsTokenType("VALUE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CODE) {
        return new NativeDefinitionsCodeImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
