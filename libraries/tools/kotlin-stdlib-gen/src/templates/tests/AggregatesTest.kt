/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates.tests

import templates.*
import templates.Family.*

object AggregatesTest : TestTemplateGroupBase() {

    val f_minBy = test("minBy()") {
        include(Iterables, Sequences, ArraysOfPrimitives, ArraysOfUnsigned)
    } builder {

        body {
            """
            assertEquals(null, ${collectionOf()}.minBy { it })
            assertEquals(${literal(1)}, ${collectionOf(1)}.minBy { it })
            assertEquals(${literal(2)}, ${collectionOf(3, 2)}.minBy { it * it })
            assertEquals(${literal(3)}, ${collectionOf(3, 2)}.minBy { "a" })
            assertEquals(${literal(2)}, ${collectionOf(3, 2)}.minBy { it.toString() })
            """
        }
        if (primitive?.isUnsigned() != true) {
            bodyAppend {
                """
                assertEquals(${literal(3)}, ${collectionOf(2, 3)}.minBy { -it })
                """
            }
        }

        bodyAppend(ArraysOfPrimitives, PrimitiveType.Long) {
            """
            assertEquals(2000000000000, longArrayOf(3000000000000, 2000000000000).minBy { it + 1 })
            """
        }
        bodyAppend(Iterables, Sequences, ArraysOfObjects) {
            """
            assertEquals('b', $collectionOf('a', 'b').maxBy { "x${"$"}it" })
            assertEquals("abc", $collectionOf("b", "abc").maxBy { it.length })
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Boolean) {
            """
            assertEquals(true, booleanArrayOf(true, false).maxBy { it.toString() })
            assertEquals(false, booleanArrayOf(true, false).maxBy { it.toString().length })
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Char) {
            """
            assertEquals('b', charArrayOf('a', 'b').maxBy { "x${"$"}it" })
            assertEquals('a', charArrayOf('a', 'b').maxBy { "${"$"}it".length })
            """
        }
    }

    val f_minWith = test("minWith()") {
        includeDefault()
        include(ArraysOfUnsigned)
        exclude(PrimitiveType.Boolean, PrimitiveType.Char)
    } builder {

        body {
            """
            assertEquals(null, ${collectionOf()}.minWith(naturalOrder()))
            assertEquals(${literal(1)}, ${collectionOf(1)}.minWith(naturalOrder()))
            assertEquals(${literal(4)}, ${collectionOf(2, 3, 4)}.minWith(compareBy { it % ${literal(4)} }))
            """
        }
        body(ArraysOfObjects) {
            """
            assertEquals(null, arrayOf<Int>().minWith(naturalOrder()) )
            assertEquals("a", arrayOf("a", "B").minWith(STRING_CASE_INSENSITIVE_ORDER))
            """
        }
    }

    val f_foldIndexed = test("foldIndexed()") {
        includeDefault()
        include(ArraysOfUnsigned)
        exclude(PrimitiveType.Boolean, PrimitiveType.Char)
    } builder {

        body {
            val p = primitive?.name ?: "Int"
            """
            expect(${literal(8)}) { ${collectionOf(1, 2, 3)}.foldIndexed(${literal(0)}) { i, acc, e -> acc + i.to$p() * e } }
            expect(10) { ${collectionOf(1, 2, 3)}.foldIndexed(1) { i, acc, e -> acc + i + e.toInt() } }
            expect(${literal(15)}) { ${collectionOf(1, 2, 3)}.foldIndexed(${literal(1)}) { i, acc, e -> acc * (i.to$p() + e) } }
            expect(" 0-${interpolate(1)} 1-${interpolate(2)} 2-${interpolate(3)}") { ${collectionOf(1, 2, 3)}.foldIndexed("") { i, acc, e -> "${"$"}acc ${"$"}i-${"$"}e" } }
            
            expect(${literal(42)}) {
                val numbers = ${collectionOf(1, 2, 3, 4)}
                numbers.foldIndexed(${literal(0)}) { index, a, b -> index.to$p() * (a + b) }
            }
    
            expect(${literal(0)}) {
                val numbers = ${collectionOf()}
                numbers.foldIndexed(${literal(0)}) { index, a, b -> index.to$p() * (a + b) }
            }
    
            expect("${interpolate(1)}${interpolate(1)}${interpolate(2)}${interpolate(3)}${interpolate(4)}") {
                val numbers = ${collectionOf(1, 2, 3, 4)}
                numbers.map { it.toString() }.foldIndexed("") { index, a, b -> if (index == 0) a + b + b else a + b }
            }
            """
        }
    }

    val f_foldRightIndexed = test("foldRightIndexed()") {
        include(Lists, ArraysOfObjects, ArraysOfPrimitives, ArraysOfUnsigned)
        exclude(PrimitiveType.Boolean, PrimitiveType.Char)
    } builder {

        body {
            val p = primitive?.name ?: "Int"
            """
            expect(${literal(8)}) { ${collectionOf(1, 2, 3)}.foldRightIndexed(${literal(0)}) { i, e, acc -> acc + i.to$p() * e } }
            expect(10) { ${collectionOf(1, 2, 3)}.foldRightIndexed(1) { i, e, acc -> acc + i + e.toInt() } }
            expect(${literal(15)}) { ${collectionOf(1, 2, 3)}.foldRightIndexed(${literal(1)}) { i, e, acc -> acc * (i.to$p() + e) } }
            expect(" 2-${interpolate(3)} 1-${interpolate(2)} 0-${interpolate(1)}") { ${collectionOf(1, 2, 3)}.foldRightIndexed("") { i, e, acc -> "${"$"}acc ${"$"}i-${"$"}e" } }
            
            expect("${interpolate(1)}${interpolate(2)}${interpolate(3)}${interpolate(4)}3210") {
                val numbers = ${collectionOf(1, 2, 3, 4)}
                numbers.map { it.toString() }.foldRightIndexed("") { index, a, b -> a + b + index }
            }
            """
        }
    }
}
