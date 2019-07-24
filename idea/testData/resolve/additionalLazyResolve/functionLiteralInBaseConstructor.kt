package test

open class B(val foo: () -> Unit)

class C : B({

})

//package test
//public open class B defined in test in file dummy.kt
//public constructor B(foo: () -> kotlin.Unit) defined in test.B
//value-parameter foo: () -> kotlin.Unit defined in test.B.<init>
//public final class C : test.B defined in test in file dummy.kt
//public constructor C() defined in test.C