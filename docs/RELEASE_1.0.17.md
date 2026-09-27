# Chocobos Reborn 1.0.17 - Held pace

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server.

**Install this jar on the server and on every rider.** The server keeps the
ladder, the purses, and the pace of the field. Every rider needs the same jar
so the other birds stay smooth and the new coats and blinks show up. A guest
still on 1.0.16 still runs the pack ahead of the server and then yanks it back.

**The pack**

- **Other birds stay on the server's line.** The client no longer simulates
  them. They are sent every tick, out to 24 chunks, and a late position glides
  for a few ticks instead of popping. A jump farther than 24 blocks is still a
  real teleport.
- **The field holds its own pace.** A gap to you does not speed the other
  racers up or slow them down. Jolo and Teiyo included.

**The ladder**

- **Nine points promote.** A sprint win is 1. A grand prix win is 3. Nine
  sprint wins, three grand prix wins, or a mix that adds to nine. The class
  never drops.
- **Marks already on a bird stay.** Each first-place mark stored before this
  version counts as one point. A bird with two marks toward the old three now
  has two of nine.
- **Purses are smaller.** A sprint pays 6 / 12 / 24 / 48 GP for classes
  C / B / A / S. A grand prix pays three times that. Racing below your class
  still pays half and does not count toward promotion.
- **The stalls follow that purse.** A chocobo saddle is 8 GP, Sylkis is 80,
  and Zeio is 96.

**The birds**

- **They blink.** A short blink, staggered so a field does not wink together.
- **The tail sits on the body.** A run no longer stretches the tail root into
  spikes, and females keep a closed short crest.
- **Coats are cleaner.** Yellow tufts stay yellow, and the beak stays orange
  on every breed.

The five-profile race from this PC to the house server, on the meadow sprint,
finished with no vehicle correction on the rider. Baseline round trip on that
LAN was 9–12 ms.
