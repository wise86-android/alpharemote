package org.staacks.alpharemote.feature.wificamera.data.transfer

import org.staacks.alpharemote.feature.wificamera.domain.ImageQuality
import org.staacks.alpharemote.feature.wificamera.domain.TransferItem
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One page of a `Browse` response.
 */
data class BrowseResult(
    val items: List<TransferItem>,
    /** Child containers to descend into — `PushRoot` may hold date folders rather than items. */
    val containerIds: List<String>,
    val returned: Int,
    val totalMatches: Int
)

/**
 * Parses the content listings the camera returns.
 *
 * The `Browse` response embeds DIDL-Lite as **escaped text inside** `<Result>`, so it has to be
 * unescaped and parsed as XML a second time.
 */
object DidlLiteParser {

    /**
     * Reads a SOAP `Browse` response, inner DIDL-Lite and all.
     */
    fun parseBrowseResponse(soapResponse: String): BrowseResult {
        val outer = documentElement(soapResponse)
        val didl = outer.firstText("Result").orEmpty()
        val returned = outer.firstText("NumberReturned")?.toIntOrNull() ?: 0
        val total = outer.firstText("TotalMatches")?.toIntOrNull() ?: 0

        if (didl.isBlank()) {
            return BrowseResult(emptyList(), emptyList(), returned, total)
        }

        val inner = documentElement(didl)
        return BrowseResult(
            items = inner.childrenNamed("item").mapNotNull { it.toTransferItem() },
            containerIds = inner.childrenNamed("container").mapNotNull { it.attribute("id") },
            returned = returned,
            totalMatches = total
        )
    }

    /**
     * Reads the `X_CurrentContent_URL` block of the Digital Imaging document.
     *
     * Present only when the user selected a single image on the camera, in which case the URLs
     * are handed over directly and there is nothing to browse.
     */
    fun parseCurrentContent(digitalImagingXml: String): TransferItem? {
        val root = documentElement(digitalImagingXml)
        val renditions = root.descendants("X_CurrentContent_URL_URL").mapNotNull { element ->
            val url = element.textContent?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            Rendition(
                url = url,
                quality = qualityOfProfile(element.attribute("profileID")),
                sizeBytes = null,
                fileType = element.attribute("type")
            )
        }
        val best = renditions.maxByOrNull { it.quality.ordinal } ?: return null
        return best.toItem(title = best.fileNameFromUrl())
    }

    /**
     * Maps a `profileID` or `DLNA.ORG_PN` value to a quality.
     *
     * **A missing or unrecognised profile means the original.** That is the opposite of the
     * intuitive reading, and getting it wrong is the documented way to end up downloading
     * thumbnails: the full-size file usually carries no profile at all.
     */
    fun qualityOfProfile(profile: String?): ImageQuality {
        val value = profile?.trim()?.uppercase().orEmpty()
        return when {
            value.isEmpty() -> ImageQuality.ORIGINAL
            value == "UNKNOWN" || value.contains("ORIGINAL") -> ImageQuality.ORIGINAL
            value.endsWith("_TN") -> ImageQuality.THUMBNAIL
            value.endsWith("_SM") -> ImageQuality.SMALL
            value.endsWith("_LRG") -> ImageQuality.LARGE
            else -> ImageQuality.ORIGINAL
        }
    }

    /**
     * Picks the best rendition of one item.
     *
     * `sony.com_PN` wins over `DLNA.ORG_PN` where both are present, matching Sony's own client.
     */
    private fun Element.toTransferItem(): TransferItem? {
        val title = firstText("title") ?: attribute("id") ?: return null

        val renditions = childrenNamed("res").mapNotNull { res ->
            val url = res.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val protocolInfo = res.attribute("protocolInfo").orEmpty()
            Rendition(
                url = url,
                quality = qualityOfProfile(protocolInfo.profileId()),
                sizeBytes = res.attribute("size")?.toLongOrNull(),
                fileType = null
            )
        }

        val best = renditions.maxByOrNull { it.quality.ordinal } ?: return null
        return best.toItem(title)
    }

    /** Extracts `sony.com_PN=` or, failing that, `DLNA.ORG_PN=` from a protocolInfo string. */
    private fun String.profileId(): String? {
        val fields = split(';', ':').map { it.trim() }
        val sony = fields.firstOrNull { it.startsWith("sony.com_PN=", ignoreCase = true) }
        val dlna = fields.firstOrNull { it.startsWith("DLNA.ORG_PN=", ignoreCase = true) }
        return (sony ?: dlna)?.substringAfter('=')?.takeIf { it.isNotBlank() }
    }

    private class Rendition(
        val url: String,
        val quality: ImageQuality,
        val sizeBytes: Long?,
        val fileType: String?
    ) {
        fun fileNameFromUrl(): String = url
            .substringBefore('?')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?: "image"

        fun toItem(title: String) = TransferItem(
            title = title,
            fileName = fileName(title),
            url = url,
            quality = quality,
            sizeBytes = sizeBytes
        )

        /**
         * The camera serves a JPEG rendition even for a `.ARW` item — RAW is not retrievable over
         * this protocol at all — so the name follows what actually arrives rather than what the
         * card holds.
         */
        private fun fileName(title: String): String {
            val fromUrl = fileNameFromUrl()
            if (fromUrl.contains('.')) return fromUrl
            val extension = when (fileType?.uppercase()) {
                "MP4" -> "MP4"
                "HIF" -> "HIF"
                else -> "JPG"
            }
            return "${title.substringBeforeLast('.')}.$extension"
        }
    }

    private fun documentElement(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature(DISALLOW_DOCTYPE, true) }
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder()
            .parse(InputSource(StringReader(xml.trim())))
            .documentElement
    }

    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"

    private val Node.localTagName: String get() = nodeName.substringAfterLast(':')

    private fun Element.attribute(name: String): String? {
        if (hasAttribute(name)) return getAttribute(name).takeIf { it.isNotBlank() }
        // Namespace-unaware parsing leaves prefixes on attributes too.
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if (attribute.localTagName == name) {
                return attribute.nodeValue?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun Element.childrenNamed(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = firstChild
        while (child != null) {
            if (child is Element && child.localTagName == localName) result += child
            child = child.nextSibling
        }
        return result
    }

    private fun Element.descendants(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(node: Node) {
            var child = node.firstChild
            while (child != null) {
                if (child is Element) {
                    if (child.localTagName == localName) result += child
                    walk(child)
                }
                child = child.nextSibling
            }
        }
        walk(this)
        return result
    }

    private fun Element.firstText(localName: String): String? =
        descendants(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
