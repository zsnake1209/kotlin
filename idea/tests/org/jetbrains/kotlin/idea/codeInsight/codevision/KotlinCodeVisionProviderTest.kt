/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionProvider.KotlinCodeVisionSettings

internal class KotlinCodeVisionProviderTest : InlayHintsProviderTestCase() {

    fun testInterfaceImplementations() {
        assertThatActualHintsMatch(
            """
<# block [ 3 Implementations] #>
interface SomeInterface {}
interface SomeOtherInterface : SomeInterface {} // <== (1): interface extension
class SomeClass : SomeInterface { // <== (2): interface implementation
    fun acceptsInterface(param: SomeInterface) {}
    fun main() = acceptsInterface(object : SomeInterface {}) // <== (3): anonymous class instance
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testInterfaceAbstractMethodImplementations() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Implementation] #>
interface SomeInterface {
<# block [     2 Implementations] #>
    fun interfaceMethodA()
}
<# block [ 1 Inheritor] #>
open class SomeClass : SomeInterface {
<# block [     1 Override] #>
    override fun interfaceMethodA() {} // <== (1)
}

class SomeDerivedClass : SomeClass() {
    override fun interfaceMethodA() {} // <== (2)
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testInterfaceMethodsOverrides() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Implementation] #>
interface SomeInterface {
<# block [     1 Override] #>
    fun interfaceMethodA() = 10
}

class SomeClass : SomeInterface {
    override fun interfaceMethodA() = 20 // <== (1)
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testInterfacePropertiesOverrides() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Implementation] #>
interface SomeInterface {
<# block [     1 Override] #>
    open val interfaceProperty: String
}

class SomeClass : SomeInterface {
    override val interfaceProperty: String = "overridden" // <== (1)
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testClassInheritors() {
        assertThatActualHintsMatch(
            """
<# block [ 4 Inheritors] #>
open class SomeClass {
    class NestedDerivedClass: SomeClass() {} <== (1): nested class
}
<# block [ 1 Inheritor] #>
open class DerivedClass : SomeClass {} <== (2): direct derived one
class AnotherDerivedClass : SomeClass {} <== (3): yet another derived one
class DerivedDerivedClass : DerivedClass { <== (): indirect inheritor of SomeClass 
    fun main() {
        val someClassInstance = object : SomeClass() { <== (4): anonymous derived one
            val somethingHere = ""
         }
    }
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testClassFunctionOverrides() {
        assertThatActualHintsMatch(
            """
<# block [ 2 Inheritors] #>
abstract class SomeClass {
<# block [     1 Override] #>
    open fun someFun() = ""
<# block [     2 Implementations] #>
    abstract fun someAbstractFun()
}

class DerivedClassA : SomeClass {
    override fun someFun() = "overridden"
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB : SomeClass {
    override fun someAbstractFun() = "overridden"
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testClassPropertiesOverrides() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Inheritor] #>
abstract class SomeClass {
<# block [     1 Override] #>
    abstract val someAbstractProperty: Int
<# block [     2 Overrides] #>
    open val nonAbstractProperty: Int = 10
    open val notToBeOverriddenProperty: Int = 10
}

<# block [ 1 Inheritor] #>
open class DerivedClassA : SomeClass() {
    override val someAbstractProperty: Int = 5
<# block [     1 Override] #>
    override val nonAbstractProperty: Int = 15 // NOTE that DerivedClassB overrides both getter and setter but counted once
}

class DerivedClassB : DerivedClassA() {
        override var nonAbstractProperty: Int = 15
        get() = 20
        set(value) {field = value / 2}
}
            """.trimIndent(),
            mode = inheritorsEnabled()
        )
    }

    fun testInterfaceUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 5 Usages] #>
interface SomeInterface {}
interface SomeOtherInterface : SomeInterface {} // <== (1): interface extension
class SomeClass : SomeInterface { // <== (2): interface implementation
<# block [     1 Usage] #>
    fun acceptsInterface(param: SomeInterface) {} // <== (3): parameter type
    fun returnsInterface(): SomeInterface {} // <== (4): return type
    fun main() = acceptsInterface(object : SomeInterface {}) // <== (5): anonymous class instance
}
            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testInterfaceMethodUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Usage] #>
interface SomeInterface {
<# block [     3 Usages] #>
    fun someFun(): String
    fun someOtherFun() = someFun() // <== (1): delegation from another interface method 
    val someProperty = someFun() // <== (2): property initializer
}

fun main() {
    val instance = object: SomeInterface {
<# block [         1 Usage] #>
        override fun someFun(): String {} // <== (): used below
    }
    instance.someFun() <== (3): call on an instance
}

            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testInterfacePropertyUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Usage] #>
interface SomeInterface {
<# block [     2 Usages] #>
    val someProperty = "initialized"
    fun someFun() = "it's " + someProperty // <== (1):
}

fun main() {
    val instance = object: SomeInterface {}
    val someString = instance.someProperty // <== (2): call on an instance
            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testClassUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 5 Usages] #>
open class SomeClass {}
class SomeOtherClass : SomeClass {} // <== (1): class extension
class SomeYetOtherClass : SomeClass { // <== (2): class extension
<# block [     1 Usage] #>
    fun acceptsClass(param: SomeClass) {} // <== (3): parameter type
    fun returnsInterface(): SomeClass {} // <== (4): return type
    fun main() = acceptsClass(object : SomeClass {}) // <== (5): anonymous class instance
}
            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testClassMethodUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Usage] #>
