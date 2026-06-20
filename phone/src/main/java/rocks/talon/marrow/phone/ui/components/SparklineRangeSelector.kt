package rocks.talon.marrow.phone.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import rocks.talon.marrow.shared.SparklineRange

/**
 * Three-segment button that lets the user switch between 1m / 5m / 15m
 * sparkline time windows. Backed by [rocks.talon.marrow.phone.MarrowViewModel.sparklineRange].
 *
 * @param selected  Currently active range.
 * @param onRangeSelected  Callback to invoke when the user taps a different segment.
 *                         Typically calls [rocks.talon.marrow.phone.MarrowViewModel.setSparklineRange].
 */
@Composable
fun SparklineRangeSelector(
    selected: SparklineRange,
    onRangeSelected: (SparklineRange) -> Unit,
) {
    val ranges = SparklineRange.entries
    SingleChoiceSegmentedButtonRow {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                onClick = { onRangeSelected(range) },
                selected = selected == range,
                label = {
                    Text(
                        text = range.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
