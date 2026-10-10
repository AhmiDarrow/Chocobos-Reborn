# Chocobos Reborn 1.1.15 - Clean Sweep

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server.

Install this jar on the server and on every rider. Network formats are unchanged, so a 1.1.14 client can still join, but the smoother races and the fixes below need it on both sides.

**Smoother races**

- Birds are skinned on the graphics card. Drawing a race field used to cost several milliseconds of every frame on the CPU; now it costs almost nothing. Glowing birds, shader packs (Iris/Oculus) and `-Dchocobosreborn.cpuBirds=true` keep the old path.
- Each breed's colours and blinking eyes are prepared in the background instead of in the middle of a frame, so a field coming into view, or blinking for the first time, no longer hitches.
- A course is laid during its heat's countdown, a little each tick, instead of freezing the server for seconds the moment the heat is posted.
- Whiskerwind's sky is drawn from the graphics card's memory instead of being rebuilt every frame.
- Birds respect your Entity Distance setting, and armoured birds are loaded with the world instead of on first sight.

**Betting**

- A bet placed with Rook before the timetable's heat now rides on that heat as promised, even if you are not standing at Rook when it starts. Rook tells you what it pays when it joins.
- A bet on the field pays at least even money (twice the stake back). Five field birds used to round down to handing back just the stake.
- No field bet is taken on a heat with no field birds. A player named like one of the rivals no longer appears as that rival on the board.
- A heat nobody finished refunds every stake. A server stop after someone has finished settles the heat; a rider still on the course gets their own stake back.
- Rook says why a bet was refused: books closed, another heat running, or no field birds.

**Racing**

- The race standings have their own line, so the lap counter, the off-road warning and shortcut tips are no longer wiped out by it.
- A ranked win counts toward your class even if you log out or leave by Pocketwatch before the heat closes.
- The one-minute heat call, the start bell, the winners' board and the win fireworks all work again.

**Around the world**

- Riding into a Square gate uses it.
- The whistle and follow never put a bird in lava, fire or mid-air, and a bird tied to a fence stays tied.
- A nut cannot skip the breeding cooldown, and a nut-fed bird says why it will not stay.
- Spawn eggs: a dispensed egg keeps its colour, an egg on your own bird hatches a chick, pick-block gives the right egg, and a named egg names the bird.
- Courses and the village are relaid once: fences and walls join on both sides, the inn's beds and the rim waterfalls are fixed, and shortcut stripes no longer cover bogs and pools.
- The Almanac fits smaller windows, and hens are shown as hens.

GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.15
