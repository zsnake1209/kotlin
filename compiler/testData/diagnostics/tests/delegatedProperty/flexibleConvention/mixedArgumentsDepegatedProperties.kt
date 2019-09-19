// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class DelegateWithMixedArguments {
    operator fun getValue() = 42
    operator fun setValue(host: Any?, newValue: Int) {}
}

val test by DelegateWithMixedArguments()