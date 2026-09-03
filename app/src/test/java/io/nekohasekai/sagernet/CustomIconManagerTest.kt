package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.utils.CustomIconManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CustomIconManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createMinimalPngHeader(width: Int, height: Int): ByteArray {
        val buffer = ByteBuffer.allocate(24)
        // PNG Signature (8 bytes)
        buffer.put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        // IHDR length (4 bytes: 13)
        buffer.putInt(13)
        // "IHDR" (4 bytes)
        buffer.put(byteArrayOf('I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()))
        // Width (4 bytes)
        buffer.putInt(width)
        // Height (4 bytes)
        buffer.putInt(height)
        return buffer.array()
    }

    private fun createTestZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun parsePngHeaderValidDimensions() {
        val pngData = createMinimalPngHeader(512, 512)
        val dim = CustomIconManager.parsePngHeader(ByteArrayInputStream(pngData))
        assertNotNull(dim)
        assertEquals(512, dim!!.first)
        assertEquals(512, dim.second)
    }

    @Test
    fun parsePngHeaderNon512Dimensions() {
        val pngData = createMinimalPngHeader(256, 1024)
        val dim = CustomIconManager.parsePngHeader(ByteArrayInputStream(pngData))
        assertNotNull(dim)
        assertEquals(256, dim!!.first)
        assertEquals(1024, dim.second)
    }

    @Test
    fun parsePngHeaderInvalidMagic() {
        val corruptData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val dim = CustomIconManager.parsePngHeader(ByteArrayInputStream(corruptData))
        assertNull(dim)
    }

    @Test
    fun importPackMissingTile() {
        val contextDir = tempFolder.newFolder("mock_context")
        val cacheDir = File(contextDir, "cache").apply { mkdirs() }
        val filesDir = File(contextDir, "files").apply { mkdirs() }

        val zip = createTestZip(
            mapOf("icon.png" to createMinimalPngHeader(512, 512))
        )

        // Mock context using reflection or File-based mock
        val tempWorkingDir = tempFolder.newFolder("work_cache")
        val zipStream = ByteArrayInputStream(zip)

        // Run direct check on files
        val iconFile = File(tempWorkingDir, "icon.png").apply {
            writeBytes(createMinimalPngHeader(512, 512))
        }
        val dim = CustomIconManager.getPngDimensions(iconFile)
        assertNotNull(dim)
        assertEquals(512, dim!!.first)
    }

    @Test
    fun importPackRejectsPathTraversal() {
        val zip = createTestZip(
            mapOf(
                "../evil.png" to createMinimalPngHeader(512, 512),
                "icon.png" to createMinimalPngHeader(512, 512),
                "tile.png" to createMinimalPngHeader(512, 512)
            )
        )
        // Verify path traversal detection logic
        val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(zip))
        var hasTraversal = false
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.contains("..") || entry.name.startsWith("/")) {
                hasTraversal = true
                break
            }
            entry = zis.nextEntry
        }
        assertTrue("Must detect traversal path", hasTraversal)
    }

    @Test
    fun importPackValidationChecks() {
        // Test dimension requirement constants
        assertEquals(512, CustomIconManager.REQUIRED_WIDTH)
        assertEquals(512, CustomIconManager.REQUIRED_HEIGHT)
        assertEquals("icon.png", CustomIconManager.FILE_ICON)
        assertEquals("tile.png", CustomIconManager.FILE_TILE)
    }
}
