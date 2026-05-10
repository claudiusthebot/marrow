package rocks.talon.marrow.shared

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import java.io.File
import java.net.NetworkInterface
import java.util.Locale

/**
 * Gathers a [DeviceInfoSnapshot] for the device this code runs on.
 *
 * Designed to be safe on both phone and Wear OS — sections that don't apply to a
 * form factor are either omitted (e.g. cameras on watch when none) or labelled
 * "not available". Permission-gated values are skipped silently.
 */
object DeviceInfoCollector {

    fun collect(context: Context, source: DeviceInfoSnapshot.Source): DeviceInfoSnapshot {
        val sections = buildList {
            add(deviceSection())
            add(systemSection())
            add(batterySection(context))
            add(cpuSection())
            add(memorySection(context))
            add(storageSection())
            add(displaySection(context))
            add(networkSection(context))
            add(sensorsSection(context))
            cameraSection(context)?.let { add(it) }
            add(buildFlagsSection())
            add(softwareSection(context))
            gpuSection()?.let { add(it) }
        }
        return DeviceInfoSnapshot(
            capturedAtEpochMs = System.currentTimeMillis(),
            source = source,
            sections = sections,
        )
    }

    // -- Software ------------------------------------------------------------

    private fun softwareSection(context: Context): Section {
        val pm = context.packageManager
        val rows = mutableListOf<Row>()
        rows += Row("Java VM", System.getProperty("java.vm.version") ?: "?")
        rows += Row("Java vendor", System.getProperty("java.vendor") ?: "?")
        rows += Row("Java home", System.getProperty("java.home") ?: "?")
        rows += Row("OS arch", System.getProperty("os.arch") ?: "?")
        rows += Row("Locale", java.util.Locale.getDefault().toLanguageTag())
        rows += Row("Time zone", java.util.TimeZone.getDefault().id)
        runCatching {
            val all = pm.getInstalledApplications(0)
            val system = all.count { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
            val user = all.size - system
            rows += Row("Installed apps", "${all.size} (${user} user · ${system} system)")
        }
        runCatching {
            rows += Row("Runtime", System.getProperty("java.runtime.version") ?: "?")
            rows += Row("Class version", System.getProperty("java.class.version") ?: "?")
        }
        rows += Row("Heap (max)", formatBytes(Runtime.getRuntime().maxMemory()))
        rows += Row("Heap (total)", formatBytes(Runtime.getRuntime().totalMemory()))
        rows += Row("Heap (free)", formatBytes(Runtime.getRuntime().freeMemory()))
        return Section(
            id = Sections.SOFTWARE,
            title = "Software",
            icon = "software",
            rows = rows,
            preview = System.getProperty("java.vm.version") ?: "ART",
        )
    }

    // -- Device --------------------------------------------------------------

    private fun deviceSection(): Section {
        val rows = listOf(
            Row("Manufacturer", Build.MANUFACTURER),
            Row("Brand", Build.BRAND),
            Row("Model", Build.MODEL),
            Row("Device", Build.DEVICE),
            Row("Product", Build.PRODUCT),
            Row("Board", Build.BOARD),
            Row("Hardware", Build.HARDWARE),
            Row("Display ID", Build.DISPLAY),
            Row("Fingerprint", Build.FINGERPRINT),
            Row("Bootloader", Build.BOOTLOADER),
        )
        return Section(
            id = Sections.DEVICE,
            title = "Device",
            icon = "device",
            rows = rows,
            preview = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    // -- System --------------------------------------------------------------

    private fun systemSection(): Section {
        val kernel = runCatching { File("/proc/version").readText().trim() }.getOrDefault(
            System.getProperty("os.version") ?: "?",
        )
        val rows = listOf(
            Row("Android version", Build.VERSION.RELEASE),
            Row("SDK", Build.VERSION.SDK_INT.toString()),
            Row("Codename", Build.VERSION.CODENAME),
            Row("Incremental", Build.VERSION.INCREMENTAL),
            Row("Security patch", Build.VERSION.SECURITY_PATCH),
            Row("Build type", Build.TYPE),
            Row("Build tags", Build.TAGS),
            Row("Build user", Build.USER),
            Row("Build host", Build.HOST),
            Row("Build time", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(Build.TIME))),
            Row("Java VM", System.getProperty("java.vm.version") ?: "?"),
            Row("Kernel", kernel),
        )
        return Section(
            id = Sections.SYSTEM,
            title = "System",
            icon = "system",
            rows = rows,
            preview = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
    }

    // -- Battery -------------------------------------------------------------

    private fun batterySection(context: Context): Section {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugged = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "Unplugged"
        }
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"

        val chargeCounterUah = mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        val currentNowUa = mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val currentAvgUa = mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE
        val energyCounterNwh = mgr?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) ?: Long.MIN_VALUE
        val capacityPct = mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE

        val rows = buildList {
            add(Row("Level", if (pct >= 0) "$pct%" else "?"))
            add(Row("Status", status))
            add(Row("Health", health))
            add(Row("Plugged", plugged))
            add(Row("Technology", technology))
            if (voltageMv >= 0) add(Row("Voltage", "$voltageMv mV"))
            if (tempTenths >= 0) add(Row("Temperature", "%.1f °C".format(tempTenths / 10f)))
            if (capacityPct != Int.MIN_VALUE) add(Row("Capacity gauge", "$capacityPct%"))
            if (chargeCounterUah != Int.MIN_VALUE) add(Row("Charge counter", "${chargeCounterUah / 1000} mAh"))
            if (currentNowUa != Int.MIN_VALUE) add(Row("Current (now)", "%.0f mA".format(currentNowUa / 1000f)))
            if (currentAvgUa != Int.MIN_VALUE) add(Row("Current (avg)", "%.0f mA".format(currentAvgUa / 1000f)))
            if (energyCounterNwh != Long.MIN_VALUE && energyCounterNwh != 0L) {
                add(Row("Energy counter", "${energyCounterNwh / 1_000_000_000L} Wh"))
            }
        }
        val preview = if (pct >= 0) "$pct% — $status" else status
        return Section(Sections.BATTERY, "Battery", "battery", rows, preview)
    }

    // -- CPU -----------------------------------------------------------------

    private fun cpuSection(): Section {
        val cores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val rows = mutableListOf(
            Row("Cores", cores.toString()),
            Row("ABIs", abis),
            Row("32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ")),
            Row("64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ")),
        )
        for (i in 0 until cores) {
            val cur = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")?.trim()?.toLongOrNull()
            val min = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")?.trim()?.toLongOrNull()
            val max = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")?.trim()?.toLongOrNull()
            val gov = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")?.trim()
            val parts = buildList {
                if (cur != null) add("now ${cur / 1000} MHz")
                if (min != null && max != null) add("${min / 1000}-${max / 1000} MHz")
                if (gov != null) add(gov)
            }
            if (parts.isNotEmpty()) rows.add(Row("CPU $i", parts.joinToString(" · ")))
        }
        return Section(Sections.CPU, "CPU", "cpu", rows, "$cores cores · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
    }

