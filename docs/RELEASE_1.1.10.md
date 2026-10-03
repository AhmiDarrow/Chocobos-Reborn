# Chocobos Reborn 1.1.10 - Smooth Riding

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server.

**Install this jar on the server and on every rider.** Race movement now has its own packets, so an old client cannot race on a new server.

**No more snapping back**

- Your bird is yours to move. The server used to replay every step you took through its own physics and pull you back whenever the two disagreed, even by a sliver. On ground that steps by a sixteenth of a block (Heartfield's dip, where dirt path meets full blocks) that happened about three times a lap at speed, and each time you lost your pace. Now the server takes your move unless it is truly impossible: too fast, through a wall, or ending inside a block.
- After a set-back, the moves your client had already sent from the old place are dropped instead of each one snapping you back again.

**Smoother rivals at speed**

- The other birds are drawn from timed snapshots the server sends every tick, played back a moment behind, instead of Minecraft's untimed entity updates. On a large modpack those updates could stall for several ticks and the field froze, then jumped ahead. Server owners can turn this off with `-Dchocobosreborn.framePlayback=false`.
- The snapshots go out as one small packet per player per tick.

**For server owners**

- Every set-back writes a `Race rescue:` line to the server log saying why (off the road too long, cut back on past the allowance, fell, strayed, an AI that got stuck) and how far back it went.
- A client that stops receiving the race snapshots for more than a quarter of a second mid-race writes `Race frames stalled` to its log.

GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.10
