package com.example.treasurewalk.ui.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.treasurewalk.R
import com.example.treasurewalk.ui.components.MenuButton

@Composable
fun HomeScreen(
    onPlayClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onProfileClick: () -> Unit
){
    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.bg_no_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.logo_nb),
                contentDescription = "Logo del Gioco",
                modifier = Modifier
                    .size(256.dp)
                    .padding(bottom = 40.dp)
            )

            MenuButton(
                text = "GIOCA",
                icon = Icons.Filled.PlayArrow,
                backgroundColor = Color(0xFF45D084),
                textColor = Color.White,
                onClick = onPlayClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuButton(
                text = "INVENTARIO",
                icon = Icons.Filled.ShoppingCart,
                backgroundColor = Color(0xFFFFD166),
                textColor = Color(0xFF5D4037),
                onClick = onInventoryClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuButton(
                text = "PROFILO",
                icon = Icons.Filled.Person,
                backgroundColor = Color(0xFF3B82F6),
                textColor = Color.White,
                onClick = onProfileClick
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        onPlayClick = {},
        onInventoryClick = {},
        onProfileClick = {}
    )
}