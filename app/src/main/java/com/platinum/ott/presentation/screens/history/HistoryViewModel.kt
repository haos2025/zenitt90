package com.platinum.ott.presentation.screens.history

import androidx.lifecycle.ViewModel
import com.platinum.ott.core.ServiceLocator

class HistoryViewModel : ViewModel() {
    val history = ServiceLocator.watchHistoryUseCase.getRecent()
}
