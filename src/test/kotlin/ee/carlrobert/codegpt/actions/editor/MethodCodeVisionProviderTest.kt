package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodCodeVisionProviderTest : BasePlatformTestCase() {
    fun testJavaEntryAnchorsAboveFirstAnnotation() {
        val file = myFixture.configureByText(
            "Sample.java",
            "class Sample {\n  @Deprecated\n  public int sum(int a, int b) { return a + b; }\n}"
        )
        val provider = JavaMethodCodeVisionProvider()
        val entries = provider.computeForEditor(myFixture.editor, file)

        assertEquals(CodeVisionAnchorKind.Top, provider.defaultAnchor)
        assertEquals(1, entries.size)
        assertEquals(file.text.indexOf("@Deprecated"), entries.single().first.startOffset)
        assertTrue(entries.single().second is ClickableTextCodeVisionEntry)
    }

    fun testKotlinEntryAnchorsAboveFirstAnnotation() {
        val file = myFixture.configureByText(
            "Sample.kt",
            "class Sample {\n  @Deprecated(\"old\")\n  fun sum(a: Int, b: Int) = a + b\n}"
        )
        val provider = KotlinMethodCodeVisionProvider()
        val entries = provider.computeForEditor(myFixture.editor, file)

        assertEquals(CodeVisionAnchorKind.Top, provider.defaultAnchor)
        assertEquals(1, entries.size)
        assertEquals(file.text.indexOf("@Deprecated"), entries.single().first.startOffset)
        assertTrue(entries.single().second is ClickableTextCodeVisionEntry)
    }
}
