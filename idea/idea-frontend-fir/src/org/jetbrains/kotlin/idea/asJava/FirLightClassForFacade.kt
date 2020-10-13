/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.asJava

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light.LightEmptyImplementsList
import com.intellij.psi.impl.light.LightModifierList
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.classes.*
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.types.ConeNullability
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.fir.low.level.api.api.LowLevelFirApiFacade
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import javax.swing.Icon

class FirLightClassForFacade(
    manager: PsiManager,
    private val facadeClassFqName: FqName,
    private val files: Collection<KtFile>
) : FirLightClassBase(manager) {

    override val clsDelegate: PsiClass get() = invalidAccess()

    private val isMultiFileClass: Boolean by lazyPub {
        files.size > 1 || files.any {
            JvmFileClassUtil.findAnnotationEntryOnFileNoResolve(
                file = it,
                shortName = JvmFileClassUtil.JVM_MULTIFILE_CLASS_SHORT
            ) != null
        }
    }

    private val _modifierList: PsiModifierList by lazyPub {
        if (isMultiFileClass)
            return@lazyPub LightModifierList(manager, KotlinLanguage.INSTANCE, PsiModifier.PUBLIC, PsiModifier.FINAL)

        val modifiers = setOf(PsiModifier.PUBLIC, PsiModifier.FINAL)

        val annotations = files.flatMap { file ->
            file.withFir<FirFile, List<PsiAnnotation>> {
                computeAnnotations(
                    parent = this@FirLightClassForFacade,
                    nullability = ConeNullability.UNKNOWN,
                    annotationUseSiteTarget = AnnotationUseSiteTarget.FILE
                )
            }
        }

        FirLightClassModifierList(this@FirLightClassForFacade, modifiers, annotations)
    }

    override fun getModifierList(): PsiModifierList = _modifierList

    override fun getScope(): PsiElement? = parent

    private inline fun <reified T : FirDeclaration, K> KtFile.withFir(body: T.() -> K): K {
        val resolveState = LowLevelFirApiFacade.getResolveStateFor(this)
        return LowLevelFirApiFacade.withFirFile(this, resolveState) {
            require(it is T)
            it.body()
        }
    }

    private fun loadMethodsFromFile(
        file: KtFile,
        result: MutableList<KtLightMethod>
    ) {
        file.withFir<FirFile, Unit> {
            createMethods(declarations, isTopLevel = true, result)
        }
    }

    private val _ownMethods: List<KtLightMethod> by lazyPub {
        val result = mutableListOf<KtLightMethod>()
        for (file in files) {
            loadMethodsFromFile(file, result)
        }
        if (!multiFileClass) result else result.filterNot { it.hasModifierProperty(PsiModifier.PRIVATE) }
    }

    private val multiFileClass: Boolean by lazyPub {
        files.any {
            it.withFir<FirFile, Boolean> {
                this.hasAnnotation(JvmFileClassUtil.JVM_MULTIFILE_CLASS.asString())
            }
        }
    }

    private fun loadFieldsFromFile(
        file: KtFile,
        result: MutableList<KtLightField>
    ) {
        file.withFir<FirFile, Unit> {
            val declarationsToProceed =
                if (multiFileClass) declarations.filter { it is FirProperty && it.isConst } else declarations

            createFields(declarationsToProceed, isTopLevel = true, result)
        }
    }

    private val _ownFields: List<KtLightField> by lazyPub {
        val result = mutableListOf<KtLightField>()
        for (file in files) {
            loadFieldsFromFile(file, result)
        }
        result
    }

    override fun getOwnFields() = _ownFields

    override fun getOwnMethods() = _ownMethods

    override fun copy(): FirLightClassForFacade =
        FirLightClassForFacade(manager, facadeClassFqName, files)

    private val firstFileInFacade by lazyPub { files.iterator().next() }

    private val packageFqName: FqName =
        facadeClassFqName.parent()

    private val modifierList: PsiModifierList =
        LightModifierList(manager, KotlinLanguage.INSTANCE, PsiModifier.PUBLIC, PsiModifier.FINAL)

    private val implementsList: LightEmptyImplementsList =
        LightEmptyImplementsList(manager)

    private val packageClsFile = FakeFileForLightClass(
        firstFileInFacade,
        lightClass = { this },
        stub = { null },
        packageFqName = packageFqName
    )

    override fun getParent(): PsiElement = containingFile

    override val kotlinOrigin: KtClassOrObject? get() = null

    val fqName: FqName
        get() = facadeClassFqName

    override fun hasModifierProperty(@NonNls name: String) = modifierList.hasModifierProperty(name)

    override fun getExtendsList(): PsiReferenceList? = null

    override fun isDeprecated() = false

    override fun isInterface() = false

    override fun isAnnotationType() = false

    override fun isEnum() = false

    override fun getContainingClass(): PsiClass? = null

    override fun getContainingFile() = packageClsFile

    override fun hasTypeParameters() = false

    override fun getTypeParameters(): Array<out PsiTypeParameter> = PsiTypeParameter.EMPTY_ARRAY

    override fun getTypeParameterList(): PsiTypeParameterList? = null

    override fun getImplementsList() = implementsList

    override fun getInterfaces(): Array<out PsiClass> = PsiClass.EMPTY_ARRAY

    override fun getInnerClasses(): Array<out PsiClass> = PsiClass.EMPTY_ARRAY

    override fun getOwnInnerClasses(): List<PsiClass> = listOf()

    override fun getAllInnerClasses(): Array<PsiClass> = PsiClass.EMPTY_ARRAY

    override fun getInitializers(): Array<out PsiClassInitializer> = PsiClassInitializer.EMPTY_ARRAY

    override fun findInnerClassByName(@NonNls name: String, checkBases: Boolean): PsiClass? = null

    override fun isInheritorDeep(baseClass: PsiClass?, classToByPass: PsiClass?): Boolean = false

    override fun getLBrace(): PsiElement? = null

    override fun getRBrace(): PsiElement? = null

    override fun getName(): String = facadeClassFqName.shortName().asString()

    override fun setName(name: String): PsiElement? = cannotModify()

    override fun getQualifiedName() = facadeClassFqName.asString()

    override fun getNameIdentifier(): PsiIdentifier? = null

    override fun isValid() = files.all { it.isValid && it.hasTopLevelCallables() && facadeClassFqName == it.javaFileFacadeFqName }

    override fun getNavigationElement() = firstFileInFacade

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return another is KtLightClassForFacade && Comparing.equal(another.qualifiedName, qualifiedName)
    }

    override fun getElementIcon(flags: Int): Icon? = throw UnsupportedOperationException("This should be done by JetIconProvider")

    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean {
        return baseClass.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT
    }

    override fun getSuperClass(): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope)
    }

    override fun getSupers(): Array<PsiClass> =
        superClass?.let { arrayOf(it) } ?: arrayOf()

    override fun getSuperTypes(): Array<PsiClassType> =
        arrayOf(PsiType.getJavaLangObject(manager, resolveScope))

    override fun hashCode() = name.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is FirLightClassForFacade) return false
        if (this::class.java != other::class.java) return false

        if (this === other) return true

        if (this.hashCode() != other.hashCode()) return false
        if (manager != other.manager) return false
        if (facadeClassFqName != other.facadeClassFqName) return false

        return true
    }

    override fun toString() = "${KtLightClassForFacade::class.java.simpleName}:$facadeClassFqName"

    override val originKind: LightClassOriginKind
        get() = LightClassOriginKind.SOURCE

    override fun getText() = firstFileInFacade.text ?: ""

    override fun getTextRange(): TextRange = firstFileInFacade.textRange ?: TextRange.EMPTY_RANGE

    override fun getTextOffset() = firstFileInFacade.textOffset

    override fun getStartOffsetInParent() = firstFileInFacade.startOffsetInParent

    override fun isWritable() = files.all { it.isWritable }

    override fun getVisibleSignatures(): MutableCollection<HierarchicalMethodSignature> = PsiSuperMethodImplUtil.getVisibleSignatures(this)
}