import { parseConfig } from "./config.js";
import { createDlnaHttpServer, publicBaseUrl } from "./http-server.js";
import { MpvPlayer } from "./player.js";
import { RendererState } from "./renderer-state.js";
import { SsdpAdvertiser } from "./ssdp.js";

const config = parseConfig();
const state = new RendererState();
const player = new MpvPlayer(config.mpvSocketPath, config.mpvPath);
const httpServer = createDlnaHttpServer(config, state, player);
const ssdp = new SsdpAdvertiser(config);

httpServer.listen(config.port, config.host, async () => {
  console.log(`DLNA renderer HTTP listening at ${publicBaseUrl(config)}/description.xml`);
  await ssdp.start();
  console.log("SSDP advertiser listening on udp://239.255.255.250:1900");
});

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

async function shutdown(): Promise<void> {
  console.log("Shutting down DLNA renderer");
  await ssdp.stop().catch((error) => console.error(error));
  await player.close().catch((error) => console.error(error));
  httpServer.close(() => process.exit(0));
}
