package com.example.treasurewalk.ui.features.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.data.local.TreasureEntity
import com.example.treasurewalk.ui.viewmodels.WalkViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InventoryScreen(
    viewModel: WalkViewModel,
    onBackClick: () -> Unit
) {
    val items by viewModel.collectedTreasures.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color(0xFF1F2937))
            Spacer(Modifier.width(12.dp))
            Text("IL TUO BOTTINO", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun tesoro trovato... ancora!", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { treasure ->
                    TreasureCard(treasure)
                }
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("TORNA AL MENU", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TreasureCard(treasure: TreasureEntity) {
    val rarityColor = when (treasure.type.name) {
        "COMMON" -> Color(0xFF45D084)
        "RARE" -> Color(0xFF3B82F6)
        "LEGENDARY" -> Color(0xFFFFD166)
        else -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(2.dp, rarityColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(rarityColor.copy(alpha = 0.2f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (treasure.type.name == "LEGENDARY") "👑" else "📦",
                    fontSize = 30.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(treasure.type.name, fontWeight = FontWeight.Black, color = rarityColor, fontSize = 14.sp)
            Text("+${treasure.xpAwarded} XP", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Spacer(Modifier.height(4.dp))

            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(treasure.timestamp))
            Text(date, fontSize = 10.sp, color = Color.Gray)
        }
    }
}