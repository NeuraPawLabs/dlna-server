import { createSocket, type Socket } from "node:dgram";
import type { AppConfig } from "./config.js";
import { publicBaseUrl } from "./http-server.js";

const multicastAddress = "239.255.255.250";
const multicastPort = 1900;

const searchTargets = [
  "upnp:rootdevice",
  "urn:schemas-upnp-org:device:MediaRenderer:1",
  "urn:schemas-upnp-org:service:AVTransport:1",
  "urn:schemas-upnp-org:service:RenderingControl:1",
  "urn:schemas-upnp-org:service:ConnectionManager:1",
];

export class SsdpAdvertiser {
  private socket: Socket | undefined;
  private interval: NodeJS.Timeout | undefined;

  constructor(private readonly config: AppConfig) {}

  async start(): Promise<void> {
    this.socket = createSocket({ type: "udp4", reuseAddr: true });

    await new Promise<void>((resolve, reject) => {
      this.socket?.once("error", reject);
      this.socket?.bind(multicastPort, () => {
        this.socket?.off("error", reject);
        this.socket?.addMembership(multicastAddress);
        this.socket?.setMulticastTTL(4);
        this.socket?.on("message", (message, remote) => this.handleMessage(message, remote.port, remote.address));
        resolve();
      });
    });

    this.notifyAlive();
    this.interval = setInterval(() => this.notifyAlive(), this.config.notifyIntervalMs);
  }

  async stop(): Promise<void> {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = undefined;
    }

    this.notifyByebye();

    await new Promise<void>((resolve) => {
      if (!this.socket) {
        resolve();
        return;
      }
      this.socket.close(() => resolve());
      this.socket = undefined;
    });
  }

  private handleMessage(message: Buffer, port: number, address: string): void {
    const text = message.toString("utf8");
    if (!/^M-SEARCH \* HTTP\/1.1/im.test(text)) {
      return;
    }

    const st = text.match(/^ST:\s*(.+)$/im)?.[1]?.trim() ?? "";
    if (st !== "ssdp:all" && !searchTargets.includes(st)) {
      return;
    }

    for (const target of matchingTargets(st)) {
      this.send(this.searchResponse(target), port, address);
    }
  }

  private notifyAlive(): void {
    for (const target of searchTargets) {
      this.send(this.notifyMessage(target, "ssdp:alive"), multicastPort, multicastAddress);
    }
  }

  private notifyByebye(): void {
    for (const target of searchTargets) {
      this.send(this.notifyMessage(target, "ssdp:byebye"), multicastPort, multicastAddress);
    }
  }

  private searchResponse(searchTarget: string): string {
    return [
      "HTTP/1.1 200 OK",
      "CACHE-CONTROL: max-age=1800",
      `DATE: ${new Date().toUTCString()}`,
      "EXT:",
      `LOCATION: ${publicBaseUrl(this.config)}/description.xml`,
      "SERVER: NewraPawDLNA/0.1 UPnP/1.0 Node.js",
      `ST: ${searchTarget}`,
      `USN: ${this.usn(searchTarget)}`,
      "",
      "",
    ].join("\r\n");
  }

  private notifyMessage(searchTarget: string, nts: "ssdp:alive" | "ssdp:byebye"): string {
    return [
      "NOTIFY * HTTP/1.1",
      `HOST: ${multicastAddress}:${multicastPort}`,
      "CACHE-CONTROL: max-age=1800",
      `LOCATION: ${publicBaseUrl(this.config)}/description.xml`,
      "SERVER: NewraPawDLNA/0.1 UPnP/1.0 Node.js",
      `NT: ${searchTarget}`,
      `NTS: ${nts}`,
      `USN: ${this.usn(searchTarget)}`,
      "",
      "",
    ].join("\r\n");
  }

  private usn(searchTarget: string): string {
    if (searchTarget === "upnp:rootdevice") {
      return `uuid:${this.config.uuid}::upnp:rootdevice`;
    }
    return `uuid:${this.config.uuid}::${searchTarget}`;
  }

  private send(message: string, port: number, address: string): void {
    this.socket?.send(Buffer.from(message), port, address);
  }
}

function matchingTargets(st: string): string[] {
  return st === "ssdp:all" ? searchTargets : [st];
}
