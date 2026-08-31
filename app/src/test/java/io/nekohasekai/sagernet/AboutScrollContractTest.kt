package io.nekohasekai.sagernet

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutScrollContractTest {

    @Test
    fun aboutPageUsesOneVerticalScrollOwnerAcrossCardsAndLicense() {
        val layout = File("src/main/res/layout/layout_about.xml").readText()
        val source = File(
            "src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt"
        ).readText()

        assertTrue(layout.contains("android:id=\"@+id/about_scroll\""))
        assertTrue(layout.contains("android:fillViewport=\"true\""))
        assertTrue(
            layout.substringAfter("android:id=\"@+id/about_fragment_holder\"")
                .substringBefore("/>")
                .contains("android:layout_height=\"wrap_content\"")
        )
        assertTrue(source.contains("view.layoutParams = view.layoutParams.apply"))
        assertTrue(source.contains("findRecyclerView(view)"))
        assertTrue(source.contains("recyclerView.isNestedScrollingEnabled = false"))
        assertTrue(source.contains("height = ViewGroup.LayoutParams.WRAP_CONTENT"))
    }
}
