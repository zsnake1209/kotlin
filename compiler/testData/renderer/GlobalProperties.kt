package rendererTest

public val pub = ""

internal var int: String = ""

val int2: Int = 5

private var private = 5

public val Int.ext: Int
get() {}

@Deprecated("") val deprecatedVal = 5

public val <T> T.extWithTwoUpperBounds: Int where T : CharSequence, T : Number
get() {}

//package rendererTest
//public val pub: kotlin.String defined in rendererTest in file dummy.kt
//internal var int: kotlin.String defined in rendererTest in file dummy.kt
//public val int2: kotlin.Int defined in rendererTest in file dummy.kt
//private var private: kotlin.Int defined in rendererTest in file dummy.kt
//public val kotlin.Int.ext: kotlin.Int defined in rendererTest in file dummy.kt
//public fun kotlin.Int.<get-ext>(): kotlin.Int defined in rendererTest in file dummy.kt
//@kotlin.Deprecated(message = "") public val deprecatedVal: kotlin.Int defined in rendererTest in file dummy.kt
//public val <T : kotlin.CharSequence> T.extWithTwoUpperBounds: kotlin.Int where T : kotlin.Number defined in rendererTest in file dummy.kt
//<T : kotlin.CharSequence & kotlin.Number> defined in rendererTest.extWithTwoUpperBounds
//public fun T.<get-extWithTwoUpperBounds>(): kotlin.Int defined in rendererTest in file dummy.kt
