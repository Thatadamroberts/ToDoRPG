package com.todorpg.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todorpg.app.audio.Music
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Settings
import com.todorpg.app.ui.Banner
import com.todorpg.app.ui.Celebration
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.ClockProvider
import com.todorpg.app.ui.components.LocalClock
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgDialog
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.Starfield
import com.todorpg.app.ui.components.sparkleBurst
import com.todorpg.app.ui.screens.BattleScreen
import com.todorpg.app.ui.screens.Label
import com.todorpg.app.ui.screens.PartyScreen
import com.todorpg.app.ui.screens.QuestsScreen
import com.todorpg.app.ui.screens.ShopScreen
import com.todorpg.app.ui.theme.RpgColors
import com.todorpg.app.ui.theme.ToDoRpgTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToDoRpgTheme {
                ClockProvider {
                    App(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        vm.audio.onForeground()
    }

    override fun onPause() {
        super.onPause()
        vm.audio.onBackground()
    }
}

private enum class Tab(val label: String) { QUESTS("Quests"), PARTY("Party"), BATTLE("Battle"), SHOP("Shop") }

@Composable
private fun App(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val celebration by vm.celebration.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.QUESTS) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(tab) {
        vm.setMusic(if (tab == Tab.BATTLE) Music.BATTLE else Music.CAMP)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(RpgColors.Night)
    ) {
        Starfield(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            TopBar(state, onSettings = { vm.click(); showSettings = true })
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(220)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 6 * dir } + fadeOut(tween(180)))
                },
                label = "tabs",
            ) { t ->
                when (t) {
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
                        MenuButton(t.label + badge, selected = tab == t) {
                            if (tab != t) vm.click()
                            tab = t
                        }
                    }
                }
            }
        }

        BannerHost(banner, onUndo = vm::undo, onDismiss = vm::dismissBanner)
        celebration?.let { LevelUpOverlay(it, state, onDone = vm::dismissCelebration) }
    }

    if (showSettings) {
        SettingsDialog(state.settings, onChange = vm::updateSettings, onDismiss = { showSettings = false })
    }
}

@Composable
private fun TopBar(state: GameState, onSettings: () -> Unit) {
    val gold by animateIntAsState(state.gold, tween(600), label = "gold")
    RpgWindow(Modifier.fillMaxWidth(), padding = 6.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FLOOR ${state.floor}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("$gold G", style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
            Spacer(Modifier.width(12.dp))
            Text(
                "⚙",
                Modifier
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 6.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
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
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 72.dp), contentAlignment = Alignment.TopCenter) {
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

/** Full-screen "LEVEL UP!" with the heroes jumping inside a sparkle burst. */
@Composable
private fun LevelUpOverlay(c: Celebration, state: GameState, onDone: () -> Unit) {
    val burst = remember(c.id) { Animatable(0f) }
    val pop = remember(c.id) { Animatable(0.3f) }
    LaunchedEffect(c.id) {
        pop.animateTo(1f, tween(250))
        repeat(2) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(800))
        }
        delay(400)
        onDone()
    }
    val clock = LocalClock.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000010))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(320.dp)) { sparkleBurst(burst.value, count = 16) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LEVEL UP!",
                Modifier.offset { IntOffset(0, (-abs(sin(clock.value / 160.0)) * 12).roundToInt()) },
                style = MaterialTheme.typography.displaySmall,
                color = RpgColors.Gold,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                c.heroIds.mapNotNull { state.hero(it) }.forEachIndexed { i, hero ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelSprite(
                            Sprites.hero(hero.heroClass),
                            Modifier
                                .size(72.dp * pop.value)
                                .offset { IntOffset(0, (-abs(sin(clock.value / 130.0 + i)) * 40).roundToInt()) },
                            frameMs = 150,
                        )
                        Text("${hero.name}  Lv ${hero.level}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(settings: Settings, onChange: (Settings) -> Unit, onDismiss: () -> Unit) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onChange(settings.copy(reminders = true))
    }
    RpgDialog("Settings", onDismiss) {
        Toggle("Music", settings.music) { onChange(settings.copy(music = it)) }
        Toggle("Sound effects", settings.sfx) { onChange(settings.copy(sfx = it)) }
        Toggle("Daily reminder", settings.reminders) { on ->
            if (on && Build.VERSION.SDK_INT >= 33) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onChange(settings.copy(reminders = on))
            }
        }
        if (settings.reminders) {
            Label("Remind me at (if dailies are left)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RpgButton("-") { onChange(settings.shiftTime(-30)) }
                Text(
                    "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                    Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                RpgButton("+") { onChange(settings.shiftTime(30)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Music and pixel art made for ToDo RPG. Fonts: Press Start 2P and Pixelify Sans (SIL OFL).",
            style = MaterialTheme.typography.labelSmall,
            color = RpgColors.Dim,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            RpgButton("Close", onClick = onDismiss)
        }
    }
}

private fun Settings.shiftTime(minutes: Int): Settings {
    val total = Math.floorMod(reminderHour * 60 + reminderMinute + minutes, 24 * 60)
    return copy(reminderHour = total / 60, reminderMinute = total % 60)
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(if (on) "ON" else "OFF", style = MaterialTheme.typography.titleSmall, color = if (on) RpgColors.Gold else RpgColors.Disabled)
    }
}
