package com.todorpg.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todorpg.app.audio.Sfx
import com.todorpg.app.data.Element
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.IntentType
import com.todorpg.app.data.Item
import com.todorpg.app.data.Monster
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Rules
import com.todorpg.app.game.ActionKind
import com.todorpg.app.game.BattleEvent
import com.todorpg.app.game.Command
import com.todorpg.app.game.GameEngine
import com.todorpg.app.game.HeroEvent
import com.todorpg.app.game.MonsterEvent
import com.todorpg.app.game.VictoryEvent
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ChargePips
import com.todorpg.app.ui.components.Cursor
import com.todorpg.app.ui.components.HeroFigure
import com.todorpg.app.ui.components.LocalClock
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.Pose
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.StatBar
import com.todorpg.app.ui.components.attackEffect
import com.todorpg.app.ui.components.castCircle
import com.todorpg.app.ui.components.guardShield
import com.todorpg.app.ui.components.healEffect
import com.todorpg.app.ui.components.hpColor
import com.todorpg.app.ui.components.iceEffect
import com.todorpg.app.ui.components.restingPose
import com.todorpg.app.ui.components.swingArc
import com.todorpg.app.ui.theme.RpgColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Backdrops cycle every 5 floors: grassland, cave, crypt, volcano. Raids have their own. */
private val backdrops = listOf(
    listOf(Color(0xFF5890E0), Color(0xFF90C0F0), Color(0xFF3C8830), Color(0xFF285820)),
    listOf(Color(0xFF181828), Color(0xFF383050), Color(0xFF504838), Color(0xFF302818)),
    listOf(Color(0xFF101830), Color(0xFF283058), Color(0xFF404858), Color(0xFF202838)),
    listOf(Color(0xFF401010), Color(0xFF802818), Color(0xFF503028), Color(0xFF281410)),
)
private val raidBackdrop = listOf(Color(0xFF200830), Color(0xFF502060), Color(0xFF302040), Color(0xFF180C20))

private const val MONSTER_ANCHOR = -1

/** Physical fighters run up to the monster; casters attack from the back row. */
private val runners = setOf(HeroClass.KNIGHT, HeroClass.MONK, HeroClass.SAMURAI)

private data class Popup(val id: Long, val text: String, val color: Color, val big: Boolean, val dx: Float)

/** Everything the battle animation script moves around. */
private class BattleFx {
    val shake = Animatable(0f)
    val dissolve = Animatable(0f)
    val effect = Animatable(0f)
    val heal = Animatable(0f)
    val swing = Animatable(0f)
    val monsterDash = Animatable(0f)
    val heroDash = Animatable(0f)
    val heroKnock = Animatable(0f)
    val appear = Animatable(1f)

    var flash by mutableStateOf(false)
    var effectClass by mutableStateOf(HeroClass.KNIGHT)
    var effectIce by mutableStateOf(false)
    var actingHero by mutableStateOf<Int?>(null)
    var monsterPose by mutableStateOf(Pose.IDLE)
    var victory by mutableStateOf<String?>(null)
    var celebrating by mutableStateOf(false)
    var monsterDamage by mutableStateOf(0)

    val poses = mutableStateMapOf<Int, Pose>()
    val tints = mutableStateMapOf<Int, Color>()
    val heroDamage = mutableStateMapOf<Int, Int>()
    val healing = mutableStateMapOf<Int, Boolean>()
    val casting = mutableStateMapOf<Int, Color>()
    val popups = mutableStateMapOf<Int, List<Popup>>()
    private var popupSeq = 0L

    fun pop(anchor: Int, text: String, color: Color = Color.White, big: Boolean = false, dx: Float = 0f) {
        popups[anchor] = popups[anchor].orEmpty() + Popup(++popupSeq, text, color, big, dx)
    }

    fun removePopup(anchor: Int, id: Long) {
        popups[anchor] = popups[anchor].orEmpty().filterNot { it.id == id }
    }

    fun reset() {
        poses.clear()
        tints.clear()
        heroDamage.clear()
        healing.clear()
        casting.clear()
        actingHero = null
        monsterPose = Pose.IDLE
        monsterDamage = 0
        flash = false
        celebrating = false
        victory = null
    }
}

