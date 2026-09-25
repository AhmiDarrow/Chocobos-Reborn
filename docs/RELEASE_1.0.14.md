# Chocobos Reborn 1.0.14 - Steady dash

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server. A guest's
dash spends stamina again, and a short lag spike no longer yanks the bird
back to the host.

**Stamina**

- **The bar keeps dropping.** After the first time a guest's stamina hit
  empty, the server turned sprint off. Their game turned it back on and never
  told the server, so the bar filled up and stayed full while they kept
  dashing. Sprint is no longer cleared. Holding sprint and riding forward
  spends stamina, for the host and for a friend on the same world.
- **The host jar is enough.** A guest who has not updated still gets the fix.
  If they update too, their game reports the dash every tick, so a laggy
  sprint key cannot drift out of step. An older guest jar is not kicked.

**Guest movement**

- **Short bursts are no longer snapped back.** A friend on the world could be
  pulled back when a lag spike moved the bird more than about ten blocks in
  one tick. A chocobo may now move up to 40 blocks in that tick. A real
  teleport across the course, hundreds of blocks, still snaps to where the
  server put them.

**Lighter heats**

- **The race does less work each tick.** Lap position is taken from the
  stretch of track the bird was just on. Birds standing in the Square no
  longer search for boost pads, and the grandstand shares one look for a
  heat instead of every fan searching on its own. Speeds, dash, and the pads
  themselves are unchanged.

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
