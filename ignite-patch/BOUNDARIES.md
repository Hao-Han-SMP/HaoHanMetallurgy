# Ignite Patch Boundaries

HaoHan Metallurgy is plugin-first. Keep gameplay, persistence, commands,
recipes, GUIs, item creation, and machine lifecycle in the Purpur plugin.

Use Ignite only when Paper/Purpur API cannot expose the needed server behavior
cleanly.

## Good Ignite Candidates

- Early server bootstrap hooks before Bukkit plugins enable.
- Small NMS hooks that remove expensive plugin-side workarounds.
- Server-only behavior that cannot be expressed through Paper events.
- Optional bridge hooks exposed back to the plugin through a tiny stable API.

## Poor Ignite Candidates

- Inventory GUI rendering. This is client-side; use a resource pack or client mod.
- ItemDisplay/BlockDisplay opacity. This is client rendering; server cannot add
  true alpha to opaque models.
- Recipes, commands, config, database, machine state, and ordinary events.
- Tick loops that are already cheap through Bukkit scheduler.

## Likely Plugin Optimizations Before Mixin

- Index forge structure block locations to avoid scanning every forge on each
  click/break interaction.
- Throttle ambient forge particles/sounds by nearby players and temperature.
- Save dirty machines periodically instead of calling `saveAll()` after every
  small state change.
- Keep display entity lookup indexed by machine location instead of scanning
  all `ItemDisplay` entities as a fallback path.

## When To Use A Client Mod

Use a client mod for custom GUI rendering, custom display opacity, custom block
models without resource-pack constraints, or client-only visual effects.
