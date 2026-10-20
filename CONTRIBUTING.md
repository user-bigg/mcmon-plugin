# Contributing

Right now the project accepts issues only: bug reports, compatibility notes and ideas. Pull requests are not merged yet.

## Reporting a bug

Open an issue with:
- server software and version (for example `Paper 26.3 build 178`, `Velocity 4.2.0`, `Arclight 1.21.1`);
- plugin version, as in the jar name (for example `mcmonitor-plugin-0.2.1.jar`);
- the login plugin and its version, if any;
- `config.yml` without the server key;
- log lines around the problem. The plugin never writes the key or player addresses to the log, so copying them is safe.

## Building

You need JDK 17 or newer to start Gradle, Python 3 for the scripts and Docker for the server tests. Gradle downloads the JDK it builds with by itself.

```
./gradlew build
```

builds the plugin (`plugin/build/libs/mcmonitor-plugin-<version>.jar`) and the client mod (`client-fabric/*/build/libs/`) and runs the unit tests. Release files on GitHub are built the same way from the release tag (`.github/workflows/release.yml`); a local build of the same tag gives the same files byte for byte.

```
scripts/check
```

runs everything, including the plugin on real Paper, Folia, Arclight, Velocity and BungeeCord servers in Docker. `scripts/check --no-docker` skips the servers.

`scripts/fake-site` starts a fake MC Monitor site on port 8089 for manual testing; point `site-url` at it and use the key `dev-key`. Nicknames starting with `Guest` are not confirmed, `Banned` are banned, `Slow` get the answer after 5 s. With `FAKE_SITE_LAUNCHER_ONLY_FILE=<path>` launcher-only mode is on while that file exists.

`scripts/client-smoke` starts a real Fabric client with the mod in a container and checks that the code reaches only the server it is meant for. It is slow and not part of `scripts/check`.
