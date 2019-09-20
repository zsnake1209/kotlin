// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class DelegateWithNoArguments {
    operator fun getValue() = 42
}

class DelegateWithThisArgumentOnly {
    operator fun getValue(thisRef: Any?) = 42
}

class DelegateWithThisAndPropertyArguments {
    operator fun getValue(thisRef: Any?, kProp: Any?) = 42
}

class Host {
    val test1 by DelegateWithNoArguments()
    val test2 by DelegateWithThisArgumentOnly()
    val test3 by DelegateWithThisAndPropertyArguments()
}

// 1 INVOKESPECIAL kotlin/jvm/internal/PropertyReference1Impl\.<init>
// 1 INVOKESTATIC kotlin/jvm/internal/Reflection.property1
// 1 final static synthetic \[Lkotlin/reflect/KProperty; \$\$delegatedProperties