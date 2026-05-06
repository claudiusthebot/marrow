package rocks.talon.marrow.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import rocks.talon.marrow.phone.ui.MarrowApp

class MainActivity : ComponentActivity() {
    private val vm: MarrowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MarrowApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // Refresh both panes on resume so values stay live.
        vm.refreshPhone()
        vm.requestWatchInfo()
    }
}
