package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.TurboViewModel

@Composable
fun AppPickerScreen(viewModel: TurboViewModel, onBack: () -> Unit = {}) {
    val allApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val addedApps by viewModel.addedApps.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SELECT GAMES", color = Color(0xFFFF551A), fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("DONE", color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allApps.size) { index ->
                val app = allApps[index]
                val isAdded = addedApps.any { it.packageName == app.packageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isAdded) viewModel.removeAppFromMagic(app.packageName, app.name)
                            else viewModel.addAppToMagic(app.packageName, app.name)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(app.name, color = Color.White, fontSize = 13.sp)
                    Text(
                        text = if (isAdded) "ADDED" else "+ ADD",
                        color = if (isAdded) Color(0xFF00E676) else Color(0xFFFF551A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
