package com.todorpg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.Equipment
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.Hero
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ChargePips
import com.todorpg.app.ui.components.HeroFigure
import com.todorpg.app.ui.components.Pose
import com.todorpg.app.ui.components.TappableHero
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgDialog
import com.todorpg.app.ui.components.RpgTextField
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.SectionTitle
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.StatBar
import com.todorpg.app.ui.components.hpColor
import com.todorpg.app.ui.theme.RpgColors

@Composable
fun PartyScreen(state: GameState, vm: GameViewModel) {
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var recruiting by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.heroes, key = { it.id }) { hero ->
            HeroCard(hero, vm, Modifier.clickable { vm.click(); selectedId = hero.id })
        }
        if (state.heroes.size < Rules.MAX_PARTY) {
            item {
                RpgWindow(Modifier.fillMaxWidth().clickable { vm.click(); recruiting = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+", style = MaterialTheme.typography.titleLarge, color = RpgColors.Gold)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Recruit a hero", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Adds a new category (${state.heroes.size}/${Rules.MAX_PARTY})",
                                style = MaterialTheme.typography.labelSmall,
                                color = RpgColors.Dim,
                            )
                        }
                    }
                }
            }
        }
        item {
            RpgWindow(Modifier.fillMaxWidth()) {
                SectionTitle("RECORDS")
                Text("Quests completed: ${state.tasksCompleted}", style = MaterialTheme.typography.bodySmall)
                Text("Monsters defeated: ${state.monstersDefeated}", style = MaterialTheme.typography.bodySmall)
                Text("Raid bosses defeated: ${state.raidsDefeated}", style = MaterialTheme.typography.bodySmall)
                Text("Deepest floor: ${state.floor}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    selectedId?.let { id ->
        val hero = state.hero(id)
        if (hero == null) selectedId = null else HeroDialog(hero, state, vm, onDismiss = { selectedId = null })
    }
    if (recruiting) {
        RecruitDialog(vm, onDismiss = { recruiting = false })
    }
}

@Composable
private fun HeroCard(hero: Hero, vm: GameViewModel, modifier: Modifier = Modifier) {
    RpgWindow(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TappableHero(hero, Modifier.size(64.dp), onTap = vm::click)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hero.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("Lv ${hero.level}", style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
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
                LabeledBar("MP", "${hero.mp}/${hero.maxMp}") {
                    StatBar(hero.mp, hero.maxMp, Color(0xFFB070F0), it)
                }
                LabeledBar("EXP", "${hero.exp}/${hero.expToNext}") {
                    StatBar(hero.exp, hero.expToNext, RpgColors.Exp, it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ATK ${hero.attack}  DEF ${hero.defense}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    GearSlot.entries.forEach { slot ->
                        val g = hero.gear[slot]
                        if (g != null) PixelSprite(Sprites.gear(slot, g.rarity), Modifier.size(14.dp).padding(end = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
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
fun GearIcon(gear: Equipment, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF080C40))
            .border(1.dp, Color(gear.rarity.color), RoundedCornerShape(3.dp))
            .padding(4.dp)
    ) {
        PixelSprite(Sprites.gear(gear.slot, gear.rarity), Modifier.fillMaxSize())
    }
}

@Composable
private fun HeroDialog(hero: Hero, state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    var name by remember(hero.id) { mutableStateOf(hero.name) }
    var category by remember(hero.id) { mutableStateOf(hero.category) }
    var pickingSlot by remember { mutableStateOf<GearSlot?>(null) }

    RpgDialog(hero.name, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroFigure(hero, Modifier.size(56.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Lv ${hero.level} ${hero.heroClass.title}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "HP ${hero.hp}/${hero.maxHp}  MP ${hero.mp}/${hero.maxMp}\nATK ${hero.attack}  DEF ${hero.defense}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${hero.heroClass.attackName} (${hero.heroClass.element.label}): ${hero.heroClass.blurb}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RpgColors.Dim,
                )
                Text(
                    "Skill ${hero.heroClass.skill} (${hero.heroClass.skillCost} MP): ${hero.heroClass.skillBlurb}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RpgColors.Exp,
                )
            }
        }

        Label("Equipment")
        GearSlot.entries.forEach { slot ->
            val g = hero.gear[slot]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { vm.click(); pickingSlot = slot }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(slot.label, Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium, color = RpgColors.Dim)
                if (g != null) {
                    GearIcon(g)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.bodySmall, color = Color(g.rarity.color))
                        Text(g.statLine, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text("— empty —", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = RpgColors.Disabled)
                }
                Text("›", style = MaterialTheme.typography.titleMedium, color = RpgColors.Gold)
            }
        }

        Label("Use item")
        Item.entries.forEach { item ->
            MenuButton("${item.label} x${state.itemCount(item)}", enabled = state.itemCount(item) > 0) {
                vm.useItem(item, hero.id)
            }
        }

        Label("Name")
        RpgTextField(name, { name = it }, placeholder = "Hero name")
        Label("Category")
        RpgTextField(category, { category = it }, placeholder = "e.g. Work")

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.heroes.size > 1) {
                RpgButton("Dismiss") {
                    vm.dismiss(hero.id)
                    if (state.tasks.none { it.heroId == hero.id }) onDismiss()
                }
            }
            Spacer(Modifier.weight(1f))
            RpgButton("Close", onClick = onDismiss)
            RpgButton("Save", enabled = name.isNotBlank() && category.isNotBlank()) {
                vm.editHero(hero.id, name, category)
                onDismiss()
            }
        }
    }

    pickingSlot?.let { slot ->
        GearPicker(hero, slot, state, vm, onDismiss = { pickingSlot = null })
    }
}

@Composable
private fun GearPicker(hero: Hero, slot: GearSlot, state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    val current = hero.gear[slot]
    val options = state.inventory.filter { it.slot == slot }
        .sortedByDescending { it.atk * 2 + it.def * 2 + it.hp / 2 }
    RpgDialog("${hero.name}: ${slot.label}", onDismiss) {
        if (current != null) {
            Text("Equipped: ${current.name} (${current.statLine})", style = MaterialTheme.typography.bodySmall, color = Color(current.rarity.color))
            MenuButton("Unequip") {
                vm.unequip(hero.id, slot)
                onDismiss()
            }
        }
        if (options.isEmpty()) {
            Text(
                "No ${slot.label.lowercase()}s in your bag. Defeat monsters to find gear!",
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = RpgColors.Dim,
            )
        }
        options.forEach { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        vm.equip(hero.id, g.id)
                        onDismiss()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GearIcon(g)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.bodySmall, color = Color(g.rarity.color))
                    Text(diff(current, g), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            RpgButton("Back", onClick = onDismiss)
        }
    }
}

/** e.g. "ATK+5 (+2)  HP+10 (-4)" comparing against what's equipped. */
private fun diff(current: Equipment?, g: Equipment): String {
    fun part(label: String, new: Int, old: Int): String? {
        if (new == 0 && old == 0) return null
        val d = new - old
        val sign = if (d >= 0) "+$d" else "$d"
        return "$label $new ($sign)"
    }
    return listOfNotNull(
        part("ATK", g.atk, current?.atk ?: 0),
        part("DEF", g.def, current?.def ?: 0),
        part("HP", g.hp, current?.hp ?: 0),
    ).joinToString("  ")
}

@Composable
private fun RecruitDialog(vm: GameViewModel, onDismiss: () -> Unit) {
    var heroClass by remember { mutableStateOf(HeroClass.BARD) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    RpgDialog("Recruit", onDismiss) {
        Label("Class")
        HeroClass.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c ->
                    val selected = c == heroClass
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(2.dp, if (selected) RpgColors.Gold else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { vm.click(); heroClass = c }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixelSprite(
                            Sprites.hero(c, if (selected) Pose.VICTORY else Pose.IDLE),
                            Modifier.size(44.dp),
                            animate = selected,
                            frameMs = 160,
                        )
                        Text(c.title, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(
            "${heroClass.attackName} (${heroClass.element.label}): ${heroClass.blurb}\n" +
                "Skill ${heroClass.skill}: ${heroClass.skillBlurb}",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = RpgColors.Gold,
        )
        Label("Name")
        RpgTextField(name, { name = it }, placeholder = "e.g. Lyra")
        Label("Category")
        RpgTextField(category, { category = it }, placeholder = "e.g. Creative, Money, Pets")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RpgButton("Cancel", onClick = onDismiss)
            RpgButton("Recruit", enabled = name.isNotBlank() && category.isNotBlank()) {
                vm.recruit(heroClass, name, category)
                onDismiss()
            }
        }
    }
}
