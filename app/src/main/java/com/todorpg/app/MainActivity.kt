package com.todorpg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todorpg.app.ui.Banner
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.screens.BattleScreen
import com.todorpg.app.ui.screens.PartyScreen
import com.todorpg.app.ui.screens.QuestsScreen
import com.todorpg.app.ui.screens.ShopScreen
import com.todorpg.app.ui.theme.RpgColors
import com.todorpg.app.ui.theme.ToDoRpgTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToDoRpgTheme {
                App(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

private enum class Tab(val label: String) { QUESTS("Quests"), PARTY("Party"), BATTLE("Battle"), SHOP("Shop") }

@Composable
private fun App(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.QUESTS) }

    Box(
        Modifier
            .fillMaxSize()
            .background(RpgColors.Night)
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            RpgWindow(Modifier.fillMaxWidth(), padding = 8.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Floor ${state.floor}", style = MaterialTheme.typography.bodyMedium)
                    Text("${state.gold} G", style = MaterialTheme.typography.bodyMedium, color = RpgColors.Gold)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                when (tab) {
                    Tab.QUESTS -> QuestsScreen(state, vm)
                    Tab.PARTY -> PartyScreen(state, vm)
                    Tab.BATTLE -> BattleScreen(state, vm)
                    Tab.SHOP -> ShopScreen(state, vm)
                }
            }
            RpgWindow(Modifier.fillMaxWidth(), padding = 2.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tab.entries.forEach { t ->
                        val badge = if (t == Tab.BATTLE && state.heroes.any { it.charges > 0 && !it.isKo }) "!" else ""
                        MenuButton(t.label + badge, selected = tab == t) { tab = t }
                    }
                }
            }
        }

        BannerHost(banner, onUndo = vm::undo, onDismiss = vm::dismissBanner)
    }
}

@Composable
private fun BannerHost(banner: Banner?, onUndo: (Banner) -> Unit, onDismiss: (Long) -> Unit) {
    // Keep the last banner around while it animates out.
    var shown by remember { mutableStateOf(banner) }
    if (banner != null) shown = banner
    LaunchedEffect(banner?.id) {
        val b = banner ?: return@LaunchedEffect
        delay(if (b.undo != null) 4000 else 2500)
        onDismiss(b.id)
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 64.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            val b = shown ?: return@AnimatedVisibility
            RpgWindow(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (b.undo != null) {
                        MenuButton("Undo", selected = true) { onUndo(b) }
                    }
                }
            }
        }
    }
}
