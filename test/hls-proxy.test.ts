import { describe, expect, it } from "vitest";
import {
  decodeProxyUrl,
  encodeProxyUrl,
  resolvePlayableUri,
  rewriteHlsManifest,
  stripPngWrapperFromSegment,
} from "../src/hls-proxy.js";

describe("HLS proxy helpers", () => {
  it("rewrites HLS segment URLs through the local segment proxy", () => {
    const manifest = `#EXTM3U
#EXT-X-VERSION:3
#EXTINF:6.0,
https://cdn.example/path/seg0.png
#EXTINF:6.0,
relative/seg1.png
#EXT-X-ENDLIST
`;

    const rewritten = rewriteHlsManifest(
      manifest,
      "https://origin.example/video/index.m3u8?token=abc",
      "http://127.0.0.1:49152",
    );

    expect(rewritten).toContain("#EXTM3U");
    expect(rewritten).toContain("http://127.0.0.1:49152/proxy/segment.ts?u=");
    expect(rewritten).not.toContain(".png");
    expect(rewritten).not.toContain("cdn.example");
  });

  it("maps likely HLS URLs to the local manifest proxy", () => {
    const playable = resolvePlayableUri(
      "https://origin.example/object?filename=video.m3u8&token=abc",
      "http://127.0.0.1:49152",
    );

    expect(playable).toMatch(/^http:\/\/127\.0\.0\.1:49152\/proxy\/hls\.m3u8\?u=/);
    expect(playable).not.toContain("origin.example");
    expect(new URL(playable).searchParams.get("u")).not.toContain(".m3u8");
  });

  it("strips a PNG wrapper before MPEG-TS packets", () => {
    const pngWrapper = Buffer.from([
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
      0x00, 0x00, 0x00, 0x00,
    ]);
    const ts = Buffer.alloc(188 * 3, 0xff);
    ts[0] = 0x47;
    ts[188] = 0x47;
    ts[376] = 0x47;

    expect(stripPngWrapperFromSegment(Buffer.concat([pngWrapper, ts]))).toEqual(ts);
  });

  it("base64url encodes proxy URLs without leaking media extensions", () => {
    const original = "https://cdn.example/path/seg0.png?token=abc";
    const encoded = encodeProxyUrl(original);

    expect(encoded).not.toContain(".png");
    expect(decodeProxyUrl(encoded)).toBe(original);
  });
});
