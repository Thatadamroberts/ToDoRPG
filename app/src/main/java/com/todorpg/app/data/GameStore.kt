package com.todorpg.app.data

import android.content.Context
import com.todorpg.app.game.GameEngine
import com.todorpg.app.widget.QuestWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

/**
 * The one copy of the game state for the whole process, shared by the app
 * and the home screen widget so they can never overwrite each other.
 */
object GameStore {
    private lateinit var appContext: Context
    private lateinit var repo: GameRepository
    private lateinit var flow: MutableStateFlow<GameState>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saveLock = Mutex()
    private var widgetJob: Job? = null

    @Volatile
    private var pendingSave: GameState? = null

    private val _unlocks = MutableSharedFlow<List<Achievement>>(extraBufferCapacity = 8)
    /** Achievements unlocked by any update, for the app to celebrate. */
    val unlocks: SharedFlow<List<Achievement>> = _unlocks.asSharedFlow()

    val state: StateFlow<GameState> get() = flow.asStateFlow()
    val value: GameState get() = flow.value

    fun saveFile(context: Context) = File(context.filesDir, "save.json")

    fun today(): Long = LocalDate.now().toEpochDay()

    @Synchronized
    fun init(context: Context) {
        if (::flow.isInitialized) return
        appContext = context.applicationContext
        repo = GameRepository(saveFile(appContext))
        flow = MutableStateFlow(repo.load() ?: GameEngine.newGame(today()))
    }

    /** Applies a change, unlocks achievements, saves, and refreshes the widget. */
    @Synchronized
    fun update(transform: (GameState) -> GameState): GameState {
        val old = flow.value
        val changed = transform(old)
        if (changed == old) return old
        val (next, unlocked) = GameEngine.checkAchievements(changed, today())
        flow.value = next
        save(next)
        if (unlocked.isNotEmpty()) _unlocks.tryEmit(unlocked)
        return next
    }

    private fun save(state: GameState) {
        pendingSave = state
        scope.launch(Dispatchers.IO) {
            saveLock.withLock { pendingSave?.let(repo::save) }
        }
        // Coalesce rapid changes into one widget refresh.
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(400)
            runCatching { QuestWidget.refresh(appContext) }
        }
    }

    /** Reads a backup file's text; returns null if it isn't a valid save. */
    fun parseBackup(text: String): GameState? = repo.parse(text)

    fun exportText(): String = repo.encode(flow.value)
}
