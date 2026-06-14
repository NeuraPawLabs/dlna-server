import { describe, expect, it } from "vitest";
import { handleControlRequest } from "../src/http-server.js";
import { RendererState } from "../src/renderer-state.js";
import type { PlayerAdapter } from "../src/player.js";

class FakePlayer implements PlayerAdapter {
  calls: string[] = [];

  async play(uri: string): Promise<void> {
    this.calls.push(`play:${uri}`);
  }

  async resume(): Promise<void> {
    this.calls.push("resume");
  }

  async pause(): Promise<void> {
    this.calls.push("pause");
  }

  async stop(): Promise<void> {
    this.calls.push("stop");
  }

  async seek(target: string): Promise<void> {
    this.calls.push(`seek:${target}`);
  }

  async setVolume(volume: number): Promise<void> {
    this.calls.push(`volume:${volume}`);
  }

  async setMuted(muted: boolean): Promise<void> {
    this.calls.push(`muted:${muted}`);
  }

  async close(): Promise<void> {
    this.calls.push("close");
  }
}

describe("HTTP SOAP control handling", () => {
  it("sets a URI and starts playback through the player", async () => {
    const state = new RendererState();
    const player = new FakePlayer();
    const logs: string[] = [];

    const setUri = await handleControlRequest(
      "AVTransport",
      '"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"',
      envelope("SetAVTransportURI", "AVTransport", {
        InstanceID: "0",
        CurrentURI: "http://media.example/video.mp4",
        CurrentURIMetaData: "",
      }),
      state,
      player,
      { info: (message) => logs.push(message) },
    );
    const play = await handleControlRequest(
      "AVTransport",
      '"urn:schemas-upnp-org:service:AVTransport:1#Play"',
      envelope("Play", "AVTransport", { InstanceID: "0", Speed: "1" }),
      state,
      player,
      { info: (message) => logs.push(message) },
    );

    expect(setUri.statusCode).toBe(200);
    expect(play.statusCode).toBe(200);
    expect(player.calls).toEqual(["play:http://media.example/video.mp4"]);
    expect(logs).toEqual([
      "[AVTransport] Set URI: http://media.example/video.mp4",
      "[AVTransport] Play: http://media.example/video.mp4",
    ]);
    expect(state.snapshot().transportState).toBe("PLAYING");
  });

  it("plays likely HLS URLs through a local proxy URL", async () => {
    const state = new RendererState();
    const player = new FakePlayer();

    await handleControlRequest(
      "AVTransport",
      '"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"',
      envelope("SetAVTransportURI", "AVTransport", {
        InstanceID: "0",
        CurrentURI: "https://origin.example/object?filename=video.m3u8&token=abc",
        CurrentURIMetaData: "",
      }),
      state,
      player,
    );
    await handleControlRequest(
      "AVTransport",
      '"urn:schemas-upnp-org:service:AVTransport:1#Play"',
      envelope("Play", "AVTransport", { InstanceID: "0", Speed: "1" }),
      state,
      player,
      undefined,
      (uri) => `http://127.0.0.1:49152/proxy/hls.m3u8?url=${encodeURIComponent(uri)}`,
    );

    expect(player.calls).toEqual([
      "play:http://127.0.0.1:49152/proxy/hls.m3u8?url=https%3A%2F%2Forigin.example%2Fobject%3Ffilename%3Dvideo.m3u8%26token%3Dabc",
    ]);
  });

  it("returns a SOAP fault when Play has no current URI", async () => {
    const response = await handleControlRequest(
      "AVTransport",
      '"urn:schemas-upnp-org:service:AVTransport:1#Play"',
      envelope("Play", "AVTransport", { InstanceID: "0", Speed: "1" }),
      new RendererState(),
      new FakePlayer(),
    );

    expect(response.statusCode).toBe(500);
    expect(response.body).toContain("<errorCode>701</errorCode>");
    expect(response.body).toContain("No current URI");
  });

  it("routes rendering control volume and mute actions", async () => {
    const state = new RendererState();
    const player = new FakePlayer();

    const volume = await handleControlRequest(
      "RenderingControl",
      '"urn:schemas-upnp-org:service:RenderingControl:1#SetVolume"',
      envelope("SetVolume", "RenderingControl", {
        InstanceID: "0",
        Channel: "Master",
        DesiredVolume: "72",
      }),
      state,
      player,
    );
    const mute = await handleControlRequest(
      "RenderingControl",
      '"urn:schemas-upnp-org:service:RenderingControl:1#SetMute"',
      envelope("SetMute", "RenderingControl", {
        InstanceID: "0",
        Channel: "Master",
        DesiredMute: "1",
      }),
      state,
      player,
    );

    expect(volume.statusCode).toBe(200);
    expect(mute.statusCode).toBe(200);
    expect(player.calls).toEqual(["volume:72", "muted:true"]);
    expect(state.snapshot()).toMatchObject({ volume: 72, muted: true });
  });
});

function envelope(action: string, service: string, args: Record<string, string>): string {
  const serviceType = `urn:schemas-upnp-org:service:${service}:1`;
  const entries = Object.entries(args)
    .map(([name, value]) => `<${name}>${value}</${name}>`)
    .join("");

  return `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:${action} xmlns:u="${serviceType}">${entries}</u:${action}></s:Body></s:Envelope>`;
}
