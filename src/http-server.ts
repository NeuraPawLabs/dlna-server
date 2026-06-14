import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AppConfig } from "./config.js";
import {
  decodeProxyUrl,
  resolvePlayableUri,
  rewriteHlsManifest,
  stripPngWrapperFromSegment,
} from "./hls-proxy.js";
import type { PlayerAdapter } from "./player.js";
import type { RendererState } from "./renderer-state.js";
import { buildSoapFault, buildSoapResponse, parseSoapAction, SoapParseError } from "./soap.js";
import {
  buildAvTransportScpdXml,
  buildConnectionManagerScpdXml,
  buildDeviceDescriptionXml,
  buildRenderingControlScpdXml,
} from "./xml.js";

export type ControlResponse = {
  statusCode: number;
  contentType: string;
  body: string;
};

export type Logger = {
  info(message: string): void;
};

export type PlaybackUrlResolver = (uri: string) => string;

const serviceTypes: Record<string, string> = {
  AVTransport: "urn:schemas-upnp-org:service:AVTransport:1",
  RenderingControl: "urn:schemas-upnp-org:service:RenderingControl:1",
  ConnectionManager: "urn:schemas-upnp-org:service:ConnectionManager:1",
};

export function createDlnaHttpServer(
  config: AppConfig,
  state: RendererState,
  player: PlayerAdapter,
): Server {
  return createServer(async (request, response) => {
    try {
      await routeRequest(config, state, player, request, response);
    } catch (error) {
      console.error(error);
      send(response, 500, "text/plain; charset=utf-8", "Internal Server Error");
    }
  });
}

export async function handleControlRequest(
  serviceName: string,
  soapActionHeader: string | undefined,
  body: string,
  state: RendererState,
  player: PlayerAdapter,
  logger: Logger = console,
  playbackUrlResolver: PlaybackUrlResolver = (uri) => uri,
): Promise<ControlResponse> {
  try {
    const action = parseSoapAction(soapActionHeader, body);
    const result = await executeAction(
      serviceName,
      action.actionName,
      action.args,
      state,
      player,
      logger,
      playbackUrlResolver,
    );
    return {
      statusCode: 200,
      contentType: "text/xml; charset=utf-8",
      body: buildSoapResponse(serviceTypes[serviceName] ?? action.serviceType, action.actionName, result),
    };
  } catch (error) {
    if (error instanceof SoapParseError) {
      return {
        statusCode: 400,
        contentType: "text/xml; charset=utf-8",
        body: buildSoapFault(402, error.message),
      };
    }

    return {
      statusCode: 500,
      contentType: "text/xml; charset=utf-8",
      body: buildSoapFault(701, error instanceof Error ? error.message : "Action failed"),
    };
  }
}

async function executeAction(
  serviceName: string,
  actionName: string,
  args: Record<string, string>,
  state: RendererState,
  player: PlayerAdapter,
  logger: Logger,
  playbackUrlResolver: PlaybackUrlResolver,
): Promise<Record<string, string | number>> {
  if (serviceName === "AVTransport") {
    return executeAvTransport(actionName, args, state, player, logger, playbackUrlResolver);
  }
  if (serviceName === "RenderingControl") {
    return executeRenderingControl(actionName, args, state, player);
  }
  if (serviceName === "ConnectionManager") {
    return executeConnectionManager(actionName);
  }
  throw new Error(`Unsupported service ${serviceName}`);
}

