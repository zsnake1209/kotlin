/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.*
import org.jetbrains.uast.test.common.ResolveTestBase
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Test

class KotlinUastAnnotationRefTest : AbstractKotlinUastTest(), ResolveTestBase {
    override fun check(testName: String, file: UFile) {
        file.accept(object : AbstractUastVisitor() {
            override fun afterVisitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                assertNotNull(node.asSourceString(), method)
                for (parameter in method!!.parameterList.parameters) {
                    for (annotation in parameter.annotations) {
                        if (annotation.javaClass.simpleName.contains("KtLightNullabilityAnnotation")) {
                            continue
                        }
                        assertNotNull(annotation.qualifiedName, annotation.nameReferenceElement)
                    }
                }

//                for (parameter in method!!.parameterList.parameters) {
//                    for (annotation in parameter.annotations) {
//                        if (annotation.javaClass.simpleName.contains("KtLightNullabilityAnnotation")) {
//                            continue
//                        }
//                        assertNotNull(annotation.qualifiedName, annotation.toUElementOfType<UAnnotation>()?.resolve())
//                        //assertNotNull(annotation.qualifiedName, annotation.)
//                    }
//                }
                super.afterVisitCallExpression(node)
            }
        })
    }

    @Test fun testAnnotations() = doTest("Annotations")
}
