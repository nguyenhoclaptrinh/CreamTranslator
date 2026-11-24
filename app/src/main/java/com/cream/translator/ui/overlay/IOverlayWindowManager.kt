package com.cream.translator.ui.overlay

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.cream.translator.core.model.TranslationResult
import kotlinx.coroutines.CoroutineScope

/**
 * Hợp đồng quản lý các View lớp phủ đè màn hình qua WindowManager.
 */
interface IOverlayWindowManager {
    val isOverlayShowing: Boolean

    fun showTranslationOverlay(
        results: List<TranslationResult>,
        onDismiss: () -> Unit,
    ): Boolean

    fun dismissTranslationOverlay(): Boolean

    /**
     * Hiển thị [RegionSelectorView] toàn màn hình để người dùng kéo chọn vùng dịch.
     *
     * @param scope CoroutineScope dùng để chạy auto-dismiss timer.
     *              Phải là scope gắn với vòng đời Service (serviceScope).
     * @param onRegionSelected Callback khi người dùng kéo đủ vùng hợp lệ (> 20x20px).
     * @param onCancel Callback khi người dùng hủy (tap không kéo, kéo quá nhỏ, timeout 10s).
     */
    fun showRegionSelector(
        scope: CoroutineScope,
        onRegionSelected: (Rect) -> Unit,
        onCancel: () -> Unit,
    ): Boolean

    fun dismissRegionSelector()

    fun getScreenWidth(): Int

    fun getScreenHeight(): Int

    fun safeAddView(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean

    fun safeRemoveView(view: View?): Boolean

    fun safeUpdateViewLayout(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean
}
