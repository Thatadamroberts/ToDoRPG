package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.Element
import com.todorpg.app.data.Intent
import com.todorpg.app.data.IntentType
import com.todorpg.app.data.Equipment
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.Hero
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Item
import com.todorpg.app.data.Monster
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Raid
import com.todorpg.app.data.Rarity
import com.todorpg.app.data.Rules
import com.todorpg.app.data.Settings
import com.todorpg.app.data.Task
import com.todorpg.app.data.TaskType
import kotlin.math.roundToInt
import kotlin.random.Random

enum class ActionKind { ATTACK, SKILL, GUARD, ITEM }

/** What the player asked a hero to do. [target] is the hero an item or skill is aimed at. */
data class Command(val kind: ActionKind, val item: Item? = null, val target: Int? = null)

/** Battle events, in order, for the battle screen to animate. */
sealed interface BattleEvent

data class HeroEvent(
    val heroId: Int,
    val heroClass: HeroClass,
    val kind: ActionKind,
    val element: Element?,
    val hits: List<Int> = emptyList(),
    val critical: Boolean = false,
    val weak: Boolean = false,
    val resisted: Boolean = false,
    val broke: Boolean = false,
    val healed: List<Int> = emptyList(),
    val revived: List<Int> = emptyList(),
    val item: Item? = null,
) : BattleEvent {
    val total: Int get() = hits.sum()
}

data class MonsterEvent(
    val intent: IntentType,
    val damage: Map<Int, Int>,
    val guarded: Set<Int>,
    val knockedOut: List<Int>,
) : BattleEvent

data class VictoryEvent(
    val monster: Monster,
    val raid: Boolean,
    val gold: Int,
    val loot: Equipment?,
    val levelUps: List<Int>,
) : BattleEvent

/** Short feedback shown after an action, e.g. "+20 EXP  +10 G". */
data class Reward(val text: String, val heroId: Int, val levelUp: Boolean = false)

/**
 * All game rules as pure functions over [GameState]. No Android code here so it
 * can be unit tested on the JVM.
 */
object GameEngine {