async function executeAvTransport(
  actionName: string,
  args: Record<string, string>,
  state: RendererState,
  player: PlayerAdapter,
  logger: Logger,
  playbackUrlResolver: PlaybackUrlResolver,
): Promise<Record<string, string | number>> {
  const snapshot = state.snapshot();

  switch (actionName) {
    case "SetAVTransportURI":
      state.setCurrentUri(args.CurrentURI ?? "", args.CurrentURIMetaData ?? "");
      logger.info(`[AVTransport] Set URI: ${args.CurrentURI ?? ""}`);
      return {};
    case "GetMediaInfo":
      return {
        NrTracks: 1,
        MediaDuration: "00:00:00",
        CurrentURI: snapshot.currentUri,
        CurrentURIMetaData: snapshot.currentUriMetadata,
        NextURI: "",
        NextURIMetaData: "",
        PlayMedium: "NETWORK",
        RecordMedium: "NOT_IMPLEMENTED",
        WriteStatus: "NOT_IMPLEMENTED",
      };
    case "GetTransportInfo":
      return {
        CurrentTransportState: snapshot.transportState,
        CurrentTransportStatus: snapshot.transportStatus,
        CurrentSpeed: "1",
      };
    case "GetPositionInfo":
      return {
        Track: 1,
        TrackDuration: "00:00:00",
        TrackMetaData: snapshot.currentUriMetadata,
        TrackURI: snapshot.currentUri,
        RelTime: snapshot.relativeTimePosition,
        AbsTime: snapshot.relativeTimePosition,
        RelCount: 0,
        AbsCount: 0,
      };
    case "Play": {
      const current = state.snapshot();
      if (!current.currentUri) {
        throw new Error("No current URI");
      }
      if (current.transportState === "PAUSED_PLAYBACK") {
        await player.resume();
      } else {
        await player.play(playbackUrlResolver(current.currentUri));
      }
      logger.info(`[AVTransport] Play: ${current.currentUri}`);
      state.play();
      return {};
    }
    case "Pause":
      await player.pause();
      state.pause();
      return {};
    case "Stop":
      await player.stop();
      state.stop();
      return {};
    case "Seek":
      state.seek(args.Target ?? "00:00:00");
      await player.seek(args.Target ?? "00:00:00");
      return {};
    default:
      throw new Error(`Unsupported AVTransport action ${actionName}`);
  }
}

async function executeRenderingControl(
  actionName: string,
  args: Record<string, string>,
  state: RendererState,
  player: PlayerAdapter,
): Promise<Record<string, string | number>> {
  switch (actionName) {
    case "GetVolume":
      return { CurrentVolume: state.snapshot().volume };
    case "SetVolume": {
      const volume = Number(args.DesiredVolume ?? 0);
      state.setVolume(volume);
      await player.setVolume(state.snapshot().volume);
      return {};
    }
    case "GetMute":
      return { CurrentMute: state.snapshot().muted ? 1 : 0 };
    case "SetMute": {
      const muted = args.DesiredMute === "1" || args.DesiredMute?.toLowerCase() === "true";
      state.setMuted(muted);
      await player.setMuted(muted);
      return {};
    }
    default:
      throw new Error(`Unsupported RenderingControl action ${actionName}`);
  }
}

function executeConnectionManager(actionName: string): Record<string, string | number> {
  switch (actionName) {
    case "GetProtocolInfo":
      return {
        Source: "",
        Sink: [
          "http-get:*:video/mp4:*",
          "http-get:*:video/x-matroska:*",
          "http-get:*:audio/mpeg:*",
          "http-get:*:audio/flac:*",
          "http-get:*:image/jpeg:*",
        ].join(","),
      };
    case "GetCurrentConnectionIDs":
      return { ConnectionIDs: "0" };
    case "GetCurrentConnectionInfo":
      return {
        RcsID: 0,
        AVTransportID: 0,
        ProtocolInfo: "",
        PeerConnectionManager: "",
        PeerConnectionID: -1,
        Direction: "Input",
        Status: "OK",
      };
    default:
      throw new Error(`Unsupported ConnectionManager action ${actionName}`);
  }
}

