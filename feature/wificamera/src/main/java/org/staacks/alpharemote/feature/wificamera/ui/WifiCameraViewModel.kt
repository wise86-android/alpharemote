package org.staacks.alpharemote.feature.wificamera.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.staacks.alpharemote.feature.wificamera.WifiCameraDefaults
import org.staacks.alpharemote.feature.wificamera.data.DefaultWifiCameraRepository
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.LiveViewFrame
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraRepository

/**
 * Screen state for the Wi-Fi camera.
 *
 * Holds nothing of its own: everything on screen is derived from the repository's flows, so a
 * change made on the camera body and a change made here arrive by exactly the same route. That
 * is what keeps the two from drifting apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WifiCameraRepository =
        DefaultWifiCameraRepository.getInstance(application)

    data class UiState(
        val connection: WifiCameraConnection = WifiCameraConnection.Idle,
        val camera: CameraSnapshot = CameraSnapshot()
    ) {
        /** Settings the camera has actually reported, in a stable display order. */
        val settings: List<CameraSetting>
            get() = CameraSettingId.entries.mapNotNull { camera[it] }
    }

    val uiState: StateFlow<UiState> =
        combine(repository.connection, repository.camera) { connection, camera ->
            UiState(connection, camera)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    /**
     * The viewfinder, deliberately its own flow rather than a field of [UiState].
     *
     * Frames arrive around 30 times a second. Folding them into the state object would make every
     * chip and readout on the screen a candidate for recomposition at that rate, for a value none
     * of them read.
     *
     * `WhileSubscribed` is what starts and stops the stream on the camera: leaving the screen
     * stops it, and the grace period means a rotation does not tear it down and build it again.
     */
    val liveView: StateFlow<LiveViewState> = repository.connection
        .map { it.isConnected }
        .distinctUntilChanged()
        .flatMapLatest { connected ->
            if (connected) liveViewFrames() else flowOf(LiveViewState.Idle)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveViewState.Idle)

    private fun liveViewFrames(): Flow<LiveViewState> {
        // One pool per stream. Held here rather than as a field so its buffers are released when
        // the stream ends instead of staying resident for the life of the view model.
        val pool = LiveViewBitmapPool()

        return repository.liveView()
            // Typed as the interface, not as Streaming: the operators below emit the other states,
            // and inference would otherwise pin this to the one branch.
            .mapNotNull<LiveViewFrame, LiveViewState> { frame ->
                pool.decode(frame)?.let { LiveViewState.Streaming(it) }
            }
            // Decoding is JPEG work; keep it off the frame the UI is drawing on.
            .flowOn(Dispatchers.Default)
            // If the UI cannot keep up, show the newest frame and drop the rest. Latency in a
            // viewfinder is worse than a missed frame.
            .conflate()
            .retryWhen { cause, attempt ->
                // A stream can drop while the camera is otherwise fine. Retry a few times before
                // concluding the body will not give us one at all.
                if (attempt >= LIVE_VIEW_RETRIES) {
                    false
                } else {
                    Log.w(TAG, "Live view stream failed, retrying", cause)
                    delay(LIVE_VIEW_RETRY_DELAY_MS * (attempt + 1))
                    true
                }
            }
            .catch { cause ->
                Log.w(TAG, "Live view unavailable", cause)
                emit(LiveViewState.Unavailable(cause.message ?: "Live view is not available"))
            }
            .onStart { emit(LiveViewState.Starting) }
            .onCompletion { pool.clear() }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-off failures — a rejected write, not a state the screen should keep showing. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun connect() {
        repository.connect(WifiCameraDefaults.CREDENTIALS)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun select(id: CameraSettingId, option: CameraOption) {
        viewModelScope.launch {
            repository.setSetting(id, option).onFailure { error ->
                _messages.tryEmit("${id.label}: ${error.message ?: "could not be changed"}")
            }
        }
    }

    fun capture() {
        viewModelScope.launch {
            repository.capture().onFailure { error ->
                _messages.tryEmit(error.message ?: "The shot could not be taken")
            }
        }
    }

    private companion object {
        const val TAG = "WifiCameraViewModel"
        const val LIVE_VIEW_RETRIES = 3
        const val LIVE_VIEW_RETRY_DELAY_MS = 1_000L
    }
}
