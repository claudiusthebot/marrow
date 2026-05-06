package rocks.talon.marrow.shared

import kotlinx.serialization.Serializable

/**
 * Marrow's device info wire format. The watch serialises one of these and ships
 * it back to the phone over the Data Layer; the phone renders it identically to
 * its own.
 */
@Serializable
data class DeviceInfoSnapshot(
    val capturedAtEpochMs: Long,
    val source: Source,
    val sections: List<Section>,
) {
    @Serializable
    enum class Source { PHONE, WEAR }
}

@Serializable
data class Section(
    val id: String,
    val title: String,
    val icon: String,
    val rows: List<Row>,
    /** Subtitle for the card preview — usually 1-2 most interesting rows joined. */
    val preview: String = "",
)

@Serializable
data class Row(
    val label: String,
    val value: String,
)

object Sections {
    const val DEVICE = "device"
    const val SYSTEM = "system"
    const val BATTERY = "battery"
    const val CPU = "cpu"
    const val MEMORY = "memory"
    const val STORAGE = "storage"
    const val DISPLAY = "display"
    const val NETWORK = "network"
    const val SENSORS = "sensors"
    const val CAMERAS = "cameras"
    const val BUILD_FLAGS = "build_flags"
    const val SOFTWARE = "software"
}

object MarrowPaths {
    /** Phone -> watch: "send me your device info". */
    const val REQUEST_INFO = "/marrow/device_info_request"
    /** Watch -> phone: payload (UTF-8 JSON of [DeviceInfoSnapshot]). */
    const val DEVICE_INFO = "/marrow/device_info"
    /** Watch -> phone: lightweight ping for the "say hi" debug action. */
    const val PING = "/marrow/ping"

    /** Capability published by the watch app. */
    const val CAPABILITY_PROVIDER = "marrow_device_info_provider"
}
