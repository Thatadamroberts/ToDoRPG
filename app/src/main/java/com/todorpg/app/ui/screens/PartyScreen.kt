package com.todorpg.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.Item
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ChargePips
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgDialog
import com.todorpg.app.ui.components.RpgTextField
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.StatBar
import com.todorpg.app.ui.components.hpColor
import com.todorpg.app.ui.theme.RpgColors

@Composable
fun PartyScreen(state: GameState, vm: GameViewModel) {
    var selected by remember { mutableStateOf<Hero?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.heroes, key = { it.id }) { hero ->
            HeroCard(hero, Modifier.clickable { selected = hero })
        }
        item {
            RpgWindow(Modifier.fillMaxWidth()) {
                Text("RECORDS", style = MaterialTheme.typography.titleMedium, color = RpgColors.Gold)
                Text("Quests completed: ${state.tasksCompleted}", style = MaterialTheme.typography.bodySmall)
                Text("Monsters defeated: ${state.monstersDefeated}", style = MaterialTheme.typography.bodySmall)
                Text("Deepest floor: ${state.floor}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    selected?.let { s ->
        // Always show the latest version of the hero.
        val hero = state.hero(s.id) ?: return@let
        HeroDialog(hero, state, vm, onDismiss = { selected = null })
    }
}

@Composable
private fun HeroCard(hero: Hero, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = if (hero.isKo) 0f else -3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "bob",
    )
    RpgWindow(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(
                Sprites.hero(hero.heroClass),
                Modifier.size(64.dp).offset(y = bob.dp),
                grayscale = hero.isKo,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hero.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("Lv ${hero.level}", style = MaterialTheme.typography.bodyMedium, color = RpgColors.Gold)
                }
                Text(
                    "${hero.heroClass.title} · ${hero.category}" + if (hero.isKo) " · KO" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hero.isKo) RpgColors.HpCrit else RpgColors.Dim,
                )
                Spacer(Modifier.height(4.dp))
                LabeledBar("HP", "${hero.hp}/${hero.maxHp}") {
                    StatBar(hero.hp, hero.maxHp, hpColor(hero.hp, hero.maxHp), it)
                }
                LabeledBar("EXP", "${hero.exp}/${hero.expToNext}") {
                    StatBar(hero.exp, hero.expToNext, RpgColors.Exp, it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ATK ${hero.attack}" + if (hero.weapon > 0) "  WPN +${hero.weapon}" else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ChargePips(hero.charges)
                }
            }
        }
    }
}

@Composable
private fun LabeledBar(label: String, value: String, bar: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall, color = RpgColors.Dim)
        bar(Modifier.weight(1f))
        Text(value, Modifier.width(64.dp).padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HeroDialog(hero: Hero, state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    var name by remember(hero.id) { mutableStateOf(hero.name) }
    var category by remember(hero.id) { mutableStateOf(hero.category) }

    RpgDialog(hero.name, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(Sprites.hero(hero.heroClass), Modifier.size(48.dp), grayscale = hero.isKo)
            Spacer(Modifier.width(8.dp))
            Text(
                "HP ${hero.hp}/${hero.maxHp}\n${hero.heroClass.title}: ${classBlurb(hero)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Label("Name")
        RpgTextField(name, { name = it }, placeholder = "Hero name")
        Label("Category")
        RpgTextField(category, { category = it }, placeholder = "e.g. Work")

        Label("Use item")
        Item.entries.forEach { item ->
            MenuButton("${item.label} x${state.itemCount(item)}", enabled = state.itemCount(item) > 0) {
                vm.useItem(item, hero.id)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RpgButton("Close", onClick = onDismiss)
            RpgButton("Save", enabled = name.isNotBlank() && category.isNotBlank()) {
                vm.editHero(hero.id, name, category)
                onDismiss()
            }
        }
    }
}

private fun classBlurb(hero: Hero): String = when (hero.heroClass) {
    com.todorpg.app.data.HeroClass.KNIGHT -> "steady, strong hits"
    com.todorpg.app.data.HeroClass.MONK -> "fast flurry of blows"
    com.todorpg.app.data.HeroClass.RANGER -> "high critical chance"
    com.todorpg.app.data.HeroClass.MAGE -> "big magic damage"
    com.todorpg.app.data.HeroClass.CLERIC -> "weak hits, heals allies"
}
