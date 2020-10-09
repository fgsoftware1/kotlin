/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.classes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.PsiClassRenderer
import org.jetbrains.kotlin.asJava.PsiClassRenderer.renderClass
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractFirClassLoadingTest : AbstractUltraLightClassLoadingTest() {

    override fun doTest(testDataPath: String) {

        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        val file = myFixture.addFileToProject(testDataPath, sourceText) as KtFile

        val classFabric = KotlinAsJavaSupport.getInstance(project)
        val lightClasses = UltraLightChecker.allClasses(file).mapNotNull { classFabric.getLightClass(it) }

        checkByJavaFile(testDataPath, lightClasses)
    }
}

abstract class AbstractUltraLightClassLoadingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    protected fun checkByJavaFile(testDataPath: String, lightClasses: List<KtLightClass>) {
        val expectedTextFile = File(testDataPath.replaceFirst("\\.kt\$".toRegex(), ".java"))
        assertTrue(expectedTextFile.exists())

        val extendedTypeRendererOld = PsiClassRenderer.extendedTypeRenderer
        val renderedResult = try {
            PsiClassRenderer.extendedTypeRenderer = testDataPath.endsWith("typeAnnotations.kt")
            lightClasses.joinToString("\n\n") { it.renderClass() }
        } finally {
            PsiClassRenderer.extendedTypeRenderer = extendedTypeRendererOld
        }

        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
    }

    open fun doTest(testDataPath: String) {
        val sourceText = File(testDataPath).readText()
        val file = myFixture.addFileToProject(testDataPath, sourceText) as KtFile

        UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

        val checkByJavaFile = InTextDirectivesUtils.isDirectiveDefined(sourceText, "CHECK_BY_JAVA_FILE")

        val ktClassOrObjects = UltraLightChecker.allClasses(file)

        if (checkByJavaFile) {
            val classFabric = LightClassGenerationSupport.getInstance(project)
            val classList = ktClassOrObjects.mapNotNull { classFabric.createUltraLightClass(it) }
            checkByJavaFile(testDataPath, classList)
            classList.forEach { checkDescriptorsLeak(it) }
        } else {
            for (ktClass in ktClassOrObjects) {
                val ultraLightClass = UltraLightChecker.checkClassEquivalence(ktClass)
                if (ultraLightClass != null) {
                    checkDescriptorsLeak(ultraLightClass)
                }
            }
        }
    }
}
