# Chocobos Reborn 1.0.2 - Lighter

Performance and size pass on 1.0.1. Minecraft 1.21.1, NeoForge 21.1.249, Java 21, client and server. No world changes: existing saves, villages and courses are untouched.

- **Smaller download**: 82 MB -> 49 MB. The five solid breeds (Green, Blue, White, Black, Gold) are coloured from the Yellow atlas when the game loads instead of shipping as separate textures; the birds look the same. End and Nether keep their own feather textures.
- **Faster birds**: the chocobo renderer does far less work per frame (each bird's 31k triangles were skinned and transformed twice over), so crowded heats and the Whiskerwind streets cost less FPS.
- **Almanac**: text, pictures and the breed row stay inside the page; Back and Release sit under the chapter list; long chapters get a scrollbar.
- **Resource packs** that replace `textures/entity/chocobo/yellow.png` (or the saddled / armour yellows) now recolour the other breeds too.