    // -- Memory --------------------------------------------------------------

    private fun memorySection(context: Context): Section {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rows = listOf(
            Row("Total RAM", formatBytes(mi.totalMem)),
            Row("Available RAM", formatBytes(mi.availMem)),
            Row("Threshold", formatBytes(mi.threshold)),
            Row("Low memory", mi.lowMemory.toString()),
        )
        val pct = if (mi.totalMem > 0) (100 * mi.availMem / mi.totalMem) else -1
        return Section(
            Sections.MEMORY, "Memory", "memory", rows,
            "${formatBytes(mi.totalMem)} total · ${pct}% free",
        )
    }

    // -- Storage -------------------------------------------------------------

    private fun storageSection(): Section {
        val rows = mutableListOf<Row>()
        val internalRoot = Environment.getDataDirectory()
        val internal = StatFs(internalRoot.path)
        val internalTotal = internal.blockCountLong * internal.blockSizeLong
        val internalAvail = internal.availableBlocksLong * internal.blockSizeLong
        rows += Row("Internal — total", formatBytes(internalTotal))
        rows += Row("Internal — available", formatBytes(internalAvail))

        val external = Environment.getExternalStorageDirectory()
        runCatching {
            val s = StatFs(external.path)
            val total = s.blockCountLong * s.blockSizeLong
            val avail = s.availableBlocksLong * s.blockSizeLong
            rows += Row("External — total", formatBytes(total))
            rows += Row("External — available", formatBytes(avail))
        }
        rows += Row("External state", Environment.getExternalStorageState())
        return Section(
            Sections.STORAGE, "Storage", "storage", rows,
            "${formatBytes(internalTotal)} total · ${formatBytes(internalAvail)} free",
        )
    }

    // -- Display -------------------------------------------------------------

    private fun displaySection(context: Context): Section {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val refresh = display.refreshRate
        val supportedRefresh = display.supportedModes.map { "%.0f Hz".format(it.refreshRate) }.distinct()
        val hdr = display.hdrCapabilities?.supportedHdrTypes?.joinToString(", ") {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                else -> "type $it"
            }
        }?.ifEmpty { "none" } ?: "none"

