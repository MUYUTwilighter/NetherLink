# NetherLink

[![Available Minecraft Versions](https://cf.way2muchnoise.eu/versions/1548571.svg)](https://www.curseforge.com/minecraft/mc-mods/netherlink-nli)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/netherlink-nli?logo=modrinth&label=Modrinth%20Downloads)](https://modrinth.com/mod/netherlink-nli)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_1548571_CurseForge%20Downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/netherlink-nli)

[![NetherLink Official Website](https://img.shields.io/badge/NetherLink-Home-purple?logo=googlehome&logoColor=purple)](https://nli.muyucloud.cool)
[![NetherLink MOD Sources](https://img.shields.io/badge/NetherLink_MOD-GitHub-blue?logo=github)](https://github.com/MUYUTwilighter/NetherLink)
[![NetherLink Server Sources](https://img.shields.io/badge/NetherLink_Server-GitHub-blue?logo=github)](https://github.com/MUYUTwilighter/nli-server)

NetherLink is a Minecraft mod for friend-based online play. It provides a friends UI, friend requests, presence sharing, and WebRTC-based joining for single-player worlds and dedicated servers.

The default backend is **NLI API v1**, hosted by NetherLink. It handles friends, presence, signaling, and TURN credentials used by the P2P connection flow.

## What it does

- Adds a NetherLink friends screen with:
  - friend list
  - friend requests
  - friend/API settings
  - player avatars
  - joinable instance display
- Lets a single-player integrated server be opened to friends through NetherLink.
- Lets friends join through WebRTC P2P, with TURN fallback when direct connectivity is not available.
- Supports dedicated servers publishing themselves as joinable NetherLink instances through a linked Minecraft account.
- Supports switching between available link backends from the Friends settings tab.

## Client usage

1. Install NetherLink on the client.
2. Launch Minecraft and sign in with a Microsoft/Minecraft account.
3. Open the Friends button in the main/multiplayer UI to manage friends and requests.
4. In a single-player world, open the multiplayer sharing screen.
5. Select **NetherLink** as the multiplayer scope and apply the change.

When enabled, your friends can see and join the world through NetherLink. The world is still hosted by your client, so it closes when you leave the world or stop sharing it.

The first use of the NLI backend may show the current service terms. NLI terms are fetched from the configured backend and accepted terms are stored locally.

## Dedicated server usage

1. Install NetherLink on the server.
2. Optional: set `NETHERLINK_CLIENT_ID` if you want to use your own Microsoft authentication app id.
3. Start the server and run:

   ```mcfunction
   /nli add
   ```

4. Open the login URL shown in chat or the server console, complete Microsoft authentication, and wait for NetherLink to store the Minecraft account.
5. Check configured accounts:

   ```mcfunction
   /nli list
   ```

6. Publish the server through the linked account:

   ```mcfunction
   /nli publish
   ```

After publishing, friends of the linked account should see the server as a joinable NetherLink instance. Incoming WebRTC join requests are accepted by NetherLink and routed into the running server.

## Server commands

- `/nli add` or `/netherlink add`: add a Microsoft/Minecraft account through device-code login.
- `/nli list`: show configured accounts and token status.
- `/nli refresh [all|<name>]`: refresh stored account tokens.
- `/nli publish [all|<name>]`: publish server presence and start accepting P2P joins.
- `/nli revoke [all|<name>]`: revoke server presence and stop accepting P2P joins.
- `/nli toggle <name>`: enable or disable an account.
- `/nli remove <name>`: remove a stored account.

Account data is stored under `netherlink/accounts`. Treat these files as credentials; do not share them publicly.

## Configuration

Client configuration is stored under `config/netherlink/`.

- `config.json`
  - `activeService`: selected backend, defaulting to `netherlink:nli_v1`.
  - `instanceName`: optional name used when publishing your current world/server instance.
- `nli-v1.json`
  - `server`: NLI API server URL. Defaults to `https://nli-api.muyucloud.cool`.
  - `acceptedTerms`: local record of accepted NLI terms revisions.

The active backend can also be changed from the Friends settings tab.

## Notes

- NetherLink uses Minecraft account identity information for friend and presence features. The mod and companion backend code are open source.
- NLI's hosted backend and TURN service are used by default.
- Connection quality depends on both peers' networks, but TURN fallback should make joining more reliable than direct-only P2P attempts.
