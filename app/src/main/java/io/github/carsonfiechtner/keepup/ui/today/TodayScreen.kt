package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.ui.components.EmptyState
import io.github.carsonfiechtner.keepup.ui.theme.KeepUpTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val dateText = remember {
        LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
    Column(modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.nav_today))
                    Text(
                        dateText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            windowInsets = TopAppBarDefaults.windowInsets,
        )
        EmptyState(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.today_empty_title),
            body = stringResource(R.string.today_empty_body),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    KeepUpTheme { TodayScreen() }
}
