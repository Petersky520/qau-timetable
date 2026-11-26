package cn.edu.qau.timetable.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared tonal surfaces keep the same hierarchy with wallpaper colors and dark mode. */
@Composable
internal fun ExpressiveIntro(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium)
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            ExpressiveEmblem(icon)
        }
    }
}

@Composable
internal fun ExpressiveEmblem(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
internal fun ExpressiveEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.AutoStories,
    onSync: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExpressiveEmblem(icon)
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    hint, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (onSync != null) {
                    Button(onClick = onSync, modifier = Modifier.heightIn(min = 52.dp)) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("同步教务数据")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
