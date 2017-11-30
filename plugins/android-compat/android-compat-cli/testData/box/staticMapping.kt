// IGNORE_BACKEND: JS, NATIVE

// FILE: Annotations.kt
package kotlin.annotations.jvm.internal
/**
 * Define Compat class from support-compat library.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Compat(val value: String)

// FILE: View.java
import kotlin.annotations.jvm.internal.Compat;

@Compat("ViewCompat")
public class View {
    public static void noArgs() { throw new RuntimeException("FAIL noArgs"); }
    public static void multipleArgs(String a, String b, String c) { throw new RuntimeException("FAIL noArgs"); }
    public static String FIELD = "FAIL FIELD";
    public static void samAdapter(Runnable r) { throw new RuntimeException("FAIL samAdapter"); }
    public static void boxing(int i) { throw new RuntimeException("boxing"); }
    public static boolean boxingResult() { return false; }
    public static void differentParamType(int i) {}
    public static int differentReturnType() { return 1; }
    public static void vararg(int... i) { throw new RuntimeException("vararg"); }
}

// FILE: ViewCompat.java
public class ViewCompat {
    public static void noArgs() {}
    public static void multipleArgs(String a, String b, String c) {}
    public static String FIELD = "OK";
    public static void samAdapter(Runnable r) {}
    public static void boxing(Integer i) {}
    public static Boolean boxingResult() { return true; }
    public static void differentParamType(long i) { throw new RuntimeException("differentParamType"); }
    public static long differentReturnType() { return 0; }
    public static void vararg(int... i) {}
}

// FILE: SubView.java
public class SubView extends View {
}

// FILE: test.kt
class KtSubView: View() {
    fun useAll() {
        noArgs()
        multipleArgs("", "", "")
        if (FIELD != "OK") throw java.lang.RuntimeException("FAIL use field")
        samAdapter {}
        boxing(1)
        if (!boxingResult()) throw java.lang.RuntimeException("FAIL: use boxingResult")
        differentParamType(0)
        if (differentReturnType() == 0) throw java.lang.RuntimeException("FAIL: use differentReturnType")
        vararg()
    }
}

fun box(): String {
    View.noArgs()
    View.multipleArgs("", "", "")
    if (View.FIELD != "OK") return View.FIELD
    View.samAdapter {}
    SubView.noArgs()
    if (SubView.FIELD != "OK") return "FAIL: SubView.FIELD"
    View.boxing(1)
    if (!View.boxingResult()) return "FAIL: boxingResult"
    View.differentParamType(0)
    if (View.differentReturnType() == 0) return "FAIL: differentReturnType"
    View.vararg()
    KtSubView().useAll()
    return "OK"
}