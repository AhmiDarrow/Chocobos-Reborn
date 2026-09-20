# Chocobos Reborn 1.0.8 - Solid ground

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server. A race course pass: a
fall-through-the-world fix, courses that are fair to every colour, and a landmark on
each of the twenty-four. Existing saves, farms and birds are untouched — courses you
have already raced are re-laid the next time you race them.

**Fixes**

- **You no longer fall out of the world on a course.** The island was stamped along the
  centre line in steps, and on a tight corner or a hill lip the steps skipped whole
  block columns: a hole clean through the island into the void. There were 761 of them
  across the twenty-four courses, 152 wide enough to swallow a bird. Falling in was not
  survivable either, because the rescue that puts a racer back on the road skipped
  anyone who was still over the road — which is exactly where the holes were. Both are
  fixed: the plan is stamped four times finer, any column left empty under the racing
  area is plugged, and a racer below y 50 is put back on the road wherever they are.
- **Nothing on the racing line that should not be there.** The plan is laid in layers
  now (ground, road, kerbs, scenery), so a corner that folds back on itself cannot drop
  a margin, a cactus, a camp fire or a fence rail from the leg next door into the road,
  and a lava pool cannot end up on a completely different part of the lap. Cacti and
  fire on the road: gone. Rails standing in the band: 250-770 samples a course, now a
  handful. Stray water, lava and mud away from their own feature: gone.
- **Pools stay in their banks.** Water and lava are placed as source blocks with no
  block update, so they sit still until something disturbs them and then drain over the
  island. Every pool had open faces on the detour side, where the kerb was skipped to
  open the detour. They are walled on both sides now.
- **Riders are safe in Whiskerwind.** The birds have always been invulnerable there;
  their riders were not, so taking a bird that cannot cross lava into a lava feature
  burned the rider to death and dropped their inventory into the void. A rider is now
  as safe as the bird, a burning one is put out, and anyone who goes over the edge on
  foot is set down in the paddock. `/kill` still works.

**Courses**

- **A colour is worth the same on every course.** What an ability saves you depends on
  where its feature sits: a detour round a feature on a straight costs only the swing
  out and back, one round the outside of a big bend costs a tenth of a lap. On the old
  layout that ranged from -6 blocks (Starfall's water detour was *shorter* than the
  direct line, so a Blue bird lost time by using its ability) to +50. Every feature has
  been re-placed to sit on a bend that costs a detour-taker about 2 % of a lap: the
  spread is now 13 to 29 blocks, 1.0 %-2.1 % of a lap, on all twenty-four.
- **The bog is the cheapest thing to go round on every course.** Nobody suits a bog, so
  its detour is everyone's route, not a toll.
- **More boosts, in better places.** Three strips on a sprint and five on a grand prix
  (up from two or three), each on the exit of a corner onto a straight rather than
  mid-bend, and clear of every detour.
- **Features are spread round the lap** instead of bunched in one half, and the opening
  stretch is kept clear so the field is not hitting a detour while still three abreast
  off the grid.
- **Every course has its own landmark.** Set pieces used to be one per theme, and two
  courses share a theme, so the sprint and the grand prix of each looked the same. The
  twelve grand prix courses get their own: a windmill on Rolling Downs, a cider barn,
  a beached shipwreck, a terracotta arch, a mill wheel, a frozen waterfall, a dripstone
  hall, a mossy idol with gold eyes, a bone arch over a soul fire, a ring hung over the
  road, a banner gatehouse and a caged crystal.

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
