package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.util.CurrencyFormatter

@Composable
internal fun ActiveBucketsSection(
    bucketSummaries: List<BucketSummaryState>,
    enabled: Boolean = true,
    onEditBucket: (String) -> Unit
) {
    val orderedSummaries = bucketSummaries.sortedWith(
        compareBy<BucketSummaryState> { it.bucket.isClosed }
            .thenBy { it.bucket.sortOrder }
            .thenBy { it.bucket.createdAtEpochMs }
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Current cycle buckets")
        orderedSummaries.forEachIndexed { index, summary ->
            if (index > 0) {
                HorizontalDivider()
            }
            val rowEnabled = enabled && summary.bucket.isOpenForEditing
            val rowModifier = if (rowEnabled) {
                Modifier.clickable { onEditBucket(summary.bucket.bucketUuid) }
            } else {
                Modifier
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (summary.bucket.isOpenForEditing) 1f else 0.6f)
                    .testTag("bucket_row_${summary.bucket.bucketUuid}")
                    .then(rowModifier)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BucketRowName(
                    name = summary.bucket.name,
                    isClosed = summary.bucket.isClosed
                )
                Text(
                    text = "${CurrencyFormatter.format(summary.allocatedThisCycleCents)} allocated · " +
                        "${CurrencyFormatter.format(summary.spentThisCycleCents)} spent · " +
                        CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun BucketRowName(
    name: String,
    isClosed: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isClosed) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Closed bucket",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isClosed) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}
