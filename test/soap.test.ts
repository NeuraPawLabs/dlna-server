import { describe, expect, it } from "vitest";
import { buildSoapFault, buildSoapResponse, parseSoapAction } from "../src/soap.js";

describe("SOAP helpers", () => {
  it("parses SOAPAction header and action arguments", () => {
    const request = `<?xml version="1.0"?>
      <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
        <s:Body>
          <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <CurrentURI>http://media.example/video.mp4?x=1&amp;y=2</CurrentURI>
            <CurrentURIMetaData></CurrentURIMetaData>
          </u:SetAVTransportURI>
        </s:Body>
      </s:Envelope>`;

    const action = parseSoapAction(
      '"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"',
      request,
    );

    expect(action).toEqual({
      serviceType: "urn:schemas-upnp-org:service:AVTransport:1",
      actionName: "SetAVTransportURI",
      args: {
        InstanceID: "0",
        CurrentURI: "http://media.example/video.mp4?x=1&y=2",
        CurrentURIMetaData: "",
      },
    });
  });

  it("falls back to the body action name when the header is unquoted", () => {
    const request = `<s:Envelope><s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><Speed>1</Speed></u:Play></s:Body></s:Envelope>`;

    const action = parseSoapAction("urn:schemas-upnp-org:service:AVTransport:1#Play", request);

    expect(action.actionName).toBe("Play");
    expect(action.args.Speed).toBe("1");
  });

  it("builds SOAP response and fault envelopes", () => {
    const response = buildSoapResponse(
      "urn:schemas-upnp-org:service:RenderingControl:1",
      "GetVolume",
      { CurrentVolume: "32" },
    );
    const fault = buildSoapFault(701, "No current URI");

    expect(response).toContain("<u:GetVolumeResponse xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">");
    expect(response).toContain("<CurrentVolume>32</CurrentVolume>");
    expect(fault).toContain("<errorCode>701</errorCode>");
    expect(fault).toContain("<errorDescription>No current URI</errorDescription>");
  });
});
