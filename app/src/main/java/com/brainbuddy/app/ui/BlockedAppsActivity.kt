package com.brainbuddy.app.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppGroupPresets
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ParentAccessGuard

class BlockedAppsActivity : AppCompatActivity() {

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
        supportActionBar?.title = "Engellenen Uygulamalar"

        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        allApps = installed
            .filter { it.packageName != packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { app ->
                val info = pm.getApplicationInfo(app.packageName, 0)
                AppInfo(
                    app.packageName,
                    app.loadLabel(pm).toString(),
                    app.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val searchBox = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchBox)
        adapter = AppListAdapter(allApps, blockedStore) { pkg, blocked ->
            val set = blockedStore.getBlockedPackages().toMutableSet()
            if (blocked) set.add(pkg) else set.remove(pkg)
            blockedStore.setBlockedPackages(set)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val chipAll = findViewById<com.google.android.material.chip.Chip>(R.id.chipAll)
        val chipSocial = findViewById<com.google.android.material.chip.Chip>(R.id.chipSocial)
        val chipGames = findViewById<com.google.android.material.chip.Chip>(R.id.chipGames)
        val chipBrowsers = findViewById<com.google.android.material.chip.Chip>(R.id.chipBrowsers)
        chipSocial?.setOnClickListener {
            val inst = com.brainbuddy.app.core.AppGroupPresets.getInstalledFromGroup(this, "social")
            val set = blockedStore.getBlockedPackages().toMutableSet()
            set.addAll(inst)
            blockedStore.setBlockedPackages(set)
            adapter.updateList(allApps)
            adapter.notifyDataSetChanged()
        }
        chipGames?.setOnClickListener {
            val inst = com.brainbuddy.app.core.AppGroupPresets.getInstalledFromGroup(this, "games")
            val set = blockedStore.getBlockedPackages().toMutableSet()
            set.addAll(inst)
            blockedStore.setBlockedPackages(set)
            adapter.updateList(allApps)
            adapter.notifyDataSetChanged()
        }
        chipBrowsers?.setOnClickListener {
            val inst = com.brainbuddy.app.core.AppGroupPresets.getInstalledFromGroup(this, "browsers")
            val set = blockedStore.getBlockedPackages().toMutableSet()
            set.addAll(inst)
            blockedStore.setBlockedPackages(set)
            adapter.updateList(allApps)
            adapter.notifyDataSetChanged()
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.lowercase() ?: ""
                adapter.updateList(allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
            }
        })
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
