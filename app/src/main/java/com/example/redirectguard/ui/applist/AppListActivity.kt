package com.example.redirectguard.ui.applist

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.redirectguard.R
import com.example.redirectguard.data.SettingsRepository
import com.example.redirectguard.databinding.ActivityAppListBinding
import com.example.redirectguard.util.AppTheme
import com.example.redirectguard.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppEntry(
    val packageName: String,
    val label: String
)

private enum class FilterMode { ALL, SELECTED, UNSELECTED }

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var settings: SettingsRepository
    private val selectedPackages = mutableSetOf<String>()
    private lateinit var allApps: List<InstalledAppEntry>
    private lateinit var adapter: AppListAdapter
    private lateinit var theme: AppTheme
    private var filterMode: FilterMode = FilterMode.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)
        theme = ThemeManager.byId(settings.themeId)
        selectedPackages.addAll(settings.protectedPackages)

        applyTheme()

        // インストール済みアプリの列挙(アイコン込みで数百件になりうる)はメインスレッドだと
        // 遷移直後の描画が固まって見えるため、バックグラウンドで読み込みローディング表示にする。
        binding.rootAppList.alpha = 0f
        binding.layoutLoading.alpha = 1f

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) { loadInstalledApps() }
            allApps = apps
            adapter = AppListAdapter(apps.toMutableList(), selectedPackages, theme) { app, isChecked ->
                if (isChecked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
                applyFilters()
            }
            binding.recyclerApps.layoutManager = LinearLayoutManager(this@AppListActivity)
            binding.recyclerApps.adapter = adapter
            revealContent()
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.iconClearSearch.visibility =
                    if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.iconClearSearch.setOnClickListener {
            binding.editSearch.text?.clear()
        }

        binding.filterAll.setOnClickListener { setFilterMode(FilterMode.ALL) }
        binding.filterSelected.setOnClickListener { setFilterMode(FilterMode.SELECTED) }
        binding.filterUnselected.setOnClickListener { setFilterMode(FilterMode.UNSELECTED) }
        updateFilterTabAppearance()

        binding.buttonDone.setOnClickListener {
            settings.protectedPackages = selectedPackages
            finish()
        }
    }

    private fun setFilterMode(mode: FilterMode) {
        filterMode = mode
        updateFilterTabAppearance()
        applyFilters()
    }

    private fun applyFilters() {
        if (!::allApps.isInitialized) return
        val query = binding.editSearch.text?.toString().orEmpty()
        val filtered = allApps
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .filter {
                when (filterMode) {
                    FilterMode.ALL -> true
                    FilterMode.SELECTED -> selectedPackages.contains(it.packageName)
                    FilterMode.UNSELECTED -> !selectedPackages.contains(it.packageName)
                }
            }
        adapter.updateItems(filtered)
    }

    private fun updateFilterTabAppearance() {
        val tabs = listOf(
            binding.filterAll to FilterMode.ALL,
            binding.filterSelected to FilterMode.SELECTED,
            binding.filterUnselected to FilterMode.UNSELECTED
        )
        for ((tab, mode) in tabs) {
            val isSelected = mode == filterMode
            tab.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999f
                if (isSelected) {
                    setColor(theme.accent)
                } else {
                    setColor(theme.surface)
                }
            }
            tab.setTextColor(
                if (isSelected) {
                    if (isLightAccent()) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                } else {
                    theme.textSecondary
                }
            )
        }
    }

    private fun isLightAccent(): Boolean {
        val c = theme.accent
        val luminance = 0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)
        return luminance > 170
    }

    /** ローディング表示から実際のリストへ、テーマカラーのスピナーをフェードアウトしつつ滑らかに切り替える。 */
    private fun revealContent() {
        binding.layoutLoading.animate()
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { binding.layoutLoading.visibility = android.view.View.GONE }
            .start()

        binding.rootAppList.animate()
            .alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun applyTheme() {
        ThemeManager.applyWindow(this, theme)
        binding.rootAppList.setBackgroundColor(theme.background)
        binding.layoutLoading.setBackgroundColor(theme.background)
        binding.progressLoading.indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        binding.textLoading.setTextColor(theme.textSecondary)
        binding.cardSearch.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(theme.surface)
            cornerRadius = 24f * resources.displayMetrics.density
        }
        binding.editSearch.setTextColor(theme.textPrimary)
        binding.editSearch.setHintTextColor(theme.textSecondary)
        binding.iconSearch.imageTintList = android.content.res.ColorStateList.valueOf(theme.textSecondary)
        binding.iconClearSearch.imageTintList = android.content.res.ColorStateList.valueOf(theme.textSecondary)
        ThemeManager.tintButton(binding.buttonDone, theme)
        updateFilterTabAppearance()
    }

    private fun loadInstalledApps(): List<InstalledAppEntry> {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != packageName }
            .map { InstalledAppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}

private class AppListAdapter(
    private var items: List<InstalledAppEntry>,
    private val selectedPackages: MutableSet<String>,
    private val theme: AppTheme,
    private val onToggle: (InstalledAppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val icon: android.widget.ImageView = itemView.findViewById(R.id.imageAppIcon)
        val text1: android.widget.TextView = itemView.findViewById(android.R.id.text1)
        val text2: android.widget.TextView = itemView.findViewById(android.R.id.text2)
        val checkbox: android.widget.CheckBox = itemView.findViewById(R.id.checkboxSelected)
    }

    fun updateItems(newItems: List<InstalledAppEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text1.text = item.label
        holder.text1.setTextColor(theme.textPrimary)
        holder.text2.text = item.packageName
        holder.text2.setTextColor(theme.textSecondary)
        holder.checkbox.isChecked = selectedPackages.contains(item.packageName)
        ThemeManager.tintCheckbox(holder.checkbox, theme)
        try {
            holder.icon.setImageDrawable(holder.itemView.context.packageManager.getApplicationIcon(item.packageName))
        } catch (e: Exception) {
            holder.icon.setImageDrawable(null)
        }
        holder.itemView.setOnClickListener {
            val newState = !selectedPackages.contains(item.packageName)
            holder.checkbox.isChecked = newState
            onToggle(item, newState)
        }
    }

    override fun getItemCount(): Int = items.size
}
