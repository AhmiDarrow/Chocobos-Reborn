# Chocobos Reborn 1.1.5 - Light on the Feet

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server.

**Install this jar on the server and on every rider.**

**Lighter on the server**

- Resting birds cost far less. A chocobo standing still in its pen no longer recomputes every collision around its
  wide frame each tick; it falls, floats, gets pushed and carries riders exactly as before.
- Square stewards stop re-planning their walk every time a door swings, and stop retrying a blocked walk every second.
- Course plans no longer stay in memory forever once raced; the server can free them and rebuilds one before a heat's
  countdown when needed.
- Many smaller savings: colour, grade and course lookups, the almanac's family list, heat-board text, and the Square's
  save data.

**Lighter on your machine**

- Chocobos are skinned about twice as fast, with identical results, and each vertex is written in one step.
- Course crowds, the Square's sky, steward poses and the almanac do far less work each frame.

**Fixes**

- GP owed to you and pending follow-ups are cleared when a server stops, so nothing carries into another world.

GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.5
