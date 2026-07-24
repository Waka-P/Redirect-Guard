package com.example.redirectguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.redirectguard.R
import com.example.redirectguard.data.AppDatabase
import com.example.redirectguard.data.DetectionLog
import com.example.redirectguard.data.SettingsRepository
import com.example.redirectguard.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RedirectGuardService : AccessibilityService() {

    private lateinit var settings: SettingsRepository
    private lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastUserInteractionAt: Long = 0L
    private var lastKnownForegroundPackage: String? = null
    private var lastForegroundProtectedPackage: String? = null
    private var lastRecoveryAt: Long = 0L
    private var homePackages: Set<String> = emptySet()
    private val recentDetectionTimestamps = mutableMapOf<String, MutableList<Long>>()

    private var touchOverlay: TouchWatcherOverlay? = null
    private var lastAutoCloseAttemptAt: Long = 0L
    private var lastDebugLogAt: Long = 0L
    private var lastTreeDumpAt: Long = 0L
    private var currentWindowClassName: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAutomatedTapAt: Long = 0L
    private val adCloseCheckRunnable = object : Runnable {
        override fun run() {
            if (::settings.isInitialized && isAutoTapFeatureEnabled()) {
                checkCurrentWindowForCloseButton()
            }
            mainHandler.postDelayed(this, AD_CLOSE_POLL_INTERVAL_MS)
        }
    }

    private fun isAutoTapFeatureEnabled(): Boolean =
        settings.autoCloseAdEnabled || settings.autoSkipAdEnabled

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsRepository(this)
        db = AppDatabase.getInstance(this)
        homePackages = resolveHomePackages()

        // AccessibilityServiceInfo はマニフェストの xml でも設定済みだが、
        // 実行時にも明示しておく(端末差異対策)。
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0
        }
        serviceInfo = info

        showPersistentNotification()

        touchOverlay = TouchWatcherOverlay(this) { onRawOutsideTouch() }
        touchOverlay?.start()

        // イベント駆動(TYPE_WINDOW_CONTENT_CHANGED)だけだと、既に画面に表示されている
        // 閉じるボタンに対して新しい変化イベントが発生しない限り検知できない。
        // そのため、有効時は一定間隔で現在のウィンドウを強制的に再スキャンする。
        mainHandler.post(adCloseCheckRunnable)

        Log.i(TAG, "RedirectGuardService connected, homePackages=$homePackages")
    }

    /**
     * ホーム画面(ランチャー)への遷移はユーザーの意図的な「アプリを閉じる」操作である可能性が高く、
     * ジェスチャーナビゲーションはクリック/タッチイベントとして拾えないことがあるため、
     * 誤検知(=ユーザーが自分でアプリを閉じられなくなる不具合)を避けるため常に許可する。
     *
     * resolveActivity() は端末によって実際のランチャーと異なる汎用パッケージ名(com.android.launcher 等)
     * を返すことがあるため、CATEGORY_HOME を処理できる全パッケージを列挙して使用する。
     * また Pixel 系のホーム画面左パネル(Google フィード/検索)はホームの一部として振る舞うため、
     * 既知のホーム隣接パッケージも合わせて許可する。
     */
    private fun resolveHomePackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toMutableSet()
        resolved += KNOWN_HOME_ADJACENT_PACKAGES
        return resolved
    }

    private fun showPersistentNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "監視中", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val pauseIntent = Intent(this, PauseMonitoringReceiver::class.java)
        val pausePendingIntent = PendingIntent.getBroadcast(
            this, 0, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setContentText(getString(R.string.notification_monitoring_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, getString(R.string.notification_pause_action), pausePendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                commitUserInteraction()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // TYPE_WINDOW_STATE_CHANGED の className は実際の Activity/Window クラス名を指す
                // (TYPE_WINDOW_CONTENT_CHANGED の className は変化した内部Viewのクラス名でしかなく、
                //  FrameLayout 等になってしまい広告SDKの判別に使えないため、ここで別途保持する)。
                currentWindowClassName = event.className?.toString()
                // 新しいウィンドウに切り替わるたびに、位置ベースのスキップボタン推定の
                // 「出現タイミング」追跡をリセットする(前の広告の状態を引きずらないため)。
                AdPositionalButtonFinder.resetTracking()
                handleWindowChange(event.packageName?.toString())
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (::settings.isInitialized && isAutoTapFeatureEnabled()) {
                    handleAdWindowContentChanged()
                }
            }
        }
    }

    /**
     * 広告の「閉じる」ボタン自動タップ(任意設定・デフォルトOFF)。
     * 現在のウィンドウ(直近の TYPE_WINDOW_STATE_CHANGED で捕捉した Activity クラス名)が
     * 既知の広告SDKパターンに一致する場合のみ、ノードツリーから閉じる/スキップボタンを探索してタップする。
     * アプリ本体の通常UIへの誤タップを避けるため、パターンに一致しないウィンドウは対象外とする。
     */
    private fun handleAdWindowContentChanged() {
        val className = currentWindowClassName ?: return
        tryAutoCloseAd(className)
    }

    /**
     * 定期ポーリング用。イベントを待たず、直近に捕捉したウィンドウクラス名で判定する。
     * 「既にボタンが表示されているが新しいコンテンツ変化イベントが来ない」ケースを拾うためのフォールバック。
     */
    private fun checkCurrentWindowForCloseButton() {
        val className = currentWindowClassName ?: return
        tryAutoCloseAd(className)
    }

    private fun tryAutoCloseAd(className: String) {
        val now = System.currentTimeMillis()
        if (now - lastDebugLogAt > DEBUG_LOG_INTERVAL_MS) {
            lastDebugLogAt = now
            Log.d(TAG, "[AutoClose] current window className=$className")
        }

        val patterns = settings.adWindowPatterns
        val matchesKnownAdClassName = patterns.any { className.contains(it, ignoreCase = true) }

        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "[AutoClose] rootInActiveWindow is null")
            return
        }

        // 広告が別Activityではなく現在の画面内にオーバーレイ表示される実装の場合、
        // TYPE_WINDOW_STATE_CHANGED が発火せず className が汎用クラス名(FrameLayout等)の
        // まま更新されないことがある。その場合は className だけで判定できないため、
        // ツリー内に WebView があるか(広告表示の代表的なシグナル)を補助的に見る。
        val isGenericClassName = GENERIC_VIEW_CLASS_NAMES.any { className == it || className.endsWith(".$it") }
        val isAdWindow = matchesKnownAdClassName || (isGenericClassName && containsWebView(root))
        if (!isAdWindow) return

        Log.d(TAG, "[AutoClose] treated as ad window: $className (knownClass=$matchesKnownAdClassName)")

        if (now - lastAutoCloseAttemptAt < AUTO_CLOSE_COOLDOWN_MS) {
            Log.d(TAG, "[AutoClose] skipped due to cooldown")
            return
        }

        if (settings.autoCloseAdEnabled) {
            val closeButton = AdCloseButtonFinder.findClose(root)
            if (closeButton != null) {
                performAutoTap(closeButton, className, "CLOSE", now)
                return
            }
        }

        if (settings.autoSkipAdEnabled) {
            // まずキーワード一致(テキスト/説明文があるケース)を試し、
            // 見つからなければアイコンのみのスキップボタン向けに位置ベースで推定する。
            val skipButton = AdCloseButtonFinder.findSkip(root)
                ?: AdPositionalButtonFinder.findLikelySkipButton(root, this)
            if (skipButton != null) {
                performAutoTap(skipButton, className, "SKIP", now)
                return
            }
        }

        Log.d(TAG, "[AutoClose] no close/skip candidate node found (childCount=${root.childCount})")
        if (now - lastTreeDumpAt > TREE_DUMP_INTERVAL_MS) {
            lastTreeDumpAt = now
            dumpNodeTree(root, 0)
        }
    }

    /**
     * 閉じる/スキップボタンをタップする。悪質な広告はスキップボタン自体が外部アプリへの
     * 遷移トリガーになっていることがあるため、タップ直後の遷移は自動遷移検知・復帰ロジックで
     * 確実に拾えるよう lastAutomatedTapAt を記録する(handleWindowChange 側で参照)。
     */
    private fun performAutoTap(button: AccessibilityNodeInfo, windowClassName: String, kind: String, now: Long) {
        val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val label = button.viewIdResourceName
            ?: button.contentDescription?.toString()
            ?: button.text?.toString()
            ?: "unknown"
        Log.d(TAG, "[AutoClose] found $kind candidate label=$label clicked=$clicked")
        if (clicked) {
            lastAutoCloseAttemptAt = now
            lastAutomatedTapAt = now
            logAdAutoClose(windowClassName, "$kind:$label")
        }
    }

    /** ツリー内に WebView が存在するかを浅く調べる(広告が表示されていることの補助シグナル)。 */
    private fun containsWebView(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 8) return false
        if (node.className?.toString()?.contains("WebView", ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsWebView(child, depth + 1)) return true
        }
        return false
    }

    /** デバッグ用: ノードツリーを再帰的にログ出力する(閉じるボタン探索が失敗した原因調査用)。 */
    private fun dumpNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 6) return
        val indent = "  ".repeat(depth)
        Log.d(
            TAG,
            "[AutoClose][tree]$indent class=${node.className} text=${node.text} " +
                "desc=${node.contentDescription} resId=${node.viewIdResourceName} " +
                "clickable=${node.isClickable} visible=${node.isVisibleToUser}"
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNodeTree(it, depth + 1) }
        }
    }

    private fun logAdAutoClose(windowClassName: String, label: String) {
        Log.i(TAG, "Auto-closed ad window=$windowClassName label=$label")
        serviceScope.launch {
            db.detectionLogDao().insert(
                DetectionLog(
                    timestamp = System.currentTimeMillis(),
                    targetPackage = windowClassName,
                    elapsedMs = 0,
                    actionTaken = "AUTO_CLOSE_TAP:$label"
                )
            )
        }
    }

    /**
     * オーバーレイ(ACTION_OUTSIDE)による生タッチ検知。
     * タップかスワイプかを問わず、画面へのあらゆる接触は「ユーザー操作あり」として扱う。
     * スワイプによって遷移が起きるなら意図した操作として通す必要があり、
     * スワイプで遷移が起きないなら WINDOW_STATE_CHANGED 自体が発火しないため判定に影響しない。
     * よって tap/swipe を区別する必要はなく、即座に確定してよい。
     */
    private fun onRawOutsideTouch() {
        commitUserInteraction()
    }

    private fun commitUserInteraction() {
        lastUserInteractionAt = System.currentTimeMillis()
    }

    private fun handleWindowChange(currentPackage: String?) {
        if (currentPackage == null) return
        if (currentPackage == lastKnownForegroundPackage) return
        val previousPackage = lastKnownForegroundPackage
        lastKnownForegroundPackage = currentPackage

        if (!::settings.isInitialized) return
        if (!settings.monitoringEnabled) return

        val protectedPackages = settings.protectedPackages
        if (protectedPackages.isEmpty()) return

        if (currentPackage in protectedPackages) {
            // 監視対象アプリ自身がフォアグラウンドに来た場合、それを「直前の監視対象アプリ」として
            // 記録する(複数アプリ監視時、遷移検知後にどのアプリへ復帰すべきかを判定するため)。
            lastForegroundProtectedPackage = currentPackage
            return
        }
        if (currentPackage in settings.allowList) return
        // ホーム画面(ホーム隣接パッケージ含む)への遷移は「ユーザーが自分でアプリを閉じた」可能性が高いため常に許可する。
        if (currentPackage in homePackages) return
        // 直前が通知シェード/クイック設定(SystemUI)だった場合、通知タップ等ユーザーの意図的な
        // 操作である可能性が高い。通知パネル上のタップはオーバーレイで確実に拾えるとは限らないため、
        // 遷移パターン自体で許可する。
        if (previousPackage == SYSTEM_UI_PACKAGE) return

        // 直前のフォアグラウンドが監視対象アプリでなかった場合(監視対象と無関係なアプリ間の
        // 切り替え)は、そもそも自動遷移の判定対象にしない。
        val protectedPackage = lastForegroundProtectedPackage ?: return

        // 復帰処理直後の連続検知はループの原因になるため、一定時間は再判定をスキップする。
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAt < RECOVERY_COOLDOWN_MS) return

        val elapsed = now - lastUserInteractionAt
        val threshold = settings.userInteractionThresholdMs
        // 直前に「閉じる/スキップ」ボタンを自動タップしていた場合、それが引き金の遷移である
        // 可能性が高いため、直近のユーザー操作時刻に関わらず自動遷移とみなす。
        // (悪質な広告は「スキップ」を押すと外部アプリへ飛ばすことがあるため)
        val causedByAutomatedTap = now - lastAutomatedTapAt < AUTOMATED_TAP_WINDOW_MS
        val looksAutomatic = causedByAutomatedTap || elapsed > threshold

        if (looksAutomatic) {
            lastRecoveryAt = now
            val repeatCount = trackRepeatOffender(currentPackage, now)
            val action = performRecovery(protectedPackage, currentPackage, repeatCount)
            logDetection(currentPackage, elapsed, action)
        }
    }

    /**
     * 同一遷移先への検知回数を直近の時間窓内でカウントする。
     * 悪質な広告SDKは押し戻した直後に再度同じ場所へ遷移させようとすることがあるため、
     * 短時間に繰り返し検知された場合はエスカレーション対応(バックグラウンドプロセスの停止)を行う。
     */
    private fun trackRepeatOffender(pkg: String, now: Long): Int {
        val timestamps = recentDetectionTimestamps.getOrPut(pkg) { mutableListOf() }
        timestamps.add(now)
        timestamps.removeAll { now - it > REPEAT_OFFENDER_WINDOW_MS }
        return timestamps.size
    }

    /**
     * GLOBAL_ACTION_BACK でまず復帰を試み、フェイルセーフとして
     * 監視対象アプリを明示的に startActivity で再起動する。
     * タスクスタックが別れているケースでは BACK だけでは元アプリに戻らないため。
     *
     * 同じ遷移先が短時間に繰り返し検知された場合(往復ループ)は、遷移先アプリの
     * バックグラウンドプロセスを停止し、即座に再遷移を仕掛けられる状態を断ち切る。
     */
    private fun performRecovery(protectedPackage: String, offendingPackage: String, repeatCount: Int): String {
        val backResult = performGlobalAction(GLOBAL_ACTION_BACK)

        val launchIntent = packageManager.getLaunchIntentForPackage(protectedPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to relaunch protected package", e)
            }
        }

        var action = if (backResult) "BACK+RELAUNCH" else "RELAUNCH_ONLY"

        if (repeatCount >= REPEAT_OFFENDER_THRESHOLD) {
            try {
                val activityManager = getSystemService(ActivityManager::class.java)
                activityManager.killBackgroundProcesses(offendingPackage)
                action += "+KILL_BG($repeatCount)"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to kill background process for $offendingPackage", e)
            }
        }

        return action
    }

    private fun logDetection(pkg: String, elapsedMs: Long, action: String) {
        Log.i(TAG, "Auto-redirect detected: pkg=$pkg elapsed=${elapsedMs}ms action=$action")
        serviceScope.launch {
            db.detectionLogDao().insert(
                DetectionLog(
                    timestamp = System.currentTimeMillis(),
                    targetPackage = pkg,
                    elapsedMs = elapsedMs,
                    actionTaken = action
                )
            )
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "RedirectGuardService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        touchOverlay?.stop()
        mainHandler.removeCallbacks(adCloseCheckRunnable)
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "RedirectGuardService"
        private const val CHANNEL_ID = "redirect_guard_monitoring"
        private const val NOTIFICATION_ID = 1
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        // TYPE_WINDOW_STATE_CHANGED が発火せず className が更新されないまま残る、
        // 識別情報を持たない汎用View/ContainerクラスName。
        private val GENERIC_VIEW_CLASS_NAMES = setOf(
            "FrameLayout", "ViewGroup", "View", "ListView", "LinearLayout", "RelativeLayout"
        )
        private const val RECOVERY_COOLDOWN_MS = 2000L
        private const val REPEAT_OFFENDER_WINDOW_MS = 15_000L
        private const val REPEAT_OFFENDER_THRESHOLD = 3
        private const val AUTO_CLOSE_COOLDOWN_MS = 1000L
        private const val AD_CLOSE_POLL_INTERVAL_MS = 800L
        private const val DEBUG_LOG_INTERVAL_MS = 1000L
        private const val TREE_DUMP_INTERVAL_MS = 3000L
        private const val AUTOMATED_TAP_WINDOW_MS = 3000L

        // ホームの一部として振る舞う既知のパッケージ(Pixelランチャーのフィード/検索面など)。
        private val KNOWN_HOME_ADJACENT_PACKAGES = setOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms"
        )
    }
}
