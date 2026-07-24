package com.example.redirectguard.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * TYPE_ACCESSIBILITY_OVERLAY の 1x1 の非タッチ可能ウィンドウを画面に乗せ、
 * FLAG_WATCH_OUTSIDE_TOUCH で画面上のあらゆるタッチ(ACTION_OUTSIDE)を検知する。
 *
 * WebView 内で Canvas 描画されたボタン(広告の「閉じる」ボタン等)は
 * TYPE_VIEW_CLICKED / TYPE_TOUCH_INTERACTION_START が発火しないことが多いため、
 * アプリの描画方式に関係なく「画面のどこかがタップされた」という事実だけを
 * 取りこぼしなく拾うための補助手段。
 *
 * ACTION_OUTSIDE は座標や移動量を返さない(タップジャッキング対策で Android が意図的に
 * 情報を渡さない)ため、この仕組み単独ではタップとスワイプを区別できない。
 * 呼び出し側で Accessibility イベントの継続時間などと組み合わせて判定すること。
 *
 * TYPE_ACCESSIBILITY_OVERLAY は AccessibilityService から追加する限り
 * SYSTEM_ALERT_WINDOW 権限は不要。
 */
class TouchWatcherOverlay(
    private val service: AccessibilityService,
    private val onOutsideTouch: () -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun start() {
        if (overlayView != null) return

        val view = object : View(service) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    onOutsideTouch()
                }
                return false
            }
        }

        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            // 一部端末/OSバージョンでは overlay 追加に失敗する可能性があるため、
            // これは補助手段として静かに諦める(メインの WINDOW_STATE_CHANGED 検知には影響しない)。
            Log.w(TAG, "Failed to add touch watcher overlay", e)
        }
    }

    fun stop() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove touch watcher overlay", e)
            }
        }
        overlayView = null
    }

    companion object {
        private const val TAG = "TouchWatcherOverlay"
    }
}
