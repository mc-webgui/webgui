# WebGUI

**Embed any web page as a full-screen GUI or transparent HUD overlay inside Minecraft.**

WebGUI is a Fabric mod that embeds a real Chromium browser (via [MCEF](https://github.com/CinemaMod/mcef)) directly in the game client. Server operators can display any React, Vue, or plain HTML app to their players — as a HUD overlay that auto-opens on join, or as a custom main menu accessible with a keybind.

[![Modrinth](https://img.shields.io/badge/Modrinth-webgui-1bd96a?logo=modrinth)](https://modrinth.com/project/webgui)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Features

- **Full-screen GUI** — open any URL as a screen that closes with Esc
- **HUD overlay** — transparent overlay on top of the game; toggle interactive mode with `` ` ``
- **Auto HUD on join** — automatically shows a HUD overlay when a player connects
- **Custom main menu** — players press `F6` to open your web page
- **Live client data** — the mod pushes `window.webgui.client` (position, dimension, server info) to the page at 20 TPS
- **Signed tokens** — every URL the mod opens carries an HMAC-signed token so your backend can verify the player's identity
- **Mod API** — other Fabric mods can open WebGUI overlays for players programmatically via `WebviewApi`

---

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.x |
| Fabric Loader | ≥ 0.18.0 |
| Fabric API | any |
| Java | 21 |

Chromium (~150 MB) is downloaded automatically on first launch. Include [MCEF](https://modrinth.com/mod/mcef) in your modpack to pre-bundle it.

---

## Installation

1. Download the latest JAR from [Modrinth](https://modrinth.com/project/webgui).
2. Place it in `.minecraft/mods/` alongside Fabric API.
3. Start the game.

---

## Quick setup (server)

Create or edit `config/webgui/server.json`:

```json
{
  "autoHudOnJoin": true,
  "autoHudUrl": "https://your-hud.example.com",
  "mainMenuUrl": "https://your-menu.example.com"
}
```

Restart the server and join to test.

---

## Server commands

```
/webgui gui <targets> <url>   — open a full-screen GUI for players
/webgui hud <targets> <url>   — open a HUD overlay for players
```

Requires operator level 2. Supports `fabric-permissions-api` (LuckPerms, etc.).

---

## React library

Build your SPA with type-safe hooks:

```bash
npm install @webgui/react
```

```tsx
import { useWebGUIClient, isInMod, isReady } from '@webgui/react';

export function PlayerInfo() {
  const client = useWebGUIClient();
  if (!isInMod())       return <p>Open this inside Minecraft.</p>;
  if (!isReady(client)) return <p>Connecting…</p>;
  return <p>Hello, {client!.username}</p>;
}
```

→ [npm: @webgui/react](https://www.npmjs.com/package/@webgui/react) · [Docs](https://webgui.space)

---

## Documentation

Full documentation at **[webgui.space](https://webgui.space)**.

---

## License

[MIT](LICENSE) © KoSHeroff
