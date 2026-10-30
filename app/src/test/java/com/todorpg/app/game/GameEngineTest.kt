package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
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
        assertNull(GameEngine.attack(s, 0).second)

        s = s.copy(heroes = s.heroes.map { it.copy(charges = 9, weapon = 10) })
        var killed = false
        repeat(20) {
            if (!killed) {
                val (n, r) = GameEngine.attack(s, 3, rng = Random(it))
                s = n
                killed = r!!.killed
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
        assertNull(GameEngine.attack(s, 0, raid = true).second)

        s = s.copy(raid = s.raid!!.copy(progress = mapOf(0 to 3, 1 to 2)))
        assertTrue(!s.raidUnsealed())
        s = s.copy(raid = s.raid!!.copy(progress = mapOf(0 to 3, 1 to 3)))
        assertTrue(s.raidUnsealed())

        var won = false
        repeat(40) {
            if (!won) {
                val (n, r) = GameEngine.attack(s, it % 5, raid = true, rng = Random(it))
                if (r != null) {
                    s = n
                    won = r.killed
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
        s = GameEngine.attack(s, 5, rng = Random(1)).first
        assertTrue(s.inspired)
        s = GameEngine.attack(s, 0, rng = Random(1)).first
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
}
