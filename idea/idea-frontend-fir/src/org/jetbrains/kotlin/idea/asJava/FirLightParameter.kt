/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.asJava

import com.intellij.navigation.NavigationItem
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isNullable
import org.jetbrains.kotlin.fir.types.toConstKind
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.psi.KtParameter

internal abstract class FirLightParameter(containingDeclaration: FirLightMethod) : PsiVariable, NavigationItem,
    KtLightElement<KtParameter, PsiParameter>, KtLightParameter, KtLightElementBase(containingDeclaration) {

    override val clsDelegate: PsiParameter
        get() = invalidAccess()

    override val givenAnnotations: List<KtLightAbstractAnnotation>
        get() = invalidAccess()

    override fun getTypeElement(): PsiTypeElement? = null
    override fun getInitializer(): PsiExpression? = null
    override fun hasInitializer(): Boolean = false
    override fun computeConstantValue(): Any? = null
    override fun getNameIdentifier(): PsiIdentifier? = null

    abstract override fun getName(): String

    @Throws(IncorrectOperationException::class)
    override fun normalizeDeclaration() {
    }

    override fun setName(p0: String): PsiElement = TODO() //cannotModify()

    //KotlinIconProviderService.getInstance().getLightVariableIcon(this, flags)

    override val method: KtLightMethod = containingDeclaration

    override fun getDeclarationScope(): KtLightMethod = method

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is JavaElementVisitor) {
            visitor.visitParameter(this)
        }
    }

    override fun toString(): String = "Fir Light Parameter $name"

    override fun isEquivalentTo(another: PsiElement?): Boolean =
        kotlinOrigin == another || another is FirLightParameterForFirNode && another.kotlinOrigin == kotlinOrigin

    override fun getModifierList(): PsiModifierList = TODO()

    override fun getNavigationElement(): PsiElement = kotlinOrigin ?: method.navigationElement

    override fun getUseScope(): SearchScope = kotlinOrigin?.useScope ?: LocalSearchScope(this)

    override fun isValid() = parent.isValid

    abstract override fun getType(): PsiType

    override fun getContainingFile(): PsiFile = method.containingFile

    override fun getParent(): PsiElement = method.parameterList

    override fun equals(other: Any?): Boolean =
        other is FirLightParameter && other.kotlinOrigin == this.kotlinOrigin

    override fun hashCode(): Int = kotlinOrigin.hashCode()

    abstract override fun isVarArgs(): Boolean
}


internal class FirLightParameterForFirNode(
    parameter: FirValueParameter,
    method: FirLightMethod
) : FirLightParameter(method) {
    // This is greedy realization of UL class.
    // This means that all data that depends on descriptor evaluated in ctor so the descriptor will be released on the end.
    // Be aware to save descriptor in class instance or any depending references

    private val _name: String = parameter.name.identifier
    override fun getName(): String = _name

    private val lazyInitializers = mutableListOf<Lazy<*>>()
    private inline fun <T> getAndAddLazy(crossinline initializer: () -> T): Lazy<T> =
        lazyPub { initializer() }.also { lazyInitializers.add(it) }


    override fun isVarArgs() = false //TODO()
    override fun hasModifierProperty(name: String): Boolean = false //TODO()

    override val kotlinOrigin: KtParameter? = parameter.psi as? KtParameter

    private val _modifiers: Set<String> by getAndAddLazy {
        (parameter as? FirMemberDeclaration)?.computeModifiers(isTopLevel = false) ?: emptySet()
    }

    private val _annotations: List<PsiAnnotation> by getAndAddLazy {
        parameter.computeAnnotations(this, parameter.returnTypeRef.coneType.nullabilityForJava)
    }

    override fun getModifierList(): PsiModifierList = _modifierList
    private val _modifierList: PsiModifierList by getAndAddLazy {
        FirLightClassModifierList(this, _modifiers, _annotations)
    }

    private val _type by getAndAddLazy {
        parameter.returnTypeRef.coneType.asPsiType(parameter.session, TypeMappingMode.DEFAULT, this)
    }

    override fun getType(): PsiType = _type

    init {
        //We should force computations on all lazy delegates to release descriptor on the end of ctor call
        with(lazyInitializers) {
            forEach { it.value }
            clear()
        }
    }
}
