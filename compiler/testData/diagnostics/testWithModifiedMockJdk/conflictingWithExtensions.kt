// !CHECK_TYPE
// SKIP_TXT

fun String.<!EXTENSION_SHADOWED_BY_MEMBER!>strip<!>() = 1

fun String.transform(<!UNUSED_PARAMETER!>x<!>: (String) -> Int) {}

fun foo(s: String) {
    // java.lang.String::strip returns String, so we are resolved to the extension (see checked return type)
    s.strip().checkType { _<Int>() }

    (s as <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.String<!>).strip().checkType { _<String>() }

    "".transform {
        it.toInt()
    }.checkType { _<Unit>() }

    "".<!DEPRECATION!>transform<!>(java.util.function.Function { x: String ->
        x.toInt()
    }).checkType { _<Int>() }

    ("" as <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.String<!>).transform {
        it.toInt()
    }.checkType { _<Int>() }
}
