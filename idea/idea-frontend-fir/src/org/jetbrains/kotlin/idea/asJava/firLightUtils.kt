/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.asJava

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.impl.cache.TypeInfo
import com.intellij.psi.impl.compiled.ClsTypeElementImpl
import com.intellij.psi.impl.compiled.SignatureParsing
import com.intellij.psi.impl.compiled.StubBuildingVisitor
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.jvm.jvmTypeMapper
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.calls.isUnit
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.SpecialNames
import java.text.StringCharacterIterator

internal fun <L : Any> L.invalidAccess(): Nothing =
    error("Cls delegate shouldn't be accessed for fir light classes! Qualified name: ${javaClass.name}")

internal fun ConeKotlinType.asPsiType(
    session: FirSession,
    mode: TypeMappingMode,
    psiContext: PsiElement,
): PsiType {

    if (this is ConeClassErrorType) return PsiType.NULL
    if (this is ConeClassLikeType) {
        val classId = classId
        if (classId != null && classId.shortClassName.asString() == SpecialNames.ANONYMOUS) return PsiType.NULL
    }

    val canonicalSignature = session.jvmTypeMapper.mapType(this, mode).descriptor
    val signature = StringCharacterIterator(canonicalSignature)
    val javaType = SignatureParsing.parseTypeString(signature, StubBuildingVisitor.GUESSING_MAPPER)
    val typeInfo = TypeInfo.fromString(javaType, false)
    val typeText = TypeInfo.createTypeText(typeInfo) ?: return PsiType.NULL

    val typeElement = ClsTypeElementImpl(psiContext, typeText, '\u0000')
    return typeElement.type
}

internal fun FirAnnotatedDeclaration.computeAnnotations(
    parent: PsiElement,
    nullability: ConeNullability = ConeNullability.UNKNOWN
): List<PsiAnnotation> {

    if (nullability == ConeNullability.UNKNOWN && annotations.isEmpty()) return emptyList()

    val nullabilityAnnotation = when (nullability) {
        ConeNullability.NOT_NULL -> NotNull::class.java
        ConeNullability.NULLABLE -> Nullable::class.java
        else -> null
    }?.let {
        FirLightSimpleAnnotation(it.name, parent)
    }

    if (annotations.isEmpty()) {
        return if (nullabilityAnnotation != null) listOf(nullabilityAnnotation) else emptyList()
    }

    val result = mutableListOf<PsiAnnotation>()
    annotations.mapTo(result) {
        FirLightAnnotationForFirNode(it, parent)
    }

    if (nullabilityAnnotation != null) {
        result.add(nullabilityAnnotation)
    }

    return result
}

internal fun FirMemberDeclaration.computeModality(isTopLevel: Boolean): Set<String> {
    val psiModifiers = mutableSetOf<String>()
    if (this !is FirConstructor) {
        val modifier = when (modality) {
            Modality.FINAL -> PsiModifier.FINAL
            Modality.OPEN -> null //PsiModifier.OPEN
            Modality.ABSTRACT -> PsiModifier.ABSTRACT
            Modality.SEALED -> PsiModifier.ABSTRACT
            else -> if (isOverride) PsiModifier.OPEN else null
        }

        if (modifier != null) psiModifiers.add(modifier)

        if (!isTopLevel && this is FirRegularClass && !isInner) psiModifiers.add(PsiModifier.STATIC)
    }
    return psiModifiers
}

internal fun FirMemberDeclaration.computeVisibility(isTopLevel: Boolean): String {
    return when (this.visibility) {
        // Top-level private class has PACKAGE_LOCAL visibility in Java
        // Nested private class has PRIVATE visibility
        Visibilities.Private -> if (isTopLevel) PsiModifier.PACKAGE_LOCAL else PsiModifier.PRIVATE
        Visibilities.Protected -> PsiModifier.PROTECTED
        else -> PsiModifier.PUBLIC
    }
}

internal fun FirMemberDeclaration.computeModifiers(isTopLevel: Boolean): Set<String> =
    computeModality(isTopLevel) + computeVisibility(isTopLevel)

internal val ConeKotlinType.nullabilityForJava: ConeNullability
    get() = if (isConstKind || isUnit) ConeNullability.UNKNOWN else nullability

internal val ConeKotlinType.isConstKind
    get() = (this as? ConeClassLikeType)?.toConstKind() != null
