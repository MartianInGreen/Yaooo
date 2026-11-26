package app.yaooo

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.yaooo.data.NewChapters
import app.yaooo.db.YaoooDb
import app.yaooo.ui.App
import platform.UIKit.UIViewController

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(YaoooDb.Schema, "yaooo.db")
}

// iOS has no hardware back button; the swipe-back gesture is handled by the host UIViewController.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

/** TODO(ios): register a BGAppRefreshTask that calls AppContainer.updateChecker.run(). */
class IosUpdateScheduler : UpdateScheduler {
    override fun schedule(everyHours: Int) {}
    override fun cancel() {}
}

/** TODO(ios): UNUserNotificationCenter. */
class IosNotifier : Notifier {
    override fun notifyNewChapters(updates: List<NewChapters>) {}
    override fun requestPermission() {}
}

private val container by lazy { AppContainer(DatabaseDriverFactory(), IosUpdateScheduler(), IosNotifier()) }

/** Entry point for the Xcode host app: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(container) }
