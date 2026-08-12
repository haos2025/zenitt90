package com.platinum.ott.presentation.screens.history

import androidx.lifecycle.ViewModel
import com.platinum.ott.core.SessionGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    val history = sessionGraph.watchHistoryUseCase.getRecent()
}
