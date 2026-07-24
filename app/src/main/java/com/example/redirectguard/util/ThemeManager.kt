package com.example.redirectguard.util

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

data class AppTheme(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val divider: Int,
    val accent: Int
)

/**
 * 背景(白/黒)とアクセントカラーの組み合わせをテーマとして提供する。
 * XML の色リソースを切り替えるのではなく、各画面が保持するビューへ実行時に色を適用する方式。
 */
object ThemeManager {

    private val LIGHT_BG = Color.parseColor("#F5F6F8")
    private val LIGHT_SURFACE = Color.parseColor("#FFFFFF")
    private val LIGHT_TEXT_PRIMARY = Color.parseColor("#1A1C1F")
    private val LIGHT_TEXT_SECONDARY = Color.parseColor("#6B7078")
    private val LIGHT_DIVIDER = Color.parseColor("#E4E6EA")

    private val DARK_BG = Color.parseColor("#0F1115")
    private val DARK_SURFACE = Color.parseColor("#191C22")
    private val DARK_TEXT_PRIMARY = Color.parseColor("#F2F3F5")
    private val DARK_TEXT_SECONDARY = Color.parseColor("#8A8F99")
    private val DARK_DIVIDER = Color.parseColor("#2A2E37")

    private val BLUE = Color.parseColor("#2F6FED")
    val NEUTRAL_GRAY = Color.parseColor("#8A8F99")
    private val GOLD = Color.parseColor("#C9A227")
    private val GREEN = Color.parseColor("#00B37E")
    private val ORANGE = Color.parseColor("#FB8C00")
    private val PINK = Color.parseColor("#E91E8C")

    val THEMES: List<AppTheme> = listOf(
        light("light_blue", "白 / 青", BLUE),
        light("light_gold", "白 / ゴールド", GOLD),
        light("light_green", "白 / 緑", GREEN),
        light("light_orange", "白 / オレンジ", ORANGE),
        light("light_pink", "白 / ピンク", PINK),
        dark("dark_blue", "黒 / 青", BLUE),
        dark("dark_gold", "黒 / ゴールド", GOLD),
        dark("dark_green", "黒 / 緑", GREEN),
        dark("dark_orange", "黒 / オレンジ", ORANGE),
        dark("dark_pink", "黒 / ピンク", PINK)
    )

    const val DEFAULT_THEME_ID = "light_blue"

    private fun light(id: String, label: String, accent: Int) = AppTheme(
        id, label, isDark = false,
        background = LIGHT_BG, surface = LIGHT_SURFACE,
        textPrimary = LIGHT_TEXT_PRIMARY, textSecondary = LIGHT_TEXT_SECONDARY,
        divider = LIGHT_DIVIDER, accent = accent
    )

    private fun dark(id: String, label: String, accent: Int) = AppTheme(
        id, label, isDark = true,
        background = DARK_BG, surface = DARK_SURFACE,
        textPrimary = DARK_TEXT_PRIMARY, textSecondary = DARK_TEXT_SECONDARY,
        divider = DARK_DIVIDER, accent = accent
    )

    fun byId(id: String): AppTheme = THEMES.find { it.id == id } ?: THEMES.first { it.id == DEFAULT_THEME_ID }

    /** ウィンドウ全体(ステータスバー・アイコン色)にテーマを適用する。 */
    fun applyWindow(activity: AppCompatActivity, theme: AppTheme) {
        activity.window.statusBarColor = theme.background
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !theme.isDark
    }

    fun styleRoot(view: View, theme: AppTheme) {
        view.setBackgroundColor(theme.background)
    }

    fun styleCard(view: View, theme: AppTheme) {
        view.background = GradientDrawable().apply {
            setColor(theme.surface)
            cornerRadius = dp(view, 14f)
        }
    }

    fun tintButton(button: Button, theme: AppTheme) {
        button.backgroundTintList = ColorStateList.valueOf(theme.accent)
        button.setTextColor(if (theme.isDark || !isLightColor(theme.accent)) Color.WHITE else Color.BLACK)
    }

    fun tintOutlinedButton(button: Button, theme: AppTheme) {
        button.setTextColor(theme.accent)
        if (button is com.google.android.material.button.MaterialButton) {
            button.strokeColor = ColorStateList.valueOf(theme.accent)
        }
    }

    fun tintTextButton(button: Button, theme: AppTheme) {
        button.setTextColor(theme.accent)
    }

    fun tintSwitch(switchView: Switch, theme: AppTheme) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(theme.accent, theme.textSecondary)
        val trackColors = intArrayOf(withAlpha(theme.accent, 0x80), withAlpha(theme.textSecondary, 0x40))
        switchView.thumbTintList = ColorStateList(states, thumbColors)
        switchView.trackTintList = ColorStateList(states, trackColors)
    }

    fun tintCheckbox(checkbox: CheckBox, theme: AppTheme) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        checkbox.buttonTintList = ColorStateList(states, intArrayOf(theme.accent, theme.textSecondary))
    }

    /**
     * テーマスウォッチ用: 円を対角線で分割し、左上=メインカラー、右下=背景色を表示する。
     */
    fun diagonalSwatchBitmap(view: View, mainColor: Int, bgColor: Int, selected: Boolean, sizeDp: Float = 44f): android.graphics.Bitmap {
        val density = view.resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val radius = size / 2f
        val strokeWidth = (if (selected) 5f else 2f) * density

        canvas.save()
        val clipPath = android.graphics.Path().apply {
            addCircle(radius, radius, radius - strokeWidth, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val mainPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = mainColor }
        val trianglePath = android.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.toFloat(), 0f)
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(trianglePath, mainPaint)
        canvas.restore()

        val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = if (selected) mainColor else Color.parseColor("#55808080")
            this.strokeWidth = strokeWidth
        }
        canvas.drawCircle(radius, radius, radius - strokeWidth / 2, ringPaint)

        return bitmap
    }

    fun dotDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /** ステータスバッジ(有効/無効表示)用の淡い背景ピル。 */
    fun chipBackground(view: View, color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(view, 999f)
        setColor(withAlpha(color, 0x26))
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun isLightColor(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color))
        return luminance > 170
    }

    private fun dp(view: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, view.resources.displayMetrics)
}
