package com.todorpg.app.data

import kotlinx.serialization.Serializable

/** Damage types. Monsters are weak to some (x1.5, fills the break gauge) and resist others (x0.5). */
@Serializable
enum class Element(val label: String, val color: Long) {
    SLASH("Slash", 0xFFE0E0F0),
    PIERCE("Pierce", 0xFFC0A070),
    BLUNT("Blunt", 0xFFE89048),
    FIRE("Fire", 0xFFFF6030),
    ICE("Ice", 0xFF70D0FF),
    HOLY("Holy", 0xFFFFF080),
    NATURE("Nature", 0xFF60D050),
    SOUND("Sound", 0xFFFF80C0),
}

/** Party classes. Each hero is tied to one to-do category. */
@Serializable
enum class HeroClass(
    val title: String,
    val attackName: String,
    val element: Element,
    val skill: String,
    val skillCost: Int,
    val skillElement: Element?,
    val blurb: String,
    val skillBlurb: String,
) {
    KNIGHT("Knight", "Slash", Element.SLASH, "Cleave", 4, Element.SLASH, "steady, strong hits", "a mighty 2x blow"),
    MONK("Monk", "Flurry", Element.BLUNT, "Chakra", 4, Element.BLUNT, "fast flurry of blows", "strike and heal self 30%"),
    RANGER("Ranger", "Snipe", Element.PIERCE, "Volley", 5, Element.PIERCE, "high critical chance", "three quick arrows"),
    MAGE("Mage", "Fire", Element.FIRE, "Frost", 6, Element.ICE, "big magic damage", "huge ice damage"),
    CLERIC("Cleric", "Holy", Element.HOLY, "Prayer", 6, null, "weak hits, heals an ally", "heal the whole party 30%"),
    BARD("Bard", "Anthem", Element.SOUND, "Lullaby", 5, Element.SOUND, "weak hits, next ally +50%", "monster loses its next turn"),
    SAMURAI("Samurai", "Iai", Element.SLASH, "Zanshin", 6, Element.SLASH, "heavy blows, good crits", "a guaranteed critical"),
    DRUID("Druid", "Thorns", Element.NATURE, "Rebirth", 8, null, "light hits, heals party", "revive a fallen ally"),
}

@Serializable
enum class GearSlot(val label: String) { WEAPON("Weapon"), ARMOR("Armor"), ACCESSORY("Accessory") }

@Serializable
enum class Rarity(val label: String, val mult: Double, val color: Long) {
    COMMON("Common", 1.0, 0xFFE0E0E0),
    RARE("Rare", 1.6, 0xFF60B0FF),
    EPIC("Epic", 2.4, 0xFFC080FF),
    LEGENDARY("Legendary", 3.5, 0xFFF8C040),
}

@Serializable
data class Equipment(
    val id: Long,
    val name: String,
    val slot: GearSlot,
    val rarity: Rarity,
    val atk: Int = 0,
    val def: Int = 0,
    val hp: Int = 0,
) {
    val sellPrice: Int get() = 5 + (atk + def + hp / 3) * 2
    val statLine: String
        get() = listOfNotNull(
            if (atk > 0) "ATK+$atk" else null,
            if (def > 0) "DEF+$def" else null,
            if (hp > 0) "HP+$hp" else null,
        ).joinToString(" ")
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
    val gear: Map<GearSlot, Equipment> = emptyMap(),
    val mp: Int = Rules.maxMp(1),
    /** Halves the next monster hit. Cleared after the monster acts. */
    val guarding: Boolean = false,
) {
    val maxHp: Int get() = Rules.maxHp(level) + gear.values.sumOf { it.hp }
    val maxMp: Int get() = Rules.maxMp(level)
    val expToNext: Int get() = Rules.expToNext(level)
    val attack: Int get() = Rules.attack(level, weapon) + gear.values.sumOf { it.atk }
    val defense: Int get() = gear.values.sumOf { it.def }
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
    GOLEM("Golem"),
    DEMON("Demon"),
}

@Serializable
enum class IntentType(val label: String, val mult: Double) {
    STRIKE("Strike", 1.0),
    HEAVY("Heavy Blow", 1.8),
    SWEEP("Sweep", 0.6),
}

/** What the monster will do on its next turn, shown to the player in advance. */
@Serializable
data class Intent(val type: IntentType, val targetId: Int? = null)

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
    /** Hero actions between monster turns. */
    val speed: Int = 3,
    val counter: Int = 0,
    val intent: Intent? = null,
    val breakGauge: Int = 0,
    val breakMax: Int = 6,
    /** Hero actions left while broken: the monster can't act and takes extra damage. */
    val broken: Int = 0,
) {
    val weak: Set<Element> get() = Rules.weaknesses(kind)
    val resist: Set<Element> get() = Rules.resistances(kind)
    val turnsUntilAction: Int get() = (speed - counter).coerceAtLeast(1)
}

/**
 * A weekly raid boss. It is sealed until every hero with quests has finished
 * [Rules.RAID_SEAL_TASKS] of them this week, which pushes you to keep every
 * part of life ticking over rather than grinding one category.
 */
