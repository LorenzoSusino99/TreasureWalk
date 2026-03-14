package com.example.treasurewalk.ui.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.ui.viewmodels.WalkViewModel
import kotlin.math.sqrt

@Composable
fun ProfileScreen(
    viewModel: WalkViewModel,
    onBackClick: () -> Unit
) {
    val totalXp by viewModel.totalXp.collectAsState()
    val treasures by viewModel.collectedTreasures.collectAsState()

    // Calcolo Livello: ogni livello richiede progressivamente più XP
    val currentLevel = (sqrt(totalXp.toDouble() / 100).toInt()) + 1
    val xpForCurrentLevel = (currentLevel - 1) * (currentLevel - 1) * 100
    val xpForNextLevel = currentLevel * currentLevel * 100
    val progress = (totalXp - xpForCurrentLevel).toFloat() / (xpForNextLevel - xpForCurrentLevel).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. AVATAR E LIVELLO
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6)),
            contentAlignment = Alignment.Center
        ) {
            Text("LV", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            Text("$currentLevel", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = getRankTitle(currentLevel),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. BARRA XP
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progresso Livello", fontSize = 12.sp, color = Color.Gray)
                Text("$totalXp / $xpForNextLevel XP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = Color(0xFF45D084),
                trackColor = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. STATISTICHE TOTALI
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                label = "Tesori",
                value = "${treasures.size}",
                icon = Icons.Default.EmojiEvents,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Missioni",
                value = "${treasures.groupBy { it.timestamp / 86400000 }.size}", // Stima giorni attivi
                icon = Icons.Default.History,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CHIUDI PROFILO")
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF3B82F6))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

fun getRankTitle(level: Int): String {
    return when {
        level < 5 -> "Esploratore Novizio"
        level < 10 -> "Cacciatore di Scie"
        level < 20 -> "Veterano delle Strade"
        else -> "Leggenda Urbana"
    }
}