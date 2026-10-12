**Your web app, running inside Minecraft.**

WebGUI opens a real web page in the game — as a full-screen interface or as a transparent overlay on top of gameplay. Not a reskinned inventory, not another UI framework to learn: actual HTML, CSS and JavaScript, rendered by actual Chromium.

If you can build a website, you can build a Minecraft interface.

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/wqZ400GWKUw" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

## What people build with it

* **Server shops** that look like an online store, with images, search and a cart
* **Quest journals and codexes** with real typography and layout
* **Live maps** — Leaflet or your own canvas, following the player as they walk
* **HUD overlays** — team scores, timers, health bars, a minimap, drawn over the world
* **Admin panels** for staff, with the same dashboard you already run in a browser
* **Custom death screens** that say who killed you and let you respawn
* **Launch menus** and lobby screens with your server's own branding
* **Entire in-game apps** — anything you would otherwise build as a web app

## Why it feels different

Everything you already use keeps working. React, Vue, Svelte or plain JavaScript. Tailwind. Your component library. Your animations, your API, your WebSocket. You develop in a browser with hot reload, then point the mod at the page — and it looks exactly the same in the game.

The page is not a decoration, either. It knows who is looking at it and what is happening around them — the player's name, health, food, position, where they are looking, which block or mob is under the crosshair — updated as they move. And it can talk back: a click in the page becomes an event on the server, so a shop can actually sell, and a button can actually run a command.

```js
// This is the whole integration
window.webgui.on('client', player => {
  document.title = `${player.username} — ${player.health} HP`;
});

window.webgui.postToGame({ channel: 'shop:buy', item: 'diamond' });
```

For React, Vue and Svelte there is one package with hooks and stores ready to go:

```bash
npm i @webgui/client
```

## You don't even need a web host

Put your files in a folder on the server and point the mod at them. They travel down the same connection the player is already on — no domain, no hosting bill, no port to open, works behind NAT. Edit a file, run one command, and the page a player has open refreshes in front of them.

Prefer your own host? Nothing changes: an `https://` address works exactly as it always did, and the two mix freely.

## For server owners

Opening a page is one command:

```
/webgui gui @a https://your-site.example/shop
/webgui hud @a webgui:/hud.html
```

Pages can also be bound to an entity, so right-clicking a villager opens your shop with that villager's context. Every URL the mod opens carries a signed token, so your backend knows which player is asking without a login screen. Players without the mod are told what they are missing instead of quietly seeing nothing.

## Compatibility

| Minecraft | Loader |
|-----------|--------|
| 26\.2 · 26\.1.2 | NeoForge |
| 1\.21.5 – 1.21.11 | Fabric · NeoForge |
| 1\.21 – 1.21.1 | Fabric · NeoForge |
| 1\.20.1 | Fabric |

Minecraft 26 is NeoForge-only for now — Fabric is waiting on Yarn mappings for 26.x.

**Installing:** on Fabric you need Fabric API, and that is it — the browser ships inside the jar. On NeoForge, add [Rinku](https://modrinth.com/mod/rinku) alongside it; launchers pick it up automatically. Chromium (~150 MB) downloads itself on first launch. Java 21, or Java 25 for Minecraft 26.

## Links

* **Documentation** — <https://webgui.space> — guides, the full API, and examples you can paste
* **GitHub** — <https://github.com/mc-webgui/webgui> — issues and source
* **npm** — <https://www.npmjs.com/package/@webgui/client>
