// !LANGUAGE: -ProperGenericSignatureWithPrimitiveUpperBounds

fun <T : Int> fooInt(x: T) = x
fun <T : String> fooStr(x: T) = x
fun <T : Number> fooNum(x: T) = x

// 1 signature <T:Ljava/lang/Integer;>
// 1 signature <T:Ljava/lang/String;>\(TT;\)TT;
// 1 signature <T:Ljava/lang/Number;>\(TT;\)TT;