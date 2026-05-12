package top.cenmin.tailcontrol.ui.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.LogRepository
import top.cenmin.tailcontrol.core.log.LogArchive
import top.cenmin.tailcontrol.core.log.LogSource
import top.cenmin.tailcontrol.core.log.LogTooLargeException
import javax.inject.Inject

private const val MAX_LINES = 5000
private const val INITIAL_LINES_DEFAULT = 500
private const val INITIAL_LINES_MAX = 5000

/** Snackbar 单次提示的载荷类型。 */
enum class TransientKind { ClearedOk, NoRoot, ClearFailed, ArchiveTooLarge, ReadFailed }

data class LogUiState(
    val source: LogSource = LogSource.App,
    val lines: List<String> = emptyList(),
    val paused: Boolean = false,
    val initialLines: Int = INITIAL_LINES_DEFAULT,
    val archives: List<LogArchive> = emptyList(),
    val selectedArchiveId: String = LogArchive.LIVE_ID,
    val confirmClear: Boolean = false,
    val transient: TransientKind? = null,
) {
    val isLive: Boolean get() = selectedArchiveId == LogArchive.LIVE_ID
}

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepo: LogRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LogUiState())
    val ui: StateFlow<LogUiState> = _ui.asStateFlow()

    private var streamJob: Job? = null

    init { switchSource(LogSource.App) }

    fun switchSource(source: LogSource) {
        streamJob?.cancel()
        _ui.value = _ui.value.copy(
            source = source,
            lines = emptyList(),
            selectedArchiveId = LogArchive.LIVE_ID,
            initialLines = INITIAL_LINES_DEFAULT,
            paused = false,
        )
        refreshArchives()
        startLive(source, INITIAL_LINES_DEFAULT)
    }

    fun selectArchive(id: String) {
        if (id == _ui.value.selectedArchiveId) return
        streamJob?.cancel()
        _ui.value = _ui.value.copy(selectedArchiveId = id, lines = emptyList(), paused = false)
        if (id == LogArchive.LIVE_ID) {
            startLive(_ui.value.source, _ui.value.initialLines)
        } else {
            startArchive(_ui.value.source, id)
        }
    }

    fun togglePause() {
        if (!_ui.value.isLive) return
        _ui.value = _ui.value.copy(paused = !_ui.value.paused)
    }

    fun loadEarlier() {
        if (!_ui.value.isLive) return
        val next = (_ui.value.initialLines * 2).coerceAtMost(INITIAL_LINES_MAX)
        if (next == _ui.value.initialLines) return
        streamJob?.cancel()
        _ui.value = _ui.value.copy(initialLines = next, lines = emptyList())
        startLive(_ui.value.source, next)
    }

    fun requestClear() {
        _ui.value = _ui.value.copy(confirmClear = true)
    }

    fun dismissClear() {
        _ui.value = _ui.value.copy(confirmClear = false)
    }

    fun confirmClear() {
        val state = _ui.value
        val source = state.source
        val target = state.archives.firstOrNull { it.id == state.selectedArchiveId }
        val targetingArchive = target != null && !target.isCurrent
        _ui.value = state.copy(confirmClear = false)
        viewModelScope.launch {
            val r = logRepo.clear(source, target)
            if (r.isSuccess) {
                _ui.value = _ui.value.copy(
                    transient = TransientKind.ClearedOk,
                    lines = emptyList(),
                )
                refreshArchives()
                streamJob?.cancel()
                if (targetingArchive) {
                    // 归档已被删除，回到 Live，重新订阅。
                    _ui.value = _ui.value.copy(selectedArchiveId = LogArchive.LIVE_ID)
                    startLive(source, _ui.value.initialLines)
                } else {
                    startLive(source, _ui.value.initialLines)
                }
            } else {
                val msg = r.exceptionOrNull()?.message
                _ui.value = _ui.value.copy(
                    transient = if (msg == "no_root") TransientKind.NoRoot else TransientKind.ClearFailed,
                )
            }
        }
    }

    fun dismissTransient() {
        _ui.value = _ui.value.copy(transient = null)
    }

    private fun refreshArchives() {
        viewModelScope.launch {
            val list = runCatching { logRepo.listArchives(_ui.value.source) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(archives = list)
        }
    }

    private fun startLive(source: LogSource, initialLines: Int) {
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            logRepo.tail(source, initialLines).collect { line ->
                if (_ui.value.paused) return@collect
                appendLine(line)
            }
        }
    }

    private fun startArchive(source: LogSource, archiveId: String) {
        val archive = _ui.value.archives.firstOrNull { it.id == archiveId } ?: return
        if (archive.sizeBytes > MAX_ARCHIVE_BYTES) {
            _ui.value = _ui.value.copy(transient = TransientKind.ArchiveTooLarge)
            return
        }
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                logRepo.readArchive(source, archive)
                    .catch { e ->
                        _ui.value = _ui.value.copy(
                            transient = if (e is LogTooLargeException) TransientKind.ArchiveTooLarge
                            else TransientKind.ReadFailed,
                        )
                    }
                    .collect { line -> appendLine(line) }
            } catch (e: LogTooLargeException) {
                _ui.value = _ui.value.copy(transient = TransientKind.ArchiveTooLarge)
            }
        }
    }

    private fun appendLine(line: String) {
        val cur = _ui.value.lines
        val next = if (cur.size >= MAX_LINES) cur.drop(cur.size - MAX_LINES + 1) + line
        else cur + line
        _ui.value = _ui.value.copy(lines = next)
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }

    companion object {
        const val MAX_ARCHIVE_BYTES: Long = 5L * 1024L * 1024L
    }
}
