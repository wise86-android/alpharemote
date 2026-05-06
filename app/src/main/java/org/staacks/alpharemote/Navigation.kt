package org.staacks.alpharemote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.serialization.Serializable
import org.staacks.alpharemote.camera.CameraAction

@Serializable
sealed interface AlphaRemoteNavKey : NavKey {
    @Serializable data object Camera : AlphaRemoteNavKey
    @Serializable data object Settings : AlphaRemoteNavKey
    @Serializable data object About : AlphaRemoteNavKey
    @Serializable data class CameraActionPicker(val index: Int, val action: CameraAction, val showDelete: Boolean) : AlphaRemoteNavKey
    @Serializable data class Help(val titleRes: Int, val textRes: Int) : AlphaRemoteNavKey
}

/**
 * An overload of `rememberNavBackStack` that returns a subtype of `NavKey`.
 */
@Composable
fun <T : NavKey> rememberNavBackStack(vararg elements: T): NavBackStack<T> {
    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) {
        NavBackStack(*elements)
    }
}

/**
 * Create a navigation state that persists config changes and process death.
 */
@Composable
fun rememberNavigationState(
    startRoute: AlphaRemoteNavKey,
    topLevelRoutes: Set<AlphaRemoteNavKey>
): NavigationState {

    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/**
 * State holder for navigation state.
 *
 * @param startRoute - the start route. The user will exit the app through this route.
 * @param topLevelRoute - the current top level route
 * @param backStacks - the back stacks for each top level route
 */
class NavigationState(
    val startRoute: AlphaRemoteNavKey,
    topLevelRoute: MutableState<AlphaRemoteNavKey>,
    val backStacks: Map<AlphaRemoteNavKey, NavBackStack<AlphaRemoteNavKey>>
) {
    var topLevelRoute: AlphaRemoteNavKey by topLevelRoute
    val stacksInUse: List<AlphaRemoteNavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

/**
 * Convert NavigationState into NavEntries.
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (AlphaRemoteNavKey) -> NavEntry<AlphaRemoteNavKey>
): SnapshotStateList<NavEntry<AlphaRemoteNavKey>> {

    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<AlphaRemoteNavKey>(),
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    return stacksInUse
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: AlphaRemoteNavKey) {
        if (route in state.backStacks.keys) {
            // This is a top level route, just switch to it.
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute] ?:
        error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
            }
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
