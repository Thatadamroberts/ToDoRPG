package com.todorpg.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.SectionTitle
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.theme.RpgColors

@Composable
fun ShopScreen(state: GameState, vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpgWindow(Modifier.fillMaxWidth()) {
            SectionTitle("ITEMS")
            Item.entries.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.label}  (own ${state.itemCount(item)})", style = MaterialTheme.typography.bodyMedium)
                        Text(item.description, style = MaterialTheme.typography.labelSmall, color = RpgColors.Dim)
                    }
                    RpgButton("${item.price} G", enabled = state.gold >= item.price) { vm.buy(item) }
                }
            }
            Text(
                "Use items from the Party screen by tapping a hero.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = RpgColors.Dim,
            )
        }

        RpgWindow(Modifier.fillMaxWidth()) {
            SectionTitle("DOJO")
            Text("Training adds +4 ATK permanently.", style = MaterialTheme.typography.labelSmall, color = RpgColors.Dim)
            state.heroes.forEach { hero ->
                val cost = Rules.weaponCost(hero.weapon)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.hero(hero.heroClass), Modifier.size(32.dp), phase = hero.id * 170)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hero.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Training +${hero.weapon} → +${hero.weapon + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RpgColors.Dim,
                        )
                    }
                    RpgButton("$cost G", enabled = state.gold >= cost) { vm.upgradeWeapon(hero.id) }
                }
            }
        }

        RpgWindow(Modifier.fillMaxWidth()) {
            SectionTitle("BAG  ${state.inventory.size}/${Rules.MAX_INVENTORY}")
            if (state.inventory.isEmpty()) {
                Text(
                    "Gear you find in battle shows up here. Equip it from the Party screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RpgColors.Dim,
                )
            }
            state.inventory.sortedByDescending { it.rarity.ordinal }.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GearIcon(g)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.bodySmall, color = Color(g.rarity.color))
                        Text("${g.rarity.label} ${g.slot.label} · ${g.statLine}", style = MaterialTheme.typography.labelSmall)
                    }
                    RpgButton("Sell ${g.sellPrice}") { vm.sell(g.id) }
                }
            }
        }
    }
}
