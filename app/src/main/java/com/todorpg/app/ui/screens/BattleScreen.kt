package com.todorpg.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.Monster
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ChargePips
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.StatBar
import com.todorpg.app.ui.components.hpColor
import com.todorpg.app.ui.theme.RpgColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Backdrops cycle every 5 floors: grassland, cave, crypt, volcano. */
private val backdrops = listOf(
    listOf(Color(0xFF5890E0), Color(0xFF90C0F0), Color(0xFF3C8830), Color(0xFF285820)),
    listOf(Color(0xFF181828), Color(0xFF383050), Color(0xFF504838), Color(0xFF302818)),
    listOf(Color(0xFF101830), Color(0xFF283058), Color(0xFF404858), Color(0xFF202838)),
    listOf(Color(0xFF401010), Color(0xFF802818), Color(0xFF503028), Color(0xFF281410)),
)

@Composable
fun BattleScreen(state: GameState, vm: GameViewModel) {
    val shake = remember { Animatable(0f) }
    val monsterAlpha = remember { Animatable(1f) }
    val heroStep = remember { Animatable(0f) }
    val dmgRise = remember { Animatable(0f) }
    var flash by remember { mutableStateOf(false) }
    var actingHero by remember { mutableStateOf<Int?>(null) }
    var dmgText by remember { mutableStateOf<String?>(null) }
    var dying by remember { mutableStateOf<Monster?>(null) }
    var victory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.battleEvents.collect { r ->
            if (r.defeated != null) dying = r.defeated
            actingHero = r.heroId
            heroStep.animateTo(-24f, tween(120))
            dmgText = (if (r.critical) "CRITICAL ${r.damage}" else "${r.damage}")
            launch {
                dmgRise.snapTo(0f)
                dmgRise.animateTo(-40f, tween(600))
                dmgText = null
            }
            flash = true
            for (x in listOf(10f, -10f, 6f, -6f, 0f)) shake.animateTo(x, tween(40))
            flash = false
            heroStep.animateTo(0f, tween(120))
            actingHero = null
            r.defeated?.let { m ->
                monsterAlpha.animateTo(0f, tween(500))
                victory = "VICTORY!  +${m.goldReward} G"
                delay(1200)
                victory = null
                dying = null
                monsterAlpha.animateTo(1f, tween(300))
            }
        }
    }

    val monster = dying ?: state.monster
    val ready = state.heroes.any { it.charges > 0 && !it.isKo }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Battlefield
        val colors = backdrops[((state.floor - 1) / 5) % backdrops.size]
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(0f to colors[0], 0.55f to colors[1], 0.56f to colors[2], 1f to colors[3]))
        ) {
            val monsterSize = minOf(maxWidth * 0.45f, maxHeight * 0.75f) * if (monster.isBoss) 1.15f else 1f
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .offset(x = shake.value.dp)
                    .alpha(monsterAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                PixelSprite(Sprites.monster(monster.kind, monster.tier), Modifier.size(monsterSize), flash = flash)
                dmgText?.let {
                    Text(
                        it,
                        Modifier.offset(y = dmgRise.value.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (it.startsWith("CRIT")) RpgColors.Gold else Color.White,
                    )
                }
            }
            val heroSize = minOf(maxHeight / (state.heroes.size + 0.5f), 56.dp)
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                state.heroes.forEachIndexed { i, hero ->
                    val step = if (actingHero == hero.id) heroStep.value else 0f
                    PixelSprite(
                        Sprites.hero(hero.heroClass),
                        Modifier
                            .size(heroSize)
                            .offset(x = (step + if (i % 2 == 0) 0f else 16f).dp),
                        grayscale = hero.isKo,
                    )
                }
            }
            victory?.let {
                RpgWindow(Modifier.align(Alignment.Center), padding = 12.dp) {
                    Text(it, style = MaterialTheme.typography.titleLarge, color = RpgColors.Gold)
                }
            }
        }

        // Monster status
        RpgWindow(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (monster.isBoss) "BOSS · " else "") + monster.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (monster.isBoss) RpgColors.HpCrit else RpgColors.Text,
                )
                Text("${monster.hp}/${monster.maxHp}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            StatBar(monster.hp, monster.maxHp, RpgColors.HpCrit, Modifier.fillMaxWidth(), height = 8.dp)
            Text(
                "Strikes for ${monster.attack} per missed daily",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = RpgColors.Dim,
            )
        }

        // Command window
        RpgWindow(Modifier.fillMaxWidth(), padding = 6.dp) {
            Text(
                if (ready) "Who attacks?" else "No turns left. Complete quests to earn more!",
                Modifier.padding(start = 6.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = RpgColors.Dim,
            )
            state.heroes.forEach { hero ->
                CommandRow(hero, enabled = dying == null && hero.charges > 0 && !hero.isKo) { vm.attack(hero.id) }
            }
        }

        // Battle log
        RpgWindow(Modifier.fillMaxWidth(), padding = 8.dp) {
            state.log.takeLast(3).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
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
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (enabled) "▶" else " ",
            color = RpgColors.Gold,
            style = MaterialTheme.typography.bodySmall,
        )
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
