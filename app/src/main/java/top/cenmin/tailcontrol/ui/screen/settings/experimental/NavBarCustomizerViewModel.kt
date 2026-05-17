package top.cenmin.tailcontrol.ui.screen.settings.experimental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.ui.nav.TopLevelDestination
import javax.inject.Inject

data class NavBarCustomizerUiState(
    val hiddenItems: Set<String> = emptySet(),
)

@HiltViewModel
class NavBarCustomizerViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(NavBarCustomizerUiState())
    val ui: StateFlow<NavBarCustomizerUiState> = _ui.asStateFlow()

    val pinnedDestinations: Set<TopLevelDestination> = setOf(
        TopLevelDestination.Home,
        TopLevelDestination.Settings,
    )

    val toggleableDestinations: List<TopLevelDestination> =
        TopLevelDestination.entries.filter { it.showInBar && it !in pinnedDestinations }

    init {
        viewModelScope.launch {
            prefs.navHiddenItems.collect { hidden ->
                _ui.value = _ui.value.copy(hiddenItems = hidden)
            }
        }
    }

    fun toggle(dest: TopLevelDestination) {
        if (dest in pinnedDestinations) return
        // 直接在当前 StateFlow 上乐观更新，再持久化，避免等待 DataStore 回调才刷新 UI
        val current = _ui.value.hiddenItems.toMutableSet()
        if (dest.name in current) current.remove(dest.name) else current.add(dest.name)
        _ui.value = _ui.value.copy(hiddenItems = current)
        viewModelScope.launch {
            prefs.setNavHiddenItems(current)
        }
    }
}