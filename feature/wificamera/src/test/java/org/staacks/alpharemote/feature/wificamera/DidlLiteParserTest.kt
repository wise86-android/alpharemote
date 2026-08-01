package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.transfer.DidlLiteParser
import org.staacks.alpharemote.feature.wificamera.domain.ImageQuality

class DidlLiteParserTest {

    private fun browseResponse(didl: String, returned: Int, total: Int) = """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
              <Result>${didl.escapeForXml()}</Result>
              <NumberReturned>$returned</NumberReturned>
              <TotalMatches>$total</TotalMatches>
              <UpdateID>1</UpdateID>
            </u:BrowseResponse>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    private fun String.escapeForXml() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * The critical case. The full-size original carries no PN at all, and treating that absence
     * as "unknown, use the thumbnail" is the documented way to silently download postage stamps.
     */
    @Test
    fun `prefers the original that carries no profile at all`() {
        val didl = """
            <DIDL-Lite>
              <item id="1">
                <dc:title>DSC00042</dc:title>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN" size="12000">http://cam/tn.JPG</res>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG" size="900000">http://cam/lrg.JPG</res>
                <res protocolInfo="http-get:*:image/jpeg:*" size="17465344">http://cam/DSC00042.JPG</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val result = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 1, 1))

        assertEquals(1, result.items.size)
        val item = result.items.single()
        assertEquals("http://cam/DSC00042.JPG", item.url)
        assertEquals(ImageQuality.ORIGINAL, item.quality)
        assertEquals(17_465_344L, item.sizeBytes)
        assertEquals("DSC00042.JPG", item.fileName)
    }

    @Test
    fun `falls back to the largest labelled rendition when there is no original`() {
        val didl = """
            <DIDL-Lite>
              <item id="1">
                <dc:title>DSC00043</dc:title>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN">http://cam/tn.JPG</res>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM">http://cam/sm.JPG</res>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG">http://cam/lrg.JPG</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val item = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 1, 1)).items.single()

        assertEquals("http://cam/lrg.JPG", item.url)
        assertEquals(ImageQuality.LARGE, item.quality)
    }

    /** An item offering nothing but a thumbnail is below the full-quality floor. */
    @Test
    fun `marks a thumbnail-only item as such`() {
        val didl = """
            <DIDL-Lite>
              <item id="1">
                <dc:title>DSC00044</dc:title>
                <res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN">http://cam/tn.JPG</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val item = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 1, 1)).items.single()

        assertEquals(ImageQuality.THUMBNAIL, item.quality)
        assertTrue(item.quality < ImageQuality.FULL_QUALITY_FLOOR)
    }

    @Test
    fun `sony profiles win over dlna ones`() {
        val didl = """
            <DIDL-Lite>
              <item id="1">
                <dc:title>DSC00045</dc:title>
                <res protocolInfo="http-get:*:image/heif:sony.com_PN=HEIF_LRG;DLNA.ORG_PN=JPEG_TN">http://cam/x.HIF</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val item = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 1, 1)).items.single()

        assertEquals(ImageQuality.LARGE, item.quality)
    }

    /** PushRoot may hold date folders rather than items; a listing that ignores them finds nothing. */
    @Test
    fun `reports child containers so they can be descended into`() {
        val didl = """
            <DIDL-Lite>
              <container id="2025-08-01" childCount="12"><dc:title>1 Aug</dc:title></container>
              <container id="2025-07-31" childCount="3"><dc:title>31 Jul</dc:title></container>
            </DIDL-Lite>
        """.trimIndent()

        val result = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 2, 2))

        assertEquals(listOf("2025-08-01", "2025-07-31"), result.containerIds)
        assertTrue(result.items.isEmpty())
    }

    /** Paging is driven by these two; without them a 91-image selection silently loses 41. */
    @Test
    fun `reads the paging counters`() {
        val didl = "<DIDL-Lite></DIDL-Lite>"

        val result = DidlLiteParser.parseBrowseResponse(browseResponse(didl, 50, 91))

        assertEquals(50, result.returned)
        assertEquals(91, result.totalMatches)
    }

    @Test
    fun `reads the single-selection urls from the digital imaging document`() {
        val item = DidlLiteParser.parseCurrentContent(
            """
            <?xml version="1.0"?>
            <scpd>
              <X_CurrentContent_URL>
                <X_CurrentContent_URL_URL profileID="JPEG_TN" type="JPG">http://cam/tn.JPG</X_CurrentContent_URL_URL>
                <X_CurrentContent_URL_URL profileID="JPEG_LRG" type="JPG">http://cam/lrg.JPG</X_CurrentContent_URL_URL>
                <X_CurrentContent_URL_URL profileID="UNKNOWN" type="JPG">http://cam/DSC00050.JPG</X_CurrentContent_URL_URL>
              </X_CurrentContent_URL>
            </scpd>
            """.trimIndent()
        )

        assertNotNull(item)
        assertEquals("http://cam/DSC00050.JPG", item!!.url)
        assertEquals(ImageQuality.ORIGINAL, item.quality)
    }

    @Test
    fun `treats a missing profile as the original`() {
        assertEquals(ImageQuality.ORIGINAL, DidlLiteParser.qualityOfProfile(null))
        assertEquals(ImageQuality.ORIGINAL, DidlLiteParser.qualityOfProfile(""))
        assertEquals(ImageQuality.ORIGINAL, DidlLiteParser.qualityOfProfile("UNKNOWN"))
        assertEquals(ImageQuality.ORIGINAL, DidlLiteParser.qualityOfProfile("PN_ORIGINAL"))
        assertEquals(ImageQuality.THUMBNAIL, DidlLiteParser.qualityOfProfile("JPEG_TN"))
        assertEquals(ImageQuality.SMALL, DidlLiteParser.qualityOfProfile("JPEG_SM"))
        assertEquals(ImageQuality.LARGE, DidlLiteParser.qualityOfProfile("HEIF_LRG"))
    }
}
