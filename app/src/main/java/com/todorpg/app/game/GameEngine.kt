package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Item
import com.todorpg.app.data.Monster
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Rules
import com.todorpg.app.data.Task
import com.todorpg.app.data.TaskType
import kotlin.math.roundToInt
import kotlin.random.Random

/** Result of a hero attacking, used by the battle screen to animate. */
data class AttackResult(
    val heroId: Int,
    val damage: Int,
    val critical: Boolean,
    val healedHeroId: Int? = null,
    /** The monster as it was when it fell, if this attack finished it off. */
    val defeated: Monster? = null,
) {
    val killed: Boolean get() = defeated != null
}

/** Short feedback shown after an action, e.g. "+20 EXP  +10 G". */
data class Reward(val text: String, val levelUp: Boolean = false)

/**
 * All game rules as pure functions over [GameState]. No Android code here so it
 * can be unit tested on the JVM.
 */
object GameEngine {

    fun newGame(today: Long): GameState = GameState(
        heroes = listOf(
            Hero(0, HeroClass.KNIGHT, "Arthur", "Work"),
            Hero(1, HeroClass.MONK, "Bo", "Health"),
            Hero(2, HeroClass.RANGER, "Wren", "Home"),
            Hero(3, HeroClass.MAGE, "Vivi", "Learning"),
            Hero(4, HeroClass.CLERIC, "Rosa", "Social"),
        ),
        monster = spawnMonster(1),
        lastDay = today,
        log = listOf("Your party sets out. Finish quests to earn turns in battle!"),
    )

    // region Monsters

    private val normalKinds = listOf(MonsterKind.SLIME, MonsterKind.BAT, MonsterKind.GOBLIN, MonsterKind.SKELETON)
    private val tierPrefixes = listOf("", "Dire ", "Elder ", "Ancient ")

    fun spawnMonster(floor: Int): Monster {
        val isBoss = floor % 5 == 0
        val kind = if (isBoss) MonsterKind.DRAGON else normalKinds[(floor - 1) % normalKinds.size]
        val tier = ((floor - 1) / 10).coerceAtMost(tierPrefixes.lastIndex)
        val baseHp = 20 + floor * 12
        val hp = if (isBoss) baseHp * 3 else baseHp
        val attack = 3 + floor * 2
        return Monster(
            kind = kind,
            name = tierPrefixes[tier] + kind.baseName + if (isBoss) " Lord" else "",
            floor = floor,
            tier = tier,
            hp = hp,
            maxHp = hp,
            attack = if (isBoss) (attack * 1.5).roundToInt() else attack,
            isBoss = isBoss,
            goldReward = (20 + floor * 8) * if (isBoss) 3 else 1,
            expReward = (10 + floor * 4) * if (isBoss) 3 else 1,
        )
    }

    // endregion

    // region Day rollover

    /**
     * Called when the app opens. For each day that passed, every daily task that was
     * not completed lets the monster strike that task's hero. Then the party rests.
     * Finished one-off quests from earlier days are cleared away.
     */
    fun rollover(state: GameState, today: Long): GameState {
        val last = state.lastDay ?: return state.copy(lastDay = today)
        if (today <= last) return state

        val firstCheckedDay = maxOf(last, today - Rules.MISSED_DAYS_CAP)
        var heroes = state.heroes
        val log = mutableListOf<String>()
        val tasks = state.tasks.mapNotNull { task ->
            when (task.type) {
                TaskType.ONE_OFF -> if (task.lastCompletedDay != null && task.lastCompletedDay < today) null else task
                TaskType.DAILY -> {
                    var misses = 0
                    for (day in firstCheckedDay until today) {
                        if (task.createdDay <= day && task.lastCompletedDay != day) misses++
                    }
                    if (misses == 0) {
                        task
                    } else {
                        val hero = heroes.first { it.id == task.heroId }
                        if (!hero.isKo) {
                            val dmg = state.monster.attack * misses
                            val newHp = (hero.hp - dmg).coerceAtLeast(0)
                            heroes = heroes.replace(hero.copy(hp = newHp))
                            log += "Missed \"${task.title}\"! ${state.monster.name} hits ${hero.name} for $dmg." +
                                if (newHp == 0) " ${hero.name} is KO'd!" else ""
                        }
                        task.copy(streak = 0)
                    }
                }
            }
        }

        // Nightly rest: living heroes recover 10% max HP per day (up to 3 days).
        val restDays = minOf(today - last, Rules.MISSED_DAYS_CAP.toLong()).toInt()
        heroes = heroes.map { h ->
            if (h.isKo) h else h.copy(hp = minOf(h.maxHp, h.hp + h.maxHp * restDays / 10))
        }
        log += "A new day dawns. The party rests at camp."

        return state.copy(
            heroes = heroes,
            tasks = tasks,
            lastDay = today,
            log = (state.log + log).takeLast(Rules.MAX_LOG),
        )
    }

