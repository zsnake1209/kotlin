/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.builder

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.InnerClassSourceStrategy
import com.intellij.psi.impl.compiled.StubBuildingVisitor
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.intellij.util.containers.Stack
import org.jetbrains.kotlin.codegen.AbstractClassBuilder
import org.jetbrains.kotlin.fileClasses.OldPackageFacadeClassUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import kotlin.properties.Delegates

class StubClassBuilder(private val parentStack: Stack<StubElement<*>>, private val fileStub: PsiJavaFileStub) : AbstractClassBuilder() {
    private val parent: StubElement<*> = parentStack.peek()
    private var v: StubBuildingVisitor<*> by Delegates.notNull()
    private var isPackageClass = false
    private var memberIndex = 0


    private val packageInternalNamePrefix: String
        get() {
            val packageName = fileStub.packageName
            return if (packageName.isEmpty()) {
                ""
            } else {
                packageName.replace('.', '/') + "/"
            }
        }

    override fun getVisitor(): ClassVisitor {
        return v.sure {
            "Called before class is defined"
        }
    }

    override fun defineClass(
        origin: PsiElement?,
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String,
        interfaces: Array<String>
    ) {
        v = StubBuildingVisitor(null, EMPTY_STRATEGY, parent, access, calculateShortName(name))

        super.defineClass(origin, version, access, name, signature, superName, interfaces)

        if (origin is KtFile) {
            val packageName = origin.packageFqName
            val packageClassName = OldPackageFacadeClassUtils.getPackageClassName(packageName)

            if (name == packageClassName || name.endsWith("/" + packageClassName)) {
                isPackageClass = true
            }
        }

        if (!isPackageClass) {
            parentStack.push(v.result)
        }

        (v.result as StubBase<*>).putUserData(ClsWrapperStubPsiFactory.ORIGIN, origin.toLightClassOrigin())
    }

    private fun calculateShortName(internalName: String): String? {
        if (parent is PsiJavaFileStub) {
            assert(parent === fileStub)
            val packagePrefix = packageInternalNamePrefix
            assert(internalName.startsWith(packagePrefix)) { internalName + " : " + packagePrefix }
            return internalName.substring(packagePrefix.length)
        }
        if (parent is PsiClassStub<*>) {
            val parentPrefix = getClassInternalNamePrefix(parent) ?: return null

            assert(internalName.startsWith(parentPrefix)) { internalName + " : " + parentPrefix }
            return internalName.substring(parentPrefix.length)
        }
        return null
    }

    private fun getClassInternalNamePrefix(classStub: PsiClassStub<*>): String? {
        val packageName = fileStub.packageName

        val classStubQualifiedName = classStub.qualifiedName ?: return null

        return if (packageName.isEmpty()) {
            classStubQualifiedName.replace('.', '$') + "$"
        } else {
            packageName.replace('.', '/') + "/" + classStubQualifiedName.substring(packageName.length + 1).replace('.', '$') + "$"
        }
    }

    override fun newMethod(
        origin: JvmDeclarationOrigin,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor {
        val internalVisitor = super.newMethod(origin, access, name, desc, signature, exceptions)

        if (internalVisitor !== AbstractClassBuilder.EMPTY_METHOD_VISITOR) {
            // If stub for method generated
            markLastChild(origin)
        }

        return internalVisitor
    }

    override fun newField(
        origin: JvmDeclarationOrigin,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        val internalVisitor = super.newField(origin, access, name, desc, signature, value)

        if (internalVisitor !== AbstractClassBuilder.EMPTY_FIELD_VISITOR) {
            // If stub for field generated
            markLastChild(origin)
        }

        return internalVisitor
    }

    private fun markLastChild(origin: JvmDeclarationOrigin) {
        val children = v.result.childrenStubs
        val last = children[children.size - 1] as StubBase<*>

        val oldOrigin = last.getUserData(ClsWrapperStubPsiFactory.ORIGIN)
        if (oldOrigin != null) {
            val originalElement = oldOrigin.originalElement
            throw IllegalStateException(
                "Rewriting origin element: " +
                        originalElement?.text + " for stub " + last.toString()
            )
        }

        last.putUserData(ClsWrapperStubPsiFactory.ORIGIN, origin.toLightMemberOrigin())
        last.putUserData(MemberIndex.KEY, MemberIndex(memberIndex++))
    }

    override fun done() {
        if (!isPackageClass) {
            val pop = parentStack.pop()
            assert(pop === v.result) { "parentStack: got " + pop + ", expected " + v.result }
        }
        super.done()
    }

    companion object {
        private val EMPTY_STRATEGY = object : InnerClassSourceStrategy<Any> {
            override fun findInnerClass(s: String, o: Any): Any? {
                return null
            }

            override fun accept(innerClass: Any, visitor: StubBuildingVisitor<Any>) {
                throw UnsupportedOperationException("Shall not be called!")
            }
        }
    }
}
