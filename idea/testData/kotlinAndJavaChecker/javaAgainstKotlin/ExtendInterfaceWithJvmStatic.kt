package test

interface KotlinInterface {
    companion object {
        @JvmStatic
        fun bar() {

        }

        @JvmStatic
        var foo = "OK"
    }
}


abstract class KotlinClass : KotlinInterface {

}