# Chocobos Reborn 1.0.16 — Smoother remote races

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
Install it on the server and every client. The riding protocol changed; older peers are rejected at connection rather than allowed to use incompatible input/state formats.

- Ordered movement backlogs are validated one packet step at a time, retaining native per-step limits and collision checks without overwriting velocity.
- Race timing uses fresh round-trip probes and accounts separately for the delay at GO and at the finish. Credit remains capped at 300 ms.
- The countdown holds the bird locally as well as on the server. Holding movement or dash on the grid no longer spends stamina or depends on repeated corrections.
- Off-course dash reports identify their mount, send an initial state on every mount, and refresh four times per second while held. During races, sequenced input frames and server acknowledgements keep local stamina and dash-lock decisions responsive without waiting for ping-delayed metadata.
- The race HUD shows predicted local stamina alongside server-provided standings. Stamina history is bounded and reconciled against server acknowledgements.
- Ridden dash, training and boost bonuses now change movement speed correctly instead of being clipped by Minecraft's direction normalization.
- Boost-pad detection sweeps the bird’s physical footprint, including diagonal edge contact and pads above mud/slabs, avoiding boosts missed between movement samples.
- Fixes a landing/step validation loop that trapped both host and guest on Maelstrom, while retaining collision checks.
- AI follows the actual detour entrances and exits with a shorter local steering target, fixing reproduced fence/ridge stalls. Normal lookahead is also bounded in metres so long-course bends no longer cut the inside rail and invalidate AI laps.
- Gold birds keep dash over airborne race terrain where flight is disabled. Jump charges are capped at their normal maximum.

The development tools now include a real rendered client race harness, an all-track six-racer suite with an integrated host, two independently delayed guests and three normal AI, an isolated dedicated server, configurable network delay/jitter/stalls, telemetry, screenshots and a result checker. The harness is not included in the shipped mod.

See [the improvement tracker](https://github.com/AhmiDarrow/Chocobos-Reborn/blob/v1.0.16/docs/LATENCY_IMPROVEMENTS.md) for measured results, validation and remaining coverage.

Validation: 162 unit tests, 27 required in-game tests, build, and all 24 six-racer stress races passed. The live sweep recorded zero correction packets, sampled stamina/lock mismatches, AI stalls or uncredited AI laps. Finish-time review flags and test limits are retained in the QA report.
