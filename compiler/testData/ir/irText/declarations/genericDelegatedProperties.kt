operator fun Int.getValue(thisRef: Any?, kProp: Any?) = this
operator fun Int.setValue(thisRef: Any?, kProp: Any?, newValue: Int) {}

val <T> T.testTopLevelVal: Int by 1

var <T> T.testTopLevelVar: Int by 1

class C<X> {
    val <T> T.testClassVal: Int by 1

    var <T> T.testClassVar: Int by 1

    inner class D<Y> {
        val <T> T.testInnerClassVal: Int by 1

        var <T> T.testInnerClassVar: Int by 1
    }
}
