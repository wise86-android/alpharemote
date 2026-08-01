package org.staacks.alpharemote.feature.wificamera.data.discovery

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * The camera's UPnP device description, reduced to what this module uses.
 */
data class DeviceDescription(
    val friendlyName: String,
    val modelName: String,
    val udn: String?,
    val services: List<UpnpService>,
    val scalarWebServiceTypes: List<String>,
    /** Base URL for JSON-RPC. Endpoints are this plus `/camera`, `/system`, … */
    val actionListUrl: String?,
    val liveViewUrl: String?,
    val defaultFunction: String?
) {
    fun serviceOfType(type: String): UpnpService? =
        services.firstOrNull { it.serviceType.equals(type, ignoreCase = true) }

    /**
     * Which of the two Sony worlds this camera speaks.
     *
     * Decided from the advertised services, never from a model list: the same body reports
     * different services depending on the function selected on it (PROTOCOL.md §0 and §1.1).
     */
    val protocol: CameraProtocol
        get() = when {
            scalarWebServiceTypes.isNotEmpty() -> CameraProtocol.LEGACY
            services.any { it.serviceType.contains(SCALAR_WEB_API, ignoreCase = true) } ->
                CameraProtocol.LEGACY
            services.any { it.serviceType.contains(CONTENT_DIRECTORY, ignoreCase = true) } ->
                CameraProtocol.LEGACY
            services.any { it.serviceType.contains(X_PUSH_LIST, ignoreCase = true) } ->
                CameraProtocol.LEGACY
            else -> CameraProtocol.UNKNOWN
        }

    /** True when this camera is in a mode that accepts remote shooting commands. */
    val supportsRemoteShooting: Boolean
        get() = scalarWebServiceTypes.any { it.equals(CAMERA_SERVICE, ignoreCase = true) }

    /**
     * True when the camera is offering images to pull — "Send to Smartphone".
     *
     * Both services are required, and for different jobs: `XPushList` carries the session
     * actions and `ContentDirectory` the browsing. A camera advertising only one of them cannot
     * complete a transfer.
     */
    val supportsPushTransfer: Boolean
        get() = services.any { it.serviceType.contains(X_PUSH_LIST, ignoreCase = true) } &&
            services.any { it.serviceType.contains(CONTENT_DIRECTORY, ignoreCase = true) }

    companion object {
        const val CAMERA_SERVICE = "camera"
        const val DIGITAL_IMAGING_SERVICE_TYPE = "urn:schemas-sony-com:service:DigitalImaging:1"
        private const val SCALAR_WEB_API = "ScalarWebAPI"
        private const val CONTENT_DIRECTORY = "ContentDirectory"
        private const val X_PUSH_LIST = "XPushList"
    }
}

data class UpnpService(
    val serviceType: String,
    val controlUrl: String?,
    val scpdUrl: String?
)

enum class CameraProtocol {
    /** HTTP + JSON-RPC. What this module implements. */
    LEGACY,

    /** PTP/IP on port 15740. A different transport entirely. */
    MODERN,
    UNKNOWN
}

/**
 * The separate Digital Imaging document, which is where the camera says what mode it is in.
 */
data class DigitalImagingDescription(
    val serverType: String?,
    val serverVersion: String?,
    val ptpVersions: String?
) {
    val isPtp: Boolean get() = !ptpVersions.isNullOrBlank()
}

/**
 * Parses Sony's description XML.
 *
 * Namespace-unaware on purpose: the Sony extensions arrive under an `av:` prefix that differs
 * between bodies, so elements are matched on their local name and the prefix is discarded.
 */
object DeviceDescriptionParser {

    fun parse(xml: String): DeviceDescription {
        val root = documentElement(xml)

        val services = root.descendants("service").map { service ->
            UpnpService(
                serviceType = service.childText("serviceType").orEmpty(),
                controlUrl = service.childText("controlURL"),
                scpdUrl = service.childText("SCPDURL")
            )
        }

        return DeviceDescription(
            friendlyName = root.firstText("friendlyName").orEmpty(),
            modelName = root.firstText("modelName").orEmpty(),
            udn = root.firstText("UDN"),
            services = services,
            scalarWebServiceTypes = root.descendants("X_ScalarWebAPI_ServiceType")
                .mapNotNull { it.textContent?.trim() }
                .filter { it.isNotEmpty() },
            actionListUrl = root.firstText("X_ScalarWebAPI_ActionList_URL")?.trimEnd('/'),
            liveViewUrl = root.firstText("X_ScalarWebAPI_LiveView_URL"),
            defaultFunction = root.firstText("X_ScalarWebAPI_DefaultFunction")
        )
    }

    fun parseDigitalImaging(xml: String): DigitalImagingDescription {
        val root = documentElement(xml)
        return DigitalImagingDescription(
            serverType = root.firstText("X_ServerType"),
            serverVersion = root.firstText("X_ServerVersion"),
            ptpVersions = root.firstText("X_PTP_Versions")
        )
    }

    /**
     * Resolves a relative URL from the description against the description's own URL.
     *
     * Deliberately string concatenation and not proper URL resolution: the official app does it
     * this way, and the camera emits paths that only come out right under this rule.
     */
    fun resolve(descriptionUrl: String, relative: String): String {
        if (relative.startsWith("http://", ignoreCase = true) ||
            relative.startsWith("https://", ignoreCase = true)
        ) {
            return relative
        }
        val cut = descriptionUrl.lastIndexOf('/')
        if (cut < 0) return relative
        return descriptionUrl.substring(0, cut) + relative
    }

    private fun documentElement(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // The description comes off an unauthenticated access point. Nothing in it should be
            // able to make the parser open a file or a socket.
            runCatching { setFeature(DISALLOW_DOCTYPE, true) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trim())))
        return document.documentElement
    }

    private const val DISALLOW_DOCTYPE =
        "http://apache.org/xml/features/disallow-doctype-decl"

    /** Local name of a possibly prefixed tag: `av:X_ServerType` -> `X_ServerType`. */
    private val Node.localTagName: String get() = nodeName.substringAfterLast(':')

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
        if (localTagName == localName) result += this
        walk(this)
        return result
    }

    private fun Element.firstText(localName: String): String? =
        descendants(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Direct children only, so a nested `service` cannot steal its parent's fields. */
    private fun Element.childText(localName: String): String? {
        var child = firstChild
        while (child != null) {
            if (child is Element && child.localTagName == localName) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
            child = child.nextSibling
        }
        return null
    }
}
