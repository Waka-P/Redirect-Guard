package com.example.redirectguard.data

import android.content.Context

/**
 * 監視対象アプリ・許可リスト・監視ON/OFF・しきい値を SharedPreferences に永続化する。
 * ログのみ Room DB (DetectionLog) を利用する。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 監視対象アプリ(複数選択可)の package name 一覧。 */
    var protectedPackages: Set<String>
        get() {
            val migrated = migrateLegacySingleProtectedPackage()
            return prefs.getStringSet(KEY_PROTECTED_PACKAGES, migrated) ?: migrated
        }
        set(value) = prefs.edit().putStringSet(KEY_PROTECTED_PACKAGES, value).apply()

    /**
     * 旧バージョン(単一アプリ監視)で保存された protected_package を
     * 複数アプリ対応の protectedPackages に一度だけ引き継ぐ。
     */
    private fun migrateLegacySingleProtectedPackage(): Set<String> {
        val legacy = prefs.getString(KEY_PROTECTED_PACKAGE_LEGACY, null) ?: return emptySet()
        return setOf(legacy)
    }

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var userInteractionThresholdMs: Long
        get() = prefs.getLong(KEY_THRESHOLD_MS, DEFAULT_THRESHOLD_MS)
        set(value) = prefs.edit().putLong(KEY_THRESHOLD_MS, value).apply()

    var allowList: Set<String>
        get() = prefs.getStringSet(KEY_ALLOW_LIST, defaultAllowList()) ?: defaultAllowList()
        set(value) = prefs.edit().putStringSet(KEY_ALLOW_LIST, value).apply()

    /**
     * 広告の「閉じる」ボタン自動タップ機能。画面内容(ノードツリー)の読み取りを伴うため
     * プライバシー配慮のためデフォルトOFF。ユーザーが明示的にONにした場合のみ動作する。
     */
    var autoCloseAdEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLOSE_AD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLOSE_AD_ENABLED, value).apply()

    /**
     * 広告の「スキップ」ボタン自動タップ機能。閉じるボタンとは独立してON/OFFできる。
     * 悪質な広告はスキップボタンを押すと外部アプリへ遷移することがあるため、
     * それ自体はスキップとは別に自動遷移検知・復帰ロジックでカバーする想定。
     */
    var autoSkipAdEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_AD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKIP_AD_ENABLED, value).apply()

    /** UIテーマ(背景色×アクセントカラーの組み合わせ)のID。デフォルトは白背景/青。 */
    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, com.example.redirectguard.util.ThemeManager.DEFAULT_THEME_ID)
            ?: com.example.redirectguard.util.ThemeManager.DEFAULT_THEME_ID
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    var adWindowPatterns: Set<String>
        get() = prefs.getStringSet(KEY_AD_WINDOW_PATTERNS, defaultAdWindowPatterns()) ?: defaultAdWindowPatterns()
        set(value) = prefs.edit().putStringSet(KEY_AD_WINDOW_PATTERNS, value).apply()

    private fun defaultAdWindowPatterns(): Set<String> = setOf(
        "applovin", "pangle", "com.google.android.gms.ads",
        "unity3d.ads", "mbridge", "vungle",
        // Pangle(TikTok/ByteDance系広告SDK)の実際のパッケージ名。"pangle" の文字列は含まれない。
        "bytedance", "openadsdk",
        // Fyber/Digital Turbine(旧Inneractive)SDK
        "fyber", "inneractive",
        // IronSource
        "ironsource",
        // AdColony
        "adcolony",
        // Meta Audience Network(旧Facebook Audience Network)
        "facebook.ads", "audiencenetwork",
        // その他主要SDK
        "chartboost", "tapjoy", "moloco", "smaato", "mopub", "criteo", "adtiming"
    )

    private fun defaultAllowList(): Set<String> = setOf(
        "com.android.systemui",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.dialer",
        "com.android.settings",
        "com.example.redirectguard"
    )

    companion object {
        private const val PREFS_NAME = "redirect_guard_settings"
        private const val KEY_PROTECTED_PACKAGE_LEGACY = "protected_package"
        private const val KEY_PROTECTED_PACKAGES = "protected_packages"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_THRESHOLD_MS = "threshold_ms"
        private const val KEY_ALLOW_LIST = "allow_list"
        private const val KEY_AUTO_CLOSE_AD_ENABLED = "auto_close_ad_enabled"
        private const val KEY_AUTO_SKIP_AD_ENABLED = "auto_skip_ad_enabled"
        private const val KEY_AD_WINDOW_PATTERNS = "ad_window_patterns"
        private const val KEY_THEME_ID = "theme_id"
        const val DEFAULT_THRESHOLD_MS = 1500L
    }
}
