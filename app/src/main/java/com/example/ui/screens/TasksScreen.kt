package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FintechCard
import com.example.ui.components.StatusTag
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun TasksScreen(
  tasks: List<TaskItem>,
  onAddTask: (TaskItem) -> Unit,
  onUpdateTask: (TaskItem) -> Unit,
  onToggleTaskDone: (TaskItem) -> Unit,
  onUpdateProgress: (TaskItem, Int) -> Unit,
  onDeleteTask: (TaskItem) -> Unit,
  modifier: Modifier = Modifier
) {
  var filterStatus by remember { mutableStateOf("ALL") } // ALL, PENDING, DONE
  var showAddDialog by remember { mutableStateOf(false) }
  var taskToDelete by remember { mutableStateOf<TaskItem?>(null) }
  var taskToEditProgress by remember { mutableStateOf<TaskItem?>(null) }

  val filteredTasks = tasks.filter {
    when (filterStatus) {
      "PENDING" -> !it.isDone
      "DONE" -> it.isDone
      else -> true
    }
  }

  val totalCount = tasks.size
  val completedCount = tasks.count { it.isDone }
  val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

  Box(modifier = modifier.fillMaxSize().testTag("tasks_screen")) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Productivity Summary Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .padding(18.dp)
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Today's Task Completion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
              Text(
                text = "$completedCount of $totalCount Done",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Text(
                text = "${(progress * 100).toInt()}% completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
            }
            Box(contentAlignment = Alignment.Center) {
              CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(56.dp),
                color = EmeraldPrimary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                strokeWidth = 5.dp
              )
              Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }
      }

      // 2. Filter Chips
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = filterStatus == "ALL",
              onClick = { filterStatus = "ALL" },
              label = { Text("All ($totalCount)") }
            )
          }
          item {
            FilterChip(
              selected = filterStatus == "PENDING",
              onClick = { filterStatus = "PENDING" },
              label = { Text("Pending (${totalCount - completedCount})") }
            )
          }
          item {
            FilterChip(
              selected = filterStatus == "DONE",
              onClick = { filterStatus = "DONE" },
              label = { Text("Completed ($completedCount)") }
            )
          }
        }
      }

      // 3. Tasks List
      if (filteredTasks.isEmpty()) {
        item {
          EmptyStateView(
            icon = Icons.Default.Task,
            title = "No Tasks Here",
            subtitle = "Add your daily accounting, client follow-ups, and business errands."
          )
        }
      } else {
        items(filteredTasks, key = { it.id }) { task ->
          val priorityColor = when (task.priority) {
            "HIGH" -> AccentRose
            "MEDIUM" -> AccentAmber
            else -> EmeraldPrimary
          }

          FintechCard(modifier = Modifier.fillMaxWidth()) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  IconButton(
                    onClick = { onToggleTaskDone(task) },
                    modifier = Modifier.size(32.dp).testTag("task_check_${task.id}")
                  ) {
                    Icon(
                      imageVector = if (task.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                      contentDescription = "Toggle Done",
                      tint = if (task.isDone) EmeraldDark else MaterialTheme.colorScheme.outline,
                      modifier = Modifier.size(24.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = task.title,
                      style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                      ),
                      color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (task.description.isNotBlank()) {
                      Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (task.streakCount > 0) {
                    StatusTag(text = "🔥 ${task.streakCount}d", color = AccentAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                  }
                  IconButton(
                    onClick = { taskToEditProgress = task },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Progress", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                  }
                  IconButton(
                    onClick = { taskToDelete = task },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Task Progress & Metadata Bar
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  StatusTag(text = task.priority, color = priorityColor)
                  StatusTag(text = task.category, color = AccentIndigo)
                  if (task.recurrence != "NONE") {
                    StatusTag(text = task.recurrence, color = MaterialTheme.colorScheme.primary)
                  }
                  if (task.reminderEnabled) {
                    Icon(Icons.Default.Alarm, contentDescription = "Reminder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                  }
                }
                Text(
                  text = "${task.progressPercent}%",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Spacer(modifier = Modifier.height(6.dp))
              LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(4.dp)
                  .clip(RoundedCornerShape(2.dp)),
                color = if (task.isDone) EmeraldDark else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
              )
            }
          }
        }
      }
    }

    // Add Task FAB
    FloatingActionButton(
      onClick = { showAddDialog = true },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp)
        .testTag("fab_add_task")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add")
        Spacer(modifier = Modifier.width(6.dp))
        Text("New Task", fontWeight = FontWeight.Bold)
      }
    }
  }

  // Add Task Dialog
  if (showAddDialog) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var category by remember { mutableStateOf("Finance") }
    var recurrence by remember { mutableStateOf("DAILY") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Create Daily Task") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title *") },
            placeholder = { Text("e.g. Audit balance sheet with Apex") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_task_title")
          )
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Notes / Reminder details") },
            modifier = Modifier.fillMaxWidth()
          )

          // Priority chips
          Text("Priority", style = MaterialTheme.typography.labelSmall)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("HIGH", "MEDIUM", "LOW").forEach { p ->
              FilterChip(
                selected = priority == p,
                onClick = { priority = p },
                label = { Text(p) }
              )
            }
          }

          // Recurrence
          Text("Recurrence", style = MaterialTheme.typography.labelSmall)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DAILY", "WEEKLY", "NONE").forEach { r ->
              FilterChip(
                selected = recurrence == r,
                onClick = { recurrence = r },
                label = { Text(r) }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (title.isNotBlank()) {
              onAddTask(
                TaskItem(
                  title = title.trim(),
                  description = description.trim(),
                  priority = priority,
                  category = category,
                  recurrence = recurrence,
                  reminderEnabled = true
                )
              )
              showAddDialog = false
            }
          },
          enabled = title.isNotBlank(),
          modifier = Modifier.testTag("submit_add_task_button")
        ) {
          Text("Add Task")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Edit Progress Slider Dialog
  if (taskToEditProgress != null) {
    var progressSlider by remember { mutableStateOf(taskToEditProgress!!.progressPercent.toFloat()) }

    AlertDialog(
      onDismissRequest = { taskToEditProgress = null },
      title = { Text("Update Task Progress") },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(taskToEditProgress!!.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(14.dp))
          Text("${progressSlider.toInt()}%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = EmeraldDark)
          Slider(
            value = progressSlider,
            onValueChange = { progressSlider = it },
            valueRange = 0f..100f,
            steps = 19
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateProgress(taskToEditProgress!!, progressSlider.toInt())
            taskToEditProgress = null
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { taskToEditProgress = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Delete Task Dialog
  if (taskToDelete != null) {
    AlertDialog(
      onDismissRequest = { taskToDelete = null },
      title = { Text("Delete Task") },
      text = { Text("Remove '${taskToDelete!!.title}'?") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteTask(taskToDelete!!)
            taskToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { taskToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
