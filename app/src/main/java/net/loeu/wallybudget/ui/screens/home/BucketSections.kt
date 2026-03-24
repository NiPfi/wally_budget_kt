package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.util.CurrencyFormatter

@Composable
internal fun ActiveBucketsSection(
    bucketSummaries: List<BucketSummaryState>,
    enabled: Boolean = true,
    onEditBucket: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Active buckets")
        bucketSummaries.forEachIndexed { index, summary ->
            if (index > 0) {
                HorizontalDivider()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bucket_row_${summary.bucket.bucketUuid}")
                    .clickable(enabled = enabled) { onEditBucket(summary.bucket.bucketUuid) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.bucket.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
