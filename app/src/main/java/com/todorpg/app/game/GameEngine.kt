package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
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

/** Result of a hero attacking, used by the battle screen to animate. */
data class AttackResult(
    val heroId: Int,
    val heroClass: HeroClass,
    val damage: Int,
    val critical: Boolean,
    val raid: Boolean = false,
    val healedHeroIds: List<Int> = emptyList(),
    /** The monster as it was when it fell, if this attack finished it off. */
    val defeated: Monster? = null,
    val goldGained: Int = 0,
    val loot: Equipment? = null,
    val levelUps: List<Int> = emptyList(),
) {
    val killed: Boolean get() = defeated != null
}

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
            monster = spawnMonster(1),
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
                attack = 0,
                isBoss = true,
                goldReward = 150 + state.floor * 10,
                expReward = 60 + avgLevel * 15,
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
            if (h.isKo) h else h.copy(hp = minOf(h.maxHp, h.hp + h.maxHp * restDays / 10))
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
        val charged = leveled.copy(charges = minOf(Rules.MAX_CHARGES, leveled.charges + Rules.chargesFor(task.difficulty)))

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
        return (if (levels > 0) leveled.copy(hp = leveled.maxHp) else leveled) to levels
    }

    // endregion

    // region Battle

    /** Attack the floor monster, or the raid boss when [raid] is true. */
    fun attack(state: GameState, heroId: Int, raid: Boolean = false, rng: Random = Random.Default): Pair<GameState, AttackResult?> {
        val hero = state.hero(heroId) ?: return state to null
        if (hero.isKo || hero.charges <= 0) return state to null
        val target = if (raid) {
            val r = state.raid ?: return state to null
            if (r.defeated || !state.raidUnsealed()) return state to null
            r.boss
        } else state.monster
        if (target.hp <= 0) return state to null

        val c = hero.heroClass
        val roll = 0.85 + rng.nextDouble() * 0.3
        val critChance = when (c) {
            HeroClass.RANGER -> 0.30
            HeroClass.SAMURAI -> 0.20
            else -> 0.10
        }
        val critical = rng.nextDouble() < critChance
        val classMult = when (c) {
            HeroClass.KNIGHT -> 1.2
            HeroClass.MONK -> 1.1
            HeroClass.RANGER -> 1.0
            HeroClass.MAGE -> 1.5
            HeroClass.CLERIC -> 0.6
            HeroClass.BARD -> 0.7
            HeroClass.SAMURAI -> 1.35
            HeroClass.DRUID -> 0.8
        }
        val inspiredNow = state.inspired && c != HeroClass.BARD
        val damage = (hero.attack * classMult * roll * (if (critical) 2.0 else 1.0) * (if (inspiredNow) 1.5 else 1.0))
            .roundToInt().coerceAtLeast(1)

        var heroes = state.heroes.replace(hero.copy(charges = hero.charges - 1))
        val log = mutableListOf(
            "${hero.name} uses ${c.skill}! ${if (critical) "Critical! " else ""}$damage damage."
        )
        var inspired = if (inspiredNow) false else state.inspired

        val healed = mutableListOf<Int>()
        when (c) {
            HeroClass.CLERIC -> {
                val t = heroes.filter { !it.isKo && it.hp < it.maxHp }.minByOrNull { it.hp.toFloat() / it.maxHp }
                if (t != null) {
                    val amount = t.maxHp / 4
                    heroes = heroes.replace(t.copy(hp = minOf(t.maxHp, t.hp + amount)))
                    healed += t.id
                    log += "${t.name} recovers $amount HP."
                }
            }
            HeroClass.DRUID -> {
                heroes = heroes.map { h ->
                    if (h.isKo || h.hp >= h.maxHp) h else {
                        healed += h.id
                        h.copy(hp = minOf(h.maxHp, h.hp + h.maxHp / 10))
                    }
                }
                if (healed.isNotEmpty()) log += "Soothing vines heal the party."
            }
            HeroClass.BARD -> {
                inspired = true
                log += "The party feels inspired!"
            }
            else -> Unit
        }

        val remaining = (target.hp - damage).coerceAtLeast(0)
        var next = state.copy(heroes = heroes, inspired = inspired)
        next = if (raid) next.copy(raid = next.raid!!.copy(boss = target.copy(hp = remaining)))
        else next.copy(monster = target.copy(hp = remaining))

        var result = AttackResult(hero.id, c, damage, critical, raid = raid, healedHeroIds = healed)
        if (remaining == 0) {
            val (afterWin, win) = defeat(next, target.copy(hp = 0), raid, log, rng)
            next = afterWin
            result = result.copy(defeated = target.copy(hp = 0), goldGained = win.first, loot = win.second, levelUps = win.third)
        }
        next = next.copy(log = (next.log + log).takeLast(Rules.MAX_LOG))
        return next to result
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
            if (h.isKo) h else {
                val (up, levels) = gainExp(h, m.expReward)
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
            val drop = if (m.isBoss) Item.ELIXIR else Item.POTION
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
            val monster = spawnMonster(floor)
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
