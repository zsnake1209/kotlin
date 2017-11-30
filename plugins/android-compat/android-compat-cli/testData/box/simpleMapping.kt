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
    public boolean noArgs() { return false; }
    public boolean subtype() { return false; }
    public boolean subtypeOverride() { return false; }
    public boolean boxing(int i) { return false; }
    public boolean boxingResult() { return false; }
    public boolean vararg(int... ii) { return false; }
    public boolean varargBoxing(int... ii) { return false; }
    public boolean valueVararg(int i) { return true; }
    public <T> boolean generic(T t) { return false; }
    public boolean samAdapter(Runnable r) { return false; }
    public boolean differentParamType(int i) { return true; }
    public boolean differentReturnType() { return true; }
    public boolean subtypeParam(String s) { return true; }
    public boolean multipleParams(int i, long l, double d, String s, char c) { return false; }
    public boolean implicitThisInSubtype() { return false; }
    public boolean getProperty() { return false; }
    public void setProperty(boolean b) { throw new RuntimeException("FAIL setProperty"); }
}

// FILE: SubView.java
import kotlin.annotations.jvm.internal.Compat;

@Compat("SubViewCompat")
public class SubView extends View {
    @Override public boolean subtypeOverride() { return false; } // todo: do we need to compat this?
    public boolean superInCompat() { return true; }
    public boolean implicitThisNotReplaced() { return !noArgs(); }
}

// FILE: AnotherView.java
import kotlin.annotations.jvm.internal.Compat;

@Compat("ViewCompat")
public class AnotherView {
    public boolean inAnotherView() { return false; }
}

// FILE: KtSubView.kt
class KtSubView: View() {
    fun useImplicitThis() = implicitThisInSubtype()
}

// FILE: ViewCompat.java
public class ViewCompat {
    static public boolean noArgs(View v) { return true; }
    static public boolean subtype(View v) { return true; }
    static public boolean subtypeOverride(View v) { return true; }
    static public boolean boxing(View v, Integer i) { return true; }
    static public Boolean boxingResult(View v) { return true; }
    static public boolean vararg(View v, int... ii) { return true; }
    static public boolean varargBoxing(View v, Integer... ii) { return true; }
    static public boolean valueVararg(View v, int... ii) { return false; }
    static public <K> boolean generic(View v, K k) { return true; }
    static public boolean samAdapter(View v, Runnable r) { return true; }
    static public boolean inAnotherView(AnotherView v) { return true; }
    static public boolean differentParamType(View v, long i) { return false; }
    static public int differentReturnType(View v) { return 0; }
    static public boolean subtypeParam(View v, Object s) { return false; }
    static public boolean multipleParams(View v, int i, long l, double d, String s, char c) { return true; }
    static public boolean implicitThisInSubtype(View v) { return true; }
    static public boolean getProperty(View v) { return true; }
    static public void setProperty(View v, boolean b) {}
}

// FILE: SubViewCompat.java
public class SubViewCompat {
    static boolean superInCompat(View v) { return false; }
}

// FILE: Movable.java
import kotlin.annotations.jvm.internal.Compat;

@Compat("MovableCompat")
public interface Movable {
    boolean move();
}

// FILE: MovableImpl.java
public class MovableImpl implements Movable {
    public boolean move() { return false; }
}

// FILE: MovableCompat.java
public class MovableCompat {
    public static boolean move(Movable m) { return true; }
}

// FILE: test.kt
fun box(): String {
    val view = View()
    if (!view.noArgs()) return "FAIL noArgs"
    if (!SubView().subtype()) return "FAIL subtype"
    if (!SubView().subtypeOverride()) return "FAIL subtypeOverride"
    if (!view.boxing(0)) return "FAIL boxing"
    if (!view.boxingResult()) return "FAIL boxingResult"
    if (!view.vararg(0)) return "FAIL vararg"
//    if (!View().varargBoxing(0)) return "FAIL varargBoxing"
    if (!view.valueVararg(0)) return "FAIL valueVararg"
//    if (!View().generic(0)) return "FAIL generic"
    if (!SubView().superInCompat()) return "FAIL superInCompat"
    if (!view.samAdapter {}) return "FAIL samAdapter"
// Interfaces are not supported
//    if (!MovableImpl().move()) return "FAIL move"
    if (!AnotherView().inAnotherView()) return "FAIL inAnotherView"
    if (!view.differentParamType(0)) return "FAIL differentParamType"
    if (!view.differentReturnType()) return "FAIL differentReturnType"
    if (!view.subtypeParam("")) return "FAIL subtypeParam"
    if (!view.multipleParams(0, 0L, .0, "", ' ')) return "FAIL subtypeParam"
    if (!view.run { noArgs() }) return "FAIL run { noArgs() }"
    if (!KtSubView().useImplicitThis()) return "FAIL useImplicitThis"
    if (!SubView().implicitThisNotReplaced()) return "FAIL implicitThisNotReplaced"
    if (!view.property) return "FAIL property"
    view.property = true // should not throw exception
    return "OK"
}