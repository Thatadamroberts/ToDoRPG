package com.todorpg.app.game

import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameState
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
                val (n, r) = GameEngine.attack(s, 3, Random(it))
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
}
