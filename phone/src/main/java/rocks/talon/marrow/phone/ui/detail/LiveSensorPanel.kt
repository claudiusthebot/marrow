package rocks.talon.marrow.phone.ui.detail

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * Live readings for the most-watched sensors. Registers/unregisters listeners
 * with the Compose lifecycle so we don't drain battery in the background.
 */
@Composable
fun LiveSensorPanel() {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager }

    var accel by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    var lightLux by remember { mutableStateOf(-1f) }
    var proximityCm by remember { mutableStateOf(-1f) }
    var pressureHpa by remember { mutableStateOf(-1f) }
    var temperatureC by remember { mutableStateOf(-1f) }

    DisposableEffect(sm) {
        if (sm == null) return@DisposableEffect onDispose {}
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val pressureSensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val tempSensor = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accel = event.values.copyOf(3)
                    Sensor.TYPE_LIGHT -> lightLux = event.values[0]
                    Sensor.TYPE_PROXIMITY -> proximityCm = event.values[0]
                    Sensor.TYPE_PRESSURE -> pressureHpa = event.values[0]
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> temperatureC = event.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accelSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        proxSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        pressureSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        tempSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Accelerometer XYZ bars
        AccelRow("X", accel[0])
        AccelRow("Y", accel[1])
        AccelRow("Z", accel[2])
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScalarTile("Light", if (lightLux >= 0) "%.0f lx".format(lightLux) else "—", Modifier.weight(1f))
            ScalarTile("Proximity",
                if (proximityCm >= 0) (if (proximityCm < 1) "near" else "far ${"%.1f".format(proximityCm)} cm") else "—",
                Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScalarTile("Pressure",
                if (pressureHpa >= 0) "%.1f hPa".format(pressureHpa) else "—",
                Modifier.weight(1f))
            ScalarTile("Ambient temp",
                if (temperatureC > -50f) "%.1f °C".format(temperatureC) else "—",
                Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccelRow(label: String, value: Float) {
    val maxAbs = 19.6f  // ~2g range
    val fraction = (abs(value) / maxAbs).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // bar growing from centre — show absolute deflection on either side
            Box(
                modifier = Modifier
                    .fillMaxWidth(min(fraction, 1f))
                    .height(8.dp)
                    .background(if (value >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "%.2f".format(value),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(56.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ScalarTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
