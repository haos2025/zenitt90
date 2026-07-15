package com.platinum.ott.presentation.screens.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.core.plugin.PluginManifest
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.entity.PluginEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PluginViewModel(
    private val pluginManager: PluginManager,
    private val pluginRepository: PluginRepository
) : ViewModel() {

    sealed interface UiState {
        object Loading : UiState
        data class Success(
            val installed: List<PluginEntity>,
            val catalog: List<PluginRepository.CatalogEntry>,
            val updates: List<Pair<PluginEntity, PluginRepository.CatalogEntry>>
        ) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface InstallState {
        object Idle : InstallState
        object Installing : InstallState
        data class Done(val manifest: PluginManifest) : InstallState
        data class Failed(val error: String) : InstallState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    val installedPlugins = pluginManager.getAllPlugins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val catalog = pluginRepository.fetchCatalog().getOrDefault(emptyList())
                val updates = pluginRepository.checkForUpdates().getOrDefault(emptyList())
                _uiState.value = UiState.Success(installedPlugins.value, catalog, updates)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun installFromCatalog(entry: PluginRepository.CatalogEntry) {
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            pluginRepository.installFromCatalog(entry)
                .onSuccess { _installState.value = InstallState.Done(it) }
                .onFailure { _installState.value = InstallState.Failed(it.message ?: "Ошибка") }
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            pluginRepository.installFromUrl(url)
                .onSuccess { _installState.value = InstallState.Done(it) }
                .onFailure { _installState.value = InstallState.Failed(it.message ?: "Ошибка") }
        }
    }

    fun installFromScript(script: String) {
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            pluginManager.installPlugin(script)
                .onSuccess { _installState.value = InstallState.Done(it) }
                .onFailure { _installState.value = InstallState.Failed(it.message ?: "Ошибка") }
        }
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        viewModelScope.launch { pluginManager.setEnabled(pluginId, enabled) }
    }

    fun uninstallPlugin(pluginId: String) {
        viewModelScope.launch { pluginManager.uninstallPlugin(pluginId) }
    }

    fun updatePlugin(pluginId: String, entry: PluginRepository.CatalogEntry) {
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            pluginRepository.installFromCatalog(entry)
                .onSuccess { _installState.value = InstallState.Done(it) }
                .onFailure { _installState.value = InstallState.Failed(it.message ?: "Ошибка") }
        }
    }

    fun getPluginById(pluginId: String): Flow<PluginEntity?> = pluginManager.getAllPlugins().map { list -> list.firstOrNull { it.id == pluginId } }

    fun resetInstallState() { _installState.value = InstallState.Idle }
}
