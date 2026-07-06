package com.example.sleepwisepoc.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalSleepColors.current
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Select wake-up time",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(20.dp))

            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor          = c.surface2,
                    clockDialSelectedContentColor = c.textPrimary,
                    clockDialUnselectedContentColor = c.textSecondary,
                    selectorColor           = c.primary,
                    containerColor          = c.surface,
                    periodSelectorBorderColor = c.border,
                    periodSelectorSelectedContainerColor = c.primary,
                    periodSelectorUnselectedContainerColor = c.surface2,
                    periodSelectorSelectedContentColor = c.textPrimary,
                    periodSelectorUnselectedContentColor = c.textSecondary,
                    timeSelectorSelectedContainerColor = c.primary,
                    timeSelectorUnselectedContainerColor = c.surface2,
                    timeSelectorSelectedContentColor = c.textPrimary,
                    timeSelectorUnselectedContentColor = c.textSecondary,
                ),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = c.textSecondary, fontSize = 14.sp)
                }
                TextButton(
                    onClick = {
                        onConfirm(LocalTime.of(state.hour, state.minute))
                    }
                ) {
                    Text("Confirm", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
