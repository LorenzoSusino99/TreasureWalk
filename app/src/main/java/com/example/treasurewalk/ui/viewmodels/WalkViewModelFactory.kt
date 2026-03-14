package com.example.treasurewalk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.treasurewalk.data.local.TreasureDao

class WalkViewModelFactory(private val treasureDao: TreasureDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalkViewModel(treasureDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}