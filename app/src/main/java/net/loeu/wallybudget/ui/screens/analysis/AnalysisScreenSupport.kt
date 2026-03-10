package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RecommendationRow(
    index: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun verdictContainerColor(
    verdict: AnalysisVerdictLevel?,
    isLoading: Boolean
): Color = when {
    isLoading -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    verdict == AnalysisVerdictLevel.AtRisk -> MaterialTheme.colorScheme.errorContainer
    verdict == AnalysisVerdictLevel.Caution -> MaterialTheme.colorScheme.tertiaryContainer
    verdict == AnalysisVerdictLevel.Watchful -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
internal fun evidenceContainerColor(
    tone: AnalysisEvidenceTone
): Color = when (tone) {
    AnalysisEvidenceTone.Positive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    AnalysisEvidenceTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    AnalysisEvidenceTone.Critical -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    AnalysisEvidenceTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
}
