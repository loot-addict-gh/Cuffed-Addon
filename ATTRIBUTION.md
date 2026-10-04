# Attribution

Cuffed Addon is released under the GNU General Public License v3 (see `LICENSE`), the same
licence as [Cuffed](https://github.com/LazrProductions/cuffed) by LazrProductions, which it is
built on and extends.

Being an addon, it reuses and adapts a fair amount of Cuffed's own work. Listed here in full,
as GPL-3.0 asks.

## Artwork

- The two combination head restraints reuse Cuffed's `bundle_overlay.png` as their screen
  overlay, shipped as a copy per restraint so each one can be retextured on its own.
- `bed_cuffs.png`, `bundle_rope.png`, `bundle_duct_tape.png` and the sixteen `bunk_*.png` colours
  are recoloured and edited from Cuffed's handcuffs, bundle and bunk textures.
- The three bundled resource packs are retextures of Cuffed's own assets.

## Code

- `entity/AnchorKnotEntity.java` is closely based on Cuffed's `ChainKnotEntity`, so that the new
  anchor points behave exactly like its own.
- `curios/CurioFriskingScreen.java` and `CurioFriskingMenu.java` are based on Cuffed's
  `FriskingScreen` and `FriskingMenu`, so the added curio column matches the original window.
- The classes under `restraints/` implement Cuffed's restraint API and follow the shape of its
  own restraints.
- The mixins under `mixin/` inject into Cuffed's classes; `mixins.cuffedaddon.json` lists the
  targets.

## Other

- Curios integration — [Curios](https://github.com/TheIllusiveC4/Curios) by TheIllusiveC4.
- Fake Players compatibility — Fake Players by duzo.
- The Reinforced Bow frames and both arrow icons are modified from Minecraft's own textures, and
  `models/item/reinforced_bow.json` follows vanilla's `bow.json`. Not an official Minecraft
  product, and not approved by or associated with Mojang or Microsoft.
