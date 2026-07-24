package com.example.redirectguard.ui.allowlist

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.redirectguard.R
import com.example.redirectguard.data.SettingsRepository
import com.example.redirectguard.databinding.ActivityAllowListBinding
import com.example.redirectguard.util.AppTheme
import com.example.redirectguard.util.ThemeManager

class AllowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllowListBinding
    private lateinit var settings: SettingsRepository
    private val currentSet = mutableSetOf<String>()
    private lateinit var adapter: AllowListAdapter
    private lateinit var theme: AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllowListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)
        theme = ThemeManager.byId(settings.themeId)

        currentSet.addAll(settings.allowList)
        adapter = AllowListAdapter(currentSet.toMutableList(), theme) { pkg -> confirmRemove(pkg) }
        binding.recyclerAllowList.layoutManager = LinearLayoutManager(this)
        binding.recyclerAllowList.adapter = adapter

        binding.buttonAddPackage.setOnClickListener {
            val pkg = binding.editPackageName.text.toString().trim()
            if (pkg.isNotEmpty()) {
                currentSet.add(pkg)
                settings.allowList = currentSet
                adapter.updateItems(currentSet.toMutableList())
                binding.editPackageName.text?.clear()
            }
        }

        applyTheme()
    }

    private fun confirmRemove(pkg: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_title)
            .setMessage(getString(R.string.confirm_remove_message, pkg))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                currentSet.remove(pkg)
                settings.allowList = currentSet
                adapter.updateItems(currentSet.toMutableList())
            }
            .show()
    }

    private fun applyTheme() {
        ThemeManager.applyWindow(this, theme)
        binding.rootAllowList.setBackgroundColor(theme.background)
        val dangerColor = 0xFFF5576C.toInt()
        binding.textWarning.setTextColor(dangerColor)
        binding.textWarning.background = android.graphics.drawable.GradientDrawable().apply {
            setColor((dangerColor and 0x00FFFFFF) or (0x26 shl 24))
            setStroke((1.5f * resources.displayMetrics.density).toInt(), dangerColor)
            cornerRadius = 10f * resources.displayMetrics.density
        }
        binding.editPackageName.setTextColor(theme.textPrimary)
        binding.editPackageName.setHintTextColor(theme.textSecondary)
        ThemeManager.tintButton(binding.buttonAddPackage, theme)
    }
}

private class AllowListAdapter(
    private var items: MutableList<String>,
    private val theme: AppTheme,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<AllowListAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val text1: android.widget.TextView = itemView.findViewById(android.R.id.text1)
        val button: android.widget.Button = itemView.findViewById(com.example.redirectguard.R.id.buttonRemove)
    }

    fun updateItems(newItems: MutableList<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(com.example.redirectguard.R.layout.item_allow_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pkg = items[position]
        holder.text1.text = pkg
        holder.text1.setTextColor(theme.textPrimary)
        holder.button.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(items[pos])
        }
    }

    override fun getItemCount(): Int = items.size
}
