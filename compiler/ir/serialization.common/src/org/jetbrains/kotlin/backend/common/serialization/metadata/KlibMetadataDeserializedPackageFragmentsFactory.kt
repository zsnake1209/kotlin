package org.jetbrains.kotlin.backend.common.serialization.metadata

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.storage.StorageManager

interface KlibMetadataDeserializedPackageFragmentsFactory {
    fun createDeserializedPackageFragments(
        library: KotlinLibrary,
        packageFragmentNames: List<String>,
        moduleDescriptor: ModuleDescriptor,
        packageAccessedHandler: PackageAccessedHandler?,
        storageManager: StorageManager
    ): List<KlibMetadataPackageFragment>
}