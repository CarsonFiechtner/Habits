package io.github.carsonfiechtner.keepup.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.carsonfiechtner.keepup.MainActivity
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import io.github.carsonfiechtner.keepup.ui.theme.DarkColors
import io.github.carsonfiechtner.keepup.ui.theme.LightColors
import io.github.carsonfiechtner.keepup.ui.today.HabitRowState
import io.github.carsonfiechtner.keepup.ui.today.RowAction
import io.github.carsonfiechtner.keepup.ui.today.TodayStateBuilder
import io.github.carsonfiechtner.keepup.ui.today.TodayUiState
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun habits(): HabitRepository
    fun entries(): EntryRepository
    fun actions(): HabitActions
    fun clock(): Clock
}

private fun entryPoint(context: Context) = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/** Home-screen widget: today's habits with one-tap logging (design doc §11.2). */
class KeepUpWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = entryPoint(context)
        val today = LocalDate.now(ep.clock())
        val state = TodayStateBuilder.build(ep.habits().activeHabits.first(), ep.entries().dailyTotals.first(), today, today)
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                WidgetContent(state)
            }
        }
    }

    companion object {
        /** Redraw all KeepUp widgets, e.g. after logging something in the app. */
        suspend fun refresh(context: Context) {
            try {
                KeepUpWidget().updateAll(context)
            } catch (_: Exception) {
                // No widgets placed, or the host is unavailable.
            }
        }
    }
}

class KeepUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KeepUpWidget()
}

@Composable
private fun WidgetContent(state: TodayUiState) {
    val rows = state.daily + state.periodic
    val done = rows.count { it.result.status == PeriodStatus.COMPLETED }
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "KeepUp",
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (rows.isNotEmpty()) {
                Text("$done / ${rows.size} done", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            }
        }
        Spacer(GlanceModifier.padding(top = 6.dp))
        if (rows.isEmpty()) {
            Text(
                "No habits yet. Tap to add one.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            LazyColumn {
                items(rows, itemId = { it.habit.id }) { row -> WidgetRow(row) }
            }
        }
    }
}

@Composable
private fun WidgetRow(row: HabitRowState) {
    val habit = row.habit
    val completed = row.result.status == PeriodStatus.COMPLETED
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(habit.icon, style = TextStyle(fontSize = 18.sp))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(
                habit.name,
                maxLines = 1,
                style = TextStyle(
                    color = if (completed) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                if (habit.unit.isBinary && habit.period == io.github.carsonfiechtner.keepup.domain.period.Period.DAILY) {
                    if (completed) "Done" else "Not done yet"
                } else {
                    HabitText.progress(row.result.value, row.result.goal, habit.unit)
                },
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        val params = actionParametersOf(HabitIdKey to habit.id)
        when (row.action) {
            RowAction.TOGGLE -> Chip(if (row.isDoneOnDate) "✓" else "○", filled = row.isDoneOnDate) {
                actionRunCallback<ToggleAction>(params)
            }
            RowAction.QUICK_ADD -> Chip("+" + ValueFormat.number(habit.unit.quickAddStep, habit.unit.decimals), filled = false) {
                actionRunCallback<QuickAddAction>(params)
            }
            RowAction.LOG_ZERO -> Chip("Log 0", filled = false) { actionRunCallback<LogZeroAction>(params) }
            RowAction.NONE -> if (completed) Text("✓", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp))
        }
    }
}

@Composable
private fun Chip(label: String, filled: Boolean, action: () -> androidx.glance.action.Action) {
    Box(
        GlanceModifier
            .background(if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(action()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                color = if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
    }
}

private val HabitIdKey = ActionParameters.Key<Long>("habit_id")

/** Widget taps go through the same locked-history write path as the app. */
private suspend fun runAndRefresh(context: Context, glanceId: GlanceId, block: suspend (HabitActions) -> Unit) {
    try {
        block(entryPoint(context).actions())
    } catch (_: Exception) {
        // E.g. the habit was archived in the meantime; just redraw.
    }
    KeepUpWidget().update(context, glanceId)
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        runAndRefresh(context, glanceId) { it.toggleDone(id) }
    }
}

class QuickAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        runAndRefresh(context, glanceId) { actions ->
            val habit = entryPoint(context).habits().getHabit(id) ?: return@runAndRefresh
            actions.logValue(id, habit.unit.quickAddStep)
        }
    }
}

class LogZeroAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        runAndRefresh(context, glanceId) { it.logZero(id) }
    }
}
