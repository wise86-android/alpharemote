package org.staacks.alpharemote.feature.ble.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import org.staacks.alpharemote.core.ble.BleConnectionState

/**
 * Wraps [connectionState] so only genuine Connected/Disconnected *transitions* invoke
 * [onConnected]/[onDisconnected] - never the value a fresh subscriber is replayed at subscribe
 * time.
 *
 * [connectionState] is a hot [kotlinx.coroutines.flow.StateFlow]: any new collector is
 * immediately replayed whatever the current value already is, which is not a live transition,
 * just a snapshot. Reacting to that snapshot as if it were one is exactly what used to make
 * [AlphaRemoteService] tear down a connection attempt's own collectors moments after creating
 * them (a fresh subscription taken out before the connect() call it belongs to had a chance to
 * move the flow off its previous, unrelated value would replay that stale value first). Dropping
 * it here removes the dependency on winning that race, provided the caller still subscribes only
 * after starting the connection it cares about - see the call site in [AlphaRemoteService].
 *
 * Extracted as a standalone function purely so this behaviour can be unit tested without an
 * Android [android.app.Service].
 */
internal fun observeConnectionTransitions(
    connectionState: Flow<BleConnectionState>,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit,
    onEachTransition: (BleConnectionState) -> Unit = {},
): Flow<BleConnectionState> =
    connectionState.drop(1).onEach {
        onEachTransition(it)
        when (it) {
            BleConnectionState.Connected -> onConnected()
            BleConnectionState.Disconnected -> onDisconnected()
            else -> {}
        }
    }
