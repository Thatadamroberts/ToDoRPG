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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Monster
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Rules
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ChargePips
import com.todorpg.app.ui.components.Cursor
import com.todorpg.app.ui.components.LocalClock
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.StatBar
import com.todorpg.app.ui.components.attackEffect
import com.todorpg.app.ui.components.healEffect
import com.todorpg.app.ui.components.hpColor
import com.todorpg.app.ui.theme.RpgColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Backdrops cycle every 5 floors: grassland, cave, crypt, volcano. Raids use the last. */
private val backdrops = listOf(
    listOf(Color(0xFF5890E0), Color(0xFF90C0F0), Color(0xFF3C8830), Color(0xFF285820)),
    listOf(Color(0xFF181828), Color(0xFF383050), Color(0xFF504838), Color(0xFF302818)),
    listOf(Color(0xFF101830), Color(0xFF283058), Color(0xFF404858), Color(0xFF202838)),
    listOf(Color(0xFF401010), Color(0xFF802818), Color(0xFF503028), Color(0xFF281410)),
)
private val raidBackdrop = listOf(Color(0xFF200830), Color(0xFF502060), Color(0xFF302040), Color(0xFF180C20))

@Composable
fun BattleScreen(state: GameState, vm: GameViewModel) {
    var raidMode by rememberSaveable { mutableStateOf(false) }

    // Animation state, driven by attack events from the view model.
    val shake = remember { Animatable(0f) }
    val dissolve = remember { Animatable(0f) }
    val heroStep = remember { Animatable(0f) }
    val effect = remember { Animatable(0f) }
    val dmgRise = remember { Animatable(0f) }
    val heal = remember { Animatable(0f) }
    var flash by remember { mutableStateOf(false) }
    var effectClass by remember { mutableStateOf(HeroClass.KNIGHT) }
    var actingHero by remember { mutableStateOf<Int?>(null) }
    var healed by remember { mutableStateOf(emptyList<Int>()) }
    var dmgText by remember { mutableStateOf<String?>(null) }
    var dmgCrit by remember { mutableStateOf(false) }
    var dying by remember { mutableStateOf<Monster?>(null) }
    var victory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.battleEvents.collect { r ->
            if (r.defeated != null) dying = r.defeated
            actingHero = r.heroId
            effectClass = r.heroClass
            heroStep.animateTo(-28f, tween(110))
            launch {
                effect.snapTo(0f)
                effect.animateTo(1f, tween(650, easing = LinearEasing))
            }
            delay(if (r.heroClass == HeroClass.RANGER) 260 else 180)
            vm.playHitSound(r)
            dmgText = r.damage.toString()
            dmgCrit = r.critical
            launch {
                dmgRise.snapTo(0f)
                dmgRise.animateTo(1f, tween(800))
                dmgText = null
            }
            if (r.healedHeroIds.isNotEmpty()) {
                healed = r.healedHeroIds
                launch {
                    heal.snapTo(0f)
                    heal.animateTo(1f, tween(900))
                    healed = emptyList()
                }
            }
            flash = true
            for (x in listOf(12f, -10f, 7f, -5f, 2f, 0f)) shake.animateTo(x, tween(35))
            flash = false
            heroStep.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            actingHero = null
            r.defeated?.let { m ->
                delay(150)
                dissolve.animateTo(1f, tween(700, easing = LinearEasing))
                vm.playVictory(r)
                victory = buildString {
                    append("VICTORY!\n+${r.goldGained} G  +${m.expReward} EXP")
                    r.loot?.let { append("\nFound ${it.name}!") }
                }
                delay(2200)
                victory = null
                dying = null
                dissolve.snapTo(0f)
            }
        }
    }

    val raid = state.raid
    val inRaid = raidMode && raid != null
    val raidSealed = inRaid && !state.raidUnsealed()
    val raidCleared = inRaid && raid!!.defeated
    val target: Monster = dying ?: if (inRaid) raid!!.boss else state.monster
    val canAttack = dying == null && (!inRaid || (!raidSealed && !raidCleared))
    val ready = state.heroes.any { it.charges > 0 && !it.isKo }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (raid != null) {
            RpgWindow(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MenuButton("Floor ${state.floor}", selected = !raidMode) {
                        if (raidMode) vm.click()
                        raidMode = false
                    }
                    val raidBadge = if (state.raidUnsealed() && !raid.defeated) " !" else ""
                    MenuButton("Weekly Raid$raidBadge", selected = raidMode) {
                        if (!raidMode) vm.click()
                        raidMode = true
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
            Clouds(Modifier.matchParentSize(), dark = inRaid || state.floor > 5)

            val monsterSize = minOf(maxWidth * 0.5f, maxHeight * 0.8f) * if (target.isBoss) 1.1f else 0.9f
            if (raidCleared && dying == null) {
                Text(
                    "Raid cleared!\nA new foe arrives Monday.",
                    Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(monsterSize)
                        .offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                    contentAlignment = Alignment.Center,
                ) {
                    MonsterFigure(
                        target,
                        Modifier.fillMaxSize(),
                        sealed = raidSealed,
                        flash = { flash },
                        dissolve = { dissolve.value },
                    )
                    if (raidSealed) Chains(Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) { attackEffect(effectClass, effect.value) }
                    dmgText?.let { text ->
                        DamageNumber(text, dmgCrit, { dmgRise.value })
                    }
                }
            }

            PartyFormation(
                heroes = state.heroes,
                maxHeight = maxHeight,
                actingHero = actingHero,
                step = { heroStep.value },
                healed = healed,
                heal = { heal.value },
                celebrating = victory != null,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            )

            victory?.let {
                RpgWindow(Modifier.align(Alignment.Center), padding = 12.dp) {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
                }
            }
        }

        // Target status
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
            Spacer(Modifier.height(4.dp))
            StatBar(target.hp, target.maxHp, RpgColors.HpCrit, Modifier.fillMaxWidth(), height = 8.dp)
            if (inRaid) {
                RaidSeals(state, vm.currentDay())
            } else {
                Text(
                    "Strikes for ${state.monster.attack} per missed daily",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = RpgColors.Dim,
                )
            }
        }

        // Command window
        RpgWindow(Modifier.fillMaxWidth(), padding = 4.dp) {
            Text(
                when {
                    raidSealed -> "Break every seal to fight the raid boss."
                    raidCleared -> "This week's raid is done."
                    ready -> "Who attacks?"
                    else -> "No turns left. Complete quests to earn more!"
                },
                Modifier.padding(start = 6.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = RpgColors.Dim,
            )
            state.heroes.forEach { hero ->
                CommandRow(hero, enabled = canAttack && hero.charges > 0 && !hero.isKo) { vm.attack(hero.id, inRaid) }
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

/** Monster with an idle animation suited to its kind. */
@Composable
private fun MonsterFigure(
    m: Monster,
    modifier: Modifier,
    sealed: Boolean,
    flash: () -> Boolean,
    dissolve: () -> Float,
) {
    val clock = LocalClock.current
    val sheet = remember(m.kind, m.tier) { Sprites.monster(m.kind, m.tier) }
    PixelSprite(
        sheet,
        modifier.graphicsLayer {
            if (sealed) return@graphicsLayer
            val t = clock.value / 1000f
            when (m.kind) {
                MonsterKind.SLIME -> {
                    val s = sin(t * 4f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + s * 0.07f
                    scaleX = 1f - s * 0.05f
                }
                MonsterKind.BAT -> translationY = sin(t * 3f) * size.height * 0.06f
                MonsterKind.DEMON, MonsterKind.DRAGON -> translationY = sin(t * 1.6f) * size.height * 0.03f
                else -> rotationZ = sin(t * 2f) * 2f
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

/** Big bouncy damage number: pops up, falls back a little, then fades. */
@Composable
private fun DamageNumber(text: String, crit: Boolean, progress: () -> Float) {
    Text(
        if (crit) "$text!" else text,
        Modifier.graphicsLayer {
            val p = progress()
            val bounce = if (p < 0.35f) -sin(p / 0.35f * Math.PI.toFloat()) * 40f else 0f
            translationY = (bounce - p * 20f) * density
            alpha = if (p > 0.75f) (1f - p) / 0.25f else 1f
            val s = if (crit) 1.4f else 1f
            scaleX = s
            scaleY = s
        },
        style = MaterialTheme.typography.displaySmall,
        color = if (crit) RpgColors.Gold else Color.White,
    )
}

@Composable
private fun PartyFormation(
    heroes: List<Hero>,
    maxHeight: Dp,
    actingHero: Int?,
    step: () -> Float,
    healed: List<Int>,
    heal: () -> Float,
    celebrating: Boolean,
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
                    Box(
                        Modifier
                            .size(figure)
                            .graphicsLayer {
                                val lunge = if (hero.id == actingHero) step() else 0f
                                translationX = (lunge + stagger) * density
                                if (celebrating && !hero.isKo) {
                                    translationY = -abs(sin(clock.value / 120.0 + hero.id).toFloat()) * figure.toPx() * 0.25f
                                }
                            }
                    ) {
                        HeroFigure(hero, Modifier.fillMaxSize(), frameMs = if (hero.id == actingHero) 120 else 480)
                        if (hero.id in healed) Canvas(Modifier.fillMaxSize()) { healEffect(heal()) }
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
private fun CommandRow(hero: Hero, enabled: Boolean, onClick: () -> Unit) {
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
            Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (hero.isKo) RpgColors.HpCrit else if (enabled) RpgColors.Text else RpgColors.Disabled,
            maxLines = 1,
        )
        Text(
            hero.heroClass.skill,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.labelSmall,
            color = RpgColors.Dim,
        )
        StatBar(hero.hp, hero.maxHp, hpColor(hero.hp, hero.maxHp), Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(66.dp)) { ChargePips(hero.charges) }
    }
}