abstract class SomeClass {
<# block [     3 Usages] #>
    abstract fun someFun(): String
    fun someOtherFun() = someFun() // <== (1): delegation from another method 
    val someProperty = someFun() // <== (2): property initializer
}

fun main() {
    val instance = object: SomeClass {
<# block [         1 Usage] #>
        override fun someFun(): String {} // <== (): used below
    }
    instance.someFun() <== (3): call on an instance
}

            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testClassPropertyUsages() {
        assertThatActualHintsMatch(
            """
<# block [ 1 Usage] #>
interface SomeClass {
<# block [     3 Usages] #>
    var someProperty = "initialized"
    fun someFun() = "it's " + someProperty // <== (1): reference from expression
}

fun main() {
    val instance = object: SomeClass {}
    val someString = instance.someProperty // <== (2): getter call
    instance.someProperty = "anotherValue" // <== (3): setter call
            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testGlobalFunctionUsages() {
        assertThatActualHintsMatch(
            """
<# block [             3 Usages] #>
            fun function(param: String): Int = 1
<# block [             1 Usage] #>
            fun higherOrderFun(s: String, param: (String) -> Int) = param(s)

            fun main() {
                function("someString")
                val functionRef = ::function
                higherOrderFun("someString", ::function)
            }
            """.trimIndent(),
            mode = usagesEnabled()
        )
    }

    fun testUsagesAndInheritanceTogether() {
        assertThatActualHintsMatch(
            """
<# block [ 4 Usages   4 Inheritors] #>
open class SomeClass {
    class NestedDerivedClass: SomeClass() {} <== (1): nested class
}
<# block [ 1 Usage   1 Inheritor] #>
open class DerivedClass : SomeClass {} <== (2): direct derived one
class AnotherDerivedClass : SomeClass {} <== (3): yet another derived one
class DerivedDerivedClass : DerivedClass { <== (): indirect inheritor of SomeClass 
    fun main() {
        val someClassInstance = object : SomeClass() { <== (4): anonymous derived one
            val somethingHere = ""
         }
    }
}
            """.trimIndent(),
            mode = usagesAndInheritorsEnabled()
        )
    }

    fun testTooManyUsagesAndInheritors() {
        assertThatActualHintsMatch(
            """
<# block [ 3+ Usages   2+ Inheritors] #>
open class SomeClass {
    class NestedDerivedClass: SomeClass() {} <== (1): nested class
}
<# block [ 1 Usage   1 Inheritor] #>
open class DerivedClass : SomeClass {} <== (2): direct derived one
class AnotherDerivedClass : SomeClass {} <== (3): yet another derived one
class DerivedDerivedClass : DerivedClass { <== (): indirect inheritor of SomeClass 
    fun main() {
        val someClassInstance = object : SomeClass() { <== (4): anonymous derived one
            val somethingHere = ""
         }
    }
}
            """.trimIndent(),
            mode = usagesAndInheritorsEnabled(), usagesLimit = 3, inheritorsLimit = 2
        )

    }

    private fun assertThatActualHintsMatch(
        text: String, mode: KotlinCodeVisionSettings, usagesLimit: Int = 100, inheritorsLimit: Int = 100
    ) {
        val codeVisionProvider = KotlinCodeVisionProvider()
        codeVisionProvider.usagesLimit = usagesLimit
        codeVisionProvider.inheritorsLimit = inheritorsLimit
        testProvider("kotlinCodeVision.kt", text, codeVisionProvider, mode)
    }

    private fun usagesAndInheritorsEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = true, showInheritors = true)

    private fun inheritorsEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = false, showInheritors = true)

    private fun usagesEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = true, showInheritors = false)
}
