import { spawn, type ChildProcess } from "node:child_process";
import { accessSync, constants, existsSync, unlinkSync } from "node:fs";
import { connect } from "node:net";
import { delimiter, isAbsolute, join } from "node:path";

export interface PlayerAdapter {
  play(uri: string): Promise<void>;
  resume(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  seek(target: string): Promise<void>;
  setVolume(volume: number): Promise<void>;
  setMuted(muted: boolean): Promise<void>;
  close(): Promise<void>;
}

export class MpvPlayer implements PlayerAdapter {
  private process: ChildProcess | undefined;

  constructor(
    private readonly socketPath: string,
    private readonly executable = "mpv",
  ) {}

  async play(uri: string): Promise<void> {
    const executablePath = resolveExecutable(this.executable);
    if (!executablePath) {
      throw new Error(`Player executable not found: ${this.executable}`);
    }

    await this.close();
    removeSocket(this.socketPath);

    const args = buildMpvArgs(this.socketPath, uri);
    console.log(`[mpv] ${executablePath} ${args.join(" ")}`);

    this.process = spawn(executablePath, args, {
      stdio: "inherit",
    });

    this.process.once("exit", () => {
      this.process = undefined;
      removeSocket(this.socketPath);
    });
    this.process.once("error", (error) => {
      console.error(`mpv failed: ${error.message}`);
    });
  }

  async resume(): Promise<void> {
    await this.command(["set_property", "pause", false]);
  }

  async pause(): Promise<void> {
    await this.command(["set_property", "pause", true]);
  }

  async stop(): Promise<void> {
    await this.command(["stop"]);
  }

  async seek(target: string): Promise<void> {
    await this.command(["seek", target, "absolute"]);
  }

  async setVolume(volume: number): Promise<void> {
    await this.command(["set_property", "volume", volume]);
  }

  async setMuted(muted: boolean): Promise<void> {
    await this.command(["set_property", "mute", muted]);
  }

  async close(): Promise<void> {
    if (!this.process) {
      removeSocket(this.socketPath);
      return;
    }

    const child = this.process;
    this.process = undefined;
    await this.command(["quit"]).catch(() => undefined);
    child.kill("SIGTERM");
    removeSocket(this.socketPath);
  }

  private async command(command: unknown[]): Promise<void> {
    if (!this.process) {
      return;
    }

    await sendIpcCommand(this.socketPath, { command });
  }
}

export function buildMpvArgs(socketPath: string, uri: string): string[] {
  const args = [
    `--input-ipc-server=${socketPath}`,
    "--idle=yes",
    "--force-window=yes",
  ];

  if (isLikelyHlsManifest(uri)) {
    args.push("--demuxer-lavf-format=hls");
    args.push("--demuxer-lavf-analyzeduration=10");
    args.push("--demuxer-lavf-probesize=50000000");
    args.push("--demuxer-lavf-o=allowed_extensions=ALL");
  }

  args.push(uri);
  return args;
}

async function sendIpcCommand(socketPath: string, message: unknown): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const socket = connect(socketPath);
    const payload = `${JSON.stringify(message)}\n`;

    socket.once("connect", () => {
      socket.write(payload, () => socket.end());
    });
    socket.once("error", reject);
    socket.once("close", () => resolve());
  });
}

function removeSocket(socketPath: string): void {
  if (existsSync(socketPath)) {
    unlinkSync(socketPath);
  }
}

function resolveExecutable(executable: string): string | undefined {
  if (isAbsolute(executable) || executable.includes("/")) {
    return canExecute(executable) ? executable : undefined;
  }

  for (const directory of (process.env.PATH ?? "").split(delimiter)) {
    if (!directory) {
      continue;
    }
    const candidate = join(directory, executable);
    if (canExecute(candidate)) {
      return candidate;
    }
  }

  return undefined;
}

function canExecute(path: string): boolean {
  try {
    accessSync(path, constants.X_OK);
    return true;
  } catch {
    return false;
  }
}

function isLikelyHlsManifest(uri: string): boolean {
  return /\.m3u8(?:$|[/?#&=;%])/i.test(uri);
}
