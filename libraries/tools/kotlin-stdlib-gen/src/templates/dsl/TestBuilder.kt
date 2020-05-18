/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates

import templates.Family.*
import java.lang.IllegalArgumentException

private fun getDefaultSourceFile(f: Family): TestSourceFile = when (f) {
    Iterables, Collections, Lists -> TestSourceFile.Collections
    Sequences -> TestSourceFile.Sequences
    Sets -> TestSourceFile.Sets
    Ranges, RangesOfPrimitives, ProgressionsOfPrimitives -> TestSourceFile.Ranges
    ArraysOfObjects, InvariantArraysOfObjects, ArraysOfPrimitives -> TestSourceFile.Arrays
    ArraysOfUnsigned -> TestSourceFile.UArrays
    Maps -> TestSourceFile.Maps
    Strings -> TestSourceFile.Strings
    CharSequences -> TestSourceFile.Strings
    Primitives -> TestSourceFile.Primitives
    Unsigned -> TestSourceFile.Unsigned
    Generic -> TestSourceFile.Misc
}

@TemplateDsl
class TestBuilder(
    val allowedPlatforms: Set<Platform>,
    val target: KotlinTarget,
    var family: Family,
    var sourceFile: TestSourceFile = getDefaultSourceFile(family),
    var primitive: PrimitiveType? = null
) {
    lateinit var name: String   // test name

    var sortingName: String? = null
        get() = field ?: name
        private set

    val f get() = family

    var doc: String? = null; private set

    var body: String? = null; private set
    val annotations: MutableList<String> = mutableListOf()
    val suppressions: MutableList<String> = mutableListOf()

    fun sourceFile(file: TestSourceFile) { sourceFile = file }

    fun name(value: String, notForSorting: Boolean = false) {
        if (notForSorting) sortingName = name
        name = value
    }

    fun annotation(annotation: String) {
        annotations += annotation
    }

    fun suppress(diagnostic: String) {
        suppressions += diagnostic
    }

    fun doc(valueBuilder: DocExtensions.() -> String) {
        doc = valueBuilder(DocExtensions)
    }

    fun specialFor(f: Family, action: () -> Unit) {
        if (family == f)
            action()
    }
    fun specialFor(vararg families: Family, action: () -> Unit) {
        require(families.isNotEmpty())
        if (family in families)
            action()
    }

    fun body(valueBuilder: TestExtensions.() -> String) {
        body = valueBuilder(testExtensions)
    }
    fun body(f: Family, valueBuilder: TestExtensions.() -> String) {
        if (family == f) {
            body(valueBuilder)
        }
    }
    fun body(f: Family, p: PrimitiveType, valueBuilder: TestExtensions.() -> String) {
        if (family == f && primitive == p) {
            body(valueBuilder)
        }
    }
    fun body(vararg families: Family, valueBuilder: TestExtensions.() -> String) {
        if (family in families) {
            body(valueBuilder)
        }
    }


    fun bodyAppend(valueBuilder: TestExtensions.() -> String) {
        body += valueBuilder(testExtensions)
    }
    fun bodyAppend(f: Family, valueBuilder: TestExtensions.() -> String) {
        if (family == f) {
            bodyAppend(valueBuilder)
        }
    }
    fun bodyAppend(f: Family, p: PrimitiveType, valueBuilder: TestExtensions.() -> String) {
        if (family == f && primitive == p) {
            bodyAppend(valueBuilder)
        }
    }
    fun bodyAppend(vararg families: Family, valueBuilder: TestExtensions.() -> String) {
        if (family in families) {
            bodyAppend(valueBuilder)
        }
    }

    fun on(platform: Platform, action: () -> Unit) {
        require(platform in allowedPlatforms) { "Platform $platform is not in the list of allowed platforms $allowedPlatforms" }
        if (target.platform == platform)
            action()
    }

    // toPrimitiveArray
    // runningFold
    // reverseRange
    // sortedTests
    // fill

    fun build(builder: Appendable) {

        fun Appendable.appendIndented(csq: CharSequence): Appendable = append("    ").append(csq)

        annotations.forEach { builder.appendIndented(it.trimIndent()).append('\n') }

        if (suppressions.isNotEmpty()) {
            suppressions.joinTo(builder, separator = ", ", prefix = "@Suppress(", postfix = ")\n") {
                """"$it""""
            }
        }

        builder.appendIndented("@Test\n")

        val testNameSuffix = when (f) {
            ArraysOfPrimitives, ArraysOfUnsigned, ArraysOfObjects, InvariantArraysOfObjects -> (primitive?.name ?: "") + "Array"
            Primitives, Unsigned -> primitive!!.name
            else -> f.name.let { if (it.last() == 's') it.dropLast(1) else it }
        }

        val signature = name.indexOf('(').let { "${name.substring(0 until it)}_$testNameSuffix${name.substring(it)}" }

        builder.appendIndented("fun $signature {")

        val body = (body ?:
        """TODO("Body is not provided")""".also { System.err.println("ERROR: $signature for ${target.fullName}: no body specified for ${family to primitive}") }
                ).trim('\n')
        val indent: Int = body.takeWhile { it == ' ' }.length

        builder.append('\n')
        body.lineSequence().forEach {
            var count = indent
            val line = it.dropWhile { count-- > 0 && it == ' ' }//.renderType()
            if (line.isNotEmpty()) {
                builder.appendIndented("    ").append(line).append("\n")
            }
        }

        builder.appendIndented("}\n\n")
    }

    private val testExtensions = TestExtensions()

    inner class TestExtensions {
        fun collectionOf(vararg values: Int): String {
            return when (family) {
                ArraysOfPrimitives, ArraysOfUnsigned -> "${primitive!!.name.toLowerCase()}ArrayOf(${values.joinToString { literal(it) }})"
                ArraysOfObjects -> "arrayOf<Int>(${values.joinToString()})"
                Iterables, Lists -> "listOf<Int>(${values.joinToString()})"
                Sequences -> "sequenceOf<Int>(${values.joinToString()})"
                else -> throw IllegalArgumentException(family.toString())
            }
        }

        val collectionOf: String
            get() = when (family) {
                ArraysOfPrimitives, ArraysOfUnsigned -> "${primitive!!.name.toLowerCase()}ArrayOf"
                ArraysOfObjects -> "arrayOf"
                Iterables, Lists -> "listOf"
                Sequences -> "sequenceOf"
                else -> throw IllegalArgumentException(family.toString())
            }

        fun literal(value: Int): String {
            return when (primitive) {
                PrimitiveType.Byte, PrimitiveType.Short, PrimitiveType.Int -> "$value"
                PrimitiveType.Long -> "${value}L"
                PrimitiveType.UByte, PrimitiveType.UShort, PrimitiveType.UInt -> "${value}u"
                PrimitiveType.ULong -> "${value}uL"
                PrimitiveType.Float -> "$value.0f"
                PrimitiveType.Double -> "$value.0"
                else -> "$value"
            }
        }

        fun convert(value: Int): String {
            return when (primitive) {
                PrimitiveType.Byte, PrimitiveType.Short, PrimitiveType.UByte, PrimitiveType.UShort -> "$value.to${primitive!!.name}()"
                else -> literal(value)
            }
        }

        fun interpolate(value: Int): String {
            return "${"$"}{${literal(value)}}"
        }


        @UseExperimental(ExperimentalUnsignedTypes::class)
        val PrimitiveType.SIZE_BITS: Int
            get() = when (this) {
                PrimitiveType.Byte -> Byte.SIZE_BITS
                PrimitiveType.Short -> Short.SIZE_BITS
                PrimitiveType.Int -> Int.SIZE_BITS
                PrimitiveType.Long -> Long.SIZE_BITS
                PrimitiveType.Float -> Float.SIZE_BITS
                PrimitiveType.Double -> Double.SIZE_BITS
                PrimitiveType.Boolean -> throw IllegalArgumentException(this.toString())
                PrimitiveType.Char -> Char.SIZE_BITS
                PrimitiveType.UByte -> UByte.SIZE_BITS
                PrimitiveType.UShort -> UShort.SIZE_BITS
                PrimitiveType.UInt -> UInt.SIZE_BITS
                PrimitiveType.ULong -> ULong.SIZE_BITS
            }

        fun PrimitiveType.randomNext(): String = randomNext(range = "")

        fun PrimitiveType.randomNextFrom(from: Int): String =
            if (this == PrimitiveType.Boolean)
                throw IllegalArgumentException(this.toString())
            else if (name.endsWith("Int") || name.endsWith("Long"))
                randomNext(range = "${literal(from)}..$name.MAX_VALUE")
            else if (this == PrimitiveType.Double)
                randomNext(range = "$from.0, $name.MAX_VALUE")
            else if (this == PrimitiveType.Float)
                randomNext(range = "$from.0, $name.MAX_VALUE.toDouble()")
            else
                randomNext(range = "$from..$name.MAX_VALUE.toInt()")

        fun PrimitiveType.randomNextUntil(until: Int): String =
            if (this == PrimitiveType.Boolean)
                throw IllegalArgumentException(this.toString())
            else if (name.endsWith("Int") || name.endsWith("Long"))
                randomNext(range = "$name.MIN_VALUE, ${literal(until)}")
            else if (this == PrimitiveType.Double)
                randomNext(range = "$name.MIN_VALUE, $until.0")
            else if (this == PrimitiveType.Float)
                randomNext(range = "$name.MIN_VALUE.toDouble(), $until.0")
            else
                randomNext(range = "$name.MIN_VALUE.toInt(), $until")

        private fun PrimitiveType.randomNext(range: String): String =
            "Random." + if (name.endsWith("Int") || name.endsWith("Long") || this == PrimitiveType.Double)
                "next$name($range)"
            else if (this == PrimitiveType.Boolean) {
                check(range.isEmpty())
                "nextBoolean()"
            } else if (this == PrimitiveType.Float)
                if (range.isEmpty()) "nextFloat()" else "nextDouble($range).toFloat()"
            else
                "nextInt($range).to$name()"
    }
}

