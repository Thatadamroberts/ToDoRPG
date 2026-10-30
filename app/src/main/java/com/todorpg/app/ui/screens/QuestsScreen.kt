package com.todorpg.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.todorpg.app.data.Difficulty
import com.todorpg.app.data.GameState
import com.todorpg.app.data.Hero
import com.todorpg.app.data.Rules
import com.todorpg.app.data.Task
import com.todorpg.app.data.TaskType
import com.todorpg.app.ui.GameViewModel
import com.todorpg.app.ui.components.CheerOnIncrease
import com.todorpg.app.ui.components.HeroFigure
import com.todorpg.app.ui.components.MenuButton
import com.todorpg.app.ui.components.Pose
import com.todorpg.app.ui.components.rememberCheer
import com.todorpg.app.ui.components.PixelSprite
import com.todorpg.app.ui.components.RpgButton
import com.todorpg.app.ui.components.RpgDialog
import com.todorpg.app.ui.components.RpgTextField
import com.todorpg.app.ui.components.RpgWindow
import com.todorpg.app.ui.components.Sprites
import com.todorpg.app.ui.components.sparkleBurst
import com.todorpg.app.ui.theme.RpgColors
import kotlinx.coroutines.launch

@Composable
fun QuestsScreen(state: GameState, vm: GameViewModel) {
    val today = vm.currentDay()
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.heroes, key = { it.id }) { hero ->
                val tasks = state.tasks
                    .filter { it.heroId == hero.id }
                    .sortedWith(compareBy<Task>({ it.isDone(today) }, { it.type != TaskType.DAILY }, { it.id }))
                CategoryWindow(
                    hero = hero,
                    tasks = tasks,
                    today = today,
                    onComplete = vm::completeTask,
                    onEdit = { editing = it },
                    onAdd = { adding = hero.id },
                )
            }
            item {
                Text(
                    "Tap a quest to complete it. Long-press to edit.\n" +
                        "Dailies: EXP + gold. One-off quests: bonus EXP.\nEvery quest gives a battle turn.",
                    Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = RpgColors.Dim,
                )
            }
        }
    }

    adding?.let { heroId ->
        TaskDialog(
            title = "New Quest",
            heroes = state.heroes,
            initial = null,
            initialHeroId = heroId,
            onDismiss = { adding = null },
            onSave = { title, hId, type, diff ->
                vm.addTask(title, hId, type, diff)
                adding = null
            },
        )
    }
    editing?.let { task ->
        TaskDialog(
            title = "Edit Quest",
            heroes = state.heroes,
            initial = task,
            initialHeroId = task.heroId,
            onDismiss = { editing = null },
            onSave = { title, hId, _, diff ->
                vm.editTask(task.id, title, hId, diff)
                editing = null
            },
            onDelete = {
                vm.deleteTask(task.id)
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryWindow(
    hero: Hero,
    tasks: List<Task>,
    today: Long,
    onComplete: (Long) -> Unit,
    onEdit: (Task) -> Unit,
    onAdd: () -> Unit,
) {
    RpgWindow(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The hero hops for joy when one of their quests is completed.
            val cheer = rememberCheer()
            CheerOnIncrease(cheer, tasks.count { it.isDone(today) })
            with(cheer) {
                HeroFigure(
                    hero,
                    Modifier.size(36.dp).cheer(),
                    pose = if (cheer.progress.value < 1f) Pose.VICTORY else null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(hero.category.uppercase(), style = MaterialTheme.typography.titleSmall, color = RpgColors.Gold)
                Text(
                    "${hero.name} the ${hero.heroClass.title}  Lv${hero.level}",
                    style = MaterialTheme.typography.labelMedium,
                    color = RpgColors.Dim,
                )
            }
            RpgButton("+", onClick = onAdd)
        }
        if (tasks.isEmpty()) {
            Text(
                "No quests yet.",
                Modifier.padding(top = 6.dp, start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = RpgColors.Dim,
            )
        }
        tasks.forEach { task ->
            TaskRow(task, today, onComplete = { onComplete(task.id) }, onLongPress = { onEdit(task) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: Task, today: Long, onComplete: () -> Unit, onLongPress: () -> Unit) {
    val done = task.isDone(today)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { if (!done) onComplete() }, onLongClick = onLongPress)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pop + sparkle when a quest is completed while on screen.
        val pop = remember { Animatable(1f) }
        val burst = remember { Animatable(0f) }
        var wasDone by remember { mutableStateOf(done) }
        LaunchedEffect(done) {
            if (done && !wasDone) {
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(600)) }
                pop.snapTo(1.6f)
                pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
            }
            wasDone = done
        }
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(36.dp)) { sparkleBurst(burst.value, count = 10) }
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .border(2.dp, if (done) RpgColors.Gold else RpgColors.Border)
                    .background(if (done) RpgColors.WindowTop else RpgColors.WindowBottom),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Text("✓", style = MaterialTheme.typography.labelMedium, color = RpgColors.Gold)
            }
        }
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) RpgColors.Dim else RpgColors.Text,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val tag = when (task.type) {
                TaskType.DAILY -> "DAILY" + if (task.streak > 1) "  streak ${task.streak}" else ""
                TaskType.ONE_OFF -> "QUEST"
            }
            Text(
                "$tag  ${"★".repeat(task.difficulty.mult)}  ${rewardHint(task)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (task.type == TaskType.DAILY) RpgColors.Exp else RpgColors.Charge,
            )
        }
    }
}

private fun rewardHint(task: Task): String = when (task.type) {
    TaskType.DAILY -> "${Rules.dailyExp(task.difficulty, task.streak + 1)}xp ${Rules.dailyGold(task.difficulty, task.streak + 1)}G"
    TaskType.ONE_OFF -> "${Rules.oneOffExp(task.difficulty)}xp"
}

@Composable
private fun TaskDialog(
    title: String,
    heroes: List<Hero>,
    initial: Task?,
    initialHeroId: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int, TaskType, Difficulty) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial?.title ?: "") }
    var heroId by remember { mutableStateOf(initialHeroId) }
    var type by remember { mutableStateOf(initial?.type ?: TaskType.DAILY) }
    var difficulty by remember { mutableStateOf(initial?.difficulty ?: Difficulty.NORMAL) }

    RpgDialog(title, onDismiss) {
        RpgTextField(text, { text = it }, placeholder = "e.g. Go for a run")
        Spacer(Modifier.height(10.dp))

        Label("Category")
        heroes.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { h ->
                    MenuButton(h.category, Modifier.weight(1f), selected = heroId == h.id) { heroId = h.id }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (initial == null) {
            Label("Type")
            Row {
                MenuButton("Daily", Modifier.weight(1f), selected = type == TaskType.DAILY) { type = TaskType.DAILY }
                MenuButton("One-off", Modifier.weight(1f), selected = type == TaskType.ONE_OFF) { type = TaskType.ONE_OFF }
            }
        }

        Label("Difficulty")
        Row {
            Difficulty.entries.forEach { d ->
                MenuButton(d.label, Modifier.weight(1f), selected = difficulty == d) { difficulty = d }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) RpgButton("Delete", onClick = onDelete)
            Spacer(Modifier.weight(1f))
            RpgButton("Cancel", onClick = onDismiss)
            RpgButton("Save", enabled = text.isNotBlank()) { onSave(text, heroId, type, difficulty) }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(
        text,
        Modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = RpgColors.Dim,
    )
}
