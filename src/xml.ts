export type DeviceDescriptionOptions = {
  baseUrl: string;
  deviceName: string;
  manufacturer: string;
  modelName: string;
  uuid: string;
};

type ServiceDescription = {
  serviceType: string;
  serviceId: string;
  controlPath: string;
  eventPath: string;
  scpdPath: string;
};

const services: ServiceDescription[] = [
  {
    serviceType: "urn:schemas-upnp-org:service:AVTransport:1",
    serviceId: "urn:upnp-org:serviceId:AVTransport",
    controlPath: "/upnp/control/AVTransport",
    eventPath: "/upnp/event/AVTransport",
    scpdPath: "/upnp/service/AVTransport.xml",
  },
  {
    serviceType: "urn:schemas-upnp-org:service:RenderingControl:1",
    serviceId: "urn:upnp-org:serviceId:RenderingControl",
    controlPath: "/upnp/control/RenderingControl",
    eventPath: "/upnp/event/RenderingControl",
    scpdPath: "/upnp/service/RenderingControl.xml",
  },
  {
    serviceType: "urn:schemas-upnp-org:service:ConnectionManager:1",
    serviceId: "urn:upnp-org:serviceId:ConnectionManager",
    controlPath: "/upnp/control/ConnectionManager",
    eventPath: "/upnp/event/ConnectionManager",
    scpdPath: "/upnp/service/ConnectionManager.xml",
  },
];

export function escapeXml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&apos;");
}

export function buildDeviceDescriptionXml(options: DeviceDescriptionOptions): string {
  const serviceList = services.map(buildServiceXml).join("");

  return xmlDocument(`\
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <URLBase>${escapeXml(options.baseUrl)}</URLBase>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>${escapeXml(options.deviceName)}</friendlyName>
    <manufacturer>${escapeXml(options.manufacturer)}</manufacturer>
    <modelName>${escapeXml(options.modelName)}</modelName>
    <UDN>uuid:${escapeXml(options.uuid)}</UDN>
    <serviceList>${serviceList}</serviceList>
  </device>
</root>`);
}

export function buildAvTransportScpdXml(): string {
  return buildScpdXml([
    "SetAVTransportURI",
    "GetMediaInfo",
    "GetTransportInfo",
    "GetPositionInfo",
    "Play",
    "Pause",
    "Stop",
    "Seek",
  ]);
}

export function buildRenderingControlScpdXml(): string {
  return buildScpdXml(["GetVolume", "SetVolume", "GetMute", "SetMute"]);
}

export function buildConnectionManagerScpdXml(): string {
  return buildScpdXml([
    "GetProtocolInfo",
    "GetCurrentConnectionIDs",
    "GetCurrentConnectionInfo",
  ]);
}

function buildServiceXml(service: ServiceDescription): string {
  return `\
<service>
  <serviceType>${service.serviceType}</serviceType>
  <serviceId>${service.serviceId}</serviceId>
  <SCPDURL>${service.scpdPath}</SCPDURL>
  <controlURL>${service.controlPath}</controlURL>
  <eventSubURL>${service.eventPath}</eventSubURL>
</service>`;
}

function buildScpdXml(actionNames: string[]): string {
  const actions = actionNames.map((name) => `<action><name>${name}</name></action>`).join("");

  return xmlDocument(`\
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <actionList>${actions}</actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_InstanceID</name>
      <dataType>ui4</dataType>
    </stateVariable>
  </serviceStateTable>
</scpd>`);
}

function xmlDocument(body: string): string {
  return `<?xml version="1.0" encoding="utf-8"?>\n${body}`;
}
