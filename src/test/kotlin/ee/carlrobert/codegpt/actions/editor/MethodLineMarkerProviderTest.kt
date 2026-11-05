package ee.carlrobert.codegpt.actions.editor

import com.intellij.psi.PsiIdentifier
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtNamedFunction

class MethodLineMarkerProviderTest : BasePlatformTestCase() {
    fun testJavaMarkerOnlyOnMethodName() {
        val file = myFixture.configureByText(
            "Sample.java",
            "class Sample { int sum(int a, int b) { return a + b; } }"
        )
        val markers = PsiTreeUtil.findChildrenOfType(file, PsiIdentifier::class.java)
            .filter { JavaMethodLineMarkerProvider().getLineMarkerInfo(it) != null }
            .map { it.text }

        assertEquals(listOf("sum"), markers)
    }

    fun testKotlinMarkerOnNamedFunction() {
        val file = myFixture.configureByText(
            "Sample.kt",
            "class Sample { fun sum(a: Int, b: Int) = a + b }"
        )
        val function = PsiTreeUtil.findChildOfType(file, KtNamedFunction::class.java)
        assertNotNull(function)
        assertNotNull(KotlinMethodLineMarkerProvider().getLineMarkerInfo(function!!.nameIdentifier!!))
    }
}
