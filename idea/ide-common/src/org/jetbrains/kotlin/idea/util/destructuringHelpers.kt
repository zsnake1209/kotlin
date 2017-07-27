/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.utils.ifEmpty

fun ClassDescriptor.destructuring(type: KotlinType): List<Pair<Name, KotlinType>>? {

    val typeSubstitutor = TypeSubstitutor.create(type)

    if (this.isData) {

        val constructorDescriptor = this.constructors.find { it.isPrimary } ?: return null
        val substitutedConstructor = constructorDescriptor.substitute(typeSubstitutor) ?: return null

        return substitutedConstructor
                .valueParameters
                .map { parameter -> parameter.name to parameter.type }
                .ifEmpty { return null }
    }

    if (DescriptorUtils.isSubclass(this, builtIns.mapEntry)) {

        val typeSubstitution = typeSubstitutor.substitution
        val memberScope = this.getMemberScope(typeSubstitution)
        val keyProp = memberScope.getContributedVariables(Name.identifier("key"), NoLookupLocation.FROM_IDE).singleOrNull()
        val valueProp = memberScope.getContributedVariables(Name.identifier("value"), NoLookupLocation.FROM_IDE).singleOrNull()
        if (keyProp == null || valueProp == null) return null

        return listOf(
                keyProp.name to keyProp.type,
                valueProp.name to valueProp.type
        )
    }

    // TODO: Allow component functions
    return null
}