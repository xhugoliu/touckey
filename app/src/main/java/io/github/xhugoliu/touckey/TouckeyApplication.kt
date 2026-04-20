package io.github.xhugoliu.touckey

import android.app.Application
import io.github.xhugoliu.touckey.app.AppContainer

class TouckeyApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer()
    }
}
