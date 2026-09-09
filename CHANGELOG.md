# Changelog

## Unreleased

### Added
- **Custom death screen.** Set `deathScreenUrl` in `config/webgui/server.json` and the vanilla death screen is replaced by your page. Leave it empty and nothing changes — the feature is opt-in because replacing that screen takes away the player's only way to respawn.
  - The page receives a `webgui:death` event describing what happened: `killer` (`{type: "player" | "mob" | "environment", name, uuid}`, plus `entityType` such as `minecraft:zombie` for mobs), the damage `cause`, the vanilla `deathMessage`, and `hardcore` / `canRespawn` flags.
  - `window.webgui.respawn()` respawns the player, standing in for the vanilla Respawn button.
  - Escape does not close the page, exactly as the vanilla screen refuses to close. If the page fails to load, Escape starts working again and respawns instead, so a broken page can never trap a player.
  - The vanilla screen is left alone while the browser is still starting, so dying during the first seconds of a session shows the normal screen rather than nothing.

## 1.7.0 - 2026-09-08

### Changed
- **The browser library is now [Rinku](https://modrinth.com/mod/rinku), not MCEF.** MCEF [Keksuccino's Fork] was renamed, and its mod id changed from `mcef` to `rinku`.
  - **On Fabric there is nothing to do** — Rinku ships inside the WebGUI jar, as MCEF did before it.
  - **On NeoForge, install [Rinku](https://modrinth.com/mod/rinku) alongside WebGUI** and remove MCEF. WebGUI now declares Rinku as a required dependency, so launchers install it for you; if you copy jars by hand you need to add it yourself.
- Chromium is now version 151. The browser is no longer capped at 60 FPS, and browser windows can be muted.
- **Minecraft 26.1 and 26.2 are out of beta.** Both ship as regular releases now instead of beta builds.
- Updated NeoForge (26.2.0.82, 26.1.2.107, 21.11.45, 21.1.250), Fabric Loader 0.19.5, Fabric API, and Yarn for 1.21.11.

### Fixed
- **Fixed a client crash on Minecraft 26.2** that hit after opening a web GUI a handful of times: `Texture view Sampler0 (MCEF Browser Texture 1x1) has been closed!`. If you worked around it by setting `browser-preload-enabled=false`, you can turn browser preloading back on.
- **Fixed the browser refusing to start against current Chromium.** WebGUI passed `--use-gl=desktop`, a switch Chromium removed years ago. It went unnoticed while the browser library still shipped an older Chromium, and would have broken on any Chromium update.
- **Fixed the game crashing on startup on NeoForge when the browser library was missing.** WebGUI declared no dependency on it at all, so launchers installed nothing and the game died during mod loading with `NoClassDefFoundError: org/cef/handler/CefDisplayHandler` instead of telling you what was missing. Fabric was never affected, because the library ships inside the jar there.
- Fixed the Minecraft 26 development server failing to boot, which made the mod impossible to test locally on those versions. Released servers were never affected.

## 1.6.2 - 2026-07-26

### Fixed
- The web page now renders crisply on HiDPI/Retina displays and no longer looks mis-scaled or changes size with Minecraft's GUI Scale setting.

## 1.6.1 - 2026-07-26

### Fixed
- Fixed a NeoForge 1.21.11 startup crash: `MouseMixin` still targeted the removed `MouseHandler.onPress`; it now injects into `onButton` on 1.21.5+.

## 1.6.0 - 2026-07-25

### Added
- **Commands from the page.** A page can now run a Minecraft command, executed **as the player** — exactly as if they typed it in chat, so there is no privilege escalation. Commands are only accepted from the main frame of an origin the server declared trusted via `trustedCommandOrigins` in `config/webgui/server.json`; requests from any other origin (e.g. after a redirect or from an iframe) are dropped. The trusted-origin list is sent to the client on join and cleared on disconnect, so it never carries across servers.
- `@webgui/react`: `runCommand(command)` and the `useRunCommand()` hook.

## 1.5.0 - 2026-07-24

### Added
- `window.webgui.client` now includes more player data: `health`, `maxHealth`, `food`, `xpLevel`, `gamemode`, and a `look` object with `yaw`/`pitch`.

### Fixed
- Server-opened HUDs and GUIs are now closed automatically when leaving a world (disconnect / exit to title), instead of lingering in the background on the main menu.
- Fixed NeoForge crash when loading a URL with a leading slash.
- Fixed NeoForge mixin error in `MouseMixin` (#7).
- Fixed crash on dedicated servers caused by loading client-only classes during payload registration (#9).
- Fixed MCEF failing to load on NeoForge 1.21.11 (NeoForge bumped to 21.11.44).

## 1.4.1