        val rows = listOf(
            Row("Resolution", "${metrics.widthPixels} × ${metrics.heightPixels} px"),
            Row("Density", "${metrics.densityDpi} dpi"),
            Row("Scaled density", "%.2f".format(metrics.scaledDensity)),
            Row("Refresh rate", "%.1f Hz".format(refresh)),
            Row("Modes", supportedRefresh.joinToString(", ")),
            Row("HDR", hdr),
        )
        return Section(
            Sections.DISPLAY, "Display", "display", rows,
            "${metrics.widthPixels}×${metrics.heightPixels} · %.0f Hz".format(refresh),
        )
    }

    // -- Network -------------------------------------------------------------

    private fun networkSection(context: Context): Section {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        val rows = mutableListOf(
            Row("Connection", transport),
        )
        if (caps != null) {
            rows += Row("Validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString())
            rows += Row("Internet", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).toString())
            val down = caps.linkDownstreamBandwidthKbps
            val up = caps.linkUpstreamBandwidthKbps
            if (down > 0) rows += Row("Downstream", "$down kbps")
            if (up > 0) rows += Row("Upstream", "$up kbps")
        }

        // Wi-Fi (only fields that are still readable without ACCESS_FINE_LOCATION on
        // modern Android). RSSI and frequency are still public via WifiManager.
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            if (info != null) {
                rows += Row("Wi-Fi RSSI", "${info.rssi} dBm")
                rows += Row("Wi-Fi link speed", "${info.linkSpeed} Mbps")
                if (info.frequency > 0) rows += Row("Wi-Fi frequency", "${info.frequency} MHz")
                val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"")
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") rows += Row("Wi-Fi SSID", ssid)
            }
        }

        // Cellular (no privileged perms — only operator name, MCC/MNC, network type).
        runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                val opName = tm.networkOperatorName
                if (!opName.isNullOrEmpty()) rows += Row("Carrier", opName)
                val op = tm.networkOperator
                if (!op.isNullOrEmpty() && op.length >= 5) {
                    rows += Row("MCC", op.substring(0, 3))
                    rows += Row("MNC", op.substring(3))
                }
                rows += Row("SIM country", tm.simCountryIso?.uppercase(Locale.US) ?: "?")
            }
        }

        // IP addresses
        runCatching {
            val ips = mutableListOf<String>()
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (!addr.isLinkLocalAddress && !addr.isLoopbackAddress) {
                        ips += "${nif.name}: ${addr.hostAddress?.substringBefore('%')}"
                    }
                }
            }
            if (ips.isNotEmpty()) rows += Row("IPs", ips.joinToString("\n"))
        }

        return Section(Sections.NETWORK, "Network", "network", rows, transport)
    }

    // -- Sensors -------------------------------------------------------------

    private fun sensorsSection(context: Context): Section {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val all = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
        val rows = all.map { s ->
            val parts = buildList {
                add(s.vendor)
                add("v${s.version}")
                add("type ${s.type}")
                if (s.maximumRange > 0) add("range %.2f".format(s.maximumRange))
                if (s.power > 0) add("%.2f mA".format(s.power))
            }
            Row(s.name, parts.joinToString(" · "))
        }
        return Section(Sections.SENSORS, "Sensors", "sensors", rows, "${all.size} sensors")
    }

    // -- Cameras -------------------------------------------------------------

    private fun cameraSection(context: Context): Section? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        val ids = runCatching { cm.cameraIdList }.getOrNull() ?: return null
        if (ids.isEmpty()) return null
        val rows = mutableListOf<Row>()
        for (id in ids) {
            val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "?"
            }
            val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let { map ->
                map.getOutputSizes(android.graphics.ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
            }
            val maxRes = sizes?.let { "${it.width}×${it.height}" } ?: "?"
            val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.joinToString(", ") { "%.1fmm".format(it) }
                ?: "?"
            val sensorPx = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                ?.let { "${it.width}×${it.height}" } ?: "?"
            rows += Row("Camera $id ($facing)", "max $maxRes · sensor $sensorPx · $focals")
        }
        return Section(
            Sections.CAMERAS, "Cameras", "cameras", rows,
            "${ids.size} camera${if (ids.size == 1) "" else "s"}",
        )
    }

    // -- Build flags / treble ------------------------------------------------

    private fun buildFlagsSection(): Section {
        val rows = mutableListOf<Row>()
        rows += Row("Radio", Build.getRadioVersion() ?: "?")
        rows += Row("ID", Build.ID)
        runCatching {
            val trebleFile = File("/system/etc/ld.config.txt")
            rows += Row("Treble (ld.config)", trebleFile.exists().toString())
        }
        runCatching {
            val sysProps = listOf(
                "ro.treble.enabled" to "Treble enabled",
                "ro.vndk.version" to "VNDK version",
                "ro.product.first_api_level" to "First API level",
                "ro.build.version.security_patch" to "Security patch",
                "ro.boot.verifiedbootstate" to "Verified boot state",
            )
            for ((prop, label) in sysProps) {
                val value = readSysProp(prop)
                if (!value.isNullOrEmpty()) rows += Row(label, value)
            }
        }
        return Section(Sections.BUILD_FLAGS, "Build flags", "flags", rows, "ID ${Build.ID}")
    }

    // -- GPU -----------------------------------------------------------------

    /**
     * Probes the GPU sysfs entries and returns a [Section] when at least one
     * frequency value is readable. Returns null on emulators or devices where
     * SELinux blocks access so the section is simply omitted rather than shown
     * as "unavailable."
     */
    private fun gpuSection(): Section? {
        val rows = mutableListOf<Row>()

        // Qualcomm Adreno (kgsl) — most popular Android GPU family
        val kgslDevfreq = "/sys/class/kgsl/kgsl-3d0/devfreq"
        val kgslPath = File(kgslDevfreq)
        if (kgslPath.exists()) {
            val cur = readSysFile("$kgslDevfreq/cur_freq")?.trim()?.toLongOrNull()
            val min = readSysFile("$kgslDevfreq/min_freq")?.trim()?.toLongOrNull()
            val max = readSysFile("$kgslDevfreq/max_freq")?.trim()?.toLongOrNull()
            val gov = readSysFile("$kgslDevfreq/governor")?.trim()
            val busy = readSysFile("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.trim()?.toLongOrNull()

            rows += Row("GPU family", "Adreno (Qualcomm Snapdragon)")
            if (cur != null) rows += Row("Frequency (current)", "${cur / 1_000_000L} MHz")
            if (min != null && max != null) rows += Row(
                "Frequency range",
                "${min / 1_000_000L}–${max / 1_000_000L} MHz",
            )
            if (gov != null) rows += Row("Governor", gov)
            if (busy != null && busy in 0..100) rows += Row("Utilisation", "$busy%")

            val preview = buildString {
                if (cur != null) append("${cur / 1_000_000L} MHz")
                if (busy != null && busy >= 0) append(" · $busy%")
                if (gov != null) append(" · $gov")
            }
            return Section(Sections.GPU, "GPU", "gpu", rows, preview.ifBlank { "Adreno" })
        }

        // Generic devfreq — Mali, PowerVR, IMG GPU (Google Tensor), etc.
        val gpuEntry = runCatching {
            File("/sys/class/devfreq").listFiles()?.firstOrNull { f ->
                val name = f.name.lowercase()
                name.contains("gpu") || name.contains("mali") ||
                    name.contains("pvr") || name.contains("rogue") ||
                    name.contains("sgx") || name.contains("g3d")
            }
        }.getOrNull() ?: return null

        val p = runCatching { gpuEntry.canonicalPath }.getOrElse { gpuEntry.absolutePath }
        val cur = readSysFile("$p/cur_freq")?.trim()?.toLongOrNull()
        val min = readSysFile("$p/min_freq")?.trim()?.toLongOrNull()
        val max = readSysFile("$p/max_freq")?.trim()?.toLongOrNull()
        val gov = readSysFile("$p/governor")?.trim()
        val load = readSysFile("$p/load")?.trim()?.toLongOrNull()?.toInt()

        if (cur == null && max == null) return null  // nothing readable

        rows += Row("GPU driver", gpuEntry.name)
        if (cur != null) rows += Row("Frequency (current)", "${cur / 1_000_000L} MHz")
        if (min != null && max != null) rows += Row(
            "Frequency range",
            "${min / 1_000_000L}–${max / 1_000_000L} MHz",
        )
        if (gov != null) rows += Row("Governor", gov)
        if (load != null && load in 0..100) rows += Row("Utilisation", "$load%")

        val preview = buildString {
            if (cur != null) append("${cur / 1_000_000L} MHz")
            if (load != null && load >= 0) append(" · $load%")
        }
        return Section(Sections.GPU, "GPU", "gpu", rows, preview.ifBlank { gpuEntry.name })
    }

    // -- helpers -------------------------------------------------------------

    private fun readSysFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    private fun readSysProp(key: String): String? = runCatching {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().trim().ifEmpty { null }
    }.getOrNull()

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        return "%.2f %s".format(v, units[i])
    }
}
