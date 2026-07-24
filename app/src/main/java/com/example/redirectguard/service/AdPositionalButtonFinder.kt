package com.example.redirectguard.service

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.hypot

/**
 * スキップボタンがアイコンのみ(contentDescription / text / resource-id が一切ない)で
 * キーワードマッチング(AdCloseButtonFinder)では検出不可能な場合のフォールバック。
 *
 * 位置(画面上部の隅)だけではミュートボタン等と区別できないため、
 * 「広告開始からしばらく経ってからクリック可能になったアイコン」を優先する。
 * スキップボタンはカウントダウン終了後に有効化されるのに対し、ミュートボタン等は
 * 広告表示開始時点から常にクリック可能であることが多いという経験則に基づく判定。
 *
 * resetTracking() を新しい広告ウィンドウが表示されるたびに呼び出すこと(状態はこのオブジェクト内に保持)。
 */
object AdPositionalButtonFinder {

    private val firstSeenClickableAt = mutableMapOf<String, Long>()

    fun resetTracking() {
        firstSeenClickableAt.clear()
    }

    fun findLikelySkipButton(root: AccessibilityNodeInfo, context: Context): AccessibilityNodeInfo? {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val screenWidth = metrics.widthPixels

        val minIconPx = (MIN_ICON_DP * density).toInt()
        val maxIconPx = (MAX_ICON_DP * density).toInt()
        val topZonePx = (TOP_ZONE_DP * density).toInt()
        val cornerZonePx = (CORNER_ZONE_DP * density).toInt()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectCandidates(root, candidates, minIconPx, maxIconPx, topZonePx, screenWidth, cornerZonePx, 0)
        if (candidates.isEmpty()) return null

        val now = System.currentTimeMillis()

        // 出現(クリック可能になった)時刻を記録する。
        val timed = candidates.map { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val key = boundsKey(bounds)
            val firstSeen = firstSeenClickableAt.getOrPut(key) { now }
            node to firstSeen
        }

        // 候補が1つしか無い場合、比較対象が無く「ミュートか、スキップか」を判断できないため
        // 誤タップを避けて何もしない(候補が複数出揃うのを待つ)。
        if (timed.size < 2) return null

        val sortedByAppearance = timed.sortedBy { it.second }
        val earliestAppearance = sortedByAppearance.first().second
        val (latestNode, latestAppearance) = sortedByAppearance.last()

        // 広告開始直後から存在する固定UI(ミュート等)とほぼ同時に現れた場合は
        // 区別できる材料が無いため見送る。他の候補より明確に後から現れた場合のみ
        // 「カウントダウン終了後に有効化されたスキップボタン」とみなす。
        if (latestAppearance - earliestAppearance < RELATIVE_APPEAR_DELAY_MS) return null

        return latestNode
    }

    private fun boundsKey(rect: Rect): String {
        // 数px程度のジッターを許容するため 8px 単位に丸める。
        fun round(v: Int) = (v / 8) * 8
        return "${round(rect.left)},${round(rect.top)},${round(rect.right)},${round(rect.bottom)}"
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        minIconPx: Int,
        maxIconPx: Int,
        topZonePx: Int,
        screenWidth: Int,
        cornerZonePx: Int,
        depth: Int
    ) {
        if (depth > 10) return

        if (node.isClickable && node.isVisibleToUser) {
            val hasNoLabel = node.contentDescription.isNullOrEmpty() &&
                node.text.isNullOrEmpty() &&
                node.viewIdResourceName.isNullOrEmpty()

            if (hasNoLabel) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val width = rect.width()
                val height = rect.height()
                val isSmallSquareIcon = width in minIconPx..maxIconPx && height in minIconPx..maxIconPx
                val isNearTopEdge = rect.top in 0..topZonePx
                val isNearLeftEdge = rect.left in 0..cornerZonePx
                val isNearRightEdge = rect.right in (screenWidth - cornerZonePx)..screenWidth
                if (isSmallSquareIcon && isNearTopEdge && (isNearLeftEdge || isNearRightEdge)) {
                    out.add(node)
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                collectCandidates(it, out, minIconPx, maxIconPx, topZonePx, screenWidth, cornerZonePx, depth + 1)
            }
        }
    }

    private const val MIN_ICON_DP = 16
    private const val MAX_ICON_DP = 64
    private const val TOP_ZONE_DP = 120
    private const val CORNER_ZONE_DP = 120

    // 候補同士の出現タイミングの差がこれ未満なら「同時に現れた=区別できない」とみなす。
    private const val RELATIVE_APPEAR_DELAY_MS = 800L
}
