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
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Item
import com.todorpg.app.data.Rules
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.theme.RpgColors

@Composable
fun ShopScreen(state: GameState, vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpgWindow(Modifier.fillMaxWidth()) {
            Text("ITEMS", style = MaterialTheme.typography.titleMedium, color = RpgColors.Gold)
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
            Text("BLACKSMITH", style = MaterialTheme.typography.titleMedium, color = RpgColors.Gold)
            Text("Each upgrade adds +4 ATK.", style = MaterialTheme.typography.labelSmall, color = RpgColors.Dim)
            state.heroes.forEach { hero ->
                val cost = Rules.weaponCost(hero.weapon)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.hero(hero.heroClass), Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hero.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Weapon +${hero.weapon} → +${hero.weapon + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RpgColors.Dim,
                        )
                    }
                    RpgButton("$cost G", enabled = state.gold >= cost) { vm.upgradeWeapon(hero.id) }
                }
            }
        }
    }
}
