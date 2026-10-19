# Contributing

Thank you for your interest. Right now the project accepts issues only: bug reports, compatibility notes and ideas. Pull requests are not merged yet.

## Reporting a bug

Open an issue with:
- server software and version (for example `Paper 26.3 build 178`, `Velocity 4.2.0`, `Arclight 1.21.1`);
- plugin version, as in the jar name (for example `mcmonitor-bukkit-0.1.0.jar`);
- the login plugin and its version, if any;
- `config.yml` without the server key;
- log lines around the problem. The plugin never writes the key or player addresses to the log, so copying them is safe.

## Building

Requirements: any JDK 17 or newer to start the Gradle wrapper, Python 3 for the scripts and Docker for the server tests. Gradle downloads the JDK 25 it runs and compiles on by itself.

```
./gradlew build
```

builds `bukkit/build/libs/mcmonitor-bukkit-<version>.jar`, `proxy/build/libs/mcmonitor-proxy-<version>.jar`, the client mod jars in `client-fabric/*/build/libs/` and runs the unit tests.

```
scripts/check
```

also formats, compiles with all lint warnings as errors, compiles the Velocity module against the Velocity 3 API, checks every class and mixin target of the client mod against each game version of its line (`scripts/mixin-targets`) and runs the plugin on real Paper, Folia, Arclight, Velocity and BungeeCord servers in Docker. `scripts/check --no-docker` skips the servers.

`scripts/client-smoke` starts a real Fabric client with the mod in a linux/amd64 container and checks that the code reaches only the server it is meant for. It is slow and not part of `scripts/check`.

`scripts/fake-site` starts a fake MC Monitor site on port 8089 for manual testing; point `site-url` at it and use the key `dev-key`.

## Code rules

- Java 17 bytecode in the platform modules and `core`; the client mod is Java 8 for Minecraft 1.14 – 1.20.1, 17 for 1.20.2 – 1.21.11 and 25 for 26.x. English identifiers, logs and comments. Comments only explain a non-obvious "why".
- The `core` module has no dependency on Paper, Velocity or BungeeCord. Platform modules only connect it to the join events.
- Nothing blocks the server main thread. Every request to the site has a timeout and a hard limit; when the site is down the server behaves as if the plugin were absent.
- The server key and player IP addresses never reach the log. Every answer from the site is untrusted input.
- New behaviour comes with a test that catches a regression in our logic. We do not test the language or third-party libraries.