@Composable
fun BattleScreen(state: GameState, vm: GameViewModel) {
    var raidMode by rememberSaveable { mutableStateOf(false) }
    var menuHero by remember { mutableStateOf<Int?>(null) }
    val fx = remember { BattleFx() }
    val anim by vm.battleAnim.collectAsStateWithLifecycle()
    var fieldWidthPx by remember { mutableStateOf(1f) }
    val currentVm by rememberUpdatedState(vm)

    // If the player leaves mid-animation, apply the result straight away.
    DisposableEffect(Unit) {
        onDispose { currentVm.battleAnim.value?.let { currentVm.finishBattleAnim(it.id) } }
    }

    LaunchedEffect(anim?.id) {
        val a = anim ?: return@LaunchedEffect
        try {
            for (e in a.events) play(e, fx, vm, fieldWidthPx, state)
        } finally {
            fx.reset()
            vm.finishBattleAnim(a.id)
        }
    }

    val raid = state.raid
    val inRaid = raidMode && raid != null
    val raidSealed = inRaid && !state.raidUnsealed()
    val raidCleared = inRaid && raid!!.defeated
    val rawTarget: Monster = if (inRaid) raid!!.boss else state.monster
    val target = rawTarget.copy(hp = (rawTarget.hp - fx.monsterDamage).coerceAtLeast(0))
    val busy = anim != null
    val canFight = !busy && (!inRaid || (!raidSealed && !raidCleared))

    // New monster: grow in from nothing.
    LaunchedEffect(rawTarget.kind, rawTarget.floor, rawTarget.name, inRaid) {
        fx.dissolve.snapTo(0f)
        fx.appear.snapTo(0f)
        fx.appear.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (raid != null) {
            RpgWindow(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MenuButton("Floor ${state.floor}", selected = !raidMode, enabled = !busy) {
                        if (raidMode) vm.click()
                        raidMode = false
                        menuHero = null
                    }
                    val raidBadge = if (state.raidUnsealed() && !raid.defeated) " !" else ""
                    MenuButton("Weekly Raid$raidBadge", selected = raidMode, enabled = !busy) {
                        if (!raidMode) vm.click()
                        raidMode = true
                        menuHero = null
                    }
                }
            }
        }

        // Battlefield
        val colors = if (inRaid) raidBackdrop else backdrops[((state.floor - 1) / 5) % backdrops.size]
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(0f to colors[0], 0.55f to colors[1], 0.56f to colors[2], 1f to colors[3]))
        ) {
            fieldWidthPx = constraints.maxWidth.toFloat()
            Clouds(Modifier.matchParentSize(), dark = inRaid || state.floor > 5)

            val monsterSize = minOf(maxWidth * 0.48f, maxHeight * 0.72f) * if (target.isBoss) 1.1f else 0.9f
            if (raidCleared && !busy) {
                Text(
                    "Raid cleared!\nA new foe arrives Monday.",
                    Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .zIndex(if (fx.monsterPose == Pose.STRIKE) 2f else 0f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IntentBubble(target, state, sealed = raidSealed)
                    Box(
                        Modifier
                            .size(monsterSize)
                            .graphicsLayer {
                                translationX = fx.shake.value * density + fx.monsterDash.value * fieldWidthPx
                                val a = fx.appear.value
                                alpha = a
                                scaleX = 0.4f + 0.6f * a
                                scaleY = 0.4f + 0.6f * a
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MonsterFigure(
                            target,
                            Modifier.fillMaxSize(),
                            pose = fx.monsterPose,
                            sealed = raidSealed,
                            flash = { fx.flash },
                            dissolve = { fx.dissolve.value },
                        )
                        if (raidSealed) Chains(Modifier.fillMaxSize())
                        if (target.broken > 0) Stars(Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            if (fx.effectIce) iceEffect(fx.effect.value) else attackEffect(fx.effectClass, fx.effect.value)
                        }
                        PopupLayer(fx, MONSTER_ANCHOR)
                    }
                }
            }

            PartyFormation(
                heroes = state.heroes,
                fx = fx,
                maxHeight = maxHeight,
                fieldWidthPx = fieldWidthPx,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            )

            fx.victory?.let {
                RpgWindow(Modifier.align(Alignment.Center).zIndex(5f), padding = 12.dp) {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
                }
            }
        }

        StatusWindow(state, target, inRaid, vm.currentDay())

        // Command window: pick a hero, then an action.
        RpgWindow(Modifier.fillMaxWidth(), padding = 4.dp) {
            val chosen = menuHero?.let { state.hero(it) }
            if (chosen != null && canFight) {
                ActionMenu(
                    hero = chosen,
                    state = state,
                    onCommand = { cmd ->
                        vm.act(chosen.id, cmd, inRaid)
                        menuHero = null
                    },
                    onBack = {
                        vm.click()
                        menuHero = null
                    },
                    click = vm::click,
                )
            } else {
                val ready = state.heroes.any { it.charges > 0 && !it.isKo }
                Text(
                    when {
                        busy -> "..."
                        raidSealed -> "Break every seal to fight the raid boss."
                        raidCleared -> "This week's raid is done."
                        ready -> "Choose a hero."
                        else -> "No turns left. Complete quests to earn more!"
                    },
                    Modifier.padding(start = 6.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = RpgColors.Dim,
                )
                state.heroes.forEach { hero ->
                    val shownHp = (hero.hp - (fx.heroDamage[hero.id] ?: 0)).coerceAtLeast(0)
                    CommandRow(hero, shownHp, enabled = canFight && hero.charges > 0 && !hero.isKo) {
                        vm.click()
                        menuHero = hero.id
                    }
                }
            }
        }

        // Battle log
        RpgWindow(Modifier.fillMaxWidth(), padding = 6.dp) {
            state.log.takeLast(2).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
    }
}

// region Animation script

private suspend fun play(e: BattleEvent, fx: BattleFx, vm: GameViewModel, width: Float, before: GameState) {
    when (e) {
        is HeroEvent -> playHero(e, fx, vm, width)
        is MonsterEvent -> playMonster(e, fx, vm)
        is VictoryEvent -> playVictory(e, fx, vm, before)
    }
}

private suspend fun playHero(e: HeroEvent, fx: BattleFx, vm: GameViewModel, width: Float) = coroutineScope {
    val id = e.heroId
    when (e.kind) {
        ActionKind.ITEM -> {
            vm.sfx(Sfx.HEAL)
            fx.poses[id] = Pose.VICTORY
            fx.healing[id] = true
            fx.pop(id, e.item?.label ?: "Item", Color(0xFF80FF90))
            fx.heal.snapTo(0f)
            fx.heal.animateTo(1f, tween(800))
            fx.healing.remove(id)
            fx.poses.remove(id)
            return@coroutineScope
        }
        ActionKind.GUARD -> {
            fx.actingHero = id
            fx.poses[id] = Pose.GUARD
            fx.heroDash.animateTo(-0.03f, tween(120))
            vm.sfx(Sfx.CURSOR)
            fx.pop(id, "GUARD", Color(0xFF80C0FF))
            delay(450)
            fx.heroDash.animateTo(0f, tween(150))
            fx.poses.remove(id)
            fx.actingHero = null
            return@coroutineScope
        }
        else -> Unit
    }

    fx.actingHero = id
    val c = e.heroClass
    val runs = c in runners && e.hits.isNotEmpty()
    val isCast = e.kind == ActionKind.SKILL || c in setOf(HeroClass.MAGE, HeroClass.CLERIC, HeroClass.BARD, HeroClass.DRUID)

    // Approach.
    if (runs) {
        fx.poses[id] = Pose.WALK
        fx.heroDash.animateTo(-0.34f, tween(280, easing = LinearEasing))
        fx.poses[id] = Pose.WINDUP
        delay(130)
    } else {
        fx.poses[id] = Pose.WALK
        fx.heroDash.animateTo(-0.07f, tween(160))
        if (isCast) {
            fx.poses[id] = Pose.CAST
            fx.casting[id] = Color(e.element?.color ?: 0xFF80FF90)
            vm.sfx(Sfx.MAGIC, 0.5f)
            delay(450)
        } else {
            fx.poses[id] = Pose.WINDUP
            delay(200)
        }
    }

    // Strike(s).
    fx.poses[id] = Pose.STRIKE
    fx.casting.remove(id)
    if (runs) launch {
        fx.swing.snapTo(0f)
        fx.swing.animateTo(1f, tween(260))
    }
    fx.effectClass = c
    fx.effectIce = e.element == Element.ICE
    e.hits.forEachIndexed { i, dmg ->
        launch {
            fx.effect.snapTo(0f)
            fx.effect.animateTo(1f, tween(if (i == 0) 620 else 380, easing = LinearEasing))
        }
        delay(if (c == HeroClass.RANGER && i == 0) 260 else 160)
        vm.playHitSound(e)
        fx.monsterDamage += dmg
        fx.pop(MONSTER_ANCHOR, if (e.critical && i == 0) "$dmg!" else "$dmg", if (e.critical) RpgColors.Gold else Color.White, big = true, dx = (i - (e.hits.size - 1) / 2f) * 0.25f)
        fx.monsterPose = Pose.HURT
        fx.flash = true
        for (x in listOf(12f, -10f, 7f, -4f, 0f)) fx.shake.animateTo(x, tween(30))
        fx.flash = false
        fx.monsterPose = Pose.IDLE
        if (i < e.hits.lastIndex) delay(90)
    }
    if (e.weak) fx.pop(MONSTER_ANCHOR, "WEAK!", Color(e.element?.color ?: 0xFFFFFFFF))
    if (e.resisted) fx.pop(MONSTER_ANCHOR, "RESIST", RpgColors.Dim)
    if (e.broke) {
        vm.sfx(Sfx.CRIT)
        fx.pop(MONSTER_ANCHOR, "BREAK!", RpgColors.Charge, big = true)
    }
    if (c == HeroClass.BARD && e.kind == ActionKind.SKILL) fx.pop(MONSTER_ANCHOR, "Zzz", Color(0xFFC0C0FF))

    // Healing and revives.
    val helped = e.healed + e.revived
    if (helped.isNotEmpty()) {
        vm.sfx(Sfx.HEAL, 0.7f)
        helped.forEach { fx.healing[it] = true }
        e.revived.forEach { fx.pop(it, "REVIVE", Color(0xFF80FF90)) }
        launch {
            fx.heal.snapTo(0f)
            fx.heal.animateTo(1f, tween(900))
            fx.healing.clear()
        }
        if (e.hits.isEmpty()) {
            fx.poses[id] = Pose.VICTORY
            delay(500)
        }
    }

    // Return to the line.
    delay(160)
    fx.poses[id] = Pose.WALK
    fx.heroDash.animateTo(0f, tween(if (runs) 300 else 160, easing = FastOutSlowInEasing))
    fx.poses.remove(id)
    fx.actingHero = null
}

private suspend fun playMonster(e: MonsterEvent, fx: BattleFx, vm: GameViewModel) = coroutineScope {
    fx.monsterPose = Pose.WINDUP
    delay(if (e.intent == IntentType.HEAVY) 450 else 250)
    fx.monsterPose = Pose.STRIKE
    fx.monsterDash.animateTo(if (e.intent == IntentType.SWEEP) 0.12f else 0.22f, tween(160))
    vm.sfx(if (e.intent == IntentType.HEAVY) Sfx.CRIT else Sfx.HIT)
    e.damage.forEach { (heroId, dmg) ->
        fx.poses[heroId] = Pose.HURT
        fx.tints[heroId] = Color(0xFFFF3030)
        fx.heroDamage[heroId] = dmg
        val guarded = heroId in e.guarded
        fx.pop(heroId, if (guarded) "$dmg 🛡" else "$dmg", if (guarded) Color(0xFF80C0FF) else Color(0xFFFF8080), big = true)
    }
    launch {
        for (x in listOf(0.04f, -0.02f, 0.015f, 0f)) fx.heroKnock.animateTo(x, tween(60))
    }
    delay(280)
    fx.monsterDash.animateTo(0f, tween(260))
    fx.monsterPose = Pose.IDLE
    fx.tints.clear()
    e.knockedOut.forEach { id -> fx.pop(id, "KO", RpgColors.HpCrit) }
    e.damage.keys.forEach { fx.poses.remove(it) }
    delay(150)
}

private suspend fun playVictory(e: VictoryEvent, fx: BattleFx, vm: GameViewModel, before: GameState) {
    delay(120)
    vm.sfx(Sfx.DEFEAT)
    fx.dissolve.animateTo(1f, tween(700, easing = LinearEasing))
    vm.sfx(Sfx.VICTORY, 0.9f)
    fx.celebrating = true
    before.heroes.filter { !it.isKo }.forEach { fx.poses[it.id] = Pose.VICTORY }
    fx.victory = buildString {
        append("VICTORY!\n+${e.gold} G  +${e.monster.expReward} EXP")
        e.loot?.let { append("\nFound ${it.name}!") }
    }
    delay(2300)
}

// endregion

// region Battlefield pieces

@Composable
private fun PopupLayer(fx: BattleFx, anchor: Int) {
    fx.popups[anchor].orEmpty().forEach { p ->
        key(p.id) {
            PopupText(p) { fx.removePopup(anchor, p.id) }
        }
    }
}

/** A number or word that bounces up out of a sprite and fades. */
@Composable
private fun PopupText(p: Popup, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(if (p.big) 950 else 1100, easing = LinearEasing))
        onDone()
    }
    Text(
        p.text,
        Modifier.graphicsLayer {
            val t = progress.value
            val bounce = if (t < 0.3f) -sin(t / 0.3f * Math.PI.toFloat()) * 36f else 0f
            translationY = (bounce - t * 22f - if (p.big) 0f else 26f) * density
            translationX = p.dx * size.width * 2
            alpha = if (t > 0.75f) (1f - t) / 0.25f else 1f
        },
        style = if (p.big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleSmall,
        color = p.color,
    )
}

/** What the monster will do next, so the player can plan. */
@Composable
private fun IntentBubble(m: Monster, state: GameState, sealed: Boolean) {
    if (sealed || m.hp <= 0) {
        Spacer(Modifier.height(22.dp))
        return
    }
    val clock = LocalClock.current
    val intent = m.intent
    val text = when {
        m.broken > 0 -> "💫 Stunned (${m.broken})"
        intent == null -> "…"
        else -> {
            val who = when (intent.type) {
                IntentType.SWEEP -> "all"
                else -> state.hero(intent.targetId ?: -1)?.name ?: "?"
            }
            "${if (intent.type == IntentType.HEAVY) "‼" else "⚔"} ${intent.type.label} → $who · ${m.turnsUntilAction}"
        }
    }
    val urgent = m.broken == 0 && m.turnsUntilAction == 1
    Box(
        Modifier
            .height(22.dp)
            .graphicsLayer {
                if (urgent) {
                    val s = 1f + 0.06f * sin(clock.value / 90f)
                    scaleX = s
                    scaleY = s
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(if (urgent) Color(0xCC801010) else Color(0xAA000020))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Monster with an idle animation suited to its kind, plus attack poses. */
@Composable
private fun MonsterFigure(
    m: Monster,
    modifier: Modifier,
    pose: Pose,
    sealed: Boolean,
    flash: () -> Boolean,
    dissolve: () -> Float,
) {
    val clock = LocalClock.current
    val sheet = remember(m.kind, m.tier, pose) { Sprites.monster(m.kind, m.tier, pose) }
    PixelSprite(
        sheet,
        modifier.graphicsLayer {
            if (sealed) return@graphicsLayer
            when (pose) {
                Pose.WINDUP -> {
                    rotationZ = -6f
                    return@graphicsLayer
                }
                Pose.STRIKE -> {
                    rotationZ = 8f
                    return@graphicsLayer
                }
                Pose.HURT -> {
                    rotationZ = -5f
                    return@graphicsLayer
                }
                else -> Unit
            }
            val t = clock.value / 1000f
            when (m.kind) {
                MonsterKind.SLIME -> {
                    val s = sin(t * 4f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + s * 0.08f
                    scaleX = 1f - s * 0.06f
                }
                MonsterKind.BAT -> {
                    translationY = sin(t * 3f) * size.height * 0.07f
                    translationX = cos(t * 1.5f) * size.width * 0.04f
                }
                MonsterKind.DEMON, MonsterKind.DRAGON -> translationY = sin(t * 1.6f) * size.height * 0.03f
                MonsterKind.GOLEM -> rotationZ = sin(t * 1.2f) * 1.5f
                else -> rotationZ = sin(t * 2f) * 2.5f
            }
        },
        frameMs = when (m.kind) {
            MonsterKind.BAT -> 160
            MonsterKind.GOLEM -> 900
            else -> 520
        },
        animate = !sealed,
        grayscale = sealed,
        flash = flash,
        dissolve = dissolve,
    )
}

@Composable
private fun PartyFormation(
    heroes: List<Hero>,
    fx: BattleFx,
    maxHeight: Dp,
    fieldWidthPx: Float,
    modifier: Modifier,
) {
    val clock = LocalClock.current
    val cols = if (heroes.size > 5) 2 else 1
    val rows = (heroes.size + cols - 1) / cols
    val figure = minOf(maxHeight / (rows + 0.6f), 56.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        heroes.chunked(rows).forEach { column ->
            Column(horizontalAlignment = Alignment.End) {
                column.forEachIndexed { i, hero ->
                    val stagger = if (i % 2 == 0) 0f else 14f
                    val acting = hero.id == fx.actingHero
                    val hurt = fx.heroDamage.containsKey(hero.id) && fx.poses[hero.id] == Pose.HURT
                    Box(
                        Modifier
                            .size(figure)
                            .zIndex(if (acting) 3f else 1f)
                            .graphicsLayer {
                                translationX = stagger * density +
                                    (if (acting) fx.heroDash.value * fieldWidthPx else 0f) +
                                    (if (hurt) fx.heroKnock.value * fieldWidthPx else 0f)
                                val pose = fx.poses[hero.id]
                                if (pose == Pose.WINDUP) rotationZ = 8f
                                if (pose == Pose.STRIKE) rotationZ = -12f
                                if (pose == Pose.HURT) rotationZ = 10f
                                if (pose == Pose.WALK && acting && fx.heroDash.isRunning && fx.heroDash.targetValue == 0f) scaleX = -1f
                                if (fx.celebrating && !hero.isKo) {
                                    val t = clock.value / 120.0 + hero.id
                                    translationY = -abs(sin(t).toFloat()) * figure.toPx() * 0.3f
                                    if ((clock.value / 900 + hero.id) % 3 == 0L) scaleX = cos(clock.value / 90f)
                                }
                            }
                    ) {
                        fx.casting[hero.id]?.let { color ->
                            Canvas(Modifier.fillMaxSize()) { castCircle(clock.value, color) }
                        }
                        val pose = fx.poses[hero.id]
                        HeroFigure(
                            hero,
                            Modifier.fillMaxSize(),
                            pose = pose ?: if (hero.isKo) null else restingPose(hero),
                            tint = { fx.tints[hero.id] },
                        )
                        if (acting && pose == Pose.STRIKE) Canvas(Modifier.fillMaxSize()) { swingArc(fx.swing.value) }
                        if (hero.guarding || pose == Pose.GUARD) Canvas(Modifier.fillMaxSize()) { guardShield(clock.value) }
                        if (fx.healing[hero.id] == true) Canvas(Modifier.fillMaxSize()) { healEffect(fx.heal.value) }
                        PopupLayer(fx, hero.id)
                    }
                }
            }
        }
    }
}

/** Slowly drifting pixel clouds (or mist in dark areas). */
@Composable
private fun Clouds(modifier: Modifier, dark: Boolean) {
    val clock = LocalClock.current
    Canvas(modifier) {
        val t = clock.value / 1000f
        val u = 4.dp.toPx()
        val color = if (dark) Color(0x22FFFFFF) else Color(0xCCFFFFFF)
        listOf(Triple(0.08f, 9f, 0f), Triple(0.22f, 6f, 0.45f), Triple(0.15f, 4f, 0.8f)).forEach { (y, speed, off) ->
            val span = size.width + 24 * u
            val x = ((t * speed * u / 4 + off * span) % span) - 12 * u
            cloud(x, size.height * y, u, color)
        }
    }
}

private fun DrawScope.cloud(x: Float, y: Float, u: Float, color: Color) {
    val shape = listOf("...XXXX.....", ".XXXXXXXXX..", "XXXXXXXXXXXX")
    shape.forEachIndexed { row, line ->
        line.forEachIndexed { col, c ->
            if (c == 'X') drawRect(color, Offset(x + col * u, y + row * u), Size(u + 0.5f, u + 0.5f))
        }
    }
}

/** Stars circling a stunned (broken) monster's head. */
@Composable
private fun Stars(modifier: Modifier) {
    val clock = LocalClock.current
    Canvas(modifier) {
        val u = size.minDimension / 22
        for (k in 0 until 3) {
            val a = clock.value / 220f + k * 2.1f
            val x = size.width / 2 + cos(a) * size.width * 0.3f
            val y = size.height * 0.12f + sin(a) * size.height * 0.05f
            drawRect(Color(0xFFFFE060), Offset(x - u / 2, y - u * 1.5f), Size(u, 3 * u))
            drawRect(Color(0xFFFFE060), Offset(x - u * 1.5f, y - u / 2), Size(3 * u, u))
        }
    }
}

/** Grey chains and a padlock over a sealed raid boss. */
@Composable
private fun Chains(modifier: Modifier) {
    val clock = LocalClock.current
    Canvas(modifier) {
        val u = size.minDimension / 22
        val links = 14
        for (dir in listOf(1f, -1f)) {
            for (i in 0..links) {
                val f = i / links.toFloat()
                val x = size.width * (if (dir > 0) f else 1 - f)
                val y = size.height * (0.1f + 0.8f * f)
                val c = if (i % 2 == 0) Color(0xFFB0B8C8) else Color(0xFF707888)
                drawRect(Color(0xFF101018), Offset(x - u, y - u), Size(2 * u, 2 * u))
                drawRect(c, Offset(x - u * 0.6f, y - u * 0.6f), Size(1.2f * u, 1.2f * u))
            }
        }
        val pulse = 0.8f + 0.2f * sin(clock.value / 300f)
        val cx = size.width / 2
        val cy = size.height / 2
        drawRect(Color(0xFF101018), Offset(cx - 3 * u, cy - u), Size(6 * u, 5 * u))
        drawRect(Color(0xFFF0C838).copy(alpha = pulse), Offset(cx - 2.4f * u, cy - 0.4f * u), Size(4.8f * u, 3.8f * u))
        drawRect(Color(0xFF101018), Offset(cx - 2 * u, cy - 3.5f * u), Size(4 * u, 3 * u))
        drawRect(Color(0xFFB0B8C8), Offset(cx - 1.2f * u, cy - 2.7f * u), Size(2.4f * u, 2 * u))
        drawRect(Color(0xFF101018), Offset(cx - 0.4f * u, cy + 0.6f * u), Size(0.8f * u, 1.6f * u))
    }
}

// endregion

// region Windows

@Composable
private fun StatusWindow(state: GameState, target: Monster, inRaid: Boolean, today: Long) {
    RpgWindow(Modifier.fillMaxWidth(), padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (target.isBoss) "BOSS · " else "") + target.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (target.isBoss) RpgColors.HpCrit else RpgColors.Text,
            )
            Text("${target.hp}/${target.maxHp}", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(3.dp))
        StatBar(target.hp, target.maxHp, RpgColors.HpCrit, Modifier.fillMaxWidth(), height = 8.dp)
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BREAK", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = RpgColors.Dim)
            if (target.broken > 0) {
                Text(
                    "BROKEN · takes 1.5x damage",
                    style = MaterialTheme.typography.labelSmall,
                    color = RpgColors.Charge,
                )
            } else {
                StatBar(target.breakGauge, target.breakMax, RpgColors.Charge, Modifier.weight(1f), height = 5.dp)
            }
        }
        val known = state.knownWeak[target.kind].orEmpty()
        val weakText = target.weak.joinToString("  ") { if (it in known) it.label else "?" }
        Text(
            "Weak: $weakText   (hit a weakness to reveal it and fill BREAK)",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = RpgColors.Dim,
            maxLines = 1,
        )
        if (inRaid) RaidSeals(state, today)
    }
}

@Composable
private fun ActionMenu(
    hero: Hero,
    state: GameState,
    onCommand: (Command) -> Unit,
    onBack: () -> Unit,
    click: () -> Unit,
) {
    var stage by remember(hero.id) { mutableStateOf("main") }
    var item by remember(hero.id) { mutableStateOf<Item?>(null) }
    val c = hero.heroClass

    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hero.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
        Text("MP ${hero.mp}/${hero.maxMp}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB070F0))
        Spacer(Modifier.width(8.dp))
        ChargePips(hero.charges)
    }
    when (stage) {
        "main" -> {
            Row {
                Column(Modifier.weight(1f)) {
                    MenuButton("Attack · ${c.element.label}", selected = true) { onCommand(Command(ActionKind.ATTACK)) }
                    val canSkill = GameEngine.canAct(state, hero, Command(ActionKind.SKILL))
                    MenuButton("${c.skill} · ${c.skillCost} MP", enabled = canSkill) { onCommand(Command(ActionKind.SKILL)) }
                }
                Column(Modifier.weight(1f)) {
                    MenuButton("Guard") { onCommand(Command(ActionKind.GUARD)) }
                    MenuButton("Item", enabled = Item.entries.any { state.itemCount(it) > 0 }) {
                        click()
                        stage = "item"
                    }
                }
            }
            Text(
                "${c.skill}: ${c.skillBlurb}. Guard halves the next hit and restores 3 MP.",
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = RpgColors.Dim,
            )
            MenuButton("Back", onClick = onBack)
        }
        "item" -> {
            Item.entries.filter { state.itemCount(it) > 0 }.forEach { i ->
                MenuButton("${i.label} x${state.itemCount(i)}") {
                    click()
                    item = i
                    stage = "target"
                }
            }
            MenuButton("Back") {
                click()
                stage = "main"
            }
        }
        else -> {
            val chosen = item ?: return
            Text("Use ${chosen.label} on:", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = RpgColors.Dim)
            state.heroes.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { h ->
                        val ok = GameEngine.useItem(state, chosen, h.id) != null
                        MenuButton("${h.name} ${h.hp}/${h.maxHp}", Modifier.weight(1f), enabled = ok) {
                            onCommand(Command(ActionKind.ITEM, chosen, h.id))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            MenuButton("Back") {
                click()
                stage = "item"
            }
        }
    }
}

@Composable
private fun RaidSeals(state: GameState, today: Long) {
    val raid = state.raid ?: return
    val heroes = state.raidSealHeroes()
    val daysLeft = 7 - (today - raid.weekStart)
    Text(
        if (raid.defeated) "Defeated! New raid in $daysLeft day${if (daysLeft == 1L) "" else "s"}."
        else "Seals (${Rules.RAID_SEAL_TASKS} quests per hero this week) · $daysLeft day${if (daysLeft == 1L) "" else "s"} left",
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = RpgColors.Dim,
    )
    if (raid.defeated) return
    if (heroes.isEmpty()) {
        Text("Add some quests first!", style = MaterialTheme.typography.labelSmall)
        return
    }
    heroes.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { h ->
                val n = (raid.progress[h.id] ?: 0).coerceAtMost(Rules.RAID_SEAL_TASKS)
                val broken = n >= Rules.RAID_SEAL_TASKS
                Text(
                    (if (broken) "✓" else "🔒") + " ${h.name} $n/${Rules.RAID_SEAL_TASKS}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (broken) RpgColors.Hp else RpgColors.Text,
                    maxLines = 1,
                )
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CommandRow(hero: Hero, shownHp: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cursor(enabled)
        Text(
            hero.name,
            Modifier.width(68.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (hero.isKo) RpgColors.HpCrit else if (enabled) RpgColors.Text else RpgColors.Disabled,
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            StatBar(shownHp, hero.maxHp, hpColor(shownHp, hero.maxHp), Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            StatBar(hero.mp, hero.maxMp, Color(0xFFB070F0), Modifier.fillMaxWidth(), height = 3.dp)
        }
        Text(
            "$shownHp",
            Modifier.width(34.dp).padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Box(Modifier.width(66.dp)) { ChargePips(hero.charges) }
    }
}

// endregion
