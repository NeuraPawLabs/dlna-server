export type TransportState = "STOPPED" | "PLAYING" | "PAUSED_PLAYBACK";

export type RendererSnapshot = {
  currentUri: string;
  currentUriMetadata: string;
  transportState: TransportState;
  transportStatus: "OK" | "ERROR_OCCURRED";
  relativeTimePosition: string;
  volume: number;
  muted: boolean;
};

export class RendererState {
  private state: RendererSnapshot = {
    currentUri: "",
    currentUriMetadata: "",
    transportState: "STOPPED",
    transportStatus: "OK",
    relativeTimePosition: "00:00:00",
    volume: 50,
    muted: false,
  };

  snapshot(): RendererSnapshot {
    return { ...this.state };
  }

  setCurrentUri(uri: string, metadata: string): void {
    this.state.currentUri = uri;
    this.state.currentUriMetadata = metadata;
    this.state.transportState = "STOPPED";
    this.state.transportStatus = "OK";
    this.state.relativeTimePosition = "00:00:00";
  }

  play(): void {
    this.state.transportState = "PLAYING";
    this.state.transportStatus = "OK";
  }

  pause(): void {
    this.state.transportState = "PAUSED_PLAYBACK";
    this.state.transportStatus = "OK";
  }

  stop(): void {
    this.state.transportState = "STOPPED";
    this.state.transportStatus = "OK";
    this.state.relativeTimePosition = "00:00:00";
  }

  seek(target: string): void {
    this.state.relativeTimePosition = target;
  }

  setVolume(volume: number): void {
    this.state.volume = clamp(Math.round(volume), 0, 100);
  }

  setMuted(muted: boolean): void {
    this.state.muted = muted;
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
