package rocks.talon.marrow.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import rocks.talon.marrow.phone.ui.MarrowApp

class MainActivity : ComponentActivity() {
    private val vm: MarrowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { MarrowApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPhone()
        vm.requestWatchInfo()
    }
}
