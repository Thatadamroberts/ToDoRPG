package com.todorpg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todorpg.app.audio.AudioController
import com.todorpg.app.audio.Music
import com.todorpg.app.audio.Sfx
import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameRepository
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.data.Settings
import com.todorpg.app.data.TaskType
import com.todorpg.app.game.AttackResult
import com.todorpg.app.game.GameEngine
import com.todorpg.app.reminders.Reminders
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
import java.time.LocalDate

/** A banner message, optionally with an undo snapshot. */
data class Banner(val id: Long, val text: String, val undo: GameState? = null)

/** Shown as a full-screen "LEVEL UP!" celebration. */
data class Celebration(val id: Long, val heroIds: List<Int>)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GameRepository(Reminders.saveFile(app))
    private val saveLock = Mutex()
    private var counter = 0L

    val audio = AudioController(app)

    private val _state = MutableStateFlow(repo.load() ?: GameEngine.newGame(today()))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    private val _battleEvents = MutableSharedFlow<AttackResult>(extraBufferCapacity = 16)
    /** One event per attack, for the battle screen to animate. */
    val battleEvents: SharedFlow<AttackResult> = _battleEvents.asSharedFlow()

    init {
        applyAudioSettings(_state.value.settings)
        if (_state.value.settings.reminders) Reminders.apply(app, _state.value.settings)
    }

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

    fun dismissCelebration() {
        _celebration.value = null
    }

    private fun celebrate(heroIds: List<Int>) {
        if (heroIds.isEmpty()) return
        _celebration.value = Celebration(++counter, heroIds)
        audio.play(Sfx.LEVEL_UP)
    }

    fun undo(banner: Banner) {
        val snapshot = banner.undo ?: return
        update { snapshot }
        _banner.value = null
        audio.play(Sfx.CURSOR)
    }

    fun click() = audio.play(Sfx.CURSOR, 0.5f)

    // region Quests

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
        audio.play(Sfx.COMPLETE)
        banner(reward.text, undo = before)
        if (reward.levelUp) celebrate(listOf(reward.heroId))
    }

    // endregion

    // region Battle

    fun attack(heroId: Int, raid: Boolean) {
        val (next, result) = GameEngine.attack(_state.value, heroId, raid)
        if (result == null) return
        update { next }
        _battleEvents.tryEmit(result)
    }

    /** Called by the battle screen when the matching animation beat lands. */
    fun playHitSound(result: AttackResult) {
        audio.play(
            when {
                result.critical -> Sfx.CRIT
                result.heroClass == HeroClass.MAGE || result.heroClass == HeroClass.BARD -> Sfx.MAGIC
                else -> Sfx.HIT
            }
        )
        if (result.healedHeroIds.isNotEmpty()) audio.play(Sfx.HEAL, 0.6f)
    }

    fun playVictory(result: AttackResult) {
        audio.play(Sfx.DEFEAT)
        audio.play(Sfx.VICTORY, 0.9f)
        celebrate(result.levelUps)
    }

    // endregion

    // region Shop, items & gear

    fun buy(item: Item) {
        val s = _state.value
        if (s.gold < item.price) {
            banner("Not enough gold!")
            return
        }
        update { GameEngine.buy(it, item) }
        audio.play(Sfx.COIN)
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
        audio.play(Sfx.COIN)
        banner("${hero.name} trains hard! ATK up.")
    }

    fun useItem(item: Item, heroId: Int) {
        val next = GameEngine.useItem(_state.value, item, heroId)
        if (next == null) {
            banner("It has no effect.")
            return
        }
        update { next }
        audio.play(Sfx.HEAL)
        banner(next.log.last())
    }

    fun equip(heroId: Int, gearId: Long) {
        update { GameEngine.equip(it, heroId, gearId) }
        audio.play(Sfx.CURSOR)
    }

    fun unequip(heroId: Int, slot: GearSlot) {
        val before = _state.value
        update { GameEngine.unequip(it, heroId, slot) }
        if (_state.value == before) banner("Your bag is full!") else audio.play(Sfx.CURSOR)
    }

    fun sell(gearId: Long) {
        val before = _state.value
        val gear = before.inventory.firstOrNull { it.id == gearId } ?: return
        update { GameEngine.sell(it, gearId) }
        audio.play(Sfx.COIN)
        banner("Sold ${gear.name} for ${gear.sellPrice} G.", undo = before)
    }

    // endregion

    // region Party & settings

    fun editHero(heroId: Int, name: String, category: String) =
        update { GameEngine.editHero(it, heroId, name, category) }

    fun recruit(heroClass: HeroClass, name: String, category: String) {
        update { GameEngine.recruit(it, heroClass, name, category) }
        audio.play(Sfx.LEVEL_UP, 0.6f)
        banner("${name.trim()} joins the party!")
    }

    fun dismiss(heroId: Int) {
        val before = _state.value
        update { GameEngine.dismiss(it, heroId) }
        if (_state.value == before) banner("Move or delete their quests first.")
        else banner("${before.hero(heroId)?.name} leaves the party.", undo = before)
    }

    fun updateSettings(settings: Settings) {
        update { GameEngine.updateSettings(it, settings) }
        applyAudioSettings(settings)
        Reminders.apply(getApplication(), settings)
    }

    private fun applyAudioSettings(settings: Settings) {
        audio.musicEnabled = settings.music
        audio.sfxEnabled = settings.sfx
    }

    fun setMusic(music: Music) = audio.setMusic(music)

    // endregion

    override fun onCleared() {
        audio.release()
    }
}
