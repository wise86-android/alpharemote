package org.staacks.alpharemote.feature.wificamera.data.transfer

import android.util.Log
import org.staacks.alpharemote.feature.wificamera.domain.TransferItem

/**
 * The camera's push-transfer session.
 *
 * The handshake is not optional. The camera shows "Connecting…" on its own screen until it is
 * told the transfer ended, and it stays that way indefinitely if the app forgets — so
 * [end] belongs in a `finally`, on every path including failure and cancellation
 * (PROTOCOL.md §4.2).
 *
 * @param pushListControlUrl the XPushList service's control URL — session actions only.
 * @param contentDirectoryControlUrl the ContentDirectory service's control URL — browsing only.
 *   Swapping the two returns HTTP 500.
 */
class PushTransferSession(
    private val soap: SoapClient,
    private val pushListControlUrl: String,
    private val contentDirectoryControlUrl: String
) {

    suspend fun start() {
        soap.callVendor(pushListControlUrl, "X_TransferStart")
    }

    /** The object id to browse from. Falls back to the conventional name if unparseable. */
    suspend fun pushRoot(): String {
        val response = soap.callVendor(pushListControlUrl, "X_GetPushRoot")
        return Regex("<PushRoot>(.*?)</PushRoot>", RegexOption.DOT_MATCHES_ALL)
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_PUSH_ROOT
    }

    /** Reported after each file. Sony's misspelling of `NumTransferd` is what the camera parses. */
    suspend fun reportProgress(total: Int, transferred: Int) {
        runCatching {
            soap.callVendor(
                pushListControlUrl,
                "X_TransferProgress",
                "<NumTotal>$total</NumTotal><NumTransferd>$transferred</NumTransferd>"
            )
        }.onFailure { Log.w(TAG, "X_TransferProgress failed: ${it.message}") }
    }

    /** [ErrorCode.OK], [ErrorCode.ERROR] or [ErrorCode.CANCELLED]. */
    suspend fun end(errorCode: Int) {
        runCatching {
            soap.callVendor(
                pushListControlUrl,
                "X_TransferEnd",
                "<ErrCode>$errorCode</ErrCode>"
            )
        }.onFailure { Log.w(TAG, "X_TransferEnd failed: ${it.message}") }
    }

    /**
     * Every item the camera is offering, following containers and paging at each level.
     *
     * Both are required. `Browse` returns at most [PAGE_SIZE] per call, so a large selection is
     * silently truncated without the paging loop, and `PushRoot` may hold date containers rather
     * than items, so a listing that does not descend comes back empty.
     */
    suspend fun listContent(rootId: String, onFound: suspend (Int) -> Unit): List<TransferItem> {
        val items = mutableListOf<TransferItem>()
        val pending = ArrayDeque(listOf(rootId))
        val visited = mutableSetOf<String>()

        while (pending.isNotEmpty()) {
            val containerId = pending.removeFirst()
            if (!visited.add(containerId)) continue

            var startingIndex = 0
            while (true) {
                val page = DidlLiteParser.parseBrowseResponse(
                    soap.browse(
                        controlUrl = contentDirectoryControlUrl,
                        objectId = containerId,
                        startingIndex = startingIndex,
                        requestedCount = PAGE_SIZE
                    )
                )

                items += page.items
                pending += page.containerIds
                onFound(items.size)

                startingIndex += maxOf(page.returned, page.items.size + page.containerIds.size)
                val exhausted = page.returned == 0 ||
                    startingIndex >= page.totalMatches ||
                    (page.items.isEmpty() && page.containerIds.isEmpty())
                if (exhausted) break
            }
        }
        return items
    }

    object ErrorCode {
        const val OK = 0
        const val ERROR = 1
        const val CANCELLED = 2
    }

    companion object {
        private const val TAG = "PushTransferSession"
        private const val DEFAULT_PUSH_ROOT = "PushRoot"
        private const val PAGE_SIZE = 50
    }
}
