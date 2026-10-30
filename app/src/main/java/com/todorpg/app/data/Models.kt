package com.todorpg.app.data

import kotlinx.serialization.Serializable

/** The five party classes. Each one is tied to a to-do category. */
@Serializable
enum class HeroClass(val title: String, val skill: String) {
    KNIGHT("Knight", "Slash"),
    MONK("Monk", "Flurry"),
    RANGER("Ranger", "Snipe"),
    MAGE("Mage", "Fire"),
    CLERIC("Cleric", "Holy"),
}

@Serializable
data class Hero(
    val id: Int,
    val heroClass: HeroClass,
    val name: String,
    val category: String,
    val level: Int = 1,
    val exp: Int = 0,
    val hp: Int = Rules.maxHp(1),
    val weapon: Int = 0,
    /** Battle turns earned by finishing tasks. Spent in the Battle screen. */
    val charges: Int = 0,
) {
    val maxHp: Int get() = Rules.maxHp(level)
    val expToNext: Int get() = Rules.expToNext(level)
    val attack: Int get() = Rules.attack(level, weapon)
    val isKo: Boolean get() = hp <= 0
}

@Serializable
enum class TaskType { DAILY, ONE_OFF }

@Serializable
enum class Difficulty(val label: String, val mult: Int) {
    EASY("Easy", 1),
    NORMAL("Normal", 2),
    HARD("Hard", 3),
}

@Serializable
data class Task(
    val id: Long,
    val title: String,
    val heroId: Int,
    val type: TaskType,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val createdDay: Long,
    /** Epoch day of the most recent completion, or null if never completed. */
    val lastCompletedDay: Long? = null,
    val streak: Int = 0,
) {
    fun isDone(today: Long): Boolean = when (type) {
        TaskType.DAILY -> lastCompletedDay == today
        TaskType.ONE_OFF -> lastCompletedDay != null
    }
}

@Serializable
enum class MonsterKind(val baseName: String) {
    SLIME("Slime"),
    BAT("Bat"),
    GOBLIN("Goblin"),
    SKELETON("Skeleton"),
    DRAGON("Dragon"),
}

@Serializable
data class Monster(
    val kind: MonsterKind,
    val name: String,
    val floor: Int,
    val tier: Int,
    val hp: Int,
    val maxHp: Int,
    val attack: Int,
    val isBoss: Boolean,
    val goldReward: Int,
    val expReward: Int,
)

@Serializable
enum class Item(val label: String, val price: Int, val description: String) {
    POTION("Potion", 15, "Restores 40 HP to one hero."),
    ELIXIR("Elixir", 60, "Fully restores the whole party."),
    FEATHER("Phoenix Feather", 40, "Revives a KO'd hero with half HP."),
}

@Serializable
data class GameState(
    val heroes: List<Hero>,
    val tasks: List<Task> = emptyList(),
    val gold: Int = 0,
    val items: Map<Item, Int> = mapOf(Item.POTION to 2),
    val floor: Int = 1,
    val monster: Monster,
    val lastDay: Long? = null,
    val log: List<String> = emptyList(),
    val nextTaskId: Long = 1,
    val tasksCompleted: Int = 0,
    val monstersDefeated: Int = 0,
) {
    fun hero(id: Int): Hero? = heroes.firstOrNull { it.id == id }
    fun itemCount(item: Item): Int = items[item] ?: 0
}

/** Balance numbers live here so they are easy to tune. */
object Rules {
    const val MAX_CHARGES = 9
    const val MAX_LOG = 30
    const val MISSED_DAYS_CAP = 3
    const val POTION_HEAL = 40

    fun maxHp(level: Int): Int = 30 + (level - 1) * 10
    fun expToNext(level: Int): Int = 40 + (level - 1) * 30
    fun attack(level: Int, weapon: Int): Int = 6 + level * 2 + weapon * 4
    fun weaponCost(weapon: Int): Int = 50 * (weapon + 1)

    fun dailyExp(d: Difficulty, streak: Int): Int = 10 * d.mult + minOf(streak, 10)
    fun dailyGold(d: Difficulty, streak: Int): Int =
        5 * d.mult + if (streak > 0 && streak % 7 == 0) 25 else 0

    fun oneOffExp(d: Difficulty): Int = 25 * d.mult
    fun oneOffGold(d: Difficulty): Int = 2 * d.mult

    fun chargesFor(d: Difficulty): Int = if (d == Difficulty.HARD) 2 else 1
}
