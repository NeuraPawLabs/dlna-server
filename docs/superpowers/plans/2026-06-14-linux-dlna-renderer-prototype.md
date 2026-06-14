# Linux DLNA Renderer Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Linux DLNA Digital Media Renderer prototype that can be discovered by DLNA controllers and play pushed media URLs with `mpv`.

**Architecture:** A Node.js service combines SSDP multicast discovery, an HTTP UPnP description/control server, a minimal SOAP DMR action layer, and an `mpv` player adapter. Pure XML, SOAP, and renderer-state behavior is covered by unit tests; LAN discovery and controller playback are verified manually.

**Tech Stack:** Node.js, TypeScript, Vitest, built-in `http`, `dgram`, `child_process`, and `net` modules.

---

## File Structure

- `package.json`: npm scripts and development dependencies.
- `tsconfig.json`: TypeScript compiler settings for Node.js ESM.
- `src/config.ts`: command-line and environment configuration.
- `src/xml.ts`: XML escaping and UPnP description/SCPD builders.
- `src/soap.ts`: SOAP action parsing and response/fault builders.
- `src/renderer-state.ts`: in-memory DMR state machine.
- `src/player.ts`: `mpv` process and IPC adapter.
- `src/http-server.ts`: HTTP routes for description, SCPD, and SOAP control.
- `src/ssdp.ts`: SSDP responder and notifier.
- `src/index.ts`: process entry point.
- `test/*.test.ts`: unit tests for XML, SOAP, and renderer state.
- `README.md`: run and manual verification instructions.
- `.gitignore`: generated files.

### Task 1: Project Scaffold

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `.gitignore`

- [ ] **Step 1: Write scaffold files**

Create npm scripts for `test`, `build`, `dev`, and `start`. Configure TypeScript for Node ESM and ignore `node_modules`, `dist`, coverage, and local socket files.

- [ ] **Step 2: Install dependencies**

Run: `npm install`

Expected: dependencies install and `package-lock.json` is created.

- [ ] **Step 3: Commit**

Run:

```bash
git add package.json package-lock.json tsconfig.json .gitignore
git commit -m "chore: scaffold node dlna renderer prototype"
```

### Task 2: XML and SOAP Pure Logic

**Files:**
- Create: `src/xml.ts`
- Create: `src/soap.ts`
- Create: `test/xml.test.ts`
- Create: `test/soap.test.ts`

- [ ] **Step 1: Write failing tests**

Tests assert device XML includes `MediaRenderer:1`, service control URLs, and SOAP parsing extracts `SetAVTransportURI` arguments.

- [ ] **Step 2: Run tests to verify failure**

Run: `npm test -- test/xml.test.ts test/soap.test.ts`

Expected: tests fail because modules do not exist.

- [ ] **Step 3: Implement minimal XML and SOAP modules**

Implement string builders for device/SCPD XML and simple SOAP action parsing using namespace-aware regular expressions for the prototype command set.

- [ ] **Step 4: Run tests to verify pass**

Run: `npm test -- test/xml.test.ts test/soap.test.ts`

Expected: tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/xml.ts src/soap.ts test/xml.test.ts test/soap.test.ts
git commit -m "feat: add upnp xml and soap helpers"
```

### Task 3: Renderer State

**Files:**
- Create: `src/renderer-state.ts`
- Create: `test/renderer-state.test.ts`

- [ ] **Step 1: Write failing tests**

Tests assert URI, transport state, volume, and mute transitions.

- [ ] **Step 2: Run tests to verify failure**

Run: `npm test -- test/renderer-state.test.ts`

Expected: tests fail because the state module does not exist.

- [ ] **Step 3: Implement minimal renderer state**

Create a `RendererState` class with methods for setting URI, play, pause, stop, seek, volume, and mute.

- [ ] **Step 4: Run tests to verify pass**

Run: `npm test -- test/renderer-state.test.ts`

Expected: tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/renderer-state.ts test/renderer-state.test.ts
git commit -m "feat: add renderer state machine"
```

### Task 4: Player, HTTP, SSDP, and Entry Point

**Files:**
- Create: `src/config.ts`
- Create: `src/player.ts`
- Create: `src/http-server.ts`
- Create: `src/ssdp.ts`
- Create: `src/index.ts`
- Create: `README.md`

- [ ] **Step 1: Write failing integration-oriented tests**

Add tests for SOAP action routing without opening real sockets, using a fake player adapter.

- [ ] **Step 2: Run tests to verify failure**

Run: `npm test`

Expected: new tests fail because HTTP routing and player integration do not exist.

- [ ] **Step 3: Implement minimal runtime modules**

Implement configuration parsing, `mpv` IPC adapter, HTTP route handling, SSDP responder/notifier, and CLI entry point.

- [ ] **Step 4: Run unit tests and build**

Run: `npm test && npm run build`

Expected: all tests pass and TypeScript emits `dist`.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/config.ts src/player.ts src/http-server.ts src/ssdp.ts src/index.ts README.md test
git commit -m "feat: add linux dlna renderer runtime"
```

### Task 5: Manual Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run the service**

Run: `npm run dev`

Expected: logs show HTTP URL and SSDP startup.

- [ ] **Step 2: Verify HTTP description**

Run: `curl http://127.0.0.1:49152/description.xml`

Expected: XML includes `NewraPaw DLNA Receiver` and `MediaRenderer:1`.

- [ ] **Step 3: Document LAN test commands**

Add README instructions for installing `mpv`, opening UDP 1900/TCP 49152 if needed, and testing from a DLNA controller.

- [ ] **Step 4: Commit**

Run:

```bash
git add README.md
git commit -m "docs: add dlna prototype verification steps"
```
