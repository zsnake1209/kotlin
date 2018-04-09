/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.io.File
import kotlin.system.measureNanoTime

class AllKotlinResolveTest : DaemonAnalyzerTestCase() {

    companion object {
        private val rootProjectFile = File("../testProject").absoluteFile
    }

    override fun setUpProject() {
        myProject = ProjectUtil.openOrImport(rootProjectFile.path, null, false)
    }


    fun DeclarationDescriptor.countErrorTypes(): Long = when (this) {
        is CallableDescriptor -> this.valueParameters.sumByLong { if (it.type.isError) 1 else 0 } + if (this.returnType?.isError == true) 1 else 0
        is ClassifierDescriptor -> this.typeConstructor.supertypes.sumByLong { if (it.isError) 1 else 0 }
        else -> 0
    }

    fun DeclarationDescriptor.countTypes(): Long = when (this) {
        is CallableDescriptor -> this.valueParameters.size + 1L
        is ClassifierDescriptor -> this.typeConstructor.supertypes.size + 1L
        else -> 0
    }


    fun testPerformance() {
        val scope = KotlinSourceFilterScope.projectSources(ProjectScope.getContentScope(project), project)

        var count = 0L
        var eerrorTypes = 0L
        var types = 0L


        val psiManager = PsiManager.getInstance(project)

        val vFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)


        var time = 0L




        vFiles.forEach {

            val ktFile = psiManager.findFile(it) as? KtFile ?: return@forEach

            val topLevelDeclarations = ktFile.declarations

            for (declaration in topLevelDeclarations) {
                time += measureNanoTime {

                    try {
                        val bindingContext = declaration.analyze(BodyResolveMode.PARTIAL)

                        val desc = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration]!!
                        //descriptors as Collection<DeclarationDescriptor>

                        ForceResolveUtil.forceResolveAllContents(desc)
                        count++


                        desc.let {
                            eerrorTypes += it.countErrorTypes()
                            types += it.countTypes()
                            if (it is ClassifierDescriptor) {

                                val contributedDescriptors = it.defaultType.memberScope.getContributedDescriptors()
                                ForceResolveUtil.forceResolveAllContents(contributedDescriptors)
                                contributedDescriptors.forEach {
                                    eerrorTypes += it.countErrorTypes()
                                    types += it.countTypes()
                                }

                                count += contributedDescriptors.size
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }

        println("Total time: ${(time * 1e-6).toLong()} ms")
        println("Total error types: $eerrorTypes")
        println("Total types: $types")
        println("Total descriptors count: $count")
    }
}