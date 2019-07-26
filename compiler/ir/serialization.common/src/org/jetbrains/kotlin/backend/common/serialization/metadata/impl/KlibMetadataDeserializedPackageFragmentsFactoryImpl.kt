package org.jetbrains.kotlin.backend.common.serialization.metadata.impl

import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataDeserializedPackageFragmentsFactory
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataPackageFragment
import org.jetbrains.kotlin.backend.common.serialization.metadata.PackageAccessedHandler
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.storage.StorageManager

object KlibMetadataDeserializedPackageFragmentsFactoryImpl: KlibMetadataDeserializedPackageFragmentsFactory {

    override fun createDeserializedPackageFragments(
        library: KotlinLibrary,
        packageFragmentNames: List<String>,
        moduleDescriptor: ModuleDescriptor,
        packageAccessedHandler: PackageAccessedHandler?,
        storageManager: StorageManager
    ) = packageFragmentNames.flatMap {
        val fqName = FqName(it)
        val parts = library.packageMetadataParts(fqName.asString())
        parts.map { partName ->
            KlibMetadataPackageFragment(fqName, library, packageAccessedHandler, storageManager, moduleDescriptor, partName)
        }
    }
}