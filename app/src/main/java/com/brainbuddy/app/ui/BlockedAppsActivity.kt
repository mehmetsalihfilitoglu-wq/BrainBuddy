package com.brainbuddy.app.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.brainbuddy.app.R
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.InstalledAppsHelper
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.receiver.PackageChangeReceiver
import com.google.android.material.snackbar.Snackbar

class BlockedAppsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BlockedApps"
        const val EXTRA_HIGHLIGHT_PACKAGE = "highlight_package"
        const val EXTRA_QUICK_BLOCK_PACKAGE = "quick_block_package"
        const val EXTRA_OPEN_SOCIAL_PRESET = "open_social_preset"

        // Process-level in-memory cache — survives config changes and re-opens
        // within the same process. Invalidated only on package install/remove.
        private var cachedApps: List<AppInfo>? = null
        private var cachedAppsWithIcons: List<AppInfo>? = null
        private var cacheTimestamp: Long = 0L

        fun invalidateCache() {
            cachedApps = null
            cachedAppsWithIcons = null
            cacheTimestamp = 0L
        }
    }

    private lateinit var blockedStore: BlockedAppsStore
    private lateinit var adapter: AppListAdapter
    private lateinit var textLoading: TextView
    private lateinit var recycler: RecyclerView
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, BlockedAppsActivity::class.java)) return

        setContentView(R.layout.activity_blocked_apps)

        blockedStore = BlockedAppsStore(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.parent_blocked_apps)

        textLoading = findViewById(R.id.textLoading)
        recycler = findViewById(R.id.recycler)

        // Read blocked set ONCE, keep in memory
        val blockedSet = blockedStore.getBlockedPackages()
        adapter = AppListAdapter(blockedSet) { pkg, blocked ->
            val set = adapter.blockedSet.toMutableSet()
            if (blocked) set.add(pkg) else set.remove(pkg)
            blockedStore.setBlockedPackages(set)
            adapter.blockedSet = set
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(20)
        recycler.adapter = adapter

        loadApps()

        val searchBox = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchBox)
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    adapter.submitList(allApps)
                } else {
                    adapter.submitList(allApps.filter {
                        it.labelLower.contains(q) || it.packageName.contains(q)
                    })
                }
            }
        })

        handleQuickBlockIntent()
    }

    private fun loadApps() {
        val t0 = SystemClock.elapsedRealtime()

        // Fast path: use process cache if available
        val cached = cachedAppsWithIcons ?: cachedApps
        if (cached != null) {
            Log.d(TAG, "Cache hit: ${cached.size} apps in ${SystemClock.elapsedRealtime() - t0}ms")
            allApps = cached
            adapter.submitList(cached)
            showList()
            if (cachedAppsWithIcons == null) {
                loadIconsAsync(cached)
            }
            return
        }

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = packageManager
                val myPkg = packageName

                // Use queryIntentActivities with LAUNCHER — only gets apps visible
                // in the launcher. Much smaller set than getInstalledApplications.
                // Also resolves labels via ResolveInfo.loadLabel which is faster.
                val tQuery = SystemClock.elapsedRealtime()
                val launchable = InstalledAppsHelper.getLaunchablePackages(pm)
                Log.d(TAG, "queryIntentActivities: ${launchable.size} apps in ${SystemClock.elapsedRealtime() - tQuery}ms")

                val tLabel = SystemClock.elapsedRealtime()
                val result = launchable.mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == myPkg) return@mapNotNull null
                    val label = InstalledAppsHelper.getAppLabel(pm, ri)
                    if (label.isBlank()) return@mapNotNull null
                    AppInfo(pkg, label, label.lowercase(), icon = null)
                }
                Log.d(TAG, "Label resolve: ${result.size} apps in ${SystemClock.elapsedRealtime() - tLabel}ms")

                // Deduplicate by package name (some apps register multiple launcher activities)
                val tSort = SystemClock.elapsedRealtime()
                val deduped = result
                    .distinctBy { it.packageName }
                    .sortedBy { it.labelLower }
                Log.d(TAG, "Dedup+sort in ${SystemClock.elapsedRealtime() - tSort}ms")

                deduped
            }

            val submitTime = SystemClock.elapsedRealtime()
            allApps = loaded
            cachedApps = loaded
            cacheTimestamp = SystemClock.elapsedRealtime()
            adapter.submitList(loaded)
            showList()
            Log.d(TAG, "First list visible: ${loaded.size} apps in ${submitTime - t0}ms total")

            // Icons loaded async, won't block list display
            loadIconsAsync(loaded)
        }
    }

    private fun showList() {
        textLoading.visibility = View.GONE
        recycler.visibility = View.VISIBLE
    }

    private fun loadIconsAsync(apps: List<AppInfo>) {
        lifecycleScope.launch {
            val tIcon = SystemClock.elapsedRealtime()
            val pm = packageManager
            val ctx = this@BlockedAppsActivity

            // Load icons in batches on IO thread
            val updated = withContext(Dispatchers.IO) {
                apps.map { app ->
                    val icon = try {
                        InstalledAppsHelper.getAppIcon(pm, app.packageName, ctx)
                    } catch (_: Exception) { null }
                    app.copy(icon = icon)
                }
            }

            Log.d(TAG, "Icons loaded: ${apps.size} apps in ${SystemClock.elapsedRealtime() - tIcon}ms")
            allApps = updated
            cachedAppsWithIcons = updated
            adapter.submitList(updated)
        }
    }

    private fun handleQuickBlockIntent() {
        val pkg = intent?.getStringExtra(EXTRA_QUICK_BLOCK_PACKAGE)
            ?: intent?.getStringExtra(EXTRA_HIGHLIGHT_PACKAGE) ?: return
        val trimmed = pkg.trim().takeIf { it.isNotBlank() } ?: return
        intent?.removeExtra(EXTRA_QUICK_BLOCK_PACKAGE)
        intent?.removeExtra(EXTRA_HIGHLIGHT_PACKAGE)
        if (blockedStore.isBlocked(trimmed)) return

        val appName = try {
            val ai = packageManager.getApplicationInfo(trimmed, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { trimmed }

        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, getString(R.string.notif_new_social_text, appName), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.preset_confirm_block)) {
                val set = adapter.blockedSet.toMutableSet()
                set.add(trimmed)
                blockedStore.setBlockedPackages(set)
                adapter.blockedSet = set
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
                Toast.makeText(this, getString(R.string.preset_blocked_success), Toast.LENGTH_SHORT).show()
            }
            .setActionTextColor(getColor(R.color.bb_primary))
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Only reload if a package was actually installed/removed
        if (PackageChangeReceiver.isDirty(this)) {
            PackageChangeReceiver.clearDirtyFlag(this)
            invalidateCache()
            loadApps()
        } else {
            // Refresh blocked state without reloading app list
            adapter.blockedSet = blockedStore.getBlockedPackages()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    data class AppInfo(
        val packageName: String,
        val label: String,
        val labelLower: String,
        val icon: Drawable?
    )

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(old: AppInfo, new: AppInfo) =
            old.packageName == new.packageName

        override fun areContentsTheSame(old: AppInfo, new: AppInfo) =
            old.packageName == new.packageName &&
            old.label == new.label &&
            old.icon === new.icon
    }

    class AppListAdapter(
        var blockedSet: Set<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : ListAdapter<AppInfo, AppListAdapter.VH>(AppDiffCallback()) {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long =
            getItem(position).packageName.hashCode().toLong()

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
            val app = getItem(position)
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon)
            } else {
                holder.icon.setImageResource(R.drawable.ic_default_app)
            }
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            // Use cached blockedSet — no SharedPreferences read per bind
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = app.packageName in blockedSet
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app.packageName, isChecked)
            }
        }
    }
}
