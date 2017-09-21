// WITH_RUNTIME
package x

class A {
    fun old1(b: B): Int = 0
    fun old2(b: B): Int = 0
    fun old3(b: B): Int = 0
    fun old4(b: B): Int = 0

    val oldProperty: Int = 0
}

class B

@ReplacementFor("old1(b)")
fun A.new1(b: B): Int = old1(b)

@ReplacementFor("this.old2(b)")
fun A.new2(b: B): Int = this.old2(b)

@ReplacementFor("a.old3(this)")
fun B.new3(a: A): Int = a.old3(this)

@ReplacementFor("a?.old4(this)")
fun B.new4(a: A?): Int? = a?.old4(this)

@ReplacementFor("a.oldProperty")
fun newProperty(a: A): Int = a.oldProperty

fun foo(a: A?, b: B) {
    val v1 = a?.old1(b)
    val v2 = a?.old2(b)
    val v3 = a?.old3(b)
    a?.old3(b)

    a?.old4(b)
    if (a != null) a.old4(b)

    val v4 = a?.oldProperty
}