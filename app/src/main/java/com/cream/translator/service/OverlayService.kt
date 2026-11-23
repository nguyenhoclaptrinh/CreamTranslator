package com.cream.translator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.cream.translator.R
import timber.log.Timber

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingTriggerView: View? = null
    private var isOverlayAttached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingTrigger()
    }

    private fun setupFloatingTrigger() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        // Minimal floating trigger view with touch drag and tap listener
        val view = View(this).apply {
            setBackgroundResource(android.R.drawable.presence_online)
            val size = (48 * resources.displayMetrics.density).toInt()
            layoutParams = WindowManager.LayoutParams(size, size)
        }

        attachDragAndClickListener(view, params)

        try {
            windowManager?.addView(view, params)
            floatingTriggerView = view
            isOverlayAttached = true
            Timber.d("Floating trigger attached to window")
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach floating overlay")
        }
    }

    private fun attachDragAndClickListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onTriggerClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTriggerClicked() {
        Timber.d("Floating trigger clicked - broadcasting translation request")
        val intent = Intent(ACTION_TRIGGER_TRANSLATION)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        if (isOverlayAttached && floatingTriggerView != null) {
            windowManager?.removeView(floatingTriggerView)
            floatingTriggerView = null
            isOverlayAttached = false
        }
        super.onDestroy()
        Timber.d("OverlayService destroyed")
    }

    companion object {
        const val ACTION_TRIGGER_TRANSLATION = "com.cream.translator.ACTION_TRIGGER_TRANSLATION"
    }
}
