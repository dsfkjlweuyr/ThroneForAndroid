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
        assertTrue(source.contains("findViewById<RecyclerView>(R.id.mal_recyclerview)"))
        assertTrue(source.contains("isNestedScrollingEnabled = false"))
        assertTrue(source.contains("height = ViewGroup.LayoutParams.WRAP_CONTENT"))
        assertTrue(
            source.windowed("override fun onViewCreated".length)
                .count { it == "override fun onViewCreated" } == 2
        )
    }
}
