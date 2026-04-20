package io.github.xhugoliu.touckey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.xhugoliu.touckey.ui.TouckeyApp
import io.github.xhugoliu.touckey.ui.theme.TouckeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TouckeyTheme {
                TouckeyApp(
                    appContainer = (application as TouckeyApplication).appContainer,
                )
            }
        }
    }
}
