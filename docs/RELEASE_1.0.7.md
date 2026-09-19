# Chocobos Reborn 1.0.7 - Fresh paint

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server. An art pass with a few fixes. Existing saves, farms and courses are untouched, and the birds themselves are unchanged.

**Art**

Every item, block and crop stage is redrawn by hand at 32x32, in the same style as the rest of the Ninjacat Skies family: one tinted outline, light from the top left, flat colour ramps, no blur.

- **The eight greens** are eight different plants now, not one leaf in eight colours: gysahl's pale bulb, curiel kale, a krakka star, mimett clover, a pahsana sprig, a reagan palm leaf, sylkis blades tied with wire, tantal vine and berries.
- **Every nut has its own shape** — carob pod, sage round, olive acorn, almond, walnut, plum, chestnut and the gold zeio in its husk — so you can tell them apart in a full chest.
- **Armour** reads as a set of four, each tier with its own hem and metal: stitched leather, riveted iron, faceted diamond, jagged netherite.
- **Tack**: the saddle has a seat, flap, blanket and stirrup; the saddlebags are two buckled pouches.
- **Gysahl crop stages** grow from two seed leaves to a full crown with the bulb showing.
- **Square gates** share one navy-and-copper frame, each with its own glyph: gem, one chevron, two chevrons, a turn-back arrow. The boost pad's chevrons still scroll.
- The Almanac's pages keep their layout in a navy panel with a copper frame.

The art now comes from `tools/pixel_items.py` in the repo, so it can be regenerated.

**Fixes**

- **Mud only slows birds on a race course.** The course-bog penalty applied to any mud block anywhere, so birds ridden through a mangrove swamp crawled. Boost pads and bogs now work in Whiskerwind only.
- **The bookie quotes the right odds.** A Class B bird entered in a Class C heat was shown "pays 3x" and paid 2x.
- **Scratching from a heat no longer sends you home.** Sneak-clicking the keeper while entered now just scratches; a second sneak-click still takes you home.
- **Crowds from crashed heats are cleared.** A crash left the grandstand fans on the course island, and every rerun added another crowd.
- **The dismount lock is server-side only**, so an operator teleport no longer leaves a racer stuck in the saddle on their own screen.
- **The bird ledger saves only when a record changes**, instead of on every tame bird's chunk save.

**Docs**

- The Almanac and README now match the code on Carob prizes: a Class B win pays one 15% of the time, a Class A win 40%, and every Class S race pays one, with a rare Zeio on a Class S win.

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
