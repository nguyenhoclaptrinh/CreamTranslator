package com.cream.translator.ui.overlay

import android.view.View
import android.view.ViewGroup
import com.cream.translator.core.model.TranslationResult

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
