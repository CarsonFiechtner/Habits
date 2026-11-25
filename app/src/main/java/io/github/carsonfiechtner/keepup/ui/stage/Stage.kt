package io.github.carsonfiechtner.keepup.ui.stage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
import io.github.carsonfiechtner.keepup.domain.period.Period

@get:DrawableRes
val StreakStage.drawable: Int
    get() = when (this) {
        StreakStage.SEED -> R.drawable.stage_1
        StreakStage.SPROUT -> R.drawable.stage_2
        StreakStage.SAPLING -> R.drawable.stage_3
        StreakStage.OAK -> R.drawable.stage_4
        StreakStage.SILVERBOUGH -> R.drawable.stage_5
        StreakStage.WORLD_TREE -> R.drawable.stage_6
    }

fun ColorScheme.isDark(): Boolean = background.luminance() < 0.5f

/** Habit colors follow the streak stage: sage, mint, leaf, forest, silver, gold. */
@Composable
fun stageColor(stage: StreakStage): Color {
    val dark = MaterialTheme.colorScheme.isDark()
    return when (stage) {
        StreakStage.SEED -> if (dark) Color(0xFFA9C5AF) else Color(0xFF6E8B74)
        StreakStage.SPROUT -> if (dark) Color(0xFF9BE3B1) else Color(0xFF2F9E5B)
        StreakStage.SAPLING -> if (dark) Color(0xFF93D96F) else Color(0xFF3F8F2C)
        StreakStage.OAK -> if (dark) Color(0xFF6FBF73) else Color(0xFF1B5E20)
        StreakStage.SILVERBOUGH -> if (dark) Color(0xFFD3DCEB) else Color(0xFF6C7A94)
        StreakStage.WORLD_TREE -> if (dark) Color(0xFFFFCE52) else Color(0xFFA87508)
    }
}

/** "23", "3w", "2mo". */
fun streakLabel(streak: Int, period: Period): String = when (period) {
    Period.DAILY -> "%,d".format(streak)
    Period.WEEKLY -> "${streak}w"
    Period.MONTHLY -> "${streak}mo"
}

fun streakDescription(streak: Int, period: Period): String {
    val unit = when (period) {
        Period.DAILY -> if (streak == 1) "day" else "days"
        Period.WEEKLY -> if (streak == 1) "week" else "weeks"
        Period.MONTHLY -> if (streak == 1) "month" else "months"
    }
    return "$streak $unit"
}

@Composable
fun StageArt(
    stage: StreakStage,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentDescription: String? = stage.displayName,
) {
    Image(
        painter = painterResource(stage.drawable),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
    )
}

/**
 * Streak badge: stage art plus the count. Silverbough shows the number in glowing white on night
 * blue, World Tree in glowing gold on dark amber.
 */
@Composable
fun StreakBadge(
    stage: StreakStage,
    streak: Int,
    period: Period,
    modifier: Modifier = Modifier,
    artSize: Dp = 24.dp,
) {
    val (bg, border, fg, glow) = when (stage) {
        StreakStage.SILVERBOUGH -> BadgeStyle(Color(0xFF1C2740), Color(0xFF56637C), Color.White, Color(0xD9DCE8FF))
        StreakStage.WORLD_TREE -> BadgeStyle(Color(0xFF2B1B08), Color(0xFFC98F2A), Color(0xFFFFCE52), Color(0xCCFFC440))
        else -> BadgeStyle(MaterialTheme.colorScheme.secondaryContainer, Color.Transparent, MaterialTheme.colorScheme.onSecondaryContainer, null)
    }
    Row(
        modifier = modifier
            .semantics { contentDescription = "${stage.displayName} streak, ${streakDescription(streak, period)}" }
            .background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(start = 3.dp, top = 3.dp, bottom = 3.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StageArt(stage, Modifier.size(artSize), RoundedCornerShape(8.dp), contentDescription = null)
        Text(
            streakLabel(streak, period),
            color = fg,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                shadow = glow?.let { Shadow(it, blurRadius = 12f) },
            ),
        )
    }
}

private data class BadgeStyle(val bg: Color, val border: Color, val fg: Color, val glow: Color?)
