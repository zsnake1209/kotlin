val <T> T.testTopLevelVal: Int
    get() = 1

var <T> T.testTopLevelVar: Int
    get() = 1
    set(v) {}

class C<X> {
    val <T> T.testClassVal: Int
        get() = 1

    var <T> T.testClassVar: Int
        get() = 1
        set(v) {}

    inner class D<Y> {
        val <T> T.testInnerClassVal: Int
            get() = 1

        var <T> T.testInnerClassVar: Int
            get() = 1
            set(v) {}
    }
}
