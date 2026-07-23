# Changelog

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
