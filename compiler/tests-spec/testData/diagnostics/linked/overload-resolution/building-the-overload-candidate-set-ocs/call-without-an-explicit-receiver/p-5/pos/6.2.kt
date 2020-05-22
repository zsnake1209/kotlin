// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-401
 * MAIN LINK: overload-resolution, building-the-overload-candidate-set-ocs, call-without-an-explicit-receiver -> paragraph 5 -> sentence 6
 * NUMBER: 2
 * DESCRIPTION:
 */

// FILE: TestCase1.kt
/*
 * TESTCASE NUMBER: 1
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-39073
 */
package testPackCase1

import libCase1.*
import kotlin.text.*

fun case1() {
    <!DEBUG_INFO_CALL("fqName: kotlin.text.Regex.<init>; typeCall: function")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase1
class Regex(pattern: String)


// FILE: TestCase2.kt
/*
 * TESTCASE NUMBER: 2
 * UNEXPECTED BEHAVIOUR
 */
package testPackCase2
import libCase2.*
import kotlin.text.*

fun case2() {
    Regex("")
}

// FILE: Lib.kt
package libCase2
fun Regex(pattern: String) {}


// FILE: TestCase3.kt
/*
 * TESTCASE NUMBER: 3
 * UNEXPECTED BEHAVIOUR
 */
package testPackCase3
import libCase3.*
import kotlin.text.*

fun case3() {
    <!DEBUG_INFO_CALL("")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase3
fun Regex(pattern: String) {}

object Regex {
    operator fun invoke(s: String) {}
}


// FILE: TestCase4.kt
/*
 * TESTCASE NUMBER: 4
 * UNEXPECTED BEHAVIOUR
 */
package testPackCase4
import libCase4.*
import lib1Case4.*
import kotlin.text.*

fun case4() {
    <!DEBUG_INFO_CALL("")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase4
fun Regex(pattern: String) {}

object Regex {
    operator fun invoke(s: String) {}
}

// FILE: Lib1.kt
package lib1Case4

enum class Regex{
    ;

    companion object {
        operator fun invoke(s: String) {}
    }
}


// FILE: TestCase5.kt
/*
 * TESTCASE NUMBER: 5
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-39073
 */
package testPackCase5
import libCase5.*
import lib1Case5.*
import kotlin.text.*

fun case5() {
    <!DEBUG_INFO_CALL("fqName: kotlin.text.Regex.<init>; typeCall: function")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase5
//fun Regex(pattern: String) {}

object Regex {
    operator fun invoke(s: String) {}
}

// FILE: Lib1.kt
package lib1Case5

enum class Regex{
    ;

    companion object {
        operator fun invoke(s: String) {}
    }
}

// FILE: TestCase6.kt
/*
 * TESTCASE NUMBER: 6
 * UNEXPECTED BEHAVIOUR
 */
package testPackCase6
import libCase6.*
import lib1Case6.*
import kotlin.text.*

fun case6() {
    <!DEBUG_INFO_CALL("fqName: libCase6.Regex; typeCall: function")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase6
fun Regex(pattern: String) {}

//object Regex {
//    operator fun invoke(s: String) {}
//}

// FILE: Lib1.kt
package lib1Case6

enum class Regex{
    ;

    companion object {
        operator fun invoke(s: String) {}
    }
}

// FILE: TestCase7.kt
/*
 * TESTCASE NUMBER: 7
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-39073
 */
package testPackCase7
import libCase7.*
import kotlin.text.*

fun case7() {
    <!DEBUG_INFO_CALL("fqName: kotlin.text.Regex.<init>; typeCall: function")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase7

enum class Regex{
    ;

    companion object {
        operator fun invoke(s: String) {}
    }
}


// FILE: TestCase8.kt
/*
 * TESTCASE NUMBER: 8
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-39073
 */
package testPackCase8
import libCase8.*
import lib1Case8.*
import kotlin.text.*

fun case8() {
    <!DEBUG_INFO_CALL("fqName: kotlin.text.Regex.<init>; typeCall: function")!>Regex("")<!>
}

// FILE: Lib.kt
package libCase8
class Regex(pattern: String) {}

// FILE: Lib1.kt
package lib1Case8

enum class Regex{
    ;

    companion object {
        operator fun invoke(s: String) {}
    }
}
