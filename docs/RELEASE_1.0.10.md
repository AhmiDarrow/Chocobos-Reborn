# Chocobos Reborn 1.0.10 - Level field

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server. Races run smoother,
and the player hosting a world gets no edge over the friends racing on it.

**Smoother races**

- **Distant birds draw lighter.** A chocobo more than 16 blocks away draws about a
  third of its triangles, and past 40 blocks about a tenth. A full race field used to
  cost every frame what the nearest bird costs, which is where the lag on slower
  machines came from. Close up the bird is unchanged.
- **No hitch at the start line.** The bird models load while the world does, not the
  moment a whole saddled field comes into view on the grid.

**Host and guests race on equal terms**

- **Ping is credited at the finish.** A guest hears GO one trip late and the host sees
  them cross the line one trip late. Each rider is now credited their own ping (up to
  300 ms) at the line, so a close finish goes to whoever really crossed first. Places
  are called a fraction of a second after the line.
- **Photo finishes are timed inside the tick.** Two birds over the line in the same
  tick are placed by where they were, not by the order they joined the heat.
- **Boost pads fire on the pad for everyone.** Each rider's own game now times the
  boost, so a guest no longer gets it a ping late, halfway into the next corner.

**Dry roads**

- **No more water on the road.** Stray water could run onto a course mid-heat and wash
  the boost pads out as items a rider picked up. Every course island is cleared of what
  older versions left behind and laid fresh the next time it is raced, and the River
  cairn's spring now sits in a basin instead of spilling off its peak.
- **Boost pads are watertight.** Water and lava flow round a pad instead of breaking it.

**Duels**

- **A duel is one on one.** Sable's duels are the two riders alone on the centre
  stalls, with no AI field and no jockeys.
- **The pot is the prize.** A duel pays only what the two riders put up against each
  other. No purse, win or lose.

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
