package io.github.xhugoliu.touckey

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.xhugoliu.touckey.feature.control.ControlEnvironmentActionId
import io.github.xhugoliu.touckey.hid.BluetoothHidForegroundService
import io.github.xhugoliu.touckey.ui.TouckeyApp
import io.github.xhugoliu.touckey.ui.theme.TouckeyTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as TouckeyApplication).appContainer }

    private val bluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshAndEnsureRegistered()
        }

    private val bluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshAndEnsureRegistered()
        }

    private val bluetoothDiscoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            appContainer.sessionController.refreshState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureWindowMode()

        setContent {
            TouckeyTheme {
                TouckeyApp(
                    appContainer = appContainer,
                    onEnvironmentAction = ::handleEnvironmentAction,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        configureWindowMode()
        refreshAndEnsureRegistered()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureWindowMode()
        }
    }

    private fun handleEnvironmentAction(actionId: ControlEnvironmentActionId): String =
        when (actionId) {
            ControlEnvironmentActionId.GrantPermissions -> {
                requestBluetoothPermissions()
                "请在系统弹窗中授予 Nearby devices 权限。"
            }

            ControlEnvironmentActionId.EnableBluetooth -> {
                bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                "请在系统蓝牙弹窗里开启蓝牙。"
            }

            ControlEnvironmentActionId.RegisterHid -> {
                BluetoothHidForegroundService.start(this)
                appContainer.sessionController.ensureRegistered().message
            }

            ControlEnvironmentActionId.MakeDiscoverable -> {
                BluetoothHidForegroundService.start(this)
                val registration = appContainer.sessionController.ensureRegistered()
                if (!registration.accepted && !appContainer.sessionController.snapshot().isAppRegistered) {
                    registration.message
                } else {
                    val intent =
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                        }
                    bluetoothDiscoverableLauncher.launch(intent)
                    "请在系统弹窗里允许当前手机在 5 分钟内被电脑发现。"
                }
            }

            ControlEnvironmentActionId.StopKeepAlive -> {
                BluetoothHidForegroundService.stop(this)
                appContainer.sessionController.refreshState()
                "已请求停止前台服务；如果随后离开 App，系统可能自动注销 HID。"
            }

            ControlEnvironmentActionId.RefreshStatus -> {
                refreshAndEnsureRegistered()
                appContainer.sessionController.snapshot().detail
            }
        }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            refreshAndEnsureRegistered()
            return
        }

        bluetoothPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ),
        )
    }

    private fun refreshAndEnsureRegistered() {
        appContainer.sessionController.refreshState()
        appContainer.sessionController.ensureRegistered()
    }

    private fun configureWindowMode() {
        configureDisplayCutoutMode()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            if (isPortraitOrientation()) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun configureDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        window.attributes = params
    }

    private fun isPortraitOrientation(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}
