package app.yaooo

import androidx.compose.runtime.Composable
import app.cash.sqldelight.db.SqlDriver
import app.yaooo.data.NewChapters

/** Everything platform-specific the shared code needs. Implemented in androidMain / iosMain. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

/** Schedules the periodic "any new chapters?" job (WorkManager on Android, BGTaskScheduler on iOS). */
interface UpdateScheduler {
    fun schedule(everyHours: Int)
    fun cancel()
}

/** Shows a system notification for fresh chapters. */
interface Notifier {
    fun notifyNewChapters(updates: List<NewChapters>)
    /** Asks for notification permission where the OS requires it (Android 13+, iOS). */
    fun requestPermission()
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
