package com.example.redirectguard.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.redirectguard.R
import com.example.redirectguard.data.SettingsRepository
import com.example.redirectguard.databinding.ActivityMainBinding
import com.example.redirectguard.ui.allowlist.AllowListActivity
import com.example.redirectguard.ui.applist.AppListActivity
import com.example.redirectguard.ui.logs.LogListActivity
import com.example.redirectguard.util.AccessibilityUtils
import com.example.redirectguard.util.AppTheme
import com.example.redirectguard.util.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)

        binding.buttonSelectApp.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.buttonAllowList.setOnClickListener {
            startActivity(Intent(this, AllowListActivity::class.java))
        }

        binding.buttonLogs.setOnClickListener {
            startActivity(Intent(this, LogListActivity::class.java))
        }

        binding.buttonOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val theme = ThemeManager.byId(settings.themeId)
        applyTheme(theme)

        val serviceEnabled = AccessibilityUtils.isServiceEnabled(this)
        // 有効時はテーマのアクセントカラー、無効時は中立なグレーで状態を示す。
        val statusColor = if (serviceEnabled) theme.accent else ThemeManager.NEUTRAL_GRAY
        binding.dotAccessibilityStatus.background = ThemeManager.dotDrawable(statusColor)
        binding.badgeAccessibilityStatus.background = ThemeManager.chipBackground(binding.badgeAccessibilityStatus, statusColor)
        binding.textAccessibilityStatus.text = if (serviceEnabled) {
            getString(R.string.accessibility_status_enabled)
        } else {
            getString(R.string.accessibility_status_disabled)
        }
        binding.textAccessibilityStatus.setTextColor(statusColor)

        val protectedPackages = settings.protectedPackages
        renderSelectedApps(protectedPackages, theme)

        binding.switchMonitoring.setOnCheckedChangeListener(null)
        binding.switchMonitoring.isChecked = settings.monitoringEnabled
        binding.switchMonitoring.isEnabled = protectedPackages.isNotEmpty() && serviceEnabled
        ThemeManager.tintSwitch(binding.switchMonitoring, theme)
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            settings.monitoringEnabled = isChecked
        }

        binding.switchAutoCloseAd.setOnCheckedChangeListener(null)
        binding.switchAutoCloseAd.isChecked = settings.autoCloseAdEnabled
        ThemeManager.tintSwitch(binding.switchAutoCloseAd, theme)
        binding.switchAutoCloseAd.setOnCheckedChangeListener { _, isChecked ->
            settings.autoCloseAdEnabled = isChecked
        }

        binding.switchAutoSkipAd.setOnCheckedChangeListener(null)
        binding.switchAutoSkipAd.isChecked = settings.autoSkipAdEnabled
        ThemeManager.tintSwitch(binding.switchAutoSkipAd, theme)
        binding.switchAutoSkipAd.setOnCheckedChangeListener { _, isChecked ->
            settings.autoSkipAdEnabled = isChecked
        }

        renderThemeSwatches(theme)
    }

    private fun applyTheme(theme: AppTheme) {
        ThemeManager.applyWindow(this, theme)
        ThemeManager.styleRoot(binding.scrollRoot, theme)
        ThemeManager.styleCard(binding.cardTargets, theme)
        ThemeManager.styleCard(binding.cardControls, theme)
        ThemeManager.styleCard(binding.cardTheme, theme)
        ThemeManager.styleCard(binding.cardMore, theme)
        ThemeManager.tintOutlinedButton(binding.buttonOpenAccessibilitySettings, theme)
        ThemeManager.tintOutlinedButton(binding.buttonSelectApp, theme)
        binding.textAppTitle.setTextColor(theme.textPrimary)

        for (divider in listOf(binding.divider1, binding.divider2, binding.divider3)) {
            divider.setBackgroundColor(theme.divider)
        }

        val primaryTextViews = listOf(
            binding.textMonitoringLabel, binding.textAutoCloseLabel, binding.textAutoSkipLabel,
            binding.buttonAllowList, binding.buttonLogs
        )
        for (tv in primaryTextViews) tv.setTextColor(theme.textPrimary)

        val secondaryTextViews = listOf(binding.textAutoCloseDescription, binding.textAutoSkipDescription)
        for (tv in secondaryTextViews) tv.setTextColor(theme.textSecondary)

        for (label in listOf(binding.labelTargets, binding.labelControls, binding.labelTheme, binding.labelMore)) {
            label.setTextColor(theme.textSecondary)
        }
    }

    private fun renderSelectedApps(packages: Set<String>, theme: AppTheme) {
        binding.layoutSelectedApps.removeAllViews()
        if (packages.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.no_target_app_selected)
                setTextColor(theme.textSecondary)
            }
            binding.layoutSelectedApps.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        for (pkg in packages) {
            val row = inflater.inflate(R.layout.item_selected_app, binding.layoutSelectedApps, false)
            val icon = row.findViewById<ImageView>(R.id.imageAppIcon)
            val name = row.findViewById<TextView>(R.id.textAppName)
            name.setTextColor(theme.textPrimary)
            try {
                icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
                name.text = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
            } catch (e: Exception) {
                name.text = pkg
            }
            binding.layoutSelectedApps.addView(row)
        }
    }

    private fun renderThemeSwatches(selected: AppTheme) {
        binding.layoutThemeSwatches.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (candidate in ThemeManager.THEMES) {
            val item = inflater.inflate(R.layout.item_theme_swatch, binding.layoutThemeSwatches, false)
            val imageSwatch = item.findViewById<ImageView>(R.id.imageSwatch)
            val isSelected = candidate.id == selected.id
            val bitmap = ThemeManager.diagonalSwatchBitmap(imageSwatch, candidate.accent, candidate.background, isSelected)
            imageSwatch.setImageBitmap(bitmap)
            item.setOnClickListener {
                settings.themeId = candidate.id
                refreshUi()
            }
            item.contentDescription = candidate.label
            binding.layoutThemeSwatches.addView(item)
        }
    }
}
