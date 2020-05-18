/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates.tests

import templates.Family.*
import templates.PrimitiveType
import templates.TestTemplateGroupBase
import templates.builder
import templates.test

object ElementsTest : TestTemplateGroupBase() {

    val f_indexOf = test("indexOf()") {
        include(Iterables, Sequences, ArraysOfObjects, ArraysOfPrimitives, ArraysOfUnsigned, Lists)
    } builder {
        body {
            """
            expect(-1) { ${collectionOf(1, 2, 3)}.indexOf(${literal(0)}) }
            expect(0) { ${collectionOf(1, 2, 3)}.indexOf(${literal(1)}) }
            expect(1) { ${collectionOf(1, 2, 3)}.indexOf(${literal(2)}) }
            expect(2) { ${collectionOf(1, 2, 3)}.indexOf(${literal(3)}) } 
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Char) {
            """
            expect(-1) { charArrayOf('a', 'b', 'c').indexOf('z') }
            expect(0) { charArrayOf('a', 'b', 'c').indexOf('a') }
            expect(1) { charArrayOf('a', 'b', 'c').indexOf('b') }
            expect(2) { charArrayOf('a', 'b', 'c').indexOf('c') } 
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Boolean) {
            """
            expect(0) { booleanArrayOf(true, false).indexOf(true) }
            expect(1) { booleanArrayOf(true, false).indexOf(false) }
            expect(-1) { booleanArrayOf(true).indexOf(false) } 
            """
        }
        bodyAppend(Iterables, Sequences, ArraysOfObjects, Lists) {
            """
            expect(-1) { $collectionOf("cat", "dog", "bird").indexOf("mouse") }
            expect(0) { $collectionOf("cat", "dog", "bird").indexOf("cat") }
            expect(1) { $collectionOf("cat", "dog", "bird").indexOf("dog") }
            expect(2) { $collectionOf("cat", "dog", "bird").indexOf("bird") }
            expect(0) { $collectionOf(null, "dog", null).indexOf(null as String?)}
            """
        }
    }

    val f_indexOfFirst = test("indexOfFirst()") {
        include(Iterables, Sequences, ArraysOfObjects, ArraysOfPrimitives, ArraysOfUnsigned, Lists)
    } builder {
        body {
            """
            expect(-1) { ${collectionOf(1, 2, 3)}.indexOfFirst { it == ${convert(0)} } }
            expect(0) { ${collectionOf(1, 2, 3)}.indexOfFirst { it % ${literal(2)} == ${literal(1)} } }
            expect(1) { ${collectionOf(1, 2, 3)}.indexOfFirst { it % ${literal(2)} == ${literal(0)} } }
            expect(2) { ${collectionOf(1, 2, 3)}.indexOfFirst { it == ${convert(3)} } }
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Char) {
            """
            expect(-1) { charArrayOf('a', 'b', 'c').indexOfFirst { it == 'z' } }
            expect(0) { charArrayOf('a', 'b', 'c').indexOfFirst { it < 'c' } }
            expect(1) { charArrayOf('a', 'b', 'c').indexOfFirst { it > 'a' } }
            expect(2) { charArrayOf('a', 'b', 'c').indexOfFirst { it != 'a' && it != 'b' } } 
            """
        }
        body(ArraysOfPrimitives, PrimitiveType.Boolean) {
            """
            expect(0) { booleanArrayOf(true, false, false, true).indexOfFirst { it } }
            expect(1) { booleanArrayOf(true, false, false, true).indexOfFirst { !it } }
            expect(-1) { booleanArrayOf(true, true).indexOfFirst { !it } } 
            """
        }
        bodyAppend(Iterables, Sequences, ArraysOfObjects, Lists) {
            """
            expect(-1) { $collectionOf("cat", "dog", "bird").indexOfFirst { it.contains("p") } }
            expect(0) { $collectionOf("cat", "dog", "bird").indexOfFirst { it.startsWith('c') } }
            expect(1) { $collectionOf("cat", "dog", "bird").indexOfFirst { it.startsWith('d') } }
            expect(2) { $collectionOf("cat", "dog", "bird").indexOfFirst { it.endsWith('d') } }
            """
        }
    }
}