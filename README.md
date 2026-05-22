# NetherLink

Minecraft 26.2-snapshot-7 introduced P2P networking and friend-list support. This project aims to enhance this feature.

## Client Features

- Add an Integrated Server sharing mode for single-player worlds.
- Stop Minecraft from broadcasting presence status except "In a joinable world" to avoid conflicts with server features.

## Server Features

- Link a dedicated server with a Microsoft/Minecraft account.
- Publish dedicated server availability to the owner's Minecraft friend list.

## How to use

### Client

1. Install NetherLink on the client.
2. Launch the game and open a single-player world.
3. Open the multiplayer sharing screen.
4. Select **Integrated Server** as the sharing mode.

When this mode is active, friends in your Minecraft friend list can join the
world without a manual approval prompt. The world is still hosted by the
integrated server in your client, so it closes when you leave the world or stop
sharing it.

NetherLink also suppresses normal client presence updates for accounts that are
configured as hosted server accounts. This keeps the hosted server presence from
being overwritten by the same account playing on a client.

### Server

1. Install NetherLink on the server.
2. [Optional] If you want to use your own `APP ID` used for authentication, set it with environment variable
   `NETHERLINK_CLIENT_ID`.
3. Start the server and run:

   ```mcfunction
   /nli add
   ```

4. Open the login URL shown in chat or the server console, complete Microsoft
   authentication, and wait for NetherLink to store the Minecraft account.
5. Check configured accounts:

   ```mcfunction
   /nli list
   ```

6. Publish the server to the account owner's Minecraft friend list:

   ```mcfunction
   /nli publish
   ```

After publishing, friends of the linked account should see it as a joinable
hosted world. Incoming P2P join requests are accepted by NetherLink and routed
into the running server.

### Server Commands

- `/nli add` or `/netherlink add`: add a Microsoft/Minecraft account through
  device-code login.
- `/nli list`: show configured accounts and token status.
- `/nli refresh [all|<name>]`: refresh stored account tokens.
- `/nli publish [all|<name>]`: publish server presence and start accepting P2P
  joins.
- `/nli revoke [all|<name>]`: revoke server presence and stop accepting P2P
  joins for the account.
- `/nli toggle <name>`: enable or disable an account.
- `/nli remove <name>`: remove a stored account.

Account data is stored under `netherlink/accounts`. Treat these files as
credentials; do not share them publicly.
