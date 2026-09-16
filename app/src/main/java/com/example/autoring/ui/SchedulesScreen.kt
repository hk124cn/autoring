package com.example.autoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autoring.data.Profile
import com.example.autoring.data.Schedule
import com.example.autoring.model.DaysOfWeek

private fun fmt(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

@Composable
fun SchedulesScreen(
    schedules: List<Schedule>,
    profiles: List<Profile>,
    onAdd: (Schedule) -> Unit,
    onUpdate: (Schedule) -> Unit,
    onDelete: (Schedule) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (schedules.isEmpty()) {
            EmptyState(Icons.Filled.Schedule, "还没有时间段，点击下方按钮把模式排进一天里")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(schedules) { s ->
                    ScheduleCard(
                        s, profiles,
                        onEdit = { editing = s },
                        onDelete = { onDelete(s) },
                        onToggle = { onUpdate(s.copy(enabled = it)) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        FilledTonalButton(
            onClick = { if (profiles.isNotEmpty()) showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = profiles.isNotEmpty()
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(if (profiles.isEmpty()) "请先在「模式」页新增模式" else "新增时间段")
        }
    }

    if (showDialog && profiles.isNotEmpty()) {
        ScheduleDialog(
            initial = null,
            profiles = profiles,
            onDismiss = { showDialog = false },
            onConfirm = { onAdd(it); showDialog = false }
        )
    }
    editing?.let { s ->
        ScheduleDialog(
            initial = s,
            profiles = profiles,
            onDismiss = { editing = null },
            onConfirm = { onUpdate(it.copy(id = s.id)); editing = null }
        )
    }
}

@Composable
private fun ScheduleCard(
    s: Schedule,
    profiles: List<Profile>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val name = profiles.firstOrNull { it.id == s.profileId }?.name ?: "（模式已删除）"
    val crossMidnight = s.startMinute > s.endMinute
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (s.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$name   ${fmt(s.startMinute)} – ${fmt(s.endMinute)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = s.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChipText("周" + DaysOfWeek.labels(s.daysOfWeek).joinToString("") { it })
                if (s.daysOfWeek == 0) AssistChipText("未选日期")
                if (crossMidnight) AssistChipText("跨午夜")
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    label: String,
    hour: Int,
    minute: Int,
    onConfirm: (Int, Int) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label    %02d:%02d".format(hour, minute))
    }
    if (show) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialogWrapped(
            title = label,
            onDismiss = { show = false },
            confirmText = "确定",
            onConfirm = { onConfirm(state.hour, state.minute); show = false }
        ) {
            TimePicker(state = state)
        }
    }
}

@Composable
private fun ScheduleDialog(
    initial: Schedule?,
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onConfirm: (Schedule) -> Unit
) {
    var profileId by remember { mutableStateOf(initial?.profileId ?: profiles.first().id) }
    var startH by remember { mutableStateOf((initial?.startMinute ?: 1320) / 60) } // 默认 22:00
    var startM by remember { mutableStateOf((initial?.startMinute ?: 1320) % 60) }
    var endH by remember { mutableStateOf((initial?.endMinute ?: 420) / 60) } // 默认 07:00
    var endM by remember { mutableStateOf((initial?.endMinute ?: 420) % 60) }
    var days by remember { mutableStateOf(initial?.daysOfWeek ?: DaysOfWeek.WEEKDAYS) }

    val crossMidnight = startH * 60 + startM > endH * 60 + endM

    AlertDialogWrapped(
        title = if (initial == null) "新增时间段" else "编辑时间段",
        onDismiss = onDismiss,
        confirmText = if (initial == null) "新增" else "保存",
        onConfirm = {
            onConfirm(
                Schedule(
                    id = initial?.id ?: 0,
                    profileId = profileId,
                    startMinute = startH * 60 + startM,
                    endMinute = endH * 60 + endM,
                    daysOfWeek = days,
                    enabled = initial?.enabled ?: true
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("关联模式")
            profiles.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = profileId == p.id, onClick = { profileId = p.id })
                    Text(p.name)
                }
            }
            Spacer(Modifier.height(4.dp))
            TimePickerField("开始时间", startH, startM) { h, m -> startH = h; startM = m }
            TimePickerField("结束时间", endH, endM) { h, m -> endH = h; endM = m }
            if (crossMidnight) {
                Text(
                    "（跨午夜：到次日 %02d:%02d 结束）".format(endH, endM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(4.dp))
            SectionTitle("重复")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    DaysOfWeek.MON to "一", DaysOfWeek.TUE to "二", DaysOfWeek.WED to "三",
                    DaysOfWeek.THU to "四", DaysOfWeek.FRI to "五", DaysOfWeek.SAT to "六",
                    DaysOfWeek.SUN to "日"
                ).forEach { (bit, label) ->
                    FilterChipSelected(
                        selected = (days and bit) != 0,
                        onClick = {
                            days = if ((days and bit) != 0) days and bit.inv() else days or bit
                        },
                        label = label
                    )
                }
            }
            Row {
                TextButton(onClick = { days = DaysOfWeek.WEEKDAYS }) { Text("工作日") }
                TextButton(onClick = { days = DaysOfWeek.WEEKEND }) { Text("周末") }
                TextButton(onClick = { days = DaysOfWeek.ALL }) { Text("每天") }
                TextButton(onClick = { days = 0 }) { Text("清空") }
            }
        }
    }
}