    // endregion

    // region Tasks

    fun addTask(state: GameState, title: String, heroId: Int, type: TaskType, difficulty: Difficulty, today: Long): GameState {
        val task = Task(
            id = state.nextTaskId,
            title = title.trim(),
            heroId = heroId,
            type = type,
            difficulty = difficulty,
            createdDay = today,
        )
        return state.copy(tasks = state.tasks + task, nextTaskId = state.nextTaskId + 1)
    }

    fun editTask(state: GameState, taskId: Long, title: String, heroId: Int, difficulty: Difficulty): GameState =
        state.copy(tasks = state.tasks.map {
            if (it.id == taskId) it.copy(title = title.trim(), heroId = heroId, difficulty = difficulty) else it
        })

    fun deleteTask(state: GameState, taskId: Long): GameState =
        state.copy(tasks = state.tasks.filterNot { it.id == taskId })

    fun completeTask(state: GameState, taskId: Long, today: Long): Pair<GameState, Reward?> {
        val task = state.tasks.firstOrNull { it.id == taskId } ?: return state to null
        if (task.isDone(today)) return state to null
        val hero = state.hero(task.heroId) ?: return state to null

        val (exp, gold, newStreak) = when (task.type) {
            TaskType.DAILY -> {
                val streak = if (task.lastCompletedDay == today - 1) task.streak + 1 else 1
                Triple(Rules.dailyExp(task.difficulty, streak), Rules.dailyGold(task.difficulty, streak), streak)
            }
            TaskType.ONE_OFF -> Triple(Rules.oneOffExp(task.difficulty), Rules.oneOffGold(task.difficulty), 0)
        }
        // KO'd heroes still learn, just slower.
        val earnedExp = if (hero.isKo) exp / 2 else exp
        val (leveled, levels) = gainExp(hero, earnedExp)
        val charged = leveled.copy(charges = minOf(Rules.MAX_CHARGES, leveled.charges + Rules.chargesFor(task.difficulty)))

        val updatedTask = task.copy(lastCompletedDay = today, streak = newStreak)
        val log = mutableListOf("${hero.name} completed \"${task.title}\".")
        if (levels > 0) log += "${hero.name} reached level ${charged.level}!"

        val text = buildString {
            append("${hero.name} +$earnedExp EXP")
            if (gold > 0) append("  +$gold G")
            if (task.type == TaskType.DAILY && newStreak > 1) append("  streak $newStreak")
            if (levels > 0) append("  LEVEL UP!")
        }
        return state.copy(
            heroes = state.heroes.replace(charged),
            tasks = state.tasks.map { if (it.id == taskId) updatedTask else it },
            gold = state.gold + gold,
            tasksCompleted = state.tasksCompleted + 1,
            log = (state.log + log).takeLast(Rules.MAX_LOG),
        ) to Reward(text, levelUp = levels > 0)
    }

    /** Adds EXP and handles level-ups. Levelling up fully heals (and revives) the hero. */
    fun gainExp(hero: Hero, amount: Int): Pair<Hero, Int> {
        var level = hero.level
        var exp = hero.exp + amount
        var levels = 0
        while (exp >= Rules.expToNext(level)) {
            exp -= Rules.expToNext(level)
            level++
            levels++
        }
        val hp = if (levels > 0) Rules.maxHp(level) else hero.hp
        return hero.copy(level = level, exp = exp, hp = hp) to levels
    }

    // endregion

    // region Battle

