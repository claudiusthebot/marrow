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

/**
 * Holds the shared device-info snapshot for every screen in the watch app and
 * the small ping-phone state machine.
 *
 * Important perf invariant: this VM is hoisted at the activity scope so the list
 * screen and any detail screen reuse the same instance — collection runs once on
 * launch, not on every navigation. All collection work happens on `Dispatchers.IO`.
 */
class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val _snapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val snapshot: StateFlow<DeviceInfoSnapshot?> = _snapshot.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _pingState = MutableStateFlow(PingState.IDLE)
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    init {
        // Kick off the first collection eagerly — UI shows a progress indicator
        // until the StateFlow flips from null to a snapshot.
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                DeviceInfoCollector.collect(getApplication(), DeviceInfoSnapshot.Source.WEAR)
            }
            _snapshot.value = snap
            _refreshing.value = false
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
