/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

/**
 * This annotation marks FIR interfaces which may be used as transformer function results
 * In case some interface is not marked, transformer function returns closest parent marked with this annotation
 */
annotation class BaseTransformedType

/**
 * This annotation is used in FIR interface super-type list
 * It *should* be used if super-type list includes more than one FIR element
 * It marks the single super-type to which visitor should proceed by default
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE)
annotation class VisitedSupertype

/**
 * By default visit methods are being generated only for abstract classes and interfaces
 * This annotation might be used to force generating methods for non-abstract classes
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class VisitedClass