    fun attack(state: GameState, heroId: Int, rng: Random = Random.Default): Pair<GameState, AttackResult?> {
        val hero = state.hero(heroId) ?: return state to null
        if (hero.isKo || hero.charges <= 0 || state.monster.hp <= 0) return state to null

        val roll = 0.85 + rng.nextDouble() * 0.3
        val critChance = if (hero.heroClass == HeroClass.RANGER) 0.30 else 0.10
        val critical = rng.nextDouble() < critChance
        val classMult = when (hero.heroClass) {
            HeroClass.KNIGHT -> 1.2
            HeroClass.MONK -> 1.1
            HeroClass.RANGER -> 1.0
            HeroClass.MAGE -> 1.5
            HeroClass.CLERIC -> 0.6
        }
        val damage = (hero.attack * classMult * roll * if (critical) 2.0 else 1.0).roundToInt().coerceAtLeast(1)

        var heroes = state.heroes.replace(hero.copy(charges = hero.charges - 1))
        val log = mutableListOf(
            "${hero.name} uses ${hero.heroClass.skill}! ${if (critical) "Critical! " else ""}$damage damage."
        )

        // Clerics also heal the most wounded living ally.
        var healedId: Int? = null
        if (hero.heroClass == HeroClass.CLERIC) {
            val target = heroes.filter { !it.isKo && it.hp < it.maxHp }.minByOrNull { it.hp.toFloat() / it.maxHp }
            if (target != null) {
                val amount = target.maxHp / 4
                heroes = heroes.replace(target.copy(hp = minOf(target.maxHp, target.hp + amount)))
                healedId = target.id
                log += "${target.name} recovers $amount HP."
            }
        }

        val monsterHp = (state.monster.hp - damage).coerceAtLeast(0)
        var next = state.copy(heroes = heroes, monster = state.monster.copy(hp = monsterHp))
        val defeated = if (monsterHp == 0) next.monster else null
        if (defeated != null) {
            next = defeatMonster(next, log, rng)
        }
        next = next.copy(log = (next.log + log).takeLast(Rules.MAX_LOG))
        return next to AttackResult(heroId, damage, critical, healedId, defeated)
    }

    private fun defeatMonster(state: GameState, log: MutableList<String>, rng: Random): GameState {
        val m = state.monster
        log += "${m.name} is defeated! +${m.goldReward} G, +${m.expReward} EXP each."
        val heroes = state.heroes.map { h ->
            if (h.isKo) h else {
                val (up, levels) = gainExp(h, m.expReward)
                if (levels > 0) log += "${h.name} reached level ${up.level}!"
                up
            }
        }
        var items = state.items
        val dropChance = if (m.isBoss) 1.0 else 0.3
        if (rng.nextDouble() < dropChance) {
            val drop = if (m.isBoss) Item.ELIXIR else Item.POTION
            items = items + (drop to (items[drop] ?: 0) + 1)
            log += "Found a ${drop.label}!"
        }
        val floor = state.floor + 1
        val next = spawnMonster(floor)
        log += "Floor $floor: a ${next.name} appears!"
        return state.copy(
            heroes = heroes,
            gold = state.gold + m.goldReward,
            items = items,
            floor = floor,
            monster = next,
            monstersDefeated = state.monstersDefeated + 1,
        )
    }

    // endregion

    // region Shop & items

    fun buy(state: GameState, item: Item): GameState {
        if (state.gold < item.price) return state
        return state.copy(
            gold = state.gold - item.price,
            items = state.items + (item to state.itemCount(item) + 1),
        )
    }

    fun upgradeWeapon(state: GameState, heroId: Int): GameState {
        val hero = state.hero(heroId) ?: return state
        val cost = Rules.weaponCost(hero.weapon)
        if (state.gold < cost) return state
        return state.copy(
            gold = state.gold - cost,
            heroes = state.heroes.replace(hero.copy(weapon = hero.weapon + 1)),
            log = (state.log + "${hero.name}'s weapon is now +${hero.weapon + 1}.").takeLast(Rules.MAX_LOG),
        )
    }

    /** Returns null if the item can't be used on that hero (wrong target or none owned). */
    fun useItem(state: GameState, item: Item, heroId: Int): GameState? {
        if (state.itemCount(item) <= 0) return null
        val hero = state.hero(heroId) ?: return null
        val heroes = when (item) {
            Item.POTION -> {
                if (hero.isKo || hero.hp >= hero.maxHp) return null
                state.heroes.replace(hero.copy(hp = minOf(hero.maxHp, hero.hp + Rules.POTION_HEAL)))
            }
            Item.FEATHER -> {
                if (!hero.isKo) return null
                state.heroes.replace(hero.copy(hp = hero.maxHp / 2))
            }
            Item.ELIXIR -> state.heroes.map { it.copy(hp = it.maxHp) }
        }
        val msg = when (item) {
            Item.ELIXIR -> "The party is fully restored!"
            Item.FEATHER -> "${hero.name} is revived!"
            Item.POTION -> "${hero.name} recovers HP."
        }
        return state.copy(
            heroes = heroes,
            items = state.items + (item to state.itemCount(item) - 1),
            log = (state.log + msg).takeLast(Rules.MAX_LOG),
        )
    }

    fun editHero(state: GameState, heroId: Int, name: String, category: String): GameState {
        val hero = state.hero(heroId) ?: return state
        return state.copy(
            heroes = state.heroes.replace(
                hero.copy(
                    name = name.trim().ifEmpty { hero.name },
                    category = category.trim().ifEmpty { hero.category },
                )
            )
        )
    }

    // endregion

    private fun List<Hero>.replace(hero: Hero): List<Hero> = map { if (it.id == hero.id) hero else it }
}
