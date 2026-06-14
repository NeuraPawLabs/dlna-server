import { describe, expect, it } from "vitest";
import { RendererState } from "../src/renderer-state.js";

describe("RendererState", () => {
  it("starts stopped without a current URI", () => {
    const state = new RendererState();

    expect(state.snapshot()).toMatchObject({
      currentUri: "",
      transportState: "STOPPED",
      transportStatus: "OK",
      volume: 50,
      muted: false,
    });
  });

  it("sets a current URI and resets playback position", () => {
    const state = new RendererState();

    state.setCurrentUri("http://media.example/video.mp4", "<metadata />");

    expect(state.snapshot()).toMatchObject({
      currentUri: "http://media.example/video.mp4",
      currentUriMetadata: "<metadata />",
      transportState: "STOPPED",
      relativeTimePosition: "00:00:00",
    });
  });

  it("transitions through play, pause, seek, and stop", () => {
    const state = new RendererState();

    state.setCurrentUri("http://media.example/video.mp4", "");
    state.play();
    expect(state.snapshot().transportState).toBe("PLAYING");

    state.pause();
    expect(state.snapshot().transportState).toBe("PAUSED_PLAYBACK");

    state.seek("00:01:05");
    expect(state.snapshot().relativeTimePosition).toBe("00:01:05");

    state.stop();
    expect(state.snapshot()).toMatchObject({
      transportState: "STOPPED",
      relativeTimePosition: "00:00:00",
    });
  });

  it("clamps volume and stores mute state", () => {
    const state = new RendererState();

    state.setVolume(130);
    expect(state.snapshot().volume).toBe(100);

    state.setVolume(-10);
    expect(state.snapshot().volume).toBe(0);

    state.setMuted(true);
    expect(state.snapshot().muted).toBe(true);
  });
});
