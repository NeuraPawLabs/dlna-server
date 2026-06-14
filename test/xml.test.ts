import { describe, expect, it } from "vitest";
import {
  buildAvTransportScpdXml,
  buildConnectionManagerScpdXml,
  buildDeviceDescriptionXml,
  buildRenderingControlScpdXml,
} from "../src/xml.js";

describe("UPnP XML builders", () => {
  it("builds a MediaRenderer device description with DMR services", () => {
    const xml = buildDeviceDescriptionXml({
      baseUrl: "http://192.168.1.20:49152",
      deviceName: "NewraPaw DLNA Receiver",
      manufacturer: "NewraPaw Labs",
      modelName: "Linux Prototype",
      uuid: "12345678-1234-1234-1234-123456789abc",
    });

    expect(xml).toContain("<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>");
    expect(xml).toContain("<friendlyName>NewraPaw DLNA Receiver</friendlyName>");
    expect(xml).toContain("<UDN>uuid:12345678-1234-1234-1234-123456789abc</UDN>");
    expect(xml).toContain("<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>");
    expect(xml).toContain("<controlURL>/upnp/control/AVTransport</controlURL>");
    expect(xml).toContain("<SCPDURL>/upnp/service/AVTransport.xml</SCPDURL>");
    expect(xml).toContain("<serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>");
    expect(xml).toContain("<serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>");
  });

  it("escapes XML text values in the device description", () => {
    const xml = buildDeviceDescriptionXml({
      baseUrl: "http://127.0.0.1:49152",
      deviceName: "Receiver & TV <Prototype>",
      manufacturer: "NewraPaw \"Labs\"",
      modelName: "Linux's Prototype",
      uuid: "abc",
    });

    expect(xml).toContain("<friendlyName>Receiver &amp; TV &lt;Prototype&gt;</friendlyName>");
    expect(xml).toContain("<manufacturer>NewraPaw &quot;Labs&quot;</manufacturer>");
    expect(xml).toContain("<modelName>Linux&apos;s Prototype</modelName>");
  });

  it("builds SCPD documents for the three renderer services", () => {
    expect(buildAvTransportScpdXml()).toContain("<name>SetAVTransportURI</name>");
    expect(buildAvTransportScpdXml()).toContain("<name>GetTransportInfo</name>");
    expect(buildRenderingControlScpdXml()).toContain("<name>SetVolume</name>");
    expect(buildRenderingControlScpdXml()).toContain("<name>GetMute</name>");
    expect(buildConnectionManagerScpdXml()).toContain("<name>GetProtocolInfo</name>");
  });
});
