/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parsing;

import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl;

public class KotlinLightParser {
    public static FlyweightCapableTreeStructure<LighterASTNode> parse(PsiBuilder builder) {
        LanguageVersionSettings languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT;
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(builder), languageVersionSettings);
        ktParsing.parseFile();

        return builder.getLightTree();
    }

    public static FlyweightCapableTreeStructure<LighterASTNode> parseLambdaExpression(PsiBuilder psiBuilder) {
        LanguageVersionSettings languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT;
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder), languageVersionSettings);
        ktParsing.parseLambdaExpression();
        return psiBuilder.getLightTree();
    }

    public static FlyweightCapableTreeStructure<LighterASTNode> parseBlockExpression(PsiBuilder builder) {
        LanguageVersionSettings languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT;
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(builder), languageVersionSettings);
        ktParsing.parseBlockExpression();
        return builder.getLightTree();
    }
}
