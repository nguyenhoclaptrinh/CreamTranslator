package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.cream.translator.core.model.TranslationResult
import timber.log.Timber

/**
 * Hiện thực quản lý đính kèm, cập nhật và giải phóng view trên WindowManager.
 */
class OverlayWindowManager(
    private val context: Context,
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager,
) : IOverlayWindowManager {
    private var inPlaceOverlay: InPlaceTranslationOverlay? = null
    override var isOverlayShowing: Boolean = false
        private set

    override fun getScreenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            metrics?.bounds?.width() ?: context.resources.displayMetrics.widthPixels
        } else {
            context.resources.displayMetrics.widthPixels
        }

    override fun getScreenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            metrics?.bounds?.height() ?: context.resources.displayMetrics.heightPixels
        } else {
            context.resources.displayMetrics.heightPixels
        }

    override fun showTranslationOverlay(
        results: List<TranslationResult>,
        onDismiss: () -> Unit,
    ): Boolean {
        if (isOverlayShowing) {
            dismissTranslationOverlay()
        }

        if (windowManager == null) return false

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val overlayParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

        val overlayView =
            InPlaceTranslationOverlay(context).apply {
                setResults(results)
                onDismissListener = {
                    dismissTranslationOverlay()
                    onDismiss()
                }
            }

        val added = safeAddView(overlayView, overlayParams)
        return if (added) {
            inPlaceOverlay = overlayView
            isOverlayShowing = true
            true
        } else {
            Timber.e("OverlayWindowManager: Không thể gắn lớp phủ dịch đè lên WindowManager")
            false
        }
    }

    override fun dismissTranslationOverlay(): Boolean {
        return if (isOverlayShowing && inPlaceOverlay != null) {
            val removed = safeRemoveView(inPlaceOverlay)
            inPlaceOverlay = null
            isOverlayShowing = false
            removed
        } else {
            false
        }
    }

    override fun safeAddView(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean =
        try {
            windowManager?.addView(view, params)
            true
        } catch (e: Throwable) {
            Timber.e(e, "OverlayWindowManager.safeAddView failed")
            false
        }

    override fun safeRemoveView(view: View?): Boolean =
        try {
            if (view != null && (view.isAttachedToWindow || view.windowToken != null || view.parent != null)) {
                windowManager?.removeView(view)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Timber.e(e, "OverlayWindowManager.safeRemoveView failed")
            false
        }

    override fun safeUpdateViewLayout(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean =
        try {
            if (view.isAttachedToWindow || view.windowToken != null || view.parent != null) {
                windowManager?.updateViewLayout(view, params)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Timber.e(e, "OverlayWindowManager.safeUpdateViewLayout failed")
            false
        }
}
