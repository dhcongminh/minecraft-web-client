# Webcake Voice Paper plugin

This plugin bridges the `minecraft-web-client:voice-chat` custom channel to an internal token issuer. It deliberately does **not** contain a LiveKit API secret.

It supports two mutually exclusive modes:

- `around`: server sends nearby authenticated web players in the same world; the browser spatializes them.
- `global`: joins the global LiveKit room; the browser hears/publishes globally.

## Build and install

```sh
mvn package
```

Copy `target/WebcakeVoice.jar` to Paper's `plugins/` directory. Set `issuer-url`, `issuer-secret`, and `server-id` in `plugins/WebcakeVoice/config.yml`, then restart Paper.

AuthMe is required by default. The plugin calls AuthMe's runtime API by reflection and rejects voice token issuance if AuthMe is absent or the player has not authenticated.

## Protocol

The browser sends `enable` or `mode` with `{ "mode": "global" | "around" }`. The plugin asks the internal issuer for a short-lived token and returns `config`. It updates `nearby` every second for Around mode. Browser input is never trusted for identity, room, or player positions.
