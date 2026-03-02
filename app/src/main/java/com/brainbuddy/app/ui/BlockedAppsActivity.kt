package com.brainbuddy.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppGroupPresets
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.SocialPresetPackages
import com.brainbuddy.app.receiver.PackageChangeReceiver
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class BlockedAppsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HIGHLIGHT_PACKAGE = "highlight_package"
        const val EXTRA_QUICK_BLOCK_PACKAGE = "quick_block_package"
        const val EXTRA_OPEN_SOCIAL_PRESET = "open_social_preset"
    }

    private lateinit var blockedStore: BlockedAppsStore
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, BlockedAppsActivity::class.java)) return

        setContentView(R.layout.activity_blocked_apps)

        blockedStore = BlockedAppsStore(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.parent_blocked_apps)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val searchBox = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchBox)
        adapter = AppListAdapter(emptyList(), blockedStore) { pkg, blocked ->
            val set = blockedStore.getBlockedPackages().toMutableSet()
            if (blocked) set.add(pkg) else set.remove(pkg)
            blockedStore.setBlockedPackages(set)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = packageManager
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                installed
                    .filter { it.packageName != packageName }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { app ->
                        AppInfo(
                            app.packageName,
                            app.loadLabel(pm).toString(),
                            app.loadIcon(pm)
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            allApps = loaded
            adapter.updateList(loaded)
            adapter.notifyDataSetChanged()

            val openPreset = intent?.getBooleanExtra(EXTRA_OPEN_SOCIAL_PRESET, false) == true
            if (openPreset) {
                intent?.removeExtra(EXTRA_OPEN_SOCIAL_PRESET)
                showSocialPresetSheet()
            }
        }

        val chipAll = findViewById<com.google.android.material.chip.Chip>(R.id.chipAll)
        val chipSocial = findViewById<com.google.android.material.chip.Chip>(R.id.chipSocial)
        val chipGames = findViewById<com.google.android.material.chip.Chip>(R.id.chipGames)
        val chipBrowsers = findViewById<com.google.android.material.chip.Chip>(R.id.chipBrowsers)

        chipAll?.setOnClickListener {
            adapter.updateList(allApps)
        }
        chipSocial?.setOnClickListener {
            val socialPkgs = AppGroupPresets.socialPackages
            adapter.updateList(allApps.filter { it.packageName in socialPkgs })
        }
        chipGames?.setOnClickListener {
            val gamePkgs = AppGroupPresets.gamesPackages
            adapter.updateList(allApps.filter { it.packageName in gamePkgs })
        }
        chipBrowsers?.setOnClickListener {
            val browserPkgs = AppGroupPresets.browsersPackages
            adapter.updateList(allApps.filter { it.packageName in browserPkgs })
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.lowercase() ?: ""
                adapter.updateList(allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
            }
        })

        findViewById<View>(R.id.btnPresetSocialMedia)?.setOnClickListener { showSocialPresetSheet() }

        handleQuickBlockIntent()
    }

    private fun handleQuickBlockIntent() {
        val pkg = intent?.getStringExtra(EXTRA_QUICK_BLOCK_PACKAGE)
            ?: intent?.getStringExtra(EXTRA_HIGHLIGHT_PACKAGE) ?: return
        val trimmed = pkg.trim().takeIf { it.isNotBlank() } ?: return
        intent?.removeExtra(EXTRA_QUICK_BLOCK_PACKAGE)
        intent?.removeExtra(EXTRA_HIGHLIGHT_PACKAGE)
        if (blockedStore.isBlocked(trimmed)) return

        val appName = SocialPresetPackages.getAppLabel(this, trimmed)
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, getString(R.string.notif_new_social_text, appName), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.preset_confirm_block)) {
                val set = blockedStore.getBlockedPackages().toMutableSet()
                set.add(trimmed)
                blockedStore.setBlockedPackages(set)
                adapter.updateList(allApps)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, getString(R.string.preset_blocked_success), Toast.LENGTH_SHORT).show()
            }
            .setActionTextColor(getColor(R.color.bb_primary))
            .show()
    }

    private fun showSocialPresetSheet() {
        val installed = SocialPresetPackages.getInstalledSocialPackages(this)
        if (installed.isEmpty()) {
            Toast.makeText(this, getString(R.string.preset_none_found), Toast.LENGTH_LONG).show()
            return
        }
        val sheet = BottomSheetDialog(this, R.style.Theme_BrainBuddy)
        val root = layoutInflater.inflate(R.layout.bottom_sheet_social_preset, null)
        sheet.setContentView(root)
        val labels = installed.map { SocialPresetPackages.getAppLabel(this, it) }
        root.findViewById<TextView>(R.id.presetAppList).text = labels.joinToString(", ")
        root.findViewById<View>(R.id.btnPresetCancel).setOnClickListener { sheet.dismiss() }
        root.findViewById<View>(R.id.btnPresetConfirm).setOnClickListener {
            val set = blockedStore.getBlockedPackages().toMutableSet()
            set.addAll(installed)
            blockedStore.setBlockedPackages(set)
            adapter.updateList(allApps)
            adapter.notifyDataSetChanged()
            sheet.dismiss()
            Toast.makeText(this, getString(R.string.preset_blocked_success), Toast.LENGTH_SHORT).show()
        }
        root.findViewById<View>(R.id.btnPresetSingleSelect)?.setOnClickListener {
            sheet.dismiss()
            val socialPkgs = AppGroupPresets.socialPackages
            adapter.updateList(allApps.filter { it.packageName in socialPkgs })
            findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroup)?.check(R.id.chipSocial)
        }
        sheet.show()
    }

    override fun onResume() {
        super.onResume()
        if (PackageChangeReceiver.isDirty(this)) {
            PackageChangeReceiver.clearDirtyFlag(this)
            val pm = packageManager
            allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .mapNotNull { app ->
                    try {
                        AppInfo(app.packageName, app.loadLabel(pm).toString(), app.loadIcon(pm))
                    } catch (_: Exception) { null }
                }
                .sortedBy { it.label.lowercase() }
            adapter.updateList(allApps)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    data class AppInfo(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable)

    class AppListAdapter(
        private var apps: List<AppInfo>,
        private val blockedStore: BlockedAppsStore,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val label: TextView = v.findViewById(R.id.appLabel)
            val pkg: TextView = v.findViewById(R.id.appPackage)
            val check: CheckBox = v.findViewById(R.id.appBlocked)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = blockedStore.getBlockedPackages().contains(app.packageName)
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app.packageName, isChecked)
            }
        }

        override fun getItemCount() = apps.size

        fun updateList(newList: List<AppInfo>) {
            apps = newList
            notifyDataSetChanged()
        }
    }
}
