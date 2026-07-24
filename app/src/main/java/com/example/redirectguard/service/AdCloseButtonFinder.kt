package com.example.redirectguard.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 全画面広告のノードツリーから「閉じる」「スキップ」ボタンらしき要素を探索する。
 * contentDescription / resource-id / 表示テキストにキーワードが含まれ、かつクリック可能な
 * 要素のみを対象とすることで誤タップを防ぐ。
 *
 * スキップボタンは「≫」「>>」「>|」のような記号がアイコンではなく表示テキスト(TextView/Button)
 * として描画されることが多いため、contentDescription だけでなく text も照合対象にする。
 */
object AdCloseButtonFinder {

    // contentDescription は広告SDK/言語設定によって日本語(「広告を閉じる」等)で
    // 提供されることがあるため、英語キーワードだけでは取りこぼす。
    private val CLOSE_KEYWORDS = listOf(
        "close", "ad_close", "dismiss",
        "閉じる", "広告を閉じる"
    )

    private val SKIP_KEYWORDS = listOf(
        "skip", "スキップ",
        "≫", "»", ">>", ">|", ">||"
    )

    fun findClose(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? = find(node, CLOSE_KEYWORDS)

    fun findSkip(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? = find(node, SKIP_KEYWORDS)

    private fun find(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null

        val desc = node.contentDescription?.toString().orEmpty()
        val resId = node.viewIdResourceName.orEmpty()
        val text = node.text?.toString().orEmpty()
        val isCandidate = keywords.any {
            desc.contains(it, ignoreCase = true) ||
                resId.contains(it, ignoreCase = true) ||
                text.contains(it, ignoreCase = true)
        }
        if (isCandidate && node.isClickable) return node

        for (i in 0 until node.childCount) {
            find(node.getChild(i), keywords)?.let { return it }
        }
        return null
    }
}
