package com.todorpg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todorpg.app.audio.AudioController
import com.todorpg.app.audio.Music
import com.todorpg.app.audio.Sfx
import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GameStore
import com.todorpg.app.data.Perk
import com.todorpg.app.data.Pet
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.data.Settings
import com.todorpg.app.data.TaskType
import com.todorpg.app.data.Element
import com.todorpg.app.game.BattleEvent
import com.todorpg.app.game.Command
import com.todorpg.app.game.HeroEvent
import com.todorpg.app.game.VictoryEvent
import com.todorpg.app.game.GameEngine
import com.todorpg.app.reminders.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A banner message, optionally with an undo snapshot. */
data class Banner(val id: Long, val text: String, val undo: GameState? = null)

/** One battle command's events, plus the state to keep showing while they play. */
data class BattleAnim(val id: Long, val events: List<BattleEvent>, val before: GameState)

/** A full-screen celebration: level-ups, promotions and hatching eggs. */
data class Celebration(val id: Long, val title: String, val heroIds: List<Int> = emptyList(), val pet: Pet? = null)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore.also { it.init(app) }
    private var counter = 0L

    val audio = AudioController(app)

    private val _battleAnim = MutableStateFlow<BattleAnim?>(null)
    /**
     * The battle action being animated. The new state is saved straight away,
     * but [state] keeps showing the old one until the battle screen calls
     * [finishBattleAnim], so HP bars and the next monster don't jump ahead.
     */
    val battleAnim: StateFlow<BattleAnim?> = _battleAnim.asStateFlow()

    val state: StateFlow<GameState> = combine(store.state, _battleAnim) { s, a -> a?.before ?: s }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.value)

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    init {
        applyAudioSettings(store.value.settings)
        if (store.value.settings.reminders) Reminders.apply(app, store.value.settings)
        viewModelScope.launch {
            store.unlocks.collect { list ->
                audio.play(Sfx.VICTORY, 0.6f)
                banner("🏆 " + list.joinToString(", ") { "${it.title} +${it.reward} G" })
            }
        }
    }

    private fun today(): Long = GameStore.today()
    fun currentDay(): Long = today()

    /** Call whenever the app comes to the foreground to process day changes. */
    fun onResume() = update { GameEngine.rollover(it, today()) }

    private fun update(transform: (GameState) -> GameState): GameState {
        _battleAnim.value?.let { finishBattleAnim(it.id) }
        return store.update(transform)
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

    private fun celebrate(heroIds: List<Int>, title: String = "LEVEL UP!", pet: Pet? = null) {
        if (heroIds.isEmpty() && pet == null) return
        _celebration.value = Celebration(++counter, title, heroIds, pet)
        audio.play(Sfx.LEVEL_UP)
    }

    fun undo(banner: Banner) {
        val snapshot = banner.undo ?: return
        // Keep achievements so undoing and redoing can't pay out twice.
        update { snapshot.copy(achievements = it.achievements) }
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
        val before = store.value
        update { GameEngine.deleteTask(it, taskId) }
        banner("Quest removed.", undo = before)
    }

    fun completeTask(taskId: Long) {
        val before = store.value
        val (next, reward) = GameEngine.completeTask(before, taskId, today())
        if (reward == null) return
        update { next }
        audio.play(Sfx.COMPLETE)
        banner(reward.text, undo = before)
        when {
            reward.hatched != null -> celebrate(emptyList(), "EGG HATCHED!", reward.hatched)
            reward.levelUp -> celebrate(listOf(reward.heroId))
        }
        if (reward.newEgg) banner("${reward.text}  · You found an egg!", undo = before)
    }

    // endregion

    // region Battle

    fun act(heroId: Int, cmd: Command, raid: Boolean) {
        if (_battleAnim.value != null) return
        val before = store.value
        val (next, events) = GameEngine.act(before, heroId, cmd, raid) ?: return
        _battleAnim.value = BattleAnim(++counter, events, before)
        store.update { next }
    }

    fun finishBattleAnim(id: Long) {
        val anim = _battleAnim.value ?: return
        if (anim.id != id) return
        _battleAnim.value = null
        anim.events.filterIsInstance<VictoryEvent>().firstOrNull()?.let { celebrate(it.levelUps) }
    }

    fun sfx(sound: Sfx, volume: Float = 0.8f) = audio.play(sound, volume)

    fun playHitSound(e: HeroEvent) {
        audio.play(
            when {
                e.critical || e.broke -> Sfx.CRIT
                e.element == Element.FIRE || e.element == Element.ICE || e.element == Element.SOUND ||
                    e.element == Element.HOLY -> Sfx.MAGIC
                else -> Sfx.HIT
            }
        )
    }

    // endregion

    // region Shop, items & gear

    fun buy(item: Item) {
        val s = store.value
        if (s.gold < item.price) {
            banner("Not enough gold!")
            return
        }
        update { GameEngine.buy(it, item) }
        audio.play(Sfx.COIN)
        banner("Bought a ${item.label}.")
    }

    fun upgradeWeapon(heroId: Int) {
        val s = store.value
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
        val next = GameEngine.useItem(store.value, item, heroId)
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
        val before = store.value
        update { GameEngine.unequip(it, heroId, slot) }
        if (store.value == before) banner("Your bag is full!") else audio.play(Sfx.CURSOR)
    }

    fun sell(gearId: Long) {
        val before = store.value
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
        val before = store.value
        update { GameEngine.dismiss(it, heroId) }
        if (store.value == before) banner("Move or delete their quests first.")
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

    // region Growth

    fun promote(heroId: Int) {
        val hero = store.value.hero(heroId) ?: return
        if (!hero.canPromote) return
        update { GameEngine.promote(it, heroId) }
        celebrate(listOf(heroId), "CLASS UP!")
        banner("${hero.name} became a ${hero.heroClass.promotedTitle}!")
    }

    fun learnPerk(heroId: Int, perk: Perk) {
        val before = store.value
        if (update { GameEngine.learnPerk(it, heroId, perk) } != before) audio.play(Sfx.HEAL, 0.6f)
    }

    fun respec(heroId: Int) {
        val before = store.value
        if (update { GameEngine.respec(it, heroId) } == before) {
            banner("Need ${Rules.RESPEC_COST} G to reset skills.")
        } else {
            audio.play(Sfx.COIN)
            banner("Skill points refunded.", undo = before)
        }
    }

    fun setActivePet(petId: Long) {
        update { GameEngine.setActivePet(it, petId) }
        audio.play(Sfx.COMPLETE, 0.6f)
    }

    fun renamePet(petId: Long, name: String) = update { GameEngine.renamePet(it, petId, name) }

    // endregion

    // region Backup

    fun exportText(): String = store.exportText()

    /** Returns null if the text isn't a valid save. */
    fun parseBackup(text: String): GameState? = store.parseBackup(text)

    fun importBackup(state: GameState) {
        val before = store.value
        update { state }
        applyAudioSettings(state.settings)
        banner("Backup restored!", undo = before)
    }

    fun notify(text: String) = banner(text)

    // endregion

    override fun onCleared() {
        audio.release()
    }
}
