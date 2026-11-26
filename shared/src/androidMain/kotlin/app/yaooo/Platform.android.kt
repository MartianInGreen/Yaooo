package app.yaooo

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.yaooo.data.Ao3Exception
import app.yaooo.data.NewChapters
import app.yaooo.db.YaoooDb
import java.util.concurrent.TimeUnit

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(YaoooDb.Schema, context, "yaooo.db")
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** Set by the Application so the Worker (which may run without any Activity) can reach the container. */
object AppHolder {
    lateinit var container: AppContainer
    val isReady get() = ::container.isInitialized
}

const val CHANNEL_UPDATES = "chapter_updates"
private const val WORK_NAME = "yaooo-update-check"

class AndroidUpdateScheduler(private val context: Context) : UpdateScheduler {
    override fun schedule(everyHours: Int) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(everyHours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class AndroidNotifier(private val context: Context, private val permissionRequester: () -> Unit = {}) : Notifier {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_UPDATES, "New chapters", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Alerts when a bookmarked fic gets a new chapter"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun requestPermission() = permissionRequester()

    @SuppressLint("MissingPermission")
    override fun notifyNewChapters(updates: List<NewChapters>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        for (u in updates) {
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                putExtra("workId", u.work.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, u.work.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (u.added == 1) "1 new chapter" else "${u.added} new chapters"
            val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(u.work.title)
                .setContentText("$text · by ${u.work.authors.joinToString()}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(u.work.id.toInt(), n)
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AppHolder.isReady) return Result.retry()
        val c = AppHolder.container
        if (!c.settings.value.notificationsEnabled) return Result.success()
        return try {
            val updates = c.updateChecker.run()
            if (updates.isNotEmpty()) c.notifier.notifyNewChapters(updates)
            Result.success()
        } catch (e: Ao3Exception) {
            if (e.retryable && runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
