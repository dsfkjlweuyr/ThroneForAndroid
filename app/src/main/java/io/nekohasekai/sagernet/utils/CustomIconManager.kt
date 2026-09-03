package io.nekohasekai.sagernet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import io.nekohasekai.sagernet.ktx.app
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object CustomIconManager {

    const val FILE_ICON = "icon.png"
    const val FILE_TILE = "tile.png"
    const val REQUIRED_WIDTH = 512
    const val REQUIRED_HEIGHT = 512

    sealed class ImportResult {
        object Success : ImportResult()
        data class MissingFile(val fileName: String) : ImportResult()
        data class InvalidDimension(val fileName: String, val width: Int, val height: Int) : ImportResult()
        data class NotPng(val fileName: String) : ImportResult()
        data class SecurityError(val reason: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    private fun getIconDir(context: Context = app): File {
        return context.filesDir.resolve("custom_icon").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getIconFile(context: Context = app): File = getIconDir(context).resolve(FILE_ICON)
    fun getTileFile(context: Context = app): File = getIconDir(context).resolve(FILE_TILE)
    private fun getAppliedFile(context: Context = app): File = getIconDir(context).resolve("tile_applied")

    fun hasCustomIcon(context: Context = app): Boolean = getIconFile(context).exists()
    fun hasCustomTile(context: Context = app): Boolean = getTileFile(context).exists()
    fun isCustomActive(context: Context = app): Boolean = hasCustomIcon(context) && hasCustomTile(context)

    fun isTileApplied(context: Context = app): Boolean = getAppliedFile(context).exists() && hasCustomTile(context)

    fun setTileApplied(context: Context = app, applied: Boolean) {
        val marker = getAppliedFile(context)
        if (applied) {
            if (!marker.exists()) {
                try {
                    marker.createNewFile()
                } catch (e: Throwable) {
                    // ignore
                }
            }
        } else {
            if (marker.exists()) {
                marker.delete()
            }
        }
    }

    /**
     * 重置恢复默认图标
     */
    fun reset(context: Context = app): Boolean {
        var success = true
        setTileApplied(context, false)
        val iconFile = getIconFile(context)
        if (iconFile.exists() && !iconFile.delete()) success = false
        val tileFile = getTileFile(context)
        if (tileFile.exists() && !tileFile.delete()) success = false
        return success
    }

    /**
     * 校验并解压安装 ZIP 图标包
     */
    fun importIconPack(inputStream: InputStream, context: Context = app): ImportResult {
        val tempDir = File(context.cacheDir, "temp_icon_pack_${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) {
            return ImportResult.Error("无法创建临时缓存目录")
        }

        try {
            val extractedFiles = mutableSetOf<String>()
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // 安全校验：防止 Zip 路径穿越漏洞
                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                        return ImportResult.SecurityError("压缩包包含不安全的路径: $entryName")
                    }

                    // 规范化文件名，只接受根目录或单级文件中的 icon.png 与 tile.png
                    val fileName = File(entryName).name.lowercase()
                    if (fileName == FILE_ICON || fileName == FILE_TILE) {
                        val targetFile = File(tempDir, fileName)
                        targetFile.outputStream().use { os ->
                            zis.copyTo(os)
                        }
                        extractedFiles.add(fileName)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!extractedFiles.contains(FILE_ICON)) {
                return ImportResult.MissingFile(FILE_ICON)
            }
            if (!extractedFiles.contains(FILE_TILE)) {
                return ImportResult.MissingFile(FILE_TILE)
            }

            val tempIcon = File(tempDir, FILE_ICON)
            val tempTile = File(tempDir, FILE_TILE)

            // 尺寸与格式校验
            val iconDim = getPngDimensions(tempIcon) ?: return ImportResult.NotPng(FILE_ICON)
            if (iconDim.first != REQUIRED_WIDTH || iconDim.second != REQUIRED_HEIGHT) {
                return ImportResult.InvalidDimension(FILE_ICON, iconDim.first, iconDim.second)
            }

            val tileDim = getPngDimensions(tempTile) ?: return ImportResult.NotPng(FILE_TILE)
            if (tileDim.first != REQUIRED_WIDTH || tileDim.second != REQUIRED_HEIGHT) {
                return ImportResult.InvalidDimension(FILE_TILE, tileDim.first, tileDim.second)
            }

            // 全部校验成功，原子覆盖保存到应用私有目录
            val targetIcon = getIconFile(context)
            val targetTile = getTileFile(context)

            tempIcon.copyTo(targetIcon, overwrite = true)
            tempTile.copyTo(targetTile, overwrite = true)

            // 导入后仅在本地预览，不自动生效 tile，等待用户显式应用
            setTileApplied(context, false)

            return ImportResult.Success
        } catch (e: Exception) {
            return ImportResult.Error(e.message ?: "解压失败")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * 读取 PNG 文件头获取分辨率，避免完整载入非 512x512 大图造成内存抖动。
     * 也保证在无 Android Runtime (如单元测试环境) 下正常运行。
     */
    fun getPngDimensions(file: File): Pair<Int, Int>? {
        if (!file.exists() || file.length() < 24) return null
        return try {
            file.inputStream().use { parsePngHeader(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun parsePngHeader(inputStream: InputStream): Pair<Int, Int>? {
        val header = ByteArray(24)
        var readTotal = 0
        while (readTotal < 24) {
            val r = inputStream.read(header, readTotal, 24 - readTotal)
            if (r == -1) break
            readTotal += r
        }
        if (readTotal < 24) return null

        // 校验 PNG 魔数: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        val isPng = header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() &&
                header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() &&
                header[7] == 0x0A.toByte()
        if (!isPng) return null

        // IHDR chunk: 12-15 字节是 "IHDR" (0x49 0x48 0x44 0x52)
        val isIhdr = header[12] == 0x49.toByte() &&
                header[13] == 0x48.toByte() &&
                header[14] == 0x44.toByte() &&
                header[15] == 0x52.toByte()
        if (!isIhdr) return null

        // 宽和高分别位于 16-19 与 20-23 (32 位大端整数)
        val dis = DataInputStream(ByteArrayInputStream(header, 16, 8))
        val width = dis.readInt()
        val height = dis.readInt()
        return Pair(width, height)
    }

    /**
     * 加载自定义应用图标全彩位图
     */
    fun loadIconBitmap(context: Context = app): Bitmap? {
        val file = getIconFile(context)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 加载磁贴图标并提取单色 Alpha 蒙版（RGB 置为纯白，透明度保持不变）
     */
    fun loadTileAlphaBitmap(context: Context = app): Bitmap? {
        val file = getTileFile(context)
        if (!file.exists()) return null
        val rawBitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Throwable) {
            null
        } ?: return null

        return extractAlphaMask(rawBitmap)
    }

    /**
     * 仅提取 Alpha 通道，生成纯白 (0xFFFFFFFF) + 原透明度的 ARGB_8888 蒙版位图
     */
    fun extractAlphaMask(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val alphaBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val alpha = Color.alpha(pixels[i])
            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        alphaBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return alphaBitmap
    }
}
