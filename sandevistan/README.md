# Cyberpunk: Sandevistan - The Edge of Time (Fabric 1.21.1)

A cybernetic implant that slows the world around you while you move at superhuman speed.
No dependencies besides Fabric API. Works in singleplayer and on dedicated servers.

## Build (GitHub, no installs)
Upload the CONTENTS of this folder to a new repo, make sure `.github/workflows/build.yml` exists,
wait for the green check in the Actions tab and download the artifact (`sandevistan-1.0.0.jar`).
Install with Fabric Loader 1.21.1 + Fabric API 0.105.0+1.21.1. The mod must also be installed on the server.

## Getting an implant
Creative tab Combat, or craft: **Experimental** (iron + redstone + diamond), **Military** (Experimental + gold + redstone blocks),
**Arasaka** (Military + netherite + eyes of ender). Right click to install (a better model replaces a worse one).

## Controls (all rebindable, category "Sandevistan")
| Key | Action |
|---|---|
| Y | Activate / deactivate | | U | Change level (1-3) |
| I | Lightning Dash | | O | Supersonic Barrage |
| P | Temporal Slash | | L | Kinetic Impact |
| K | Lightning Execution | | N | Perfect Dodge (also automatic vs projectiles) |
| M | Super Jump | | ; | Temporal Rift (Arasaka only) |
Movement: sprint = supersonic run; jump = extra impulse; sneak while running = slide; sneak while falling = controlled fall;
sprint into a wall in mid-air = **wall run** (Military+); jump while wall-running = wall jump.

## Levels
| Level | World speed | Player speed |
|---|---|---|
| 1 Enhanced Reflex | 40% | 1.5x |
| 2 Sandevistan | 15% | 2.5x |
| 3 Overdrive | 3% | 4x |
Models change these: Experimental (levels 1-2, weaker slow), Military (all levels), Arasaka (stronger slow and speed, exclusive Rift, much more stress).

## How the time manipulation works (real, not a potion)
Every entity asks "do I run this tick?" through mixins on `ServerWorld.tickEntity` and `ClientWorld.tickEntity`.
Mobs, items, TNT, falling blocks... run their whole tick only a fraction of the time, so AI, attack timers, physics, fire and
effects all slow. Projectiles run every tick with scaled velocity (gravity and acceleration scaled too), so arrows and fireballs crawl
smoothly along their normal arc. The owner, other implant owners, anything the owner rides or shot are never slowed.
Other players are slowed through movement attributes. The client mirrors the schedule and pitches sounds down while time is slow.

## Energy, stress, Cyberpsychosis
Energy drains while active and recharges after the cooldown. Stress rises while active and with every skill; above 45% the screen
glitches, above 65% the camera trembles and the energy flickers. At 100%: CYBERPSYCHOSIS - the implant fries, shuts down,
you take damage, nausea and slowness, and it stays locked for 10 s. Fully configurable, or switch it off.

## Config: `config/sandevistan.json` (created on first launch)
World/player speed per level, `affect` (`ALL`, `MOBS_ONLY`, `SELECTED` + `selectedEntities`), `excludedEntities` (for fragile modded mobs),
`affectProjectiles`, `affectOtherPlayers`, cooldown, energy drain/regen, overload on/off and strength, each ability on/off,
and client-side visual and sound intensity plus individual screen effects. Gameplay values are read by the server.

## Honest limitations
- Never compiled or run by the author (no Minecraft available): the mixins are the risky part. If the game does not start, send `logs/latest.log`.
- There is no in-game settings screen (no external mods allowed): use the JSON file.
- Sounds use vanilla sound events; the "distant, slowed world" effect is a pitch drop, not a real low-pass filter.
- Screen distortion is simulated with overlays (no custom shaders). Particles and explosions are not slowed.
- Slowed mobs move in small steps (stop-motion) at low speeds; projectiles are smooth.
- Custom ability animations of the player model are limited to arm swings plus particles.
