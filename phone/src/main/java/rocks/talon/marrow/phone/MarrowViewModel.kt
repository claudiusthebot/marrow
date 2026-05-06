package rocks.talon.marrow.phone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.talon.marrow.phone.sync.WatchInfoRepository
import rocks.talon.marrow.shared.DeviceInfoCollector
import rocks.talon.marrow.shared.DeviceInfoSnapshot

class MarrowViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchInfoRepository.get(app)

    private val _phoneSnapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val phoneSnapshot: StateFlow<DeviceInfoSnapshot?> = _phoneSnapshot.asStateFlow()

    private val _watchRefreshing = MutableStateFlow(false)
    val watchRefreshing: StateFlow<Boolean> = _watchRefreshing.asStateFlow()

    val watchSnapshot: StateFlow<DeviceInfoSnapshot?> = repo.liveSnapshot
    val watchConnection: StateFlow<WatchInfoRepository.WatchConnectionState> = repo.connection

    val cachedWatchSnapshot: StateFlow<DeviceInfoSnapshot?> =
        repo.cachedSnapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        refreshPhone()
        // Kick off a watch refresh on startup so the Watch tab has data ready.
        requestWatchInfo()
    }

    fun refreshPhone() {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                DeviceInfoCollector.collect(getApplication(), DeviceInfoSnapshot.Source.PHONE)
            }
            _phoneSnapshot.value = snap
        }
    }

    fun requestWatchInfo() {
        if (_watchRefreshing.value) return
        viewModelScope.launch {
            _watchRefreshing.value = true
            try {
                repo.requestRefresh()
            } finally {
                _watchRefreshing.value = false
            }
        }
    }
}