async function routeRequest(
  config: AppConfig,
  state: RendererState,
  player: PlayerAdapter,
  request: IncomingMessage,
  response: ServerResponse,
): Promise<void> {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);

  if (request.method === "GET" && url.pathname === "/description.xml") {
    send(response, 200, "text/xml; charset=utf-8", buildDeviceDescriptionXml({
      baseUrl: publicBaseUrl(config),
      deviceName: config.deviceName,
      manufacturer: config.manufacturer,
      modelName: config.modelName,
      uuid: config.uuid,
    }));
    return;
  }

  if (request.method === "GET" && url.pathname === "/upnp/service/AVTransport.xml") {
    send(response, 200, "text/xml; charset=utf-8", buildAvTransportScpdXml());
    return;
  }
  if (request.method === "GET" && url.pathname === "/upnp/service/RenderingControl.xml") {
    send(response, 200, "text/xml; charset=utf-8", buildRenderingControlScpdXml());
    return;
  }
  if (request.method === "GET" && url.pathname === "/upnp/service/ConnectionManager.xml") {
    send(response, 200, "text/xml; charset=utf-8", buildConnectionManagerScpdXml());
    return;
  }

  const controlMatch = url.pathname.match(/^\/upnp\/control\/(\w+)$/);
  if (request.method === "POST" && controlMatch) {
    const controlResponse = await handleControlRequest(
      controlMatch[1],
      firstHeaderValue(request.headers.soapaction),
      await readBody(request),
      state,
      player,
      console,
      (uri) => resolvePlayableUri(uri, localBaseUrl(config)),
    );
    send(response, controlResponse.statusCode, controlResponse.contentType, controlResponse.body);
    return;
  }

  if (request.method === "GET" && url.pathname === "/proxy/hls.m3u8") {
    await proxyManifest(url, config, response);
    return;
  }

  if (request.method === "GET" && url.pathname === "/proxy/segment.ts") {
    await proxySegment(url, response);
    return;
  }

  send(response, 404, "text/plain; charset=utf-8", "Not Found");
}

function send(response: ServerResponse, statusCode: number, contentType: string, body: string | Buffer): void {
  response.writeHead(statusCode, {
    "content-type": contentType,
    "content-length": Buffer.byteLength(body),
    "server": "NewraPawDLNA/0.1 UPnP/1.0 Node.js",
  });
  response.end(body);
}

function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];

    request.on("data", (chunk: Buffer) => chunks.push(chunk));
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function firstHeaderValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export function publicBaseUrl(config: AppConfig): string {
  const address = config.advertiseAddress ?? (config.host === "0.0.0.0" ? "127.0.0.1" : config.host);
  return `http://${address}:${config.port}`;
}

function localBaseUrl(config: AppConfig): string {
  return `http://127.0.0.1:${config.port}`;
}

async function proxyManifest(url: URL, config: AppConfig, response: ServerResponse): Promise<void> {
  const upstreamUrl = getProxyUrl(url);
  if (!upstreamUrl) {
    send(response, 400, "text/plain; charset=utf-8", "Missing url");
    return;
  }

  const upstream = await fetch(upstreamUrl);
  if (!upstream.ok) {
    send(response, upstream.status, "text/plain; charset=utf-8", `Upstream manifest failed: ${upstream.status}`);
    return;
  }

  const manifest = await upstream.text();
  send(response, 200, "application/vnd.apple.mpegurl; charset=utf-8", rewriteHlsManifest(
    manifest,
    upstreamUrl,
    localBaseUrl(config),
  ));
}

async function proxySegment(url: URL, response: ServerResponse): Promise<void> {
  const upstreamUrl = getProxyUrl(url);
  if (!upstreamUrl) {
    send(response, 400, "text/plain; charset=utf-8", "Missing url");
    return;
  }

  const upstream = await fetch(upstreamUrl);
  if (!upstream.ok) {
    send(response, upstream.status, "text/plain; charset=utf-8", `Upstream segment failed: ${upstream.status}`);
    return;
  }

  const segment = Buffer.from(await upstream.arrayBuffer());
  const stripped = stripPngWrapperFromSegment(segment);
  send(response, 200, "video/mp2t", stripped);
}

function getProxyUrl(url: URL): string | undefined {
  const encoded = url.searchParams.get("u");
  if (encoded) {
    return decodeProxyUrl(encoded);
  }

  return url.searchParams.get("url") ?? undefined;
}
