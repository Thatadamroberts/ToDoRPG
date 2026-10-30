package com.todorpg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameRepository
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.data.TaskType
import com.todorpg.app.game.AttackResult
import com.todorpg.app.game.GameEngine
import kotlinx.coroutines.Dispatchers
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

/** A banner message, optionally with an undo snapshot. */
data class Banner(val id: Long, val text: String, val undo: GameState? = null)


class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GameRepository(File(app.filesDir, "save.json"))
    private val saveLock = Mutex()
    private var counter = 0L

    private val _state = MutableStateFlow(repo.load() ?: GameEngine.newGame(today()))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _battleEvents = MutableSharedFlow<AttackResult>(extraBufferCapacity = 16)
    /** One event per attack, for the battle screen to animate. */
    val battleEvents: SharedFlow<AttackResult> = _battleEvents.asSharedFlow()

    private fun today(): Long = LocalDate.now().toEpochDay()
    fun currentDay(): Long = today()

    /** Call whenever the app comes to the foreground to process day changes. */
    fun onResume() = update { GameEngine.rollover(it, today()) }

    private fun update(transform: (GameState) -> GameState) {
        val old = _state.value
        val new = transform(old)
        if (new == old) return
        _state.value = new
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { repo.save(_state.value) }
        }
    }

    private fun banner(text: String, undo: GameState? = null) {
        _banner.value = Banner(++counter, text, undo)
    }

    fun dismissBanner(id: Long) {
        if (_banner.value?.id == id) _banner.value = null
    }

    fun undo(banner: Banner) {
        val snapshot = banner.undo ?: return
        update { snapshot }
        _banner.value = null
    }

    fun addTask(title: String, heroId: Int, type: TaskType, difficulty: Difficulty) {
        if (title.isBlank()) return
        update { GameEngine.addTask(it, title, heroId, type, difficulty, today()) }
    }

    fun editTask(taskId: Long, title: String, heroId: Int, difficulty: Difficulty) {
        if (title.isBlank()) return
        update { GameEngine.editTask(it, taskId, title, heroId, difficulty) }
    }

    fun deleteTask(taskId: Long) {
        val before = _state.value
        update { GameEngine.deleteTask(it, taskId) }
        banner("Quest removed.", undo = before)
    }

    fun completeTask(taskId: Long) {
        val before = _state.value
        val (next, reward) = GameEngine.completeTask(before, taskId, today())
        if (reward == null) return
        update { next }
        banner(reward.text, undo = before)
    }

    fun attack(heroId: Int) {
        val (next, result) = GameEngine.attack(_state.value, heroId)
        if (result == null) return
        update { next }
        _battleEvents.tryEmit(result)
    }

    fun buy(item: Item) {
        val s = _state.value
        if (s.gold < item.price) {
            banner("Not enough gold!")
            return
        }
        update { GameEngine.buy(it, item) }
        banner("Bought a ${item.label}.")
    }

    fun upgradeWeapon(heroId: Int) {
        val s = _state.value
        val hero = s.hero(heroId) ?: return
        if (s.gold < Rules.weaponCost(hero.weapon)) {
            banner("Not enough gold!")
            return
        }
        update { GameEngine.upgradeWeapon(it, heroId) }
        banner("${hero.name}'s weapon upgraded!")
    }

    fun useItem(item: Item, heroId: Int) {
        val next = GameEngine.useItem(_state.value, item, heroId)
        if (next == null) {
            banner("It has no effect.")
            return
        }
        update { next }
        banner(next.log.last())
    }

    fun editHero(heroId: Int, name: String, category: String) =
        update { GameEngine.editHero(it, heroId, name, category) }
}
