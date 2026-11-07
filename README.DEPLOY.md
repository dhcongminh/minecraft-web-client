# Webcake Minecraft web login

The login screen connects players to the server configured in `.env`. Its password is kept only in browser memory for that connection, then sent as `/login <password>` after the player spawns. It is not placed in the URL or persisted by this deployment.

## Deploy

Copy `.env.example` to `.env`, set the public `DOMAIN` and the target `MC_SERVER`, then run:

```sh
docker compose up -d --build
```

This Compose file expects an external Docker network named `proxy`, used by the VPS Traefik instance. For the existing Webcake Minecraft stack the target is `webcake-team-minecraft:25565` and the domain is `webcake-team.dhcongminh.id.vn`.
