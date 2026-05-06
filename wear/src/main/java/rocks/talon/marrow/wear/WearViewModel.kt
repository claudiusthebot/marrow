package rocks.talon.marrow.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.talon.marrow.shared.DeviceInfoCollector
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.wear.sync.pingPhone

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val _snapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val snapshot: StateFlow<DeviceInfoSnapshot?> = _snapshot.asStateFlow()

    private val _pingState = MutableStateFlow(PingState.IDLE)
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                DeviceInfoCollector.collect(getApplication(), DeviceInfoSnapshot.Source.WEAR)
            }
            _snapshot.value = snap
        }
    }

    fun ping() {
        viewModelScope.launch {
            _pingState.value = PingState.SENDING
            val ok = withContext(Dispatchers.IO) { pingPhone(getApplication()) }
            _pingState.value = if (ok) PingState.SENT else PingState.FAILED
            kotlinx.coroutines.delay(1500)
            _pingState.value = PingState.IDLE
        }
    }

    enum class PingState { IDLE, SENDING, SENT, FAILED }
}