@Serializable
data class Raid(
    val weekStart: Long,
    val boss: Monster,
    val progress: Map<Int, Int> = emptyMap(),
    val defeated: Boolean = false,
)

@Serializable
enum class Item(val label: String, val price: Int, val description: String) {
    POTION("Potion", 15, "Restores 40 HP to one hero."),
    ELIXIR("Elixir", 60, "Fully restores the whole party."),
    FEATHER("Phoenix Feather", 40, "Revives a KO'd hero with half HP."),
    ETHER("Ether", 30, "Restores 15 MP to one hero."),
}

@Serializable
data class Settings(
    val music: Boolean = true,
    val sfx: Boolean = true,
    val reminders: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
)

@Serializable
data class GameState(
    val heroes: List<Hero>,
    val tasks: List<Task> = emptyList(),
    val gold: Int = 0,
    val items: Map<Item, Int> = mapOf(Item.POTION to 2),
    val inventory: List<Equipment> = emptyList(),
    val floor: Int = 1,
    val monster: Monster,
    val raid: Raid? = null,
    /** Set by a Bard: the next non-Bard attack deals bonus damage. */
    val inspired: Boolean = false,
    val lastDay: Long? = null,
    val log: List<String> = emptyList(),
    val nextTaskId: Long = 1,
    val nextGearId: Long = 1,
    val tasksCompleted: Int = 0,
    val monstersDefeated: Int = 0,
    val raidsDefeated: Int = 0,
    val settings: Settings = Settings(),
    /** Weaknesses the party has discovered by hitting them. */
    val knownWeak: Map<MonsterKind, Set<Element>> = emptyMap(),
) {
    fun hero(id: Int): Hero? = heroes.firstOrNull { it.id == id }
    fun itemCount(item: Item): Int = items[item] ?: 0

    /** Heroes that must break their seal for this week's raid. */
    fun raidSealHeroes(): List<Hero> = heroes.filter { h -> tasks.any { it.heroId == h.id } }

    fun raidUnsealed(): Boolean {
        val r = raid ?: return false
        val needed = raidSealHeroes()
        return needed.isNotEmpty() && needed.all { (r.progress[it.id] ?: 0) >= Rules.RAID_SEAL_TASKS }
    }
}

/** Balance numbers live here so they are easy to tune. */
object Rules {
    const val MAX_CHARGES = 9
    const val MAX_LOG = 30
    const val MISSED_DAYS_CAP = 3
    const val POTION_HEAL = 40
    const val MAX_PARTY = 8
    const val RAID_SEAL_TASKS = 3
    const val MAX_INVENTORY = 40

    const val ETHER_MP = 15
    const val MP_PER_TASK = 2
    const val BROKEN_TURNS = 3

    fun maxHp(level: Int): Int = 30 + (level - 1) * 10
    fun maxMp(level: Int): Int = 10 + level * 2

    fun weaknesses(kind: MonsterKind): Set<Element> = when (kind) {
        MonsterKind.SLIME -> setOf(Element.FIRE, Element.ICE)
        MonsterKind.BAT -> setOf(Element.PIERCE, Element.SOUND)
        MonsterKind.GOBLIN -> setOf(Element.SLASH, Element.FIRE)
        MonsterKind.SKELETON -> setOf(Element.HOLY, Element.BLUNT)
        MonsterKind.DRAGON -> setOf(Element.ICE, Element.PIERCE)
        MonsterKind.GOLEM -> setOf(Element.BLUNT, Element.NATURE)
        MonsterKind.DEMON -> setOf(Element.HOLY, Element.SOUND)
    }

    fun resistances(kind: MonsterKind): Set<Element> = when (kind) {
        MonsterKind.SLIME -> setOf(Element.BLUNT)
        MonsterKind.BAT -> setOf(Element.NATURE)
        MonsterKind.GOBLIN -> setOf(Element.PIERCE)
        MonsterKind.SKELETON -> setOf(Element.PIERCE, Element.NATURE)
        MonsterKind.DRAGON -> setOf(Element.FIRE)
        MonsterKind.GOLEM -> setOf(Element.SLASH, Element.PIERCE)
        MonsterKind.DEMON -> setOf(Element.FIRE, Element.ICE)
    }
    fun expToNext(level: Int): Int = 40 + (level - 1) * 30
    fun attack(level: Int, weapon: Int): Int = 6 + level * 2 + weapon * 4
    fun weaponCost(weapon: Int): Int = 50 * (weapon + 1)

    fun dailyExp(d: Difficulty, streak: Int): Int = 10 * d.mult + minOf(streak, 10)
    fun dailyGold(d: Difficulty, streak: Int): Int =
        5 * d.mult + if (streak > 0 && streak % 7 == 0) 25 else 0

    fun oneOffExp(d: Difficulty): Int = 25 * d.mult
    fun oneOffGold(d: Difficulty): Int = 2 * d.mult

    fun chargesFor(d: Difficulty): Int = if (d == Difficulty.HARD) 2 else 1

    /** Monday-based week start for an epoch day (epoch day 0 was a Thursday). */
    fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)
}
