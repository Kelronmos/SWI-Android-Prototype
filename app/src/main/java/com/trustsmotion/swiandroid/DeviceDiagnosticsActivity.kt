package com.trustsmotion.swiandroid

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import android.view.ViewGroup

class DeviceDiagnosticsActivity : Activity() {

    private lateinit var report: TextView

    private fun addText(
        parent: LinearLayout,
        value: String,
        bold: Boolean = false,
        size: Float = 15f
    ) {
        val view = TextView(this)
        view.text = value
        view.textSize = size
        view.setPadding(12, 9, 12, 9)
        if (bold) view.setTypeface(null, Typeface.BOLD)
        parent.addView(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(16, 16, 16, 16)

        val heading = TextView(this)
        heading.text = "SWI | DEVICE DIAGNOSTICS"
        heading.textSize = 22f
        heading.setTypeface(null, Typeface.BOLD)
        root.addView(heading)

        addText(
            root,
            "PASSIVE MODE\nLocal inventory only. No network scanning, " +
            "device pairing, data upload or automatic execution."
        )

        val refresh = Button(this)
        refresh.text = "REFRESH DIAGNOSTICS"
        root.addView(refresh)

        report = TextView(this)
        report.textSize = 14f
        report.setPadding(8, 12, 8, 12)
        root.addView(
            report,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        refresh.setOnClickListener { refreshReport() }
        refreshReport()
    }

    private fun refreshReport() {
        val out = StringBuilder()

        out.appendLine("===== DEVICE =====")
        out.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        out.appendLine("Model: ${Build.MODEL}")
        out.appendLine("Android: ${Build.VERSION.RELEASE}")
        out.appendLine("SDK: ${Build.VERSION.SDK_INT}")
        out.appendLine("")

        out.appendLine("===== SENSOR INVENTORY =====")

        val manager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val sensors = manager.getSensorList(Sensor.TYPE_ALL)

        out.appendLine("SENSOR_COUNT=${sensors.size}")

        if (sensors.isEmpty()) {
            out.appendLine("SENSOR_INVENTORY=EMPTY")
        }

        sensors.forEachIndexed { index, sensor ->
            out.appendLine("${index + 1}. ${sensor.name}")
            out.appendLine("   TYPE=${sensor.stringType}")
            out.appendLine("   VENDOR=${sensor.vendor}")
            out.appendLine("   VERSION=${sensor.version}")
            out.appendLine("   WAKE_UP=${sensor.isWakeUpSensor}")
            out.appendLine("   STATUS=REPORTED_BY_ANDROID")
        }

        out.appendLine("")
        out.appendLine("===== HARDWARE CAPABILITIES =====")

        val pm = packageManager

        val gps = pm.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LOCATION_GPS
        )
        val bluetooth = pm.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_BLUETOOTH
        )
        val wifi = pm.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WIFI
        )
        val camera = pm.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_CAMERA_ANY
        )

        out.appendLine("GPS_HARDWARE=$gps")
        out.appendLine("BLUETOOTH_HARDWARE=$bluetooth")
        out.appendLine("WIFI_HARDWARE=$wifi")
        out.appendLine("CAMERA_HARDWARE=$camera")

        out.appendLine("")
        out.appendLine("===== NETWORK TRANSPORTS =====")

        val cm = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val networks = cm.allNetworks
        out.appendLine("ANDROID_NETWORK_COUNT=${networks.size}")

        if (networks.isEmpty()) {
            out.appendLine("NETWORK_STATE=NO_NETWORK_REPORTED")
        }

        networks.forEachIndexed { index, network ->
            val caps = cm.getNetworkCapabilities(network)
            out.appendLine("NETWORK_${index + 1}:")

            if (caps == null) {
                out.appendLine("  CAPABILITIES=UNKNOWN")
            } else {
                val transports = mutableListOf<String>()

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    transports.add("WIFI")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    transports.add("CELLULAR")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    transports.add("ETHERNET")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    transports.add("VPN")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
                    transports.add("BLUETOOTH")

                out.appendLine(
                    "  TRANSPORTS=${transports.joinToString(",").ifEmpty { "UNKNOWN" }}"
                )
                out.appendLine("  INTERNET_CAPABILITY=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                out.appendLine("  VALIDATED_BY_ANDROID=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                out.appendLine("  NOT_METERED=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}")
            }
        }

        out.appendLine("")
        out.appendLine("===== SWI EVIDENCE BOUNDARIES =====")
        out.appendLine("CAPABILITY_REPORTED=YES")
        out.appendLine("SENSOR_FUNCTION_TESTED=NO")
        out.appendLine("NETWORK_SECURITY_PROVEN=NO")
        out.appendLine("DEVICE_IDENTITY_VERIFIED=NO")
        out.appendLine("HUMAN_AUTHORITY_BOUND=UNKNOWN")
        out.appendLine("EXECUTION_PERMITTED=NO")
        out.appendLine("")
        out.appendLine("NOTE:")
        out.appendLine("Android-reported capability is not independent proof.")
        out.appendLine("Network presence is not permission to communicate.")
        out.appendLine("No nearby-device discovery or transmission was performed.")

        report.text = out.toString()
    }
}
