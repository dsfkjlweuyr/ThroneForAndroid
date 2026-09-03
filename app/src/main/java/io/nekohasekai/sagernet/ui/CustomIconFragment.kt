package io.nekohasekai.sagernet.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutCustomIconBinding
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.CustomIconManager

class CustomIconFragment : NamedFragment(R.layout.layout_custom_icon) {

    private lateinit var binding: LayoutCustomIconBinding
    private var isTileActive = false

    override fun name0(): String = app.getString(R.string.custom_icon)

    private val pickZipPack = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runOnDefaultDispatcher {
                val result = try {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        CustomIconManager.importIconPack(stream, requireContext())
                    } ?: CustomIconManager.ImportResult.Error("无法打开所选文件流")
                } catch (e: Exception) {
                    CustomIconManager.ImportResult.Error(e.message ?: "导入失败")
                }

                onMainDispatcher {
                    when (result) {
                        is CustomIconManager.ImportResult.Success -> {
                            snackbar(getString(R.string.custom_icon_import_success)).show()
                            refreshPreview()
                        }
                        is CustomIconManager.ImportResult.MissingFile -> {
                            snackbar(getString(R.string.custom_icon_error_missing, result.fileName)).show()
                        }
                        is CustomIconManager.ImportResult.InvalidDimension -> {
                            snackbar(
                                getString(
                                    R.string.custom_icon_error_dimension,
                                    result.fileName,
                                    result.width,
                                    result.height
                                )
                            ).show()
                        }
                        is CustomIconManager.ImportResult.NotPng -> {
                            snackbar(getString(R.string.custom_icon_error_not_png, result.fileName)).show()
                        }
                        is CustomIconManager.ImportResult.SecurityError -> {
                            snackbar(getString(R.string.custom_icon_error_security, result.reason)).show()
                        }
                        is CustomIconManager.ImportResult.Error -> {
                            snackbar(result.message).show()
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutCustomIconBinding.bind(view)

        binding.btnImportPack.setOnClickListener {
            // 采用通用选择器，并在代码中严格校验 ZIP
            pickZipPack.launch("*/*")
        }

        binding.btnResetDefault.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.custom_icon_reset)
                .setMessage(R.string.custom_icon_reset_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CustomIconManager.reset(requireContext())
                    snackbar(getString(R.string.custom_icon_reset_success)).show()
                    refreshPreview()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.cardSimulatedTile.setOnClickListener {
            isTileActive = !isTileActive
            updateSimulatedTileUi(isTileActive)
        }

        refreshPreview()
    }

    private fun refreshPreview() {
        val context = requireContext()

        // 1. 应用图标预览：直接显示原图
        val customAppBitmap = CustomIconManager.loadIconBitmap(context)
        if (customAppBitmap != null) {
            binding.ivAppIconPreview.setImageBitmap(customAppBitmap)
        } else {
            binding.ivAppIconPreview.setImageResource(R.mipmap.ic_launcher)
        }

        // 2. 磁贴图标加载（提取 Alpha 蒙版）
        val customTileBitmap = CustomIconManager.loadTileAlphaBitmap(context)
        if (customTileBitmap != null) {
            binding.ivSimulatedTileIcon.setImageBitmap(customTileBitmap)
        } else {
            binding.ivSimulatedTileIcon.setImageResource(R.drawable.ic_throne_tile)
        }

        // 3. 刷新磁贴模拟状态表现
        updateSimulatedTileUi(isTileActive)
    }

    private fun updateSimulatedTileUi(active: Boolean) {
        val context = context ?: return
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val primaryColor = try {
            context.getColorAttr(androidx.appcompat.R.attr.colorPrimary)
        } catch (e: Throwable) {
            Color.parseColor("#1976D2")
        }

        if (active) {
            binding.cardSimulatedTile.setCardBackgroundColor(primaryColor)
            binding.tvSimulatedTileName.setTextColor(Color.WHITE)
            binding.tvSimulatedTileState.setTextColor(Color.WHITE)
            binding.tvSimulatedTileState.setText(R.string.custom_icon_tile_state_active)
            binding.ivSimulatedTileIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            // Inactive 状态：仿 Android 真实 QS Tile 关闭状态
            val inactiveBgColor = if (isNight) Color.parseColor("#2D3038") else Color.parseColor("#E2E2E6")
            val titleTextColor = if (isNight) Color.parseColor("#E3E2E6") else Color.parseColor("#1A1C1E")
            val subtitleTextColor = if (isNight) Color.parseColor("#C4C6D0") else Color.parseColor("#44474E")

            binding.cardSimulatedTile.setCardBackgroundColor(inactiveBgColor)
            binding.tvSimulatedTileName.setTextColor(titleTextColor)
            binding.tvSimulatedTileState.setTextColor(subtitleTextColor)
            binding.tvSimulatedTileState.setText(R.string.custom_icon_tile_state_inactive)
            binding.ivSimulatedTileIcon.imageTintList = ColorStateList.valueOf(titleTextColor)
        }
    }
}
