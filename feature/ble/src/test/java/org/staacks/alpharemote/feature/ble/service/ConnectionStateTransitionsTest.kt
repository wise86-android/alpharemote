package org.staacks.alpharemote.feature.ble.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.staacks.alpharemote.core.ble.BleConnectionState

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateTransitionsTest {

    @Test
    fun `does not react to the value already present when subscribing`() = runTest {
        // Mirrors CameraBleConnection.state: a StateFlow that already holds a value (e.g. left
        // over from before this connection attempt) before anyone subscribes to it.
        val state = MutableStateFlow(BleConnectionState.Disconnected)
        var connectedCalls = 0
        var disconnectedCalls = 0

        val job = observeConnectionTransitions(
            state,
            onConnected = { connectedCalls++ },
            onDisconnected = { disconnectedCalls++ },
        ).launchIn(this)
        advanceUntilIdle()

        assertEquals(0, connectedCalls)
        assertEquals(0, disconnectedCalls)

        job.cancel()
    }

    @Test
    fun `does not react even if the replayed initial value is Connected`() = runTest {
        // Defensive: even if a fresh subscriber's first value happened to be Connected rather
        // than Disconnected, it must still be treated as a snapshot, not a transition.
        val state = MutableStateFlow(BleConnectionState.Connected)
        var connectedCalls = 0

        val job = observeConnectionTransitions(
            state,
            onConnected = { connectedCalls++ },
            onDisconnected = {},
        ).launchIn(this)
        advanceUntilIdle()

        assertEquals(0, connectedCalls)

        job.cancel()
    }

    @Test
    fun `invokes onConnected for a real transition to Connected`() = runTest {
        val state = MutableStateFlow(BleConnectionState.Disconnected)
        var connectedCalls = 0
        var disconnectedCalls = 0

        val job = observeConnectionTransitions(
            state,
            onConnected = { connectedCalls++ },
            onDisconnected = { disconnectedCalls++ },
        ).launchIn(this)
        advanceUntilIdle()

        state.value = BleConnectionState.Connecting
        state.value = BleConnectionState.Connected
        advanceUntilIdle()

        assertEquals(1, connectedCalls)
        assertEquals(0, disconnectedCalls)

        job.cancel()
    }

    @Test
    fun `invokes onDisconnected for a real transition to Disconnected after connecting`() = runTest {
        val state = MutableStateFlow(BleConnectionState.Disconnected)
        var connectedCalls = 0
        var disconnectedCalls = 0

        val job = observeConnectionTransitions(
            state,
            onConnected = { connectedCalls++ },
            onDisconnected = { disconnectedCalls++ },
        ).launchIn(this)
        advanceUntilIdle()

        state.value = BleConnectionState.Connected
        advanceUntilIdle()
        state.value = BleConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(1, connectedCalls)
        assertEquals(1, disconnectedCalls)

        job.cancel()
    }

    @Test
    fun `a failed connection attempt still reaches onDisconnected without ever connecting`() = runTest {
        // A connection attempt that never bonds (BoundLost forever) but is later explicitly torn
        // down must still clean up - dropping the replayed value must not turn into "only
        // disconnect if we were previously Connected".
        val state = MutableStateFlow(BleConnectionState.Disconnected)
        var connectedCalls = 0
        var disconnectedCalls = 0

        val job = observeConnectionTransitions(
            state,
            onConnected = { connectedCalls++ },
            onDisconnected = { disconnectedCalls++ },
        ).launchIn(this)
        advanceUntilIdle()

        state.value = BleConnectionState.BoundLost
        advanceUntilIdle()
        state.value = BleConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(0, connectedCalls)
        assertEquals(1, disconnectedCalls)

        job.cancel()
    }

    @Test
    fun `onEachTransition is not invoked for the dropped initial value`() = runTest {
        val state = MutableStateFlow(BleConnectionState.Idle)
        val seenTransitions = mutableListOf<BleConnectionState>()

        val job = observeConnectionTransitions(
            state,
            onConnected = {},
            onDisconnected = {},
            onEachTransition = { seenTransitions += it },
        ).launchIn(this)
        advanceUntilIdle()

        assertEquals(emptyList<BleConnectionState>(), seenTransitions)

        // advanceUntilIdle() between each write: MutableStateFlow conflates values a slow
        // collector hasn't caught up to yet, so writing both before advancing at all would only
        // ever let the collector observe the latest one.
        state.value = BleConnectionState.Connecting
        advanceUntilIdle()
        state.value = BleConnectionState.Connected
        advanceUntilIdle()

        assertEquals(
            listOf(BleConnectionState.Connecting, BleConnectionState.Connected),
            seenTransitions
        )

        job.cancel()
    }
}
