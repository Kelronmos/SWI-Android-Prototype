package com.trustsmotion.swiandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SWI Device Shield — Phase 1 real Android HOME launcher.
 *
 * This activity is registered with CATEGORY_HOME so the user can select
 * SWI as the default home application through Android system settings.
 *
 * It does not claim production authorization, device protection, or
 * independent verification. All security claims remain explicit.
 */
class SwiLauncherActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var drawerContainer: LinearLayout
    private lateinit var homeContent: LinearLayout

    private var showingDrawer = false
    private var installedApps: List<AppEntry> = emptyList()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    data class AppEntry(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannels()
        applyPersistedWallpaper()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 14, 18))
            setPadding(0, 24, 0, 0)
        }

        buildHome()
        setContentView(root)

        refreshClock()
        refreshStatusCards()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        refreshClock()
        refreshStatusCards()
        if (showingDrawer) {
            loadInstalledApps()
            renderDrawer(searchField.text?.toString().orEmpty())
        }
    }

    private fun buildHome() {
        root.removeAllViews()

        // Brand bar
        val brandBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 16, 28, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "TRUSTS MOTION  ·  SWI"
            textSize = 13f
            setTextColor(Color.rgb(242, 140, 40))
            setTypeface(null, Typeface.BOLD)
        }

        val governance = TextView(this).apply {
            text = "PROTOTYPE"
            textSize = 11f
            setTextColor(Color.rgb(160, 160, 170))
            setPadding(16, 0, 0, 0)
        }

        brandBar.addView(brand)
        brandBar.addView(governance)
        root.addView(brandBar)

        // Clock
        clockView = TextView(this).apply {
            textSize = 48f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 12, 28, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        dateView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(180, 180, 190))
            setPadding(28, 4, 28, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(clockView)
        root.addView(dateView)

        // Search
        searchField = EditText(this).apply {
            hint = "Search applications"
            setHintTextColor(Color.rgb(120, 120, 130))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(28, 30, 36))
            setPadding(24, 18, 24, 18)
            textSize = 16f
            setSingleLine(true)
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (showingDrawer) renderDrawer(s?.toString().orEmpty())
            }
        })
        val searchParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(20, 8, 20, 12) }
        root.addView(searchField, searchParams)

        // Content area (home or drawer)
        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        drawerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val scroll = ScrollView(this)
        val contentHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(homeContent)
            addView(drawerContainer)
        }
        scroll.addView(contentHolder)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        buildHomeContent()
        buildBottomBar()
    }

    private fun buildHomeContent() {
        homeContent.removeAllViews()

        // Status section
        val statusTitle = TextView(this).apply {
            text = "DEVICE STATUS"
            textSize = 13f
            setTextColor(Color.rgb(242, 140, 40))
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 8, 28, 8)
        }
        homeContent.addView(statusTitle)

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 16, 8)
        }
        homeContent.addView(statusContainer)

        // Quick actions
        val actionsTitle = TextView(this).apply {
            text = "QUICK ACTIONS"
            textSize = 13f
            setTextColor(Color.rgb(242, 140, 40))
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 16, 28, 8)
        }
        homeContent.addView(actionsTitle)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 8)
        }

        actions.addView(actionButton("APP DRAWER") { openDrawer() })
        actions.addView(actionButton("DEVICE DIAGNOSTICS") {
            startActivity(Intent(this@SwiLauncherActivity, DeviceDiagnosticsActivity::class.java))
        })
        actions.addView(actionButton("SWI AUTHORITY (BIOMETRIC)") {
            startActivity(Intent(this@SwiLauncherActivity, MainActivity::class.java))
        })
        actions.addView(actionButton("CHANGE WALLPAPER") { pickWallpaper() })
        actions.addView(actionButton("ANDROID SETTINGS") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        actions.addView(actionButton("SET DEFAULT LAUNCHER") {
            openDefaultLauncherSettings()
        })
        actions.addView(actionButton("RETURN TO SYSTEM LAUNCHER") {
            returnToSystemLauncher()
        })
        actions.addView(actionButton("SEND TEST NOTIFICATION") {
            sendTestNotification()
        })

        homeContent.addView(actions)

        // Governance footer
        val footer = TextView(this).apply {
            text = "SWI AUTHORIZED=NO\nPROVEN=NO  ·  SEALED=NO  ·  PRODUCTION_AUTHORIZED=NO\n" +
                "This is an experimental prototype. Monitoring is not protection."
            textSize = 11f
            setTextColor(Color.rgb(110, 110, 120))
            setPadding(28, 24, 28, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        homeContent.addView(footer)
    }

    private fun buildBottomBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(20, 22, 28))
            setPadding(12, 14, 12, 18)
            gravity = Gravity.CENTER
        }

        bar.addView(bottomButton("HOME") {
            showingDrawer = false
            homeContent.visibility = View.VISIBLE
            drawerContainer.visibility = View.GONE
            searchField.setText("")
            searchField.clearFocus()
        })
        bar.addView(bottomButton("APPS") { openDrawer() })
        bar.addView(bottomButton("DIAG") {
            startActivity(Intent(this@SwiLauncherActivity, DeviceDiagnosticsActivity::class.java))
        })
        bar.addView(bottomButton("AUTH") {
            startActivity(Intent(this@SwiLauncherActivity, MainActivity::class.java))
        })

        root.addView(bar)
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            setBackgroundColor(Color.rgb(32, 34, 42))
            setTextColor(Color.WHITE)
            textSize = 14f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 10)
            layoutParams = lp
        }
    }

    private fun bottomButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.rgb(242, 140, 40))
            textSize = 12f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
    }

    private fun openDrawer() {
        showingDrawer = true
        homeContent.visibility = View.GONE
        drawerContainer.visibility = View.VISIBLE
        loadInstalledApps()
        renderDrawer(searchField.text?.toString().orEmpty())
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList: List<ResolveInfo> = try {
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }

        installedApps = resolveList
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm)?.toString() ?: return@mapNotNull null
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                val act = ri.activityInfo?.name ?: return@mapNotNull null
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                AppEntry(label, pkg, act, icon)
            }
            .distinctBy { it.packageName + "/" + it.activityName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun renderDrawer(filter: String) {
        drawerContainer.removeAllViews()

        val title = TextView(this).apply {
            text = "APPLICATIONS (${installedApps.size})"
            textSize = 13f
            setTextColor(Color.rgb(242, 140, 40))
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 8, 28, 12)
        }
        drawerContainer.addView(title)

        val query = filter.trim().lowercase(Locale.getDefault())
        val filtered = if (query.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.lowercase(Locale.getDefault()).contains(query) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(query)
            }
        }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (installedApps.isEmpty()) {
                    "NO LAUNCHABLE APPLICATIONS VISIBLE\n" +
                        "Android package visibility rules may limit the list."
                } else {
                    "NO MATCH FOR \"$filter\""
                }
                textSize = 14f
                setTextColor(Color.rgb(160, 160, 170))
                setPadding(28, 24, 28, 24)
            }
            drawerContainer.addView(empty)
            return
        }

        filtered.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 14, 20, 14)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.rgb(22, 24, 30))
                setOnClickListener { launchApp(app) }
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    marginEnd = 20
                }
                if (app.icon != null) {
                    setImageDrawable(app.icon)
                } else {
                    setBackgroundColor(Color.rgb(50, 50, 60))
                }
            }

            val labelCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val name = TextView(this).apply {
                text = app.label
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            val pkg = TextView(this).apply {
                text = app.packageName
                textSize = 11f
                setTextColor(Color.rgb(130, 130, 140))
            }
            labelCol.addView(name)
            labelCol.addView(pkg)

            row.addView(iconView)
            row.addView(labelCol)

            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(12, 0, 12, 6) }
            drawerContainer.addView(row, rowParams)
        }
    }

    private fun launchApp(app: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to launch: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshClock() {
        val now = Date()
        clockView.text = timeFormat.format(now)
        dateView.text = dateFormat.format(now)
    }

    private fun refreshStatusCards() {
        statusContainer.removeAllViews()

        // Battery
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        addStatusCard(
            "BATTERY",
            if (level >= 0) "$level%${if (charging) " · CHARGING" else ""}" else "UNKNOWN",
            "BatteryManager"
        )

        // Storage
        val storageText = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val available = stat.availableBytes / (1024 * 1024 * 1024)
            val total = stat.totalBytes / (1024 * 1024 * 1024)
            "${available} GB free of ${total} GB"
        } catch (_: Exception) {
            "UNAVAILABLE"
        }
        addStatusCard("STORAGE", storageText, "StatFs")

        // Device
        addStatusCard(
            "DEVICE",
            "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Build"
        )

        // Network
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val netText = when {
            caps == null -> "NO ACTIVE NETWORK"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER / UNKNOWN"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        addStatusCard(
            "NETWORK",
            "$netText${if (validated) " · VALIDATED" else ""}",
            "ConnectivityManager"
        )

        // Security posture (honest)
        addStatusCard(
            "SWI SECURITY",
            "OBSERVATION ONLY\nNo active containment. Authority unknown.",
            "Local state"
        )
    }

    private fun addStatusCard(title: String, value: String, source: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(26, 28, 34))
            setPadding(20, 16, 20, 16)
        }

        val t = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.rgb(242, 140, 40))
            setTypeface(null, Typeface.BOLD)
        }
        val v = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 6, 0, 4)
        }
        val s = TextView(this).apply {
            text = "Source: $source"
            textSize = 11f
            setTextColor(Color.rgb(120, 120, 130))
        }

        card.addView(t)
        card.addView(v)
        card.addView(s)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 10) }
        statusContainer.addView(card, lp)
    }

    private fun pickWallpaper() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            startActivity(Intent.createChooser(intent, "Select wallpaper"))
            // Persist preference flag so we know user chose a wallpaper
            getSharedPreferences("swi_launcher", MODE_PRIVATE)
                .edit()
                .putBoolean("wallpaper_user_selected", true)
                .putLong("wallpaper_selected_at", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Toast.makeText(this, "Wallpaper picker unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPersistedWallpaper() {
        // Android system wallpaper is applied by the system wallpaper manager.
        // We only track that the user has selected one; we do not store the image.
        val prefs = getSharedPreferences("swi_launcher", MODE_PRIVATE)
        if (prefs.getBoolean("wallpaper_user_selected", false)) {
            // System already holds the wallpaper; nothing further required for persistence.
        }
    }

    private fun openDefaultLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun returnToSystemLauncher() {
        // Opens the system Home settings so the user can choose another launcher.
        // We never force-change the default ourselves.
        openDefaultLauncherSettings()
        Toast.makeText(
            this,
            "Select your preferred Home app in Android settings.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_INFO,
                "SWI Informational",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Low-priority SWI observations" },
            NotificationChannel(
                CHANNEL_WARNING,
                "SWI Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Moderate risk observations" },
            NotificationChannel(
                CHANNEL_HIGH,
                "SWI High Risk",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "High risk security observations" },
            NotificationChannel(
                CHANNEL_CRITICAL,
                "SWI Critical",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Critical observations requiring review" }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    private fun sendTestNotification() {
        val builder = NotificationCompat.Builder(this, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SWI Device Shield")
            .setContentText("Test notification channel is active. This is not a security event.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(9001, builder.build())
            Toast.makeText(this, "Test notification sent", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Notification permission required. Grant it in Android settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        const val CHANNEL_INFO = "swi_info"
        const val CHANNEL_WARNING = "swi_warning"
        const val CHANNEL_HIGH = "swi_high"
        const val CHANNEL_CRITICAL = "swi_critical"
    }
}
