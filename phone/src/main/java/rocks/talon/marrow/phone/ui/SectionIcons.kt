package rocks.talon.marrow.phone.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import rocks.talon.marrow.shared.Sections

/** Maps the icon hint that lives in the wire format to a real Material icon. */
fun iconFor(sectionId: String): ImageVector = when (sectionId) {
    Sections.DEVICE -> Icons.Outlined.PhoneAndroid
    Sections.SYSTEM -> Icons.Outlined.Settings
    Sections.BATTERY -> Icons.Outlined.BatteryFull
    Sections.CPU -> Icons.Outlined.Speed
    Sections.MEMORY -> Icons.Outlined.Memory
    Sections.STORAGE -> Icons.Outlined.Storage
    Sections.DISPLAY -> Icons.Outlined.Monitor
    Sections.NETWORK -> Icons.Outlined.Wifi
    Sections.SENSORS -> Icons.Outlined.Sensors
    Sections.CAMERAS -> Icons.Outlined.CameraAlt
    Sections.BUILD_FLAGS -> Icons.Outlined.Tag
    else -> Icons.Outlined.Tag
}
