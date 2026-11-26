package app.yaooo.android

import android.app.Application
import app.yaooo.AndroidNotifier
import app.yaooo.AndroidUpdateScheduler
import app.yaooo.AppContainer
import app.yaooo.AppHolder
import app.yaooo.DatabaseDriverFactory

class YaoooApplication : Application() {
    /** Activity registers this so enabling notifications in settings can trigger the permission dialog. */
    var permissionRequester: () -> Unit = {}

    override fun onCreate() {
        super.onCreate()
        AppHolder.container = AppContainer(
            DatabaseDriverFactory(this),
            AndroidUpdateScheduler(this),
            AndroidNotifier(this) { permissionRequester() },
        )
    }
}
