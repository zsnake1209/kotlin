// !LANGUAGE: +InlineClasses

@Suppress("INLINE_CLASS_HAS_INAPPLICABLE_PARAMETER_TYPE")
inline class NonNull<T : Any>(val x: T)

@Suppress("INLINE_CLASS_HAS_INAPPLICABLE_PARAMETER_TYPE")
inline class NullableValue<T : Any>(val x: T?)

object Test {
    fun withNotNullPrimitive(a: NonNull<Int>) {}
    fun asNullable(a: NonNull<Int>?) {}

    fun withNotNullForNullableValue(a: NullableValue<Int>) {}
    fun asNullableForNullableValue(a: NullableValue<Int>?) {}
}

// method: Test::withNotNullPrimitive-lbfhYF8
// jvm signature: (Ljava/lang/Object;)V
// generic signature: null

// method: Test::asNullable-mH0Yc75
// jvm signature: (Ljava/lang/Object;)V
// generic signature: null

// method: Test::withNotNullForNullableValue-pMSpPVd
// jvm signature: (Ljava/lang/Object;)V
// generic signature: null

// method: Test::asNullableForNullableValue-ZGSVM5c
// jvm signature: (LNullableValue;)V
// generic signature: (LNullableValue<Ljava/lang/Integer;>;)V