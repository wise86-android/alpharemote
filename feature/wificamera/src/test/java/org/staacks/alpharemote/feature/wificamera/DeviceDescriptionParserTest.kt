package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.discovery.CameraProtocol
import org.staacks.alpharemote.feature.wificamera.data.discovery.DeviceDescription
import org.staacks.alpharemote.feature.wificamera.data.discovery.DeviceDescriptionParser

class DeviceDescriptionParserTest {

    /** Shaped after a real α6600 `dd.xml`, including the `av:` prefixes. */
    private val remoteShootingXml = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <friendlyName>ILCE-6600</friendlyName>
            <modelName>ILCE-6600</modelName>
            <UDN>uuid:00000000-0005-0010-8000-1c994c0d1234</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-sony-com:service:ScalarWebAPI:1</serviceType>
                <SCPDURL>/ScalarWebApi_scpd.xml</SCPDURL>
                <controlURL>/upnp/control/ScalarWebApi</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-sony-com:service:DigitalImaging:1</serviceType>
                <SCPDURL>/DigitalImagingDesc.xml</SCPDURL>
                <controlURL>/upnp/control/DigitalImaging</controlURL>
              </service>
            </serviceList>
            <av:X_ScalarWebAPI_DeviceInfo xmlns:av="urn:schemas-sony-com:av">
              <av:X_ScalarWebAPI_Version>1.0</av:X_ScalarWebAPI_Version>
              <av:X_ScalarWebAPI_ServiceList>
                <av:X_ScalarWebAPI_Service>
                  <av:X_ScalarWebAPI_ServiceType>camera</av:X_ScalarWebAPI_ServiceType>
                  <av:X_ScalarWebAPI_ActionList_URL>http://192.168.122.1:10000/sony</av:X_ScalarWebAPI_ActionList_URL>
                </av:X_ScalarWebAPI_Service>
                <av:X_ScalarWebAPI_Service>
                  <av:X_ScalarWebAPI_ServiceType>system</av:X_ScalarWebAPI_ServiceType>
                </av:X_ScalarWebAPI_Service>
              </av:X_ScalarWebAPI_ServiceList>
              <av:X_ScalarWebAPI_LiveView_URL>http://192.168.122.1:60152/liveviewstream</av:X_ScalarWebAPI_LiveView_URL>
            </av:X_ScalarWebAPI_DeviceInfo>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun `parses a remote shooting camera`() {
        val description = DeviceDescriptionParser.parse(remoteShootingXml)

        assertEquals("ILCE-6600", description.friendlyName)
        assertEquals("uuid:00000000-0005-0010-8000-1c994c0d1234", description.udn)
        assertEquals("http://192.168.122.1:10000/sony", description.actionListUrl)
        assertEquals(listOf("camera", "system"), description.scalarWebServiceTypes)
        assertEquals(CameraProtocol.LEGACY, description.protocol)
        assertTrue(description.supportsRemoteShooting)
    }

    @Test
    fun `finds a service by type and keeps its own urls`() {
        val service = DeviceDescriptionParser.parse(remoteShootingXml)
            .serviceOfType(DeviceDescription.DIGITAL_IMAGING_SERVICE_TYPE)

        assertEquals("/DigitalImagingDesc.xml", service?.scpdUrl)
        assertEquals("/upnp/control/DigitalImaging", service?.controlUrl)
    }

    /**
     * "Send to Smartphone" advertises transfer services and no `camera` service. There is no API
     * to switch it back, so this has to be distinguishable from an ordinary failure.
     */
    @Test
    fun `recognises a camera in transfer mode`() {
        val description = DeviceDescriptionParser.parse(
            """
            <?xml version="1.0"?>
            <root>
              <device>
                <friendlyName>ILCE-6600</friendlyName>
                <modelName>ILCE-6600</modelName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                    <controlURL>/upnp/control/ContentDirectory</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-sony-com:service:XPushList:1</serviceType>
                    <controlURL>/upnp/control/XPushList</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """.trimIndent()
        )

        assertEquals(CameraProtocol.LEGACY, description.protocol)
        assertFalse(description.supportsRemoteShooting)
    }

    @Test
    fun `reads the digital imaging document`() {
        val digitalImaging = DeviceDescriptionParser.parseDigitalImaging(
            """
            <?xml version="1.0"?>
            <scpd>
              <av:X_DigitalImagingDeviceInfo xmlns:av="urn:schemas-sony-com:av">
                <av:X_ServerType>Control with Smartphone</av:X_ServerType>
                <av:X_ServerVersion>2.0</av:X_ServerVersion>
              </av:X_DigitalImagingDeviceInfo>
            </scpd>
            """.trimIndent()
        )

        assertEquals("Control with Smartphone", digitalImaging.serverType)
        assertEquals("2.0", digitalImaging.serverVersion)
        assertFalse(digitalImaging.isPtp)
    }

    @Test
    fun `detects a ptp body`() {
        val digitalImaging = DeviceDescriptionParser.parseDigitalImaging(
            """
            <?xml version="1.0"?>
            <scpd><X_PTP_Versions>1.0</X_PTP_Versions></scpd>
            """.trimIndent()
        )

        assertTrue(digitalImaging.isPtp)
    }

    /** Concatenation, not URL resolution — the camera's paths only come out right this way. */
    @Test
    fun `resolves relative urls the way the official app does`() {
        assertEquals(
            "http://192.168.122.1:64321/DigitalImagingDesc.xml",
            DeviceDescriptionParser.resolve(
                "http://192.168.122.1:64321/dd.xml",
                "/DigitalImagingDesc.xml"
            )
        )
    }

    @Test
    fun `leaves absolute urls alone`() {
        assertEquals(
            "http://192.168.122.1:10000/sony",
            DeviceDescriptionParser.resolve(
                "http://192.168.122.1:64321/dd.xml",
                "http://192.168.122.1:10000/sony"
            )
        )
    }
}
