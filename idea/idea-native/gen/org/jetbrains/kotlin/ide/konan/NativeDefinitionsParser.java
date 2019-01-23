/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlin.ide.konan;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class NativeDefinitionsParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == CODE) {
      r = CODE(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return ROOT(b, l + 1);
  }

  /* ********************************************************** */
  // CODE_CHARS
  public static boolean CODE(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CODE")) return false;
    if (!nextTokenIs(b, CODE_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CODE_CHARS);
    exit_section_(b, m, CODE, r);
    return r;
  }

  /* ********************************************************** */
  // definitions_ (DELIM CODE)?
  static boolean ROOT(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ROOT")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = definitions_(b, l + 1);
    r = r && ROOT_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (DELIM CODE)?
  private static boolean ROOT_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ROOT_1")) return false;
    ROOT_1_0(b, l + 1);
    return true;
  }

  // DELIM CODE
  private static boolean ROOT_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ROOT_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DELIM);
    r = r && CODE(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // key_ SEPARATOR VALUE
  static boolean definition_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definition_")) return false;
    if (!nextTokenIs(b, "", KEY_KNOWN, KEY_UNKNOWN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = key_(b, l + 1);
    r = r && consumeTokens(b, 0, SEPARATOR, VALUE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // definition_ | COMMENT
  static boolean definition_item_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definition_item_")) return false;
    boolean r;
    r = definition_(b, l + 1);
    if (!r) r = consumeToken(b, COMMENT);
    return r;
  }

  /* ********************************************************** */
  // definition_item_*
  static boolean definitions_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definitions_")) return false;
    while (true) {
      int c = current_position_(b);
      if (!definition_item_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "definitions_", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // (KEY_KNOWN | KEY_UNKNOWN) PLATFORM?
  static boolean key_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "key_")) return false;
    if (!nextTokenIs(b, "", KEY_KNOWN, KEY_UNKNOWN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = key__0(b, l + 1);
    r = r && key__1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KEY_KNOWN | KEY_UNKNOWN
  private static boolean key__0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "key__0")) return false;
    boolean r;
    r = consumeToken(b, KEY_KNOWN);
    if (!r) r = consumeToken(b, KEY_UNKNOWN);
    return r;
  }

  // PLATFORM?
  private static boolean key__1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "key__1")) return false;
    consumeToken(b, PLATFORM);
    return true;
  }

}
