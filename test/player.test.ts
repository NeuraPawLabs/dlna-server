import { describe, expect, it } from "vitest";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { buildMpvArgs, MpvPlayer } from "../src/player.js";

describe("MpvPlayer", () => {
  it("rejects with a clear message when the player executable is missing", async () => {
    const socketPath = join(tmpdir(), "newrapaw-dlna-missing-player-test.sock");
    const player = new MpvPlayer(socketPath, "/definitely/missing/newrapaw-mpv");

    await expect(player.play("http://media.example/video.mp4")).rejects.toThrow(
      "Player executable not found: /definitely/missing/newrapaw-mpv",
    );
  });

  it("forces the HLS demuxer for signed URLs whose m3u8 extension is in query metadata", () => {
    const args = buildMpvArgs(
      "/tmp/newrapaw-dlna.sock",
      "https://media.example/object-id?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27video.m3u8&X-Amz-Signature=abc",
    );

    expect(args).toEqual([
      "--input-ipc-server=/tmp/newrapaw-dlna.sock",
      "--idle=yes",
      "--force-window=yes",
      "--demuxer-lavf-format=hls",
      "--demuxer-lavf-analyzeduration=10",
      "--demuxer-lavf-probesize=50000000",
      "--demuxer-lavf-o=allowed_extensions=ALL",
      "https://media.example/object-id?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27video.m3u8&X-Amz-Signature=abc",
    ]);
  });

  it("does not force HLS for regular media URLs", () => {
    const args = buildMpvArgs("/tmp/newrapaw-dlna.sock", "https://media.example/video.mp4");

    expect(args).toEqual([
      "--input-ipc-server=/tmp/newrapaw-dlna.sock",
      "--idle=yes",
      "--force-window=yes",
      "https://media.example/video.mp4",
    ]);
  });
});
