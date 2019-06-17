/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.binding;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.codegen.context.EnclosedValueDescriptor;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;

public interface CalculatedClosure {
    @NotNull
    ClassDescriptor getClosureClass();

    @Nullable
    ClassDescriptor getCapturedOuterClassDescriptor();

    @Nullable
    KotlinType getCapturedReceiverFromOuterContext();

    @NotNull
    String getCapturedReceiverFieldName(BindingContext bindingContext, LanguageVersionSettings languageVersionSettings);

    @NotNull
    Map<DeclarationDescriptor, EnclosedValueDescriptor> getCaptureVariables();

    @NotNull
    List<Pair<String, Type>> getRecordedFields();

    boolean isSuspend();

    boolean isSuspendLambda();

    boolean isTailCall();
}
