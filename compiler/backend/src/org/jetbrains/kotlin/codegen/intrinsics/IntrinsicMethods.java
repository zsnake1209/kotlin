/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.intrinsics;

import com.google.common.collect.ImmutableList;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.builtins.PrimitiveType;
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.FqNameUnsafe;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType;
import org.jetbrains.kotlin.types.expressions.OperatorConventions;

import static org.jetbrains.kotlin.builtins.KotlinBuiltIns.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

public class IntrinsicMethods<IM extends GeneralIntrinsicMethod>  {
    public static final String INTRINSICS_CLASS_NAME = "kotlin/jvm/internal/Intrinsics";

    private static final FqName KOTLIN_JVM = new FqName("kotlin.jvm");
    /* package */ static final FqNameUnsafe RECEIVER_PARAMETER_FQ_NAME = new FqNameUnsafe("T");

    private static final FqNameUnsafe KOTLIN_ULONG = new FqNameUnsafe("kotlin.ULong");

    private final IntrinsicsList<IM> intrinsicsList;

    private final IntrinsicsMap<IM> intrinsicsMap = new IntrinsicsMap<>();

    public IntrinsicMethods(IntrinsicsList<IM> intrinsicsList) {
        this.intrinsicsList = intrinsicsList;
        intrinsicsMap.registerIntrinsic(KOTLIN_JVM, RECEIVER_PARAMETER_FQ_NAME, "javaClass", -1, intrinsicsList.getJavaClassProperty());
        IM kClassJavaProperty = intrinsicsList.getKClassJavaProperty();
        if (kClassJavaProperty != null) {
            intrinsicsMap.registerIntrinsic(KOTLIN_JVM, KotlinBuiltIns.FQ_NAMES.kClass, "java", -1, intrinsicsList.getKClassJavaProperty());
        }
        IM kClassJavaObjectTypeProperty = intrinsicsList.getKClassJavaObjectTypeProperty();
        if (kClassJavaObjectTypeProperty != null) {
            intrinsicsMap.registerIntrinsic(KOTLIN_JVM, KotlinBuiltIns.FQ_NAMES.kClass, "javaObjectType", -1,
                                            intrinsicsList.getKClassJavaObjectTypeProperty());
        }
        IM kClassJavaPrimitiveTypeProperty = intrinsicsList.getKCLassJavaPrimitiveTypeProperty();
        if (kClassJavaPrimitiveTypeProperty != null) {
            intrinsicsMap.registerIntrinsic(KOTLIN_JVM, KotlinBuiltIns.FQ_NAMES.kClass, "javaPrimitiveType", -1,
                                            intrinsicsList.getKCLassJavaPrimitiveTypeProperty());
        }
        IM kCallableNameProperty = intrinsicsList.getKCallableNameProperty();
        if (kCallableNameProperty != null) {
            intrinsicsMap.registerIntrinsic(KotlinBuiltIns.FQ_NAMES.kCallable.toSafe(), null, "name", -1,
                                            intrinsicsList.getKCallableNameProperty());
        }
        intrinsicsMap.registerIntrinsic(new FqName("kotlin.jvm.internal.unsafe"), null, "monitorEnter", 1, intrinsicsList.getMonitorEnter());
        intrinsicsMap.registerIntrinsic(new FqName("kotlin.jvm.internal.unsafe"), null, "monitorExit", 1, intrinsicsList.getMonitorExit());
        intrinsicsMap.registerIntrinsic(KOTLIN_JVM, KotlinBuiltIns.FQ_NAMES.array, "isArrayOf", 0, intrinsicsList.getCheckIsArrayOf());

        IM lateinitIsInitialized = intrinsicsList.getLateinitIsInitialized();
        if (lateinitIsInitialized != null) {
            intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, KotlinBuiltIns.FQ_NAMES.kProperty0, "isInitialized", -1,
                                            intrinsicsList.getLateinitIsInitialized());
        }

        intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, null, "arrayOf", 1, intrinsicsList.getArrayOf());

        IM mutableMapSet = intrinsicsList.getMutableMapSet();
        if (mutableMapSet != null) {
            intrinsicsMap.registerIntrinsic(new FqName("kotlin.collections"), new FqNameUnsafe("kotlin.collections.MutableMap"), "set", 2,
                                            intrinsicsList.getMutableMapSet());
        }

        ImmutableList<Name> primitiveCastMethods = OperatorConventions.NUMBER_CONVERSIONS.asList();
        for (Name method : primitiveCastMethods) {
            String methodName = method.asString();
            declareIntrinsicFunction(FQ_NAMES.number.toSafe(), methodName, 0, intrinsicsList.getNumberCast());
            for (PrimitiveType type : PrimitiveType.NUMBER_TYPES) {
                declareIntrinsicFunction(type.getTypeFqName(), methodName, 0, intrinsicsList.getNumberCast());
            }
        }

        for (PrimitiveType type : PrimitiveType.NUMBER_TYPES) {
            FqName typeFqName = type.getTypeFqName();
            declareIntrinsicFunction(typeFqName, "plus", 0, intrinsicsList.getUnaryPlus());
            declareIntrinsicFunction(typeFqName, "unaryPlus", 0, intrinsicsList.getUnaryPlus());
            declareIntrinsicFunction(typeFqName, "minus", 0, intrinsicsList.getUnaryMinus());
            declareIntrinsicFunction(typeFqName, "unaryMinus", 0, intrinsicsList.getUnaryMinus());
            declareIntrinsicFunction(typeFqName, "inv", 0, intrinsicsList.getInv());
            declareIntrinsicFunction(typeFqName, "rangeTo", 1, intrinsicsList.getRangeTo());
            declareIntrinsicFunction(typeFqName, "inc", 0, intrinsicsList.incrementIntrinsic(1));
            declareIntrinsicFunction(typeFqName, "dec", 0, intrinsicsList.incrementIntrinsic(-1));
        }

        for (PrimitiveType type : PrimitiveType.values()) {
            FqName typeFqName = type.getTypeFqName();

            declareIntrinsicFunction(typeFqName, "equals", 1, intrinsicsList.equalsIntrinsic(type));
            declareIntrinsicFunction(typeFqName, "hashCode", 0, intrinsicsList.getHashCode());
            declareIntrinsicFunction(typeFqName, "toString", 0, intrinsicsList.getToString());

            intrinsicsMap.registerIntrinsic(
                    BUILT_INS_PACKAGE_FQ_NAME, null, StringsKt.decapitalize(type.getArrayTypeName().asString()) + "Of", 1, intrinsicsList.getArrayOf()
            );
        }

        declareBinaryOp("plus", IADD);
        declareBinaryOp("minus", ISUB);
        declareBinaryOp("times", IMUL);
        declareBinaryOp("div", IDIV);
        declareBinaryOp("mod", IREM);
        declareBinaryOp("rem", IREM);
        declareBinaryOp("shl", ISHL);
        declareBinaryOp("shr", ISHR);
        declareBinaryOp("ushr", IUSHR);
        declareBinaryOp("and", IAND);
        declareBinaryOp("or", IOR);
        declareBinaryOp("xor", IXOR);

        declareIntrinsicFunction(FQ_NAMES._boolean, "not", 0, intrinsicsList.getNot());

        declareIntrinsicFunction(FQ_NAMES.string, "plus", 1, intrinsicsList.getStringConcat());
        declareIntrinsicFunction(FQ_NAMES.string, "get", 1, intrinsicsList.getStringGetChar());

        IM trimMargin = intrinsicsList.getStringTrimMargin();
        if (trimMargin != null) {
            intrinsicsMap.registerIntrinsic(TEXT_PACKAGE_FQ_NAME, FQ_NAMES.string, "trimMargin", 1, trimMargin);
        }
        IM trimIndent = intrinsicsList.getStringTrimIndent();
        if (trimIndent != null) {
            intrinsicsMap.registerIntrinsic(TEXT_PACKAGE_FQ_NAME, FQ_NAMES.string, "trimIndent", 0, intrinsicsList.getStringTrimIndent());
        }

        declareIntrinsicFunction(FQ_NAMES.cloneable.toSafe(), "clone", 0, intrinsicsList.getClone());

        intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, KotlinBuiltIns.FQ_NAMES.any, "toString", 0, intrinsicsList.getToString());
        intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, KotlinBuiltIns.FQ_NAMES.string, "plus", 1, intrinsicsList.getStringPlus());
        intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, null, "arrayOfNulls", 1, intrinsicsList.getArrayOfNulls());

        for (PrimitiveType type : PrimitiveType.values()) {
            declareIntrinsicFunction(type.getTypeFqName(), "compareTo", 1, intrinsicsList.getCompareTo());
            declareIntrinsicFunction(COLLECTIONS_PACKAGE_FQ_NAME.child(Name.identifier(type.getTypeName().asString() + "Iterator")), "next", 0, intrinsicsList.getIteratorNext());
        }

        declareArrayMethods();

        IM java8ULongDivide = intrinsicsList.getJava8UlongDivide();
        if (java8ULongDivide != null) {
            intrinsicsMap.registerIntrinsic(KOTLIN_ULONG.toSafe(), null, "div", 1, java8ULongDivide);
            intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, null, "ulongDivide", 2, java8ULongDivide);
        }
        IM java8ULongRemainder = intrinsicsList.getJava8UlongRemainder();
        if (java8ULongRemainder != null) {
            intrinsicsMap.registerIntrinsic(KOTLIN_ULONG.toSafe(), null, "rem", 1, java8ULongRemainder);
            intrinsicsMap.registerIntrinsic(BUILT_INS_PACKAGE_FQ_NAME, null, "ulongRemainder", 2, java8ULongRemainder);
        }
    }

    private void declareArrayMethods() {
        for (JvmPrimitiveType jvmPrimitiveType : JvmPrimitiveType.values()) {
            declareArrayMethods(jvmPrimitiveType.getPrimitiveType().getArrayTypeFqName());
        }
        declareArrayMethods(FQ_NAMES.array.toSafe());
    }

    private void declareArrayMethods(@NotNull FqName arrayTypeFqName) {
        declareIntrinsicFunction(arrayTypeFqName, "size", -1, intrinsicsList.getArraySize());
        declareIntrinsicFunction(arrayTypeFqName, "set", 2, intrinsicsList.getArraySet());
        declareIntrinsicFunction(arrayTypeFqName, "get", 1, intrinsicsList.getArrayGet());
        declareIntrinsicFunction(arrayTypeFqName, "clone", 0, intrinsicsList.getClone());
        declareIntrinsicFunction(arrayTypeFqName, "iterator", 0, intrinsicsList.getArrayIterator());
        declareIntrinsicFunction(arrayTypeFqName, "<init>", 2, intrinsicsList.getArrayConstructor());
    }

    private void declareBinaryOp(@NotNull String methodName, int opcode) {
        IM op = intrinsicsList.binaryOpIntrinsic(opcode);
        for (PrimitiveType type : PrimitiveType.values()) {
            declareIntrinsicFunction(type.getTypeFqName(), methodName, 1, op);
        }
    }

    private void declareIntrinsicFunction(
            @NotNull FqName classFqName,
            @NotNull String methodName,
            int arity,
            @NotNull IM implementation
    ) {
        intrinsicsMap.registerIntrinsic(classFqName, null, methodName, arity, implementation);
    }

    private void declareIntrinsicFunction(
            @NotNull FqNameUnsafe classFqName,
            @NotNull String methodName,
            int arity,
            @NotNull IM implementation
    ) {
        intrinsicsMap.registerIntrinsic(classFqName.toSafe(), null, methodName, arity, implementation);
    }

    @Nullable
    public IM getIntrinsic(@NotNull CallableMemberDescriptor descriptor) {
        return intrinsicsMap.getIntrinsic(descriptor);
    }
}