    fun newGame(today: Long): GameState {
        val base = GameState(
            heroes = listOf(
                Hero(0, HeroClass.KNIGHT, "Arthur", "Work"),
                Hero(1, HeroClass.MONK, "Bo", "Health"),
                Hero(2, HeroClass.RANGER, "Wren", "Home"),
                Hero(3, HeroClass.MAGE, "Vivi", "Learning"),
                Hero(4, HeroClass.CLERIC, "Rosa", "Social"),
            ),
            monster = spawnMonster(1).copy(intent = Intent(IntentType.STRIKE, 0)),
            lastDay = today,
            log = listOf("Your party sets out. Finish quests to earn turns in battle!"),
        )
        return base.copy(raid = spawnRaid(Rules.weekStart(today), base))
    }

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
            speed = if (isBoss) 2 else 3,
            breakMax = if (isBoss) 10 else 6,
        )
    }

    fun spawnRaid(weekStart: Long, state: GameState): Raid {
        val week = weekStart / 7
        val kind = if (week % 2 == 0L) MonsterKind.GOLEM else MonsterKind.DEMON
        val avgLevel = state.heroes.map { it.level }.average().roundToInt().coerceAtLeast(1)
        val hp = 200 + avgLevel * 60 + state.floor * 10
        val title = if (kind == MonsterKind.GOLEM) "Iron Colossus" else "Arch Demon"
        return Raid(
            weekStart = weekStart,
            boss = Monster(
                kind = kind,
                name = title,
                floor = state.floor,
                tier = ((week / 2) % 4).toInt(),
                hp = hp,
                maxHp = hp,
                attack = 4 + avgLevel * 3,
                isBoss = true,
                goldReward = 150 + state.floor * 10,
                expReward = 60 + avgLevel * 15,
                speed = 2,
                breakMax = 12,
            ),
        )
    }

    // endregion

    // region Day rollover

    /**
     * Called when the app opens. For each day that passed, every daily task that was
     * not completed lets the monster strike that task's hero (armour reduces this).
     * Then the party rests. Finished one-off quests from earlier days are cleared,
     * and a new raid boss arrives each Monday.
     */
    fun rollover(state: GameState, today: Long): GameState {
        val last = state.lastDay ?: return state.copy(lastDay = today)
        if (today <= last) return ensureRaid(state, today)

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
                        val hero = heroes.firstOrNull { it.id == task.heroId }
                        if (hero != null && !hero.isKo) {
                            val dmg = maxOf(1, state.monster.attack * misses - hero.defense)
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
            if (h.isKo) h else h.copy(
                hp = minOf(h.maxHp, h.hp + h.maxHp * restDays / 10),
                mp = minOf(h.maxMp, h.mp + h.maxMp * restDays * 3 / 10),
            )
        }
        log += "A new day dawns. The party rests at camp."

        val next = state.copy(
            heroes = heroes,
            tasks = tasks,
            lastDay = today,
            log = (state.log + log).takeLast(Rules.MAX_LOG),
        )
        return ensureRaid(next, today)
    }

    private fun ensureRaid(state: GameState, today: Long): GameState {
        val week = Rules.weekStart(today)
        if (state.raid?.weekStart == week) return state
        val raid = spawnRaid(week, state)
        return state.copy(
            raid = raid,
            log = (state.log + "A raid boss stirs: the ${raid.boss.name}!").takeLast(Rules.MAX_LOG),
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
        val charged = leveled.copy(
            charges = minOf(Rules.MAX_CHARGES, leveled.charges + Rules.chargesFor(task.difficulty)),
            mp = minOf(leveled.maxMp, leveled.mp + Rules.MP_PER_TASK),
        )

        val updatedTask = task.copy(lastCompletedDay = today, streak = newStreak)
        val log = mutableListOf("${hero.name} completed \"${task.title}\".")
        if (levels > 0) log += "${hero.name} reached level ${charged.level}!"

        val raid = state.raid?.let { r ->
            if (r.weekStart == Rules.weekStart(today) && !r.defeated) {
                val before = r.progress[hero.id] ?: 0
                if (before + 1 == Rules.RAID_SEAL_TASKS) log += "${hero.name} broke a raid seal!"
                r.copy(progress = r.progress + (hero.id to before + 1))
            } else r
        }

        val text = buildString {
            append("${hero.name} +$earnedExp EXP")
            if (gold > 0) append("  +$gold G")
            if (task.type == TaskType.DAILY && newStreak > 1) append("  streak $newStreak")
        }
        val next = state.copy(
            heroes = state.heroes.replace(charged),
            tasks = state.tasks.map { if (it.id == taskId) updatedTask else it },
            gold = state.gold + gold,
            raid = raid,
            tasksCompleted = state.tasksCompleted + 1,
            log = (state.log + log).takeLast(Rules.MAX_LOG),
        )
        return next to Reward(text, hero.id, levelUp = levels > 0)
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
        val leveled = hero.copy(level = level, exp = exp)
        return (if (levels > 0) leveled.copy(hp = leveled.maxHp, mp = leveled.maxMp) else leveled) to levels
    }

    // endregion

    // region Battle

    private fun pickIntent(m: Monster, heroes: List<Hero>, rng: Random): Intent {
        val alive = heroes.filter { !it.isKo }
        val roll = rng.nextDouble()
        val type = when {
            m.isBoss && roll < 0.25 -> IntentType.SWEEP
            (m.isBoss || m.floor >= 3) && roll > 0.7 -> IntentType.HEAVY
            else -> IntentType.STRIKE
        }
        val target = if (type == IntentType.SWEEP || alive.isEmpty()) null else alive[rng.nextInt(alive.size)].id
        return Intent(type, target)
    }

    /** Gives a monster from an older save (or a fresh one) its first intent. */
    fun withIntent(m: Monster, heroes: List<Hero>, rng: Random = Random.Default): Monster =
        if (m.intent != null) m else m.copy(intent = pickIntent(m, heroes, rng))

    fun canAct(state: GameState, hero: Hero, cmd: Command): Boolean = when (cmd.kind) {
        ActionKind.ITEM -> cmd.item != null && cmd.target != null && GameEngine.useItem(state, cmd.item, cmd.target) != null
        ActionKind.SKILL -> !hero.isKo && hero.charges > 0 && hero.mp >= hero.heroClass.skillCost &&
            (hero.heroClass != HeroClass.DRUID || state.heroes.any { it.isKo || it.hp < it.maxHp })
        else -> !hero.isKo && hero.charges > 0
    }

    /**
     * One hero action against the floor monster (or the raid boss when [raid]).
     * Returns the new state plus the events to animate, or null if not allowed.
     * Attacks, skills and guards cost a battle turn and advance the monster's clock;
     * items are free.
     */
    fun act(
        state: GameState,
        heroId: Int,
        cmd: Command,
        raid: Boolean = false,
        rng: Random = Random.Default,
    ): Pair<GameState, List<BattleEvent>>? {
        val hero = state.hero(heroId) ?: return null
        val raidState = state.raid
        if (raid && (raidState == null || raidState.defeated || !state.raidUnsealed())) return null
        val baseTarget = if (raid) raidState!!.boss else state.monster
        if (baseTarget.hp <= 0 || !canAct(state, hero, cmd)) return null

        if (cmd.kind == ActionKind.ITEM) {
            val next = useItem(state, cmd.item!!, cmd.target!!) ?: return null
            return next to listOf(HeroEvent(cmd.target, next.hero(cmd.target)!!.heroClass, ActionKind.ITEM, null, item = cmd.item, healed = listOf(cmd.target)))
        }

        var target = withIntent(baseTarget, state.heroes, rng)
        var heroes = state.heroes
        val log = mutableListOf<String>()
        var inspired = state.inspired
        var knownWeak = state.knownWeak
        val c = hero.heroClass

        fun setHero(h: Hero) {
            heroes = heroes.map { if (it.id == h.id) h else it }
        }

        fun current(id: Int) = heroes.first { it.id == id }

        // Pay for the action.
        setHero(
            hero.copy(
                charges = hero.charges - 1,
                mp = if (cmd.kind == ActionKind.SKILL) hero.mp - c.skillCost
                else if (cmd.kind == ActionKind.GUARD) minOf(hero.maxMp, hero.mp + 3) else hero.mp,
                guarding = cmd.kind == ActionKind.GUARD || hero.guarding,
            )
        )

        var event = HeroEvent(hero.id, c, cmd.kind, null)

        fun strike(mult: Double, element: Element, critChance: Double, forceCrit: Boolean = false): Int {
            val critical = forceCrit || rng.nextDouble() < critChance
            val weak = element in target.weak
            val resisted = element in target.resist
            val inspiredNow = inspired && c != HeroClass.BARD
            if (inspiredNow) inspired = false
            val roll = 0.85 + rng.nextDouble() * 0.3
            val dmg = (hero.attack * mult * roll *
                (if (critical) 2.0 else 1.0) *
                (if (inspiredNow) 1.5 else 1.0) *
                (if (weak) 1.5 else if (resisted) 0.5 else 1.0) *
                (if (target.broken > 0) 1.5 else 1.0)).roundToInt().coerceAtLeast(1)
            var gauge = target.breakGauge
            var broken = target.broken
            var broke = false
            if (weak && broken == 0) {
                gauge += if (critical) 3 else 2
                if (!state.knownWeak[target.kind].orEmpty().contains(element)) {
                    knownWeak = knownWeak + (target.kind to (knownWeak[target.kind].orEmpty() + element))
                }
                if (gauge >= target.breakMax) {
                    gauge = 0
                    broken = Rules.BROKEN_TURNS
                    broke = true
                    log += "${target.name} is BROKEN!"
                }
            }
            target = target.copy(hp = (target.hp - dmg).coerceAtLeast(0), breakGauge = gauge, broken = broken, counter = if (broke) 0 else target.counter)
            event = event.copy(
                element = element,
                hits = event.hits + dmg,
                critical = event.critical || critical,
                weak = event.weak || weak,
                resisted = event.resisted || resisted,
                broke = event.broke || broke,
            )
            return dmg
        }

        fun healHero(id: Int, amount: Int) {
            val h = current(id)
            if (h.isKo) return
            setHero(h.copy(hp = minOf(h.maxHp, h.hp + amount)))
            event = event.copy(healed = event.healed + id)
        }

        val baseCrit = when (c) {
            HeroClass.RANGER -> 0.30
            HeroClass.SAMURAI -> 0.20
            else -> 0.10
        }
        when (cmd.kind) {
            ActionKind.GUARD -> log += "${hero.name} takes a defensive stance."
            ActionKind.ATTACK -> {
                val mult = when (c) {
                    HeroClass.KNIGHT -> 1.2
                    HeroClass.MONK -> 1.1
                    HeroClass.RANGER -> 1.0
                    HeroClass.MAGE -> 1.5
                    HeroClass.CLERIC -> 0.6
                    HeroClass.BARD -> 0.7
                    HeroClass.SAMURAI -> 1.35
                    HeroClass.DRUID -> 0.8
                }
                val dmg = strike(mult, c.element, baseCrit)
                log += "${hero.name} uses ${c.attackName}! $dmg damage."
                when (c) {
                    HeroClass.CLERIC -> heroes.filter { !it.isKo && it.hp < it.maxHp }
                        .minByOrNull { it.hp.toFloat() / it.maxHp }?.let { healHero(it.id, it.maxHp / 4) }
                    HeroClass.DRUID -> heroes.filter { !it.isKo && it.hp < it.maxHp }.forEach { healHero(it.id, it.maxHp / 10) }
                    HeroClass.BARD -> {
                        inspired = true
                        log += "The party feels inspired!"
                    }
                    else -> Unit
                }
            }
            ActionKind.SKILL -> {
                log += "${hero.name} uses ${c.skill}!"
                when (c) {
                    HeroClass.KNIGHT -> strike(2.0, Element.SLASH, baseCrit)
                    HeroClass.MONK -> {
                        strike(1.2, Element.BLUNT, baseCrit)
                        healHero(hero.id, hero.maxHp * 3 / 10)
                    }
                    HeroClass.RANGER -> repeat(3) { if (target.hp > 0) strike(0.7, Element.PIERCE, baseCrit) }
                    HeroClass.MAGE -> strike(2.2, Element.ICE, baseCrit)
                    HeroClass.CLERIC -> heroes.filter { !it.isKo }.forEach { healHero(it.id, it.maxHp * 3 / 10) }
                    HeroClass.BARD -> {
                        strike(0.5, Element.SOUND, 0.0)
                        target = target.copy(counter = 0)
                        log += "${target.name} grows drowsy..."
                    }
                    HeroClass.SAMURAI -> strike(1.35, Element.SLASH, 1.0, forceCrit = true)
                    HeroClass.DRUID -> {
                        val fallen = heroes.firstOrNull { it.isKo }
                        if (fallen != null) {
                            setHero(fallen.copy(hp = fallen.maxHp * 4 / 10))
                            event = event.copy(revived = listOf(fallen.id))
                            log += "${fallen.name} is revived!"
                        } else {
                            heroes.filter { !it.isKo && it.hp < it.maxHp }
                                .minByOrNull { it.hp.toFloat() / it.maxHp }?.let { healHero(it.id, it.maxHp / 2) }
                        }
                    }
                }
                if (event.total > 0) log += "${event.total} damage" + if (event.hits.size > 1) " in ${event.hits.size} hits." else "."
            }
            ActionKind.ITEM -> Unit
        }
        if (event.weak) log += "It's super effective!"

        val events = mutableListOf<BattleEvent>(event)
        var next = state.copy(heroes = heroes, inspired = inspired, knownWeak = knownWeak)
        next = if (raid) next.copy(raid = next.raid!!.copy(boss = target)) else next.copy(monster = target)

        if (target.hp == 0) {
            val (afterWin, win) = defeat(next, target, raid, log, rng)
            next = afterWin
            events += VictoryEvent(target, raid, win.first, win.second, win.third)
        } else if (!event.broke) {
            // The hit that breaks a monster doesn't also count down its stun.
            val (afterTurn, monsterEvent) = monsterTurn(next, target, raid, log, rng)
            next = afterTurn
            monsterEvent?.let { events += it }
        }
        return next.copy(log = (next.log + log).takeLast(Rules.MAX_LOG)) to events
    }

    /** Advances the monster's clock by one hero action, and lets it act when ready. */
    private fun monsterTurn(
        state: GameState,
        target: Monster,
        raid: Boolean,
        log: MutableList<String>,
        rng: Random,
    ): Pair<GameState, MonsterEvent?> {
        fun withTarget(s: GameState, m: Monster) =
            if (raid) s.copy(raid = s.raid!!.copy(boss = m)) else s.copy(monster = m)

        if (target.broken > 0) {
            val m = target.copy(broken = target.broken - 1)
            if (m.broken == 0) log += "${m.name} recovers!"
            return withTarget(state, m) to null
        }
        val counter = target.counter + 1
        if (counter < target.speed) return withTarget(state, target.copy(counter = counter)) to null

        val intent = target.intent ?: pickIntent(target, state.heroes, rng)
        val alive = state.heroes.filter { !it.isKo }
        val victims = when {
            alive.isEmpty() -> emptyList()
            intent.type == IntentType.SWEEP -> alive
            else -> listOf(alive.firstOrNull { it.id == intent.targetId } ?: alive[rng.nextInt(alive.size)])
        }
        val damage = mutableMapOf<Int, Int>()
        val guarded = mutableSetOf<Int>()
        val kos = mutableListOf<Int>()
        val heroes = state.heroes.map { h ->
            if (victims.none { it.id == h.id }) return@map h.copy(guarding = false)
            var dmg = (target.attack * intent.type.mult * (0.9 + rng.nextDouble() * 0.2)).roundToInt() - h.defense / 2
            if (h.guarding) {
                dmg /= 2
                guarded += h.id
            }
            dmg = dmg.coerceAtLeast(1)
            damage[h.id] = dmg
            val hp = (h.hp - dmg).coerceAtLeast(0)
            if (hp == 0) kos += h.id
            h.copy(hp = hp, guarding = false)
        }
        log += when (intent.type) {
            IntentType.SWEEP -> "${target.name} sweeps the party!"
            else -> "${target.name} uses ${intent.type.label} on ${victims.firstOrNull()?.name ?: "nobody"}!"
        }
        kos.forEach { id -> log += "${heroes.first { it.id == id }.name} is KO'd!" }
        val m = target.copy(counter = 0, intent = pickIntent(target, heroes, rng))
        return withTarget(state.copy(heroes = heroes), m) to MonsterEvent(intent.type, damage, guarded, kos)
    }

    private fun defeat(
        state: GameState,
        m: Monster,
        raid: Boolean,
        log: MutableList<String>,
        rng: Random,
    ): Pair<GameState, Triple<Int, Equipment?, List<Int>>> {
        log += "${m.name} is defeated! +${m.goldReward} G, +${m.expReward} EXP each."
        val levelUps = mutableListOf<Int>()
        val heroes = state.heroes.map { h ->
            if (h.isKo) h.copy(guarding = false) else {
                val (up, levels) = gainExp(h.copy(guarding = false), m.expReward)
                if (levels > 0) {
                    log += "${h.name} reached level ${up.level}!"
                    levelUps += h.id
                }
                up
            }
        }
        var next = state.copy(heroes = heroes, gold = state.gold + m.goldReward)

        // Consumable drops.
        if (!raid && rng.nextDouble() < if (m.isBoss) 1.0 else 0.25) {
            val drop = if (m.isBoss) Item.ELIXIR else if (rng.nextBoolean()) Item.POTION else Item.ETHER
            next = next.copy(items = next.items + (drop to next.itemCount(drop) + 1))
            log += "Found a ${drop.label}!"
        }

        // Gear drops: raids guarantee Epic+, bosses Rare+, others sometimes drop gear.
        val minRarity = when {
            raid -> Rarity.EPIC
            m.isBoss -> Rarity.RARE
            else -> Rarity.COMMON
        }
        var loot: Equipment? = null
        if (raid || m.isBoss || rng.nextDouble() < 0.35) {
            val gear = rollGear(next.nextGearId, m.floor, rng, minRarity)
            loot = gear
            next = addToInventory(next.copy(nextGearId = next.nextGearId + 1), gear, log)
        }

        next = if (raid) {
            next.copy(raid = next.raid!!.copy(defeated = true), raidsDefeated = next.raidsDefeated + 1)
        } else {
            val floor = next.floor + 1
            val monster = withIntent(spawnMonster(floor), next.heroes, rng)
            log += "Floor $floor: a ${monster.name} appears!"
            next.copy(floor = floor, monster = monster, monstersDefeated = next.monstersDefeated + 1)
        }
        return next to Triple(m.goldReward, loot, levelUps)
    }

    // endregion

    // region Gear

    private val gearNames = mapOf(
        GearSlot.WEAPON to listOf("Dagger", "Sword", "Axe", "Spear", "Staff", "Bow", "Katana", "Mace"),
        GearSlot.ARMOR to listOf("Tunic", "Chainmail", "Robe", "Breastplate", "Cloak", "Gi"),
        GearSlot.ACCESSORY to listOf("Ring", "Amulet", "Charm", "Bangle", "Earring", "Talisman"),
    )
    private val rarityPrefix = mapOf(
        Rarity.COMMON to listOf("Iron", "Leather", "Plain", "Old"),
        Rarity.RARE to listOf("Silver", "Fine", "Hunter's", "Knight's"),
        Rarity.EPIC to listOf("Mythril", "Runed", "Heroic", "Storm"),
        Rarity.LEGENDARY to listOf("Celestial", "Dragon", "Eternal", "Starfall"),
    )

    fun rollGear(id: Long, power: Int, rng: Random, minRarity: Rarity = Rarity.COMMON): Equipment {
        val roll = rng.nextDouble()
        val rolled = when {
            roll < 0.04 -> Rarity.LEGENDARY
            roll < 0.16 -> Rarity.EPIC
            roll < 0.45 -> Rarity.RARE
            else -> Rarity.COMMON
        }
        val rarity = if (rolled.ordinal < minRarity.ordinal) minRarity else rolled
        val slot = GearSlot.entries[rng.nextInt(GearSlot.entries.size)]
        val budget = ((2 + power * 0.8) * rarity.mult).roundToInt().coerceAtLeast(1)
        val (atk, def, hp) = when (slot) {
            GearSlot.WEAPON -> Triple(budget, 0, 0)
            GearSlot.ARMOR -> Triple(0, (budget + 1) / 2, budget * 2)
            GearSlot.ACCESSORY -> {
                val a = rng.nextInt(budget + 1)
                Triple(a / 2, (budget - a) / 2, (budget - a) * 2)
            }
        }
        val name = rarityPrefix.getValue(rarity).random(rng) + " " + gearNames.getValue(slot).random(rng)
        return Equipment(id, name, slot, rarity, atk = atk, def = def, hp = hp)
    }

    private fun addToInventory(state: GameState, gear: Equipment, log: MutableList<String>): GameState =
        if (state.inventory.size >= Rules.MAX_INVENTORY) {
            log += "Bag full! Sold ${gear.name} for ${gear.sellPrice} G."
            state.copy(gold = state.gold + gear.sellPrice)
        } else {
            log += "Found ${gear.rarity.label} ${gear.name}!"
            state.copy(inventory = state.inventory + gear)
        }

    fun equip(state: GameState, heroId: Int, gearId: Long): GameState {
        val hero = state.hero(heroId) ?: return state
        val gear = state.inventory.firstOrNull { it.id == gearId } ?: return state
        val old = hero.gear[gear.slot]
        val updated = hero.copy(gear = hero.gear + (gear.slot to gear))
        val inventory = state.inventory.filterNot { it.id == gearId } + listOfNotNull(old)
        return state.copy(heroes = state.heroes.replace(updated.clampHp()), inventory = inventory)
    }

    fun unequip(state: GameState, heroId: Int, slot: GearSlot): GameState {
        val hero = state.hero(heroId) ?: return state
        val gear = hero.gear[slot] ?: return state
        if (state.inventory.size >= Rules.MAX_INVENTORY) return state
        val updated = hero.copy(gear = hero.gear - slot)
        return state.copy(heroes = state.heroes.replace(updated.clampHp()), inventory = state.inventory + gear)
    }

    fun sell(state: GameState, gearId: Long): GameState {
        val gear = state.inventory.firstOrNull { it.id == gearId } ?: return state
        return state.copy(inventory = state.inventory - gear, gold = state.gold + gear.sellPrice)
    }

    private fun Hero.clampHp(): Hero = if (hp > maxHp) copy(hp = maxHp) else this

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
            log = (state.log + "${hero.name}'s training is now +${hero.weapon + 1}.").takeLast(Rules.MAX_LOG),
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
            Item.ETHER -> {
                if (hero.isKo || hero.mp >= hero.maxMp) return null
                state.heroes.replace(hero.copy(mp = minOf(hero.maxMp, hero.mp + Rules.ETHER_MP)))
            }
        }
        val msg = when (item) {
            Item.ELIXIR -> "The party is fully restored!"
            Item.FEATHER -> "${hero.name} is revived!"
            Item.POTION -> "${hero.name} recovers HP."
            Item.ETHER -> "${hero.name} recovers MP."
        }
        return state.copy(
            heroes = heroes,
            items = state.items + (item to state.itemCount(item) - 1),
            log = (state.log + msg).takeLast(Rules.MAX_LOG),
        )
    }

    // endregion

    // region Party

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

    /** Adds a new hero (and so a new category). New recruits start at the party's lowest level. */
    fun recruit(state: GameState, heroClass: HeroClass, name: String, category: String): GameState {
        if (state.heroes.size >= Rules.MAX_PARTY || name.isBlank() || category.isBlank()) return state
        val id = (state.heroes.maxOfOrNull { it.id } ?: -1) + 1
        val level = state.heroes.minOfOrNull { it.level } ?: 1
        val hero = Hero(id, heroClass, name.trim(), category.trim(), level = level, hp = Rules.maxHp(level))
        return state.copy(
            heroes = state.heroes + hero,
            log = (state.log + "${hero.name} the ${heroClass.title} joins the party!").takeLast(Rules.MAX_LOG),
        )
    }

    /** Removes a hero with no quests. Their gear goes back in the bag. */
    fun dismiss(state: GameState, heroId: Int): GameState {
        val hero = state.hero(heroId) ?: return state
        if (state.heroes.size <= 1 || state.tasks.any { it.heroId == heroId }) return state
        return state.copy(
            heroes = state.heroes.filterNot { it.id == heroId },
            inventory = state.inventory + hero.gear.values,
            log = (state.log + "${hero.name} leaves the party.").takeLast(Rules.MAX_LOG),
        )
    }

    fun updateSettings(state: GameState, settings: Settings): GameState = state.copy(settings = settings)

    // endregion

    private fun List<Hero>.replace(hero: Hero): List<Hero> = map { if (it.id == hero.id) hero else it }
}
