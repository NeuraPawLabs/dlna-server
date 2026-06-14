import { escapeXml } from "./xml.js";

export type SoapAction = {
  serviceType: string;
  actionName: string;
  args: Record<string, string>;
};

export class SoapParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SoapParseError";
  }
}

export function parseSoapAction(soapActionHeader: string | undefined, body: string): SoapAction {
  const header = parseSoapActionHeader(soapActionHeader);
  const bodyAction = findBodyAction(body);

  return {
    serviceType: header?.serviceType ?? bodyAction.serviceType,
    actionName: header?.actionName ?? bodyAction.actionName,
    args: parseArguments(bodyAction.innerXml),
  };
}

export function buildSoapResponse(
  serviceType: string,
  actionName: string,
  values: Record<string, string | number | boolean>,
): string {
  const entries = Object.entries(values)
    .map(([name, value]) => `<${name}>${escapeXml(String(value))}</${name}>`)
    .join("");

  return soapEnvelope(
    `<u:${actionName}Response xmlns:u="${escapeXml(serviceType)}">${entries}</u:${actionName}Response>`,
  );
}

export function buildSoapFault(errorCode: number, description: string): string {
  return soapEnvelope(`\
<s:Fault>
  <faultcode>s:Client</faultcode>
  <faultstring>UPnPError</faultstring>
  <detail>
    <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
      <errorCode>${errorCode}</errorCode>
      <errorDescription>${escapeXml(description)}</errorDescription>
    </UPnPError>
  </detail>
</s:Fault>`);
}

function parseSoapActionHeader(header: string | undefined): Omit<SoapAction, "args"> | undefined {
  if (!header) {
    return undefined;
  }

  const match = header.trim().match(/^"?([^"#]+)#([^"]+)"?$/);
  if (!match) {
    return undefined;
  }

  return {
    serviceType: match[1],
    actionName: match[2],
  };
}

function findBodyAction(body: string): { actionName: string; serviceType: string; innerXml: string } {
  const bodyMatch = body.match(/<([\w.-]+:)?Body\b[^>]*>([\s\S]*?)<\/(?:[\w.-]+:)?Body>/i);
  const bodyXml = bodyMatch?.[2] ?? body;
  const actionMatch = bodyXml.match(
    /<(?:(?<prefix>[\w.-]+):)?(?<name>[\w.-]+)\b(?<attrs>[^>]*)>(?<inner>[\s\S]*?)<\/(?:(?:[\w.-]+):)?\k<name>>/i,
  );

  if (!actionMatch?.groups) {
    throw new SoapParseError("Could not find SOAP action body");
  }

  const serviceType = extractNamespace(actionMatch.groups.attrs, actionMatch.groups.prefix);
  if (!serviceType) {
    throw new SoapParseError("SOAP action is missing a service namespace");
  }

  return {
    actionName: actionMatch.groups.name,
    serviceType,
    innerXml: actionMatch.groups.inner,
  };
}

function extractNamespace(attrs: string, prefix: string | undefined): string | undefined {
  const namespaceName = prefix ? `xmlns:${prefix}` : "xmlns";
  const namespaceMatch = attrs.match(new RegExp(`${escapeRegex(namespaceName)}="([^"]+)"`));
  return namespaceMatch?.[1];
}

function parseArguments(innerXml: string): Record<string, string> {
  const args: Record<string, string> = {};
  const childPattern = /<(?:(?:[\w.-]+):)?(?<name>[\w.-]+)\b[^>]*>(?<value>[\s\S]*?)<\/(?:(?:[\w.-]+):)?\k<name>>/g;

  for (const match of innerXml.matchAll(childPattern)) {
    if (!match.groups) {
      continue;
    }
    args[match.groups.name] = unescapeXml(match.groups.value.trim());
  }

  return args;
}

function unescapeXml(value: string): string {
  return value
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", "\"")
    .replaceAll("&apos;", "'")
    .replaceAll("&amp;", "&");
}

function soapEnvelope(body: string): string {
  return `<?xml version="1.0" encoding="utf-8"?>\
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>${body}</s:Body>
</s:Envelope>`;
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
