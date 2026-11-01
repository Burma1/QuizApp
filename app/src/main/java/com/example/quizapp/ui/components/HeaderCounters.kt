package com.example.quizapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusLearningDark
import com.example.ui.theme.StatusMasteredDark

@Composable
fun HeaderCounters(
    stillLearningCount: Int,
    knowCount: Int,
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left pill: Still learning / Review count
        Surface(
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 8.dp, bottomStart = 8.dp),
            color = StatusLearningDark.copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, StatusLearningDark),
            modifier = Modifier.testTag("counter_still_learning")
        ) {
            Text(
                text = stillLearningCount.toString(),
                color = StatusLearningDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }

        // Center progress label
        Text(
            text = "${currentIndex + 1} / $totalCount",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("counter_progress_text")
        )

        // Right pill: Known / Mastered count
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            color = StatusMasteredDark.copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, StatusMasteredDark),
            modifier = Modifier.testTag("counter_know")
        ) {
            Text(
                text = knowCount.toString(),
                color = StatusMasteredDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}
