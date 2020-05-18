/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates.tests

import templates.*
import templates.Family.*

object ComparablesTest : TestTemplateGroupBase() {

    private val Family.sourceFileComparisons: TestSourceFile
        get() = when (this) {
            Generic, Primitives -> TestSourceFile.Comparisons
            Unsigned -> TestSourceFile.UComparisons
            else -> error(this)
        }


    val f_minOf_2 = test("minOf_2()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(1)}) { minOf(${convert(2)}, ${convert(1)}) }
            expect(${convert(58)}) { minOf(${convert(58)}, ${convert(126)}) }
            expect(${convert(23)}) { minOf(${p.randomNextFrom(23)}, ${convert(23)}) }
            expect($type.MIN_VALUE) { minOf($type.MIN_VALUE, $type.MAX_VALUE) }
            expect($type.MAX_VALUE) { minOf($type.MAX_VALUE, $type.MAX_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                assertEquals(-0.0$f, minOf(0.0$f, -0.0$f))
                assertEquals(-0.0$f, minOf(-0.0$f, 0.0$f))
                assertEquals($type.NEGATIVE_INFINITY, minOf($type.NEGATIVE_INFINITY, $type.POSITIVE_INFINITY))
                """
            }
        }
    }

    val f_minOf_3 = test("minOf_3()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(1)}) { minOf(${convert(2)}, ${convert(1)}, ${convert(3)}) }
            expect(${convert(55)}) { minOf(${convert(58)}, ${convert(126)}, ${convert(55)}) }
            expect(${convert(23)}) { minOf(${p.randomNextFrom(23)}, ${convert(23)}, ${p.randomNextFrom(23)}) }
            expect($type.MAX_VALUE) { minOf($type.MAX_VALUE, $type.MAX_VALUE, $type.MAX_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                expect(${literal(0)}) { minOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}) }
                assertEquals(-0.0$f, minOf(0.0$f, -0.0$f, -0.0$f))
                assertEquals(-0.0$f, minOf(-0.0$f, 0.0$f, 0.0$f))
                assertEquals($type.MIN_VALUE, minOf($type.POSITIVE_INFINITY, $type.MAX_VALUE, $type.MIN_VALUE))
                """
            }
        } else {
            bodyAppend {
                """
                expect($type.MIN_VALUE) { minOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}) }
                """
            }
        }
    }

    val f_minOf_vararg = test("minOf_vararg()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(1)}) { minOf(${convert(2)}, ${convert(1)}, ${convert(3)}, ${convert(10)}) }
            expect(${convert(55)}) { minOf(${convert(58)}, ${convert(126)}, ${convert(55)}, ${convert(87)}) }
            expect(${convert(21)}) { minOf(${p.randomNextFrom(23)}, ${convert(23)}, ${p.randomNextFrom(23)}, ${convert(21)}) }
            expect($type.MAX_VALUE) { minOf($type.MAX_VALUE, $type.MAX_VALUE, $type.MAX_VALUE, $type.MAX_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                expect(${literal(0)}) { minOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}, ${convert(1)}) }
                assertEquals(-0.0$f, minOf(0.0$f, -0.0$f, -0.0$f, 0.0$f))
                assertEquals(-0.0$f, minOf(-0.0$f, 0.0$f, 0.0$f, -0.0$f))
                assertEquals($type.NEGATIVE_INFINITY, minOf($type.POSITIVE_INFINITY, $type.NEGATIVE_INFINITY, $type.MAX_VALUE, $type.MIN_VALUE))
                """
            }
        } else {
            bodyAppend {
                """
                expect($type.MIN_VALUE) { minOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}, ${convert(1)}) }
                """
            }
        }
    }

    val f_maxOf_2 = test("maxOf_2()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(2)}) { maxOf(${convert(2)}, ${convert(1)}) }
            expect(${convert(126)}) { maxOf(${convert(58)}, ${convert(126)}) }
            expect(${convert(23)}) { maxOf(${p.randomNextUntil(23)}, ${convert(23)}) }
            expect($type.MAX_VALUE) { maxOf($type.MIN_VALUE, $type.MAX_VALUE) }
            expect($type.MIN_VALUE) { maxOf($type.MIN_VALUE, $type.MIN_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                assertEquals(0.0$f, maxOf(0.0$f, -0.0$f))
                assertEquals(0.0$f, maxOf(-0.0$f, 0.0$f))
                assertEquals($type.POSITIVE_INFINITY, maxOf($type.NEGATIVE_INFINITY, $type.POSITIVE_INFINITY))
                """
            }
        }
    }

    val f_maxOf_3 = test("maxOf_3()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(3)}) { maxOf(${convert(2)}, ${convert(1)}, ${convert(3)}) }
            expect(${convert(126)}) { maxOf(${convert(58)}, ${convert(126)}, ${convert(55)}) }
            expect(${convert(23)}) { maxOf(${p.randomNextUntil(23)}, ${convert(23)}, ${p.randomNextUntil(23)}) }
            expect($type.MIN_VALUE) { maxOf($type.MIN_VALUE, $type.MIN_VALUE, $type.MIN_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                expect($type.MAX_VALUE) { maxOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}) }
                assertEquals(0.0$f, maxOf(0.0$f, -0.0$f, -0.0$f))
                assertEquals(0.0$f, maxOf(-0.0$f, 0.0$f, 0.0$f))
                assertEquals($type.POSITIVE_INFINITY, maxOf($type.POSITIVE_INFINITY, $type.MAX_VALUE, $type.MIN_VALUE))
                """
            }
        } else {
            bodyAppend {
                """
                expect($type.MAX_VALUE) { maxOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}) }
                """
            }
        }
    }

    val f_maxOf_vararg = test("maxOf_vararg()") {
        //        include(Generic)
        include(Primitives, PrimitiveType.numericPrimitives)
        include(Unsigned)
    } builder {
        sourceFile(f.sourceFileComparisons)

        val p = primitive!!
        val type = p.name

        body {
            """
            expect(${convert(10)}) { maxOf(${convert(2)}, ${convert(1)}, ${convert(3)}, ${convert(10)}) }
            expect(${convert(126)}) { maxOf(${convert(58)}, ${convert(126)}, ${convert(55)}, ${convert(87)}) }
            expect(${convert(23)}) { maxOf(${p.randomNextUntil(23)}, ${convert(23)}, ${p.randomNextUntil(23)}, ${convert(21)}) }
            expect($type.MIN_VALUE) { maxOf($type.MIN_VALUE, $type.MIN_VALUE, $type.MIN_VALUE, $type.MIN_VALUE) }
            """
        }
        if (p.isFloatingPoint()) {
            bodyAppend {
                val f = if (p == PrimitiveType.Float) "f" else ""
                """
                expect($type.MAX_VALUE) { maxOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}, ${convert(1)}) }
                assertEquals(0.0$f, maxOf(0.0$f, -0.0$f, -0.0$f, 0.0$f))
                assertEquals(0.0$f, maxOf(-0.0$f, 0.0$f, 0.0$f, -0.0$f))
                assertEquals($type.POSITIVE_INFINITY, maxOf($type.POSITIVE_INFINITY, $type.NEGATIVE_INFINITY, $type.MAX_VALUE, $type.MIN_VALUE))
                """
            }
        } else {
            bodyAppend {
                """
                expect($type.MAX_VALUE) { maxOf($type.MIN_VALUE, $type.MAX_VALUE, ${convert(0)}, ${convert(1)}) }
                """
            }
        }
    }
}