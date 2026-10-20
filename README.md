# MC Monitor plugin

Connects a Minecraft server to the [MC Monitor](https://mc-mon.ru) catalog. When a player joins, the plugin takes the launch code from the MC Monitor client mod and asks the site whether the code is valid for this player and your server. The site answers with the player's number on your server and the ban, if you set one in the dashboard. Then the plugin lets the player in or keeps them out.

The plugin is for servers that let players in by nickname (`online-mode=false`). On a server that verifies Mojang accounts (`online-mode=true`) it does nothing useful: players with a Mojang account do not go through the MC Monitor launcher.

## What it sends and receives

For a join with a launch code the plugin sends the site exactly two things: the nickname and the launch code of the joining player. The player's IP address is not sent. A join without a code sends nothing at all. The request carries the plugin version in its `User-Agent` header and nothing else about your server.

The launch code comes from the game: before each start the MC Monitor launcher gets a code for one catalog server and puts the MC Monitor client mod into the game (Fabric, see [Client mod](#client-mod)). While logging in, the plugin asks the game over the login channel `mcmonitor:play`, and the mod answers with the code. Players install nothing themselves. A game without the mod (another launcher, Forge, a version without Fabric) answers that it does not know the channel; for the plugin such a player is someone who did not come through the launcher.

The site answers with facts: whether the player is confirmed by the launcher, the player's number on your server (16 hex digits, the same for one account on one server, different on every other server), and the ban with its reason and end date if the owner set one in the dashboard. A ban applies to the account and to the computers that account played from. Every 5 minutes the plugin also fetches the ban list of your server and whether launcher-only mode is turned on in the dashboard.

The plugin never receives e-mail addresses, account ids, hardware details or anything about the player's other servers. The server key, launch codes and player IP addresses never appear in the log, and launch codes are not stored on disk.

"Confirmed" means the player came through the launcher with an MC Monitor account that owns this nickname. It is not your server's password: your login plugin still asks for it.

Skins and capes from MC Monitor are shown by the client-side mod in the game. The plugin does not touch player appearance.

## Install in five minutes

Your server must let players in by nickname: `online-mode=false` in `server.properties`, or on a proxy `online-mode = false` in `velocity.toml` and `online_mode: false` in BungeeCord's `config.yml`. Velocity and BungeeCord ship with it on.

1. Download `mcmonitor-plugin-<version>.jar`. The same file works on Paper, Spigot, Folia, Arclight, Velocity and BungeeCord.
2. Put it into `plugins/` and start the server once. The plugin writes `config.yml` and the message files.
3. Open your server in the MC Monitor dashboard, copy the server key and paste it into `server-key` in `plugins/MCMonitor/config.yml` (`plugins/mcmonitor/config.yml` on Velocity). The key stops working when the server changes owner on the site: the new owner takes a fresh key from the dashboard, and until then the log says the site rejected the key.
4. Restart the server. Within a few seconds the log shows `connected to the site: …`: the site accepted the key. If it shows `could not update bans (site rejected the server key)` instead, check the key you pasted.

On a network with a proxy install the plugin on the proxy only. The game answers the launch code request of the server it connects to, which is the proxy; Velocity and BungeeCord do not pass a backend's request on to the game. The plugin on a backend therefore never gets a code and is of no use there. When the plugin on a Bukkit server sees `settings.bungeecord: true` in `spigot.yml` or Velocity forwarding in Paper's settings, it says so in the log at startup, does not ask for launch codes and ignores launcher-only mode, so it never keeps players out for lack of a code.

## Settings

`config.yml`:

| Key | Default | Meaning |
|---|---|---|
| `server-key` | empty | Key from the server dashboard. Keep it secret. With an empty key the plugin does not contact the site; saved bans still apply. |
| `site-url` | `https://mc-mon.ru` | Site address. Change it only to test against a fake site. |
| `timeout-ms` | `2000` | How long to wait for the site (100–5000 ms). After that the player is handled by the server as usual, as if the site were unreachable. The plugin waits the same time for the game to answer the launch code request; a game that does not answer in time counts as a game without the mod. |
| `language` | `ru` | Language of messages to players: `ru`, `en`, `es`, `pt`, `de`. Console messages stay in English. |

Launcher-only mode lets in only players confirmed by the launcher. It is turned on and off in the server dashboard on MC Monitor, not in `config.yml`. The plugin picks the change up with the ban list within 5 minutes and keeps it in `bans.json`, so the mode stays as it was while the site is down and after a restart. When the site does not answer, everyone except already known banned players gets in as if the plugin were absent.

Messages live in `plugins/MCMonitor/messages/<language>.yml` (`plugins/mcmonitor/messages/` on Velocity). Edit them freely: a key you remove falls back to the built-in text, and texts added by an update are used from the built-in file until you copy them over.

## How joins are decided

| Site answer | Launcher-only mode off | Launcher-only mode on |
|---|---|---|
| Confirmed, no ban | let in | same |
| Confirmed, banned | kicked with the reason and end date | same |
| Not confirmed (the code is unknown, expired, for another server or nickname) | handled by the server as usual | kicked: launcher required |
| No launch code: game without the mod, no answer within `timeout-ms`, or a nickname that is not a valid Minecraft name (letters, digits and `_`, up to 16) | handled by the server as usual, the site is not asked | kicked: launcher required |
| The server cannot ask for the code (see [Compatibility](#compatibility)); the log says so once | handled by the server as usual | same |
| Site unreachable, timeout, error, rate limit, wrong key | handled by the server as usual | same |

The plugin never skips the password of your login plugin: logging in stays the job of the login plugin (AuthMe, nLogin and others), and it asks a player let in by MC Monitor for the password as usual.

When the site rejects the key, the log says so once and asks you to copy the key into `server-key` again; a rate limit right after that means the same thing.

A player the plugin has already seen is checked against the saved ban list (`bans.json`, refreshed every 5 minutes) even when the site is down or the player came without the launcher.

## Compatibility

| Platform | Versions | Java |
|---|---|---|
| Paper, Spigot, Purpur and other forks | Minecraft 1.18 – 26.3 | 17+ (whatever the server needs) |
| Folia | 1.21.11 | 21+ |
| Arclight and other Forge or NeoForge servers with the Bukkit API | the plugin in `plugins/`; tested on Arclight 1.20.1 (Forge, 1.0.6) and 1.21.1 (NeoForge, 1.0.1) | 17+ / 21+ |
| Velocity | 3.5.x, 4.x | 21+ / 25+ |
| BungeeCord | current builds | 17+ |

Not supported: Forge and NeoForge servers (without the Bukkit API), Bedrock. The plugin needs `AsyncPlayerPreLoginEvent` on Bukkit and `PreLoginEvent` with `LoginEvent` on proxies. It talks to the site off the main thread and releases a join at most one second after `timeout-ms` even if the request hangs.

How the launch code is asked for:

| Platform | How | Notes |
|---|---|---|
| Velocity 3.5, 4.x | `LoginPhaseConnection#sendLoginPluginMessage` | Velocity holds the login until the game answers; a game that never answers is dropped by Velocity's own `read-timeout` (30 s by default). The vanilla game and the mod answer at once. |
| BungeeCord | `PendingConnection#sendData` | Builds from September 2024 on. Older builds and Waterfall lack the method: the plugin says so once in the log at startup, cannot ask, and players are handled as usual; launcher-only mode has no effect there. |
| Paper, Spigot, Folia, Arclight | the plugin's own network handler | Bukkit has no API for this. The handler relies on the server's network channel layout, which has stayed the same on 1.18 – 26.3. If a server build differs, the plugin says so once in the log and handles players as usual (saved bans still apply); launcher-only mode has no effect there. A game that answers after `timeout-ms` is not dropped: the plugin takes the late answer away from the server. |

The game needs Minecraft 1.13 or newer to answer at all; the MC Monitor mod exists for 1.14 and newer with Fabric. Players on older versions, including everyone on a 1.12.2 server, never have a code: only saved bans apply to them. Games older than 1.13 that join a newer server through ViaVersion and ViaBackwards are handled like games without the mod (tested with 5.12.1 on Paper 26.3).

## Files

| File | What it holds |
|---|---|
| `config.yml` | settings |
| `messages/<language>.yml` | texts shown to players |
| `players.json` | nickname → last seen player numbers |
| `bans.json` | the last ban list received from the site and whether launcher-only mode is on |

`players.json` and `bans.json` contain no IP addresses, launch codes or server key; the key lives only in `config.yml`.

## Client mod

The repository also holds the MC Monitor client mod for Fabric (`client-fabric/`). Players do not install it: the MC Monitor launcher puts it into every game it starts with Fabric. It needs only Fabric Loader, not Fabric API.

Before each launch the launcher writes `mcmonitor/play.json` into the game folder: a launch code for one catalog server, the nickname and that server's address. When a server asks over the login channel `mcmonitor:play`, the mod answers with the code, and only if the file is for the current nickname and the game is connecting to the address in the file (as it stands in the server list or in the launcher's quick-play argument, before any SRV lookup). Any other server, and any other request, gets the same empty answer as from a game without the mod. The mod sends nothing else, talks to no website and never writes the code to the log.

| File | Minecraft | Fabric Loader |
|---|---|---|
| `mcmonitor-client-fabric-mc1.14-<version>.jar` | 1.14 – 1.20.1 | 0.15.0+ |
| `mcmonitor-client-fabric-mc1.20.2-<version>.jar` | 1.20.2 – 1.21.11 | 0.15.0+ |
| `mcmonitor-client-fabric-mc26.1-<version>.jar` | 26.1 and newer | 0.18.4+ |

If a future game version changes the code the mod hooks into, the game still starts; the mod just stops answering, and the server sees a player without a code.

## Reporting a problem

Open an issue in this repository. Please include the server software and version, the plugin version, your `config.yml` without the key, and the relevant log lines. Pull requests are not accepted yet.

## License

GPL-3.0, see `LICENSE`. The MC Monitor name and logo are not covered by the license.

## Changelog

### 0.2.0
- One jar for all platforms, `mcmonitor-plugin-<version>.jar`, instead of separate Bukkit and proxy jars. Remove the old `mcmonitor-bukkit-*.jar` or `mcmonitor-proxy-*.jar` from `plugins/` when you update.
- Launcher-only mode is turned on in the server dashboard. The `require-launcher` key in `config.yml` is gone; if your `config.yml` still has it, the plugin ignores it, so turn the mode on in the dashboard.

### 0.1.0
First version: join check against the site, bans from the dashboard, saved ban list for site outages, launcher-only mode, Folia and Arclight support, messages in Russian, English, Spanish, Portuguese and German.
