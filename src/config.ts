import { randomUUID } from "node:crypto";
import { tmpdir } from "node:os";
import { join } from "node:path";

export type AppConfig = {
  host: string;
  port: number;
  deviceName: string;
  manufacturer: string;
  modelName: string;
  uuid: string;
  advertiseAddress?: string;
  mpvPath: string;
  mpvSocketPath: string;
  notifyIntervalMs: number;
};

export function parseConfig(argv = process.argv.slice(2), env = process.env): AppConfig {
  const options = parseArgs(argv);
  const uuid = options.uuid ?? env.DLNA_UUID ?? randomUUID();

  return {
    host: options.host ?? env.DLNA_HOST ?? "0.0.0.0",
    port: Number(options.port ?? env.DLNA_PORT ?? 49152),
    deviceName: options.name ?? env.DLNA_NAME ?? "NewraPaw DLNA Receiver",
    manufacturer: options.manufacturer ?? env.DLNA_MANUFACTURER ?? "NewraPaw Labs",
    modelName: options.model ?? env.DLNA_MODEL ?? "Linux Prototype",
    uuid,
    advertiseAddress: options["advertise-address"] ?? env.DLNA_ADVERTISE_ADDRESS,
    mpvPath: options["mpv-path"] ?? env.DLNA_MPV_PATH ?? "mpv",
    mpvSocketPath: options["mpv-socket"] ?? env.DLNA_MPV_SOCKET ?? join(tmpdir(), `newrapaw-dlna-${uuid}.sock`),
    notifyIntervalMs: Number(options["notify-interval-ms"] ?? env.DLNA_NOTIFY_INTERVAL_MS ?? 30000),
  };
}

function parseArgs(argv: string[]): Record<string, string> {
  const options: Record<string, string> = {};

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith("--")) {
      continue;
    }

    const [rawName, rawValue] = arg.slice(2).split("=", 2);
    if (rawValue !== undefined) {
      options[rawName] = rawValue;
      continue;
    }

    const next = argv[index + 1];
    if (next && !next.startsWith("--")) {
      options[rawName] = next;
      index += 1;
    }
  }

  return options;
}
