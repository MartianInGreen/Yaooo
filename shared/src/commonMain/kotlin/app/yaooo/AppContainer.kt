package app.yaooo

import app.yaooo.data.Ao3Client
import app.yaooo.data.Repository
import app.yaooo.data.UpdateChecker
import app.yaooo.db.YaoooDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ReaderPrefs(val fontScale: Float = 1.0f, val lineSpacing: Float = 1.5f, val serif: Boolean = true)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val checkIntervalHours: Int = 6,
    val notificationsEnabled: Boolean = true,
    val reader: ReaderPrefs = ReaderPrefs(),
)

/** Hand-rolled DI root; one instance per process. Created by the platform entry point. */
class AppContainer(
    driverFactory: DatabaseDriverFactory,
    val scheduler: UpdateScheduler,
    val notifier: Notifier,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = Repository(YaoooDb(driverFactory.create()), Ao3Client(), Dispatchers.Default)
    val updateChecker = UpdateChecker(repository)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings

    init {
        applySchedule(_settings.value)
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        saveSettings(new)
        if (new.checkIntervalHours != old.checkIntervalHours || new.notificationsEnabled != old.notificationsEnabled) {
            applySchedule(new)
            if (new.notificationsEnabled && !old.notificationsEnabled) notifier.requestPermission()
        }
    }

    private fun applySchedule(s: AppSettings) {
        if (s.notificationsEnabled) scheduler.schedule(s.checkIntervalHours) else scheduler.cancel()
    }

    private fun loadSettings(): AppSettings {
        val r = repository
        return AppSettings(
            theme = r.setting("theme")?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            checkIntervalHours = r.setting("interval")?.toIntOrNull() ?: 6,
            notificationsEnabled = r.setting("notify") != "0",
            reader = ReaderPrefs(
                fontScale = r.setting("font")?.toFloatOrNull() ?: 1f,
                lineSpacing = r.setting("spacing")?.toFloatOrNull() ?: 1.5f,
                serif = r.setting("serif") != "0",
            ),
        )
    }

    private fun saveSettings(s: AppSettings) {
        repository.putSetting("theme", s.theme.name)
        repository.putSetting("interval", s.checkIntervalHours.toString())
        repository.putSetting("notify", if (s.notificationsEnabled) "1" else "0")
        repository.putSetting("font", s.reader.fontScale.toString())
        repository.putSetting("spacing", s.reader.lineSpacing.toString())
        repository.putSetting("serif", if (s.reader.serif) "1" else "0")
    }
}
