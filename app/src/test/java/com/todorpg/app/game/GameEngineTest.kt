package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.Element
import com.todorpg.app.data.IntentType
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Rarity
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.data.TaskType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val ATTACK = Command(ActionKind.ATTACK)
    private val day = 20_000L

    private fun withTask(type: TaskType, difficulty: Difficulty = Difficulty.NORMAL, heroId: Int = 0): GameState =
        GameEngine.addTask(GameEngine.newGame(day), "Do thing", heroId, type, difficulty, day)

    @Test
    fun completingDailyGivesExpGoldAndCharge() {
        val (s, reward) = GameEngine.completeTask(withTask(TaskType.DAILY), 1, day)
        val hero = s.hero(0)!!
        assertEquals(Rules.dailyExp(Difficulty.NORMAL, 1), hero.exp)
        assertEquals(Rules.dailyGold(Difficulty.NORMAL, 1), s.gold)
        assertEquals(1, hero.charges)
        assertNotNull(reward)
        assertTrue(s.tasks.single().isDone(day))
    }

    @Test
    fun cannotCompleteTwiceInOneDay() {
        val (s, _) = GameEngine.completeTask(withTask(TaskType.DAILY), 1, day)
        val (s2, reward) = GameEngine.completeTask(s, 1, day)
        assertNull(reward)
        assertEquals(s, s2)
    }

    @Test
    fun oneOffGivesBonusExp() {
        val (s, _) = GameEngine.completeTask(withTask(TaskType.ONE_OFF, Difficulty.EASY), 1, day)
        assertEquals(Rules.oneOffExp(Difficulty.EASY), s.hero(0)!!.exp)
        assertTrue(Rules.oneOffExp(Difficulty.EASY) > Rules.dailyExp(Difficulty.EASY, 1))
    }

    @Test
    fun streakGrowsOnConsecutiveDays() {
        var s = withTask(TaskType.DAILY)
        s = GameEngine.completeTask(s, 1, day).first
        s = GameEngine.rollover(s, day + 1)
        s = GameEngine.completeTask(s, 1, day + 1).first
        assertEquals(2, s.tasks.single().streak)
    }

    @Test
    fun missedDailyHurtsHeroAndResetsStreak() {
        var s = withTask(TaskType.DAILY)
        s = GameEngine.completeTask(s, 1, day).first
        s = GameEngine.rollover(s, day + 1)
        val before = s.hero(0)!!.hp
        s = GameEngine.rollover(s, day + 2) // day+1 was missed
        val hero = s.hero(0)!!
        val expected = minOf(hero.maxHp, before - s.monster.attack + hero.maxHp / 10)
        assertEquals(expected, hero.hp)
        assertEquals(0, s.tasks.single().streak)
    }

    @Test
    fun levelUpHealsHero() {
        val hero = GameEngine.newGame(day).hero(0)!!.copy(hp = 1)
        val (up, levels) = GameEngine.gainExp(hero, Rules.expToNext(1) + 5)
        assertEquals(1, levels)
        assertEquals(2, up.level)
        assertEquals(5, up.exp)
        assertEquals(up.maxHp, up.hp)
    }

    @Test
    fun finishedOneOffsClearNextDay() {
        var s = withTask(TaskType.ONE_OFF)
        s = GameEngine.completeTask(s, 1, day).first
        s = GameEngine.rollover(s, day + 1)
        assertTrue(s.tasks.isEmpty())
    }

    @Test
    fun attackNeedsChargeAndKillsAdvanceFloor() {
        var s = GameEngine.newGame(day)
        assertNull(GameEngine.act(s, 0, ATTACK))

        s = s.copy(heroes = s.heroes.map { it.copy(charges = 9, weapon = 10, hp = 500) })
        var killed = false
        repeat(20) {
            if (!killed) {
                val (n, events) = GameEngine.act(s, 3, ATTACK, rng = Random(it))!!
                s = n
                killed = events.any { e -> e is VictoryEvent }
            }
        }
        assertTrue(killed)
        assertEquals(2, s.floor)
        assertEquals(1, s.monstersDefeated)
        assertTrue(s.gold > 0)
    }

    @Test
    fun itemsWork() {
        var s = GameEngine.newGame(day)
        s = s.copy(gold = 100, heroes = s.heroes.map { if (it.id == 1) it.copy(hp = 0) else it.copy(hp = 5) })
        assertNull(GameEngine.useItem(s, Item.POTION, 1)) // can't potion a KO'd hero
        s = GameEngine.useItem(s, Item.POTION, 0)!!
        assertEquals(minOf(s.hero(0)!!.maxHp, 5 + Rules.POTION_HEAL), s.hero(0)!!.hp)
        s = GameEngine.buy(s, Item.FEATHER)
        assertEquals(60, s.gold)
        s = GameEngine.useItem(s, Item.FEATHER, 1)!!
        assertTrue(!s.hero(1)!!.isKo)
    }

    @Test
    fun bossEveryFifthFloor() {
        assertTrue(GameEngine.spawnMonster(5).isBoss)
        assertTrue(!GameEngine.spawnMonster(4).isBoss)
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val (s, _) = GameEngine.completeTask(withTask(TaskType.DAILY), 1, day)
        val json = Json.encodeToString(GameState.serializer(), s)
        assertEquals(s, Json.decodeFromString(GameState.serializer(), json))
    }

    @Test
    fun gearAddsStatsAndSwaps() {
        var s = GameEngine.newGame(day)
        val sword = GameEngine.rollGear(1, 5, Random(1)).copy(slot = GearSlot.WEAPON, atk = 7, def = 0, hp = 0)
        s = s.copy(inventory = listOf(sword), nextGearId = 2)
        val baseAtk = s.hero(0)!!.attack
        s = GameEngine.equip(s, 0, sword.id)
        assertEquals(baseAtk + 7, s.hero(0)!!.attack)
        assertTrue(s.inventory.isEmpty())
        s = GameEngine.unequip(s, 0, GearSlot.WEAPON)
        assertEquals(baseAtk, s.hero(0)!!.attack)
        val gold = s.gold
        s = GameEngine.sell(s, sword.id)
        assertEquals(gold + sword.sellPrice, s.gold)
    }

    @Test
    fun unequippingArmorClampsHp() {
        var s = GameEngine.newGame(day)
        val armor = GameEngine.rollGear(1, 5, Random(1)).copy(slot = GearSlot.ARMOR, atk = 0, def = 1, hp = 20)
        s = GameEngine.equip(s.copy(inventory = listOf(armor)), 0, armor.id)
        s = s.copy(heroes = s.heroes.map { if (it.id == 0) it.copy(hp = it.maxHp) else it })
        s = GameEngine.unequip(s, 0, GearSlot.ARMOR)
        assertEquals(s.hero(0)!!.maxHp, s.hero(0)!!.hp)
    }

    @Test
    fun rolledGearRespectsMinimumRarity() {
        repeat(50) {
            assertTrue(GameEngine.rollGear(it.toLong(), 3, Random(it), Rarity.EPIC).rarity.ordinal >= Rarity.EPIC.ordinal)
        }
    }

    @Test
    fun raidIsSealedUntilEveryActiveHeroDoesThreeTasks() {
        var s = withTask(TaskType.ONE_OFF, heroId = 0)
        s = GameEngine.addTask(s, "Stretch", 1, TaskType.ONE_OFF, Difficulty.EASY, day)
        s = s.copy(heroes = s.heroes.map { it.copy(charges = 9, weapon = 50) })
        assertTrue(!s.raidUnsealed())
        assertNull(GameEngine.act(s, 0, ATTACK, raid = true))

        s = s.copy(raid = s.raid!!.copy(progress = mapOf(0 to 3, 1 to 2)))
        assertTrue(!s.raidUnsealed())
        s = s.copy(raid = s.raid!!.copy(progress = mapOf(0 to 3, 1 to 3)))
        assertTrue(s.raidUnsealed())

        var won = false
        repeat(40) {
            if (!won) {
                val r = GameEngine.act(s, it % 5, ATTACK, raid = true, rng = Random(it))
                if (r != null) {
                    s = r.first
                    won = r.second.any { e -> e is VictoryEvent }
                }
            }
        }
        assertTrue(won)
        assertTrue(s.raid!!.defeated)
        assertEquals(1, s.raidsDefeated)
        assertTrue(s.inventory.any { it.rarity.ordinal >= Rarity.EPIC.ordinal })
        assertEquals(1, s.floor) // raids don't advance floors
    }

    @Test
    fun newWeekBringsNewRaid() {
        val s = GameEngine.newGame(day)
        val nextWeek = GameEngine.rollover(s, day + 7)
        assertEquals(com.todorpg.app.data.Rules.weekStart(day + 7), nextWeek.raid!!.weekStart)
        assertTrue(nextWeek.raid!!.weekStart != s.raid!!.weekStart)
    }

    @Test
    fun weekStartsOnMonday() {
        // 2026-10-26 was a Monday.
        val monday = java.time.LocalDate.of(2026, 10, 26).toEpochDay()
        for (d in 0..6) assertEquals(monday, com.todorpg.app.data.Rules.weekStart(monday + d))
    }

    @Test
    fun recruitAndDismiss() {
        var s = GameEngine.newGame(day)
        s = GameEngine.recruit(s, HeroClass.BARD, "Lyra", "Creative")
        val bard = s.heroes.last()
        assertEquals(5, bard.id)
        assertEquals("Creative", bard.category)
        s = GameEngine.addTask(s, "Sketch", bard.id, TaskType.DAILY, Difficulty.EASY, day)
        assertEquals(6, GameEngine.dismiss(s, bard.id).heroes.size) // has quests: can't dismiss
        s = GameEngine.deleteTask(s, s.tasks.single().id)
        assertEquals(5, GameEngine.dismiss(s, bard.id).heroes.size)
    }

    @Test
    fun bardInspiresNextAttack() {
        var s = GameEngine.newGame(day)
        s = GameEngine.recruit(s, HeroClass.BARD, "Lyra", "Creative")
        s = s.copy(heroes = s.heroes.map { it.copy(charges = 5) })
        s = GameEngine.act(s, 5, ATTACK, rng = Random(1))!!.first
        assertTrue(s.inspired)
        s = GameEngine.act(s, 0, ATTACK, rng = Random(1))!!.first
        assertTrue(!s.inspired)
    }

    @Test
    fun armorReducesMissedDailyDamage() {
        var s = withTask(TaskType.DAILY)
        val armor = GameEngine.rollGear(1, 1, Random(1)).copy(slot = GearSlot.ARMOR, atk = 0, def = 3, hp = 0)
        s = GameEngine.equip(s.copy(inventory = listOf(armor)), 0, armor.id)
        s = GameEngine.rollover(s, day + 1) // day was missed
        val hero = s.hero(0)!!
        val dmg = maxOf(1, s.monster.attack - 3)
        assertEquals(minOf(hero.maxHp, hero.maxHp - dmg + hero.maxHp / 10), hero.hp)
    }

    private fun battleReady(): GameState {
        val s = GameEngine.newGame(day)
        // A sturdy monster so tests don't end the fight by accident.
        return s.copy(
            heroes = s.heroes.map { it.copy(charges = 9, mp = 30) },
            monster = s.monster.copy(hp = 10_000, maxHp = 10_000),
        )
    }

    @Test
    fun monsterActsAfterItsSpeedAndGuardHalves() {
        var s = battleReady()
        val m = s.monster
        val targetId = m.intent!!.targetId!!
        s = GameEngine.act(s, targetId, Command(ActionKind.GUARD), rng = Random(1))!!.first
        assertTrue(s.hero(targetId)!!.guarding)
        s = GameEngine.act(s, 2, ATTACK, rng = Random(2))!!.first
        assertEquals(2, s.monster.counter)
        val (after, events) = GameEngine.act(s, 2, ATTACK, rng = Random(3))!!
        val hit = events.filterIsInstance<MonsterEvent>().single()
        assertEquals(IntentType.STRIKE, hit.intent)
        assertTrue(targetId in hit.guarded)
        assertTrue(hit.damage.getValue(targetId) <= (m.attack * 1.1).toInt() / 2 + 1)
        assertEquals(0, after.monster.counter)
        assertTrue(after.heroes.none { it.guarding })
        assertNotNull(after.monster.intent)
    }

    @Test
    fun itemsDontAdvanceTheMonster() {
        var s = battleReady()
        s = s.copy(heroes = s.heroes.map { it.copy(hp = 5) })
        val (after, _) = GameEngine.act(s, 0, Command(ActionKind.ITEM, Item.POTION, target = 1))!!
        assertEquals(0, after.monster.counter)
        assertEquals(9, after.hero(0)!!.charges)
    }

    @Test
    fun weaknessDoesMoreDamageRevealsAndBreaks() {
        var s = battleReady() // floor 1 slime: weak to fire
        assertTrue(Element.FIRE in s.monster.weak)
        val (a, ev) = GameEngine.act(s, 3, ATTACK, rng = Random(5))!!
        val hit = ev.first() as HeroEvent
        assertTrue(hit.weak)
        assertTrue(Element.FIRE in a.knownWeak[s.monster.kind]!!)
        s = a
        var broke = false
        repeat(4) {
            if (!broke) {
                val (n, e) = GameEngine.act(s, 3, ATTACK, rng = Random(10 + it))!!
                s = n
                broke = (e.first() as HeroEvent).broke
            }
        }
        assertTrue(broke)
        assertEquals(com.todorpg.app.data.Rules.BROKEN_TURNS, s.monster.broken)
        // While broken, the monster doesn't act.
        repeat(3) {
            val (n, e) = GameEngine.act(s, 0, ATTACK, rng = Random(50 + it))!!
            assertTrue(e.none { x -> x is MonsterEvent })
            s = n
        }
    }

    @Test
    fun skillsCostMp() {
        val s = battleReady()
        val (after, events) = GameEngine.act(s, 2, Command(ActionKind.SKILL), rng = Random(1))!!
        assertEquals(30 - HeroClass.RANGER.skillCost, after.hero(2)!!.mp)
        assertEquals(3, (events.first() as HeroEvent).hits.size)
        val broke = s.copy(heroes = s.heroes.map { it.copy(mp = 0) })
        assertNull(GameEngine.act(broke, 2, Command(ActionKind.SKILL)))
    }

    @Test
    fun druidRebirthRevives() {
        var s = GameEngine.recruit(battleReady(), HeroClass.DRUID, "Fern", "Garden")
        s = s.copy(heroes = s.heroes.map { if (it.id == 1) it.copy(hp = 0) else it.copy(charges = 9, mp = 30) })
        val (after, events) = GameEngine.act(s, 5, Command(ActionKind.SKILL), rng = Random(1))!!
        assertTrue(!after.hero(1)!!.isKo)
        assertEquals(listOf(1), (events.first() as HeroEvent).revived)
    }

    @Test
    fun loadsSaveFromPreviousVersion() {
        // Trimmed save written by v0.2 (no MP, intents, break gauge or known weaknesses).
        val old = """
            {"heroes":[{"id":0,"heroClass":"KNIGHT","name":"Arthur","category":"Work","level":3,"exp":12,
              "hp":40,"weapon":1,"charges":2,"gear":{}}],
             "tasks":[{"id":1,"title":"Email","heroId":0,"type":"DAILY","difficulty":"NORMAL","createdDay":20000}],
             "gold":55,"items":{"POTION":2},"inventory":[],"floor":4,
             "monster":{"kind":"SKELETON","name":"Skeleton","floor":4,"tier":0,"hp":60,"maxHp":68,"attack":11,
               "isBoss":false,"goldReward":52,"expReward":26},
             "raid":null,"lastDay":20000,"log":[],"nextTaskId":2,"nextGearId":1,"settings":{"music":true}}
        """.trimIndent()
        val file = java.io.File.createTempFile("save", ".json")
        file.writeText(old)
        val s = com.todorpg.app.data.GameRepository(file).load()!!
        assertEquals(55, s.gold)
        assertEquals(3, s.hero(0)!!.level)
        assertEquals(4, s.floor)
        // New systems pick sensible defaults and the old monster can be fought.
        val ready = s.copy(heroes = s.heroes.map { it.copy(charges = 3) })
        val (after, events) = GameEngine.act(ready, 0, ATTACK, rng = Random(1))!!
        assertTrue(events.first() is HeroEvent)
        assertNotNull(after.monster.intent)
        file.delete()
    }
}
