package org.staacks.alpharemote.feature.wificamera.data.transfer

import org.staacks.alpharemote.feature.wificamera.data.net.NetworkHttpClient

/**
 * SOAP for the UPnP and Sony vendor actions used by push transfer.
 *
 * Several things here look like typos and are not. Sony's own client sends them, and the camera's
 * parser was written against that client rather than against the specification, so they are
 * reproduced verbatim (PROTOCOL.md §4.2).
 */
class SoapClient(private val http: NetworkHttpClient) {

    /**
     * Invokes a vendor action on the XPushList service.
     *
     * Note the space in `encoding= "UTF-8"` — copied exactly from Sony's client.
     */
    suspend fun callVendor(
        controlUrl: String,
        action: String,
        body: String = ""
    ): String {
        val envelope = "<?xml version=\"1.0\" encoding= \"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:$action xmlns:u=\"$X_PUSH_LIST\">$body</u:$action>" +
            "</s:Body></s:Envelope>"

        return post(controlUrl, "$X_PUSH_LIST#$action", envelope)
    }

    /**
     * Invokes `Browse` on the ContentDirectory service.
     *
     * Must go to the **ContentDirectory** control URL, never the XPushList one — the camera
     * answers HTTP 500 if they are swapped, and the two URLs are both present in the same
     * description.
     */
    suspend fun browse(
        controlUrl: String,
        objectId: String,
        startingIndex: Int,
        requestedCount: Int
    ): String {
        val envelope = "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:Browse xmlns:u=\"$CONTENT_DIRECTORY\">" +
            "<ObjectID>${objectId.escapeXml()}</ObjectID>" +
            "<BrowseFlag>BrowseDirectChildren</BrowseFlag>" +
            "<Filter>*</Filter>" +
            "<StartingIndex>$startingIndex</StartingIndex>" +
            "<RequestedCount>$requestedCount</RequestedCount>" +
            "<SortCriteria></SortCriteria>" +
            "</u:Browse></s:Body></s:Envelope>"

        return post(controlUrl, "$CONTENT_DIRECTORY#Browse", envelope)
    }

    private suspend fun post(url: String, soapAction: String, envelope: String): String =
        http.postSoap(url, soapAction, envelope)

    companion object {
        const val X_PUSH_LIST = "urn:schemas-sony-com:service:XPushList:1"
        const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"
        const val X_PUSH_LIST_SERVICE_TYPE = X_PUSH_LIST
        const val CONTENT_DIRECTORY_SERVICE_TYPE = CONTENT_DIRECTORY
    }
}

internal fun String.escapeXml(): String = buildString(length) {
    this@escapeXml.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(character)
        }
    }
}
