package com.example.redirectguard.ui.logs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.redirectguard.R
import com.example.redirectguard.data.AppDatabase
import com.example.redirectguard.data.DetectionLog
import com.example.redirectguard.data.SettingsRepository
import com.example.redirectguard.databinding.ActivityLogListBinding
import com.example.redirectguard.util.AppTheme
import com.example.redirectguard.util.ThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogListBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: LogListAdapter
    private lateinit var theme: AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getInstance(this)
        theme = ThemeManager.byId(SettingsRepository(this).themeId)

        adapter = LogListAdapter(emptyList(), theme) { log ->
            lifecycleScope.launch {
                db.detectionLogDao().update(log.copy(falsePositive = !log.falsePositive))
                refreshStats()
            }
        }
        binding.recyclerLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerLogs.adapter = adapter

        lifecycleScope.launch {
            db.detectionLogDao().observeAll().collect { logs ->
                adapter.updateItems(logs)
                refreshStats()
            }
        }

        applyTheme()
    }

    private fun applyTheme() {
        ThemeManager.applyWindow(this, theme)
        binding.rootLogList.setBackgroundColor(theme.background)
        binding.textWarning.setTextColor(theme.textSecondary)
        binding.textWarning.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(theme.surface)
            cornerRadius = 10f * resources.displayMetrics.density
        }
        binding.textStats.setTextColor(theme.textSecondary)
    }

    private suspend fun refreshStats() {
        val total = db.detectionLogDao().countAll()
        val falsePositives = db.detectionLogDao().countFalsePositives()
        val rate = if (total > 0) (falsePositives * 100 / total) else 0
        binding.textStats.text = getString(R.string.false_positive_rate_format, falsePositives, total, rate)
    }
}

private class LogListAdapter(
    private var items: List<DetectionLog>,
    private val theme: AppTheme,
    private val onToggleFalsePositive: (DetectionLog) -> Unit
) : RecyclerView.Adapter<LogListAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val text1: android.widget.TextView = itemView.findViewById(android.R.id.text1)
        val text2: android.widget.TextView = itemView.findViewById(android.R.id.text2)
        val checkbox: android.widget.CheckBox = itemView.findViewById(R.id.checkboxFalsePositive)
    }

    fun updateItems(newItems: List<DetectionLog>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = items[position]
        holder.text1.text = "${dateFormat.format(Date(log.timestamp))}  →  ${log.targetPackage}"
        holder.text1.setTextColor(theme.textPrimary)
        holder.text2.text = "elapsed=${log.elapsedMs}ms action=${log.actionTaken}"
        holder.text2.setTextColor(theme.textSecondary)
        ThemeManager.tintCheckbox(holder.checkbox, theme)
        holder.checkbox.setTextColor(theme.textPrimary)
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = log.falsePositive
        holder.checkbox.setOnCheckedChangeListener { _, _ -> onToggleFalsePositive(log) }
    }

    override fun getItemCount(): Int = items.size
}
