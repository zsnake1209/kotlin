// !LANGUAGE: +ProperGenericSignatureWithPrimitiveUpperBounds

fun <T : Int> fooInt(x: T) = x
fun <T : String> fooStr(x: T) = x
fun <T : Number> fooNum(x: T) = x
fun <T : Int> fooListInt(xs: List<T>, x: T) = xs
fun <X : Y, Y : Int> fooXY(x: X, y: Y) = x
fun <X : Y, Y : Int> fooListXListY(xs: List<X>, ys: List<Y>) = xs

// 1 signature <T:Ljava/lang/Integer;>\(I\)I
// 1 signature <T:Ljava/lang/String;>\(TT;\)TT;
// 1 signature <T:Ljava/lang/Number;>\(TT;\)TT;
// 1 signature <T:Ljava/lang/Integer;>\(Ljava/util/List<\+TT;>;I\)Ljava/util/List<TT;>;
// 1 signature <X::TY;Y:Ljava/lang/Integer;>\(II\)I
// 1 signature <X::TY;Y:Ljava/lang/Integer;>\(Ljava/util/List<\+TX;>;Ljava/util/List<\+TY;>;\)Ljava/util/List<TX;>;