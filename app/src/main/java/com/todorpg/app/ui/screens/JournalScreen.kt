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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.Achievement
import com.todorpg.app.data.GameState
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Rules
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.SectionTitle
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.theme.RpgColors
import java.time.LocalDate

/** Trophies and the monster bestiary. */
@Composable
fun JournalScreen(state: GameState, vm: GameViewModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpgWindow(Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MenuButton("Trophies ${state.achievements.size}/${Achievement.entries.size}", selected = page == 0) {
                    vm.click()
                    page = 0
                }
                val seen = MonsterKind.entries.count { (state.defeatedKinds[it] ?: 0) > 0 }
                MenuButton("Bestiary $seen/${MonsterKind.entries.size}", selected = page == 1) {
                    vm.click()
                    page = 1
                }
            }
        }
        if (page == 0) Trophies(state) else Bestiary(state)
    }
}

@Composable
private fun Trophies(state: GameState) {
    val sorted = Achievement.entries.sortedBy { if (it in state.achievements) 0 else 1 }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            RpgWindow(Modifier.fillMaxWidth()) {
                SectionTitle("RECORDS")
                Text(
                    "Quests ${state.tasksCompleted} · Best streak ${state.bestStreak} · Monsters ${state.monstersDefeated}\n" +
                        "Raids ${state.raidsDefeated} · Breaks ${state.breaks} · Gold earned ${state.totalGold}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        items(sorted, key = { it.name }) { a ->
            val day = state.achievements[a]
            val unlocked = day != null
            RpgWindow(Modifier.fillMaxWidth(), padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.trophy, Modifier.size(36.dp), animate = false, grayscale = !unlocked)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unlocked) RpgColors.Gold else RpgColors.Disabled,
                        )
                        Text(
                            a.description + (day?.let { " · " + LocalDate.ofEpochDay(it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unlocked) RpgColors.Text else RpgColors.Dim,
                        )
                    }
                    Text("${a.reward} G", style = MaterialTheme.typography.labelMedium, color = if (unlocked) RpgColors.Gold else RpgColors.Disabled)
                }
            }
        }
    }
}

@Composable
private fun Bestiary(state: GameState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(MonsterKind.entries, key = { it.name }) { kind ->
            val count = state.defeatedKinds[kind] ?: 0
            val known = count > 0 || state.knownWeak.containsKey(kind)
            val weakKnown = state.knownWeak[kind].orEmpty()
            val resistKnown = state.knownResist[kind].orEmpty()
            RpgWindow(Modifier.fillMaxWidth(), padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(
                        Sprites.monster(kind, 0),
                        Modifier.size(56.dp),
                        animate = known,
                        grayscale = !known,
                        tint = { if (known) null else Color(0xFF000010) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (known) kind.baseName else "???",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (known) RpgColors.Gold else RpgColors.Disabled,
                        )
                        Text(
                            when (kind) {
                                MonsterKind.DRAGON -> "Boss of every fifth floor."
                                MonsterKind.GOLEM, MonsterKind.DEMON -> "Weekly raid boss."
                                else -> "Roams the dungeon floors."
                            } + "  Defeated: $count",
                            style = MaterialTheme.typography.labelSmall,
                            color = RpgColors.Dim,
                        )
                        Text(
                            "Weak: " + Rules.weaknesses(kind).joinToString(" ") { if (it in weakKnown) it.label else "?" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF80FF90),
                        )
                        Text(
                            "Resists: " + Rules.resistances(kind).joinToString(" ") { if (it in resistKnown) it.label else "?" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9090),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Hit monsters with different elements to fill in their entries.",
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = RpgColors.Dim,
            )
        }
    }
}
