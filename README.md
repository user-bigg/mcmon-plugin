# MC Monitor plugin

Connects a Minecraft server to the [MC Monitor](https://mc-mon.ru) catalog. When a player joins, the plugin takes the launch code from the MC Monitor client mod and asks the site whether the code is valid for this player and your server. The site answers with the player's number on your server and the ban, if you set one in the dashboard. Then the plugin lets the player in or keeps them out.

The plugin is for servers that let players in by nickname (`online-mode=false`). On a server that verifies Mojang accounts it does nothing useful.

## What it sends and receives

For a join with a launch code the plugin sends the site the nickname and the launch code, and nothing else: no IP address, nothing about your server except the plugin version. A join without a code sends nothing at all.

The launch code comes from the game. Before each start the MC Monitor launcher gets a code for one catalog server and adds the MC Monitor client mod to the game; players install nothing themselves. A game without the mod (another launcher, Forge, a version without Fabric) has no code: for the plugin such a player did not come through the launcher.

The site answers whether the player is confirmed by the launcher, the player's number on your server (16 hex digits, different on every server) and the ban with its reason and end date. A ban applies to the account and to the computers that account played from. Every 5 minutes the plugin also fetches your server's ban list and whether launcher-only mode is on.

The plugin never gets e-mail addresses, account ids, hardware details or anything about the player's other servers. Player IP addresses and launch codes are never written to the log or to disk; the server key stays in `config.yml` and never reaches the log.

"Confirmed" means the player came through the launcher with an MC Monitor account that owns this nickname. It is not your server's password: your login plugin (AuthMe, nLogin and others) still asks for it as usual.

## Install in five minutes

Your server must let players in by nickname: `online-mode=false` in `server.properties`, or on a proxy `online-mode = false` in `velocity.toml` and `online_mode: false` in BungeeCord's `config.yml`.

1. Download `mcmonitor-plugin-<version>.jar`. The same file works on Paper, Spigot, Folia, Arclight, Velocity and BungeeCord.
2. Put it into `plugins/` and start the server once. The plugin writes `config.yml` and the message files.
3. Open your server in the MC Monitor dashboard, copy the server key and paste it into `server-key` in `plugins/MCMonitor/config.yml` (`plugins/mcmonitor/config.yml` on Velocity).
4. Restart the server. The log shows `connected to the site: …`. If it shows `could not update bans (site rejected the server key)`, check the key. A server that changed owner on the site needs a fresh key from the new owner's dashboard.

On a network with a proxy install the plugin on the proxy only: the game answers the proxy, and a backend never gets a code. On a backend the plugin notices the proxy, says so in the log and never keeps players out for lack of a code.

## Settings

`config.yml`:

| Key | Default | Meaning |
|---|---|---|
| `server-key` | empty | Key from the server dashboard. Keep it secret. With an empty key the plugin does not contact the site; saved bans still apply. |
| `site-url` | `https://mc-mon.ru` | Site address. Change it only for testing. |
| `timeout-ms` | `2000` | How long to wait for the site and for the game's answer (100–5000 ms). After that the player is handled as if the site were down or the game had no mod. |
| `language` | `ru` | Language of messages to players: `ru`, `en`, `es`, `pt`, `de`. The console is always in English. |

Launcher-only mode lets in only players confirmed by the launcher. It is turned on and off in the server dashboard on MC Monitor, not in `config.yml`. The plugin picks the change up within 5 minutes and remembers it, so the mode stays as it was while the site is down and after a restart.

Messages live in `plugins/MCMonitor/messages/<language>.yml` (`plugins/mcmonitor/messages/` on Velocity). Edit them freely: a key you remove falls back to the built-in text.

## How joins are decided

| Site answer | Launcher-only mode off | Launcher-only mode on |
|---|---|---|
| Confirmed, no ban | let in | same |
| Confirmed, banned | kicked with the reason and end date | same |
| Not confirmed (the code is unknown, expired, for another server or nickname) | handled by the server as usual | kicked: launcher required |
| No launch code: game without the mod, no answer within `timeout-ms`, or an invalid nickname | handled by the server as usual | kicked: launcher required |
| The server cannot ask for the code (see [Compatibility](#compatibility)) | handled by the server as usual | same |
| Site down, timeout, error, wrong key | handled by the server as usual | same |

A player the plugin has seen before is also checked against the saved ban list, so a ban works while the site is down and for a player who came without the launcher.

## Compatibility

| Platform | Versions | Java |
|---|---|---|
| Paper, Spigot, Purpur and other forks | Minecraft 1.18 – 26.3 | 17+ |
| Folia | 1.21.11 | 21+ |
| Arclight and other Forge or NeoForge servers with the Bukkit API | tested on Arclight 1.20.1 (Forge) and 1.21.1 (NeoForge) | 17+ / 21+ |
| Velocity | 3.5.x, 4.x | 21+ / 25+ |
| BungeeCord | builds from September 2024 on | 17+ |

Not supported: Forge and NeoForge servers without the Bukkit API, Bedrock.

The plugin does not lag the server: it talks to the site off the main thread, and a slow site never holds a join longer than `timeout-ms` plus a second.

Where the plugin cannot ask the game for the code, it says so once in the log and players are handled as usual: saved bans still apply, launcher-only mode does nothing. This is the case on Waterfall, on BungeeCord older than September 2024 and on a Bukkit server build with unusual networking.

On Velocity a game that never answers the code request waits for Velocity's own `read-timeout` (30 s by default). The vanilla game and the mod answer at once.

The mod exists for Minecraft 1.14 and newer. Players on older versions, including those who join a newer server through ViaVersion and ViaBackwards, never have a code: only saved bans apply to them.

## Files

| File | What it holds |
|---|---|
| `config.yml` | settings |
| `messages/<language>.yml` | texts shown to players |
| `players.json` | nickname → last seen player numbers |
| `bans.json` | the last ban list from the site and whether launcher-only mode is on |

## Client mod

The repository also holds the MC Monitor client mod for Fabric (`client-fabric/`). The MC Monitor launcher adds it to every game it starts with Fabric. It needs only Fabric Loader, not Fabric API.

The mod gives the code only to the server the player launched the game for. Any other server gets the same answer as from a game without the mod. The mod talks to no website.

| File | Minecraft | Fabric Loader |
|---|---|---|
| `mcmonitor-client-fabric-mc1.14-<version>.jar` | 1.14 – 1.20.1 | 0.15.0+ |
| `mcmonitor-client-fabric-mc1.20.2-<version>.jar` | 1.20.2 – 1.21.11 | 0.15.0+ |
| `mcmonitor-client-fabric-mc26.1-<version>.jar` | 26.1 and newer | 0.18.4+ |

If a future game version breaks the mod, the game still starts; the server then sees a player without a code.

## Reporting a problem

Open an issue in this repository with the server software and version, the plugin version, your `config.yml` without the key, and the log lines around the problem. Pull requests are not accepted yet.

## License

GPL-3.0, see `LICENSE`. The MC Monitor name and logo are not covered by the license.

## Changelog

### 0.2.1
- Nothing changed in the plugin or the client mod. If you run 0.2.0, there is no need to update.
- GitHub now builds the release files from the published code. Building this tag yourself gives the same files byte for byte.

### 0.2.0
- One jar for all platforms, `mcmonitor-plugin-<version>.jar`, instead of separate Bukkit and proxy jars. Remove the old `mcmonitor-bukkit-*.jar` or `mcmonitor-proxy-*.jar` from `plugins/` when you update.
- Launcher-only mode is turned on in the server dashboard. The `require-launcher` line in `config.yml` no longer does anything: if it was `true`, turn the mode on in the dashboard.

### 0.1.0
First version: join check against the site, bans from the dashboard, saved ban list for site outages, launcher-only mode, Folia and Arclight support, messages in Russian, English, Spanish, Portuguese and German.
