package com.todorpg.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.todorpg.app.MainActivity
import com.todorpg.app.data.GameState
import com.todorpg.app.data.GameStore
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.Task
import com.todorpg.app.data.TaskType
import com.todorpg.app.game.GameEngine
import com.todorpg.app.ui.components.Sprites

private val Gold = ColorProvider(Color(0xFFF8D040))
private val White = ColorProvider(Color(0xFFF8F8F8))
private val Dim = ColorProvider(Color(0xFFA8B0D8))
private val TaskKey = ActionParameters.Key<Long>("taskId")

/** Home screen widget: today's unfinished dailies, tick them off in place. */
class QuestWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        GameStore.init(context)
        provideContent {
            val state by GameStore.state.collectAsState()
            WidgetContent(state)
        }
    }

    companion object {
        suspend fun refresh(context: Context) = QuestWidget().updateAll(context)
    }
}

class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}

/** Completes a quest from the widget, exactly as tapping it in the app would. */
class CompleteQuestAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskKey] ?: return
        GameStore.init(context)
        val today = GameStore.today()
        GameStore.update { GameEngine.completeTask(GameEngine.rollover(it, today), taskId, today).first }
        QuestWidget().update(context, glanceId)
    }
}

@Composable
private fun WidgetContent(state: GameState) {
    val today = GameStore.today()
    val dailies = state.tasks.filter { it.type == TaskType.DAILY }
    val left = dailies.filter { !it.isDone(today) }
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(12.dp)
            .background(ColorProvider(Color(0xFF101870)))
            .padding(10.dp)
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TODAY'S QUESTS",
                style = TextStyle(color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "${dailies.size - left.size}/${dailies.size}",
                style = TextStyle(color = White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            dailies.isEmpty() -> Message("No daily quests yet. Tap to add some!")
            left.isEmpty() -> Message("All done! The party rests easy.")
            else -> LazyColumn(GlanceModifier.defaultWeight()) {
                items(left, itemId = { it.id }) { task -> TaskRow(state, task) }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "${state.gold} G  ·  Floor ${state.floor}",
            GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        GlanceModifier.clickable(actionStartActivity<MainActivity>()),
        style = TextStyle(color = White, fontSize = 13.sp),
    )
}

@Composable
private fun TaskRow(state: GameState, task: Task) {
    val hero = state.hero(task.heroId)
    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionRunCallback<CompleteQuestAction>(actionParametersOf(TaskKey to task.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☐", style = TextStyle(color = Gold, fontSize = 16.sp))
        Spacer(GlanceModifier.width(6.dp))
        if (hero != null) {
            Image(ImageProvider(heroBitmap(hero.heroClass)), hero.name, GlanceModifier.size(22.dp))
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            task.title + if (task.streak > 1) "  🔥${task.streak}" else "",
            style = TextStyle(color = White, fontSize = 14.sp),
            maxLines = 1,
        )
    }
}

private val bitmaps = HashMap<HeroClass, Bitmap>()

/** Renders a hero's pixel sprite into a small bitmap for the widget. */
private fun heroBitmap(c: HeroClass): Bitmap = bitmaps.getOrPut(c) {
    val sheet = Sprites.hero(c)
    val scale = 4
    val bmp = Bitmap.createBitmap(sheet.cols * scale, sheet.rows * scale, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    sheet.frames.first().forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            val color = sheet.palette[ch] ?: return@forEachIndexed
            paint.color = color.toArgb()
            canvas.drawRect(
                (x * scale).toFloat(), (y * scale).toFloat(),
                ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), paint,
            )
        }
    }
    bmp
}
