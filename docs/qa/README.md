# Multiplayer race QA

The final six-racer suite uses an actual integrated host, two separate rendered TCP clients, and three normal AI racers. It covers every C/B/A/S track with its normal lap count. Guests receive 150 ms added RTT and 300 ms added RTT plus +/-40 ms one-way jitter and periodic 600 ms ordered stalls. Human starting slots are rotated. The bot supplies ordinary movement input; course traversal never teleports or sets lap progress.

## Final all-track result

[Full stress report](latency-six-racer-stress-all-tracks.json): 24/24 tracks, 72 human finishes, zero vehicle-correction packets, zero sampled host/guest stamina differences or dash-lock mismatches, no AI stalls, and no uncredited AI lap crossings. Lowest client/track median FPS: 58. Worst track's sampled server-tick p95: 8.96 ms. Production source hash and exact run settings are embedded in the report.

Finish-time differences are retained as review flags on seven tracks. Zero-delay controls and rotated slots reproduce path/grid effects, particularly Shore and Starfall; Lagoon varies with the driven line and boost-arrival timing. The report does not force ties or claim all finish differences are network-free. Periodic 600 ms stalls also exceed the deliberately bounded 300 ms finish credit.

## Regressions and before/after evidence

- [Three-course pre-prediction baseline](latency-six-racer-before-prediction.json): delayed stamina/lock decisions before acknowledged input prediction.
- [Cider zero-delay pad reproduction](latency-cider-zero-delay-control.json): per-tick geometry isolated missed physical boost contact from injected delay.
- [Steady-delay all-track baseline](latency-six-racer-all-tracks.json): all 72 humans finished without corrections or sampled stamina mismatches; six courses exposed AI stalls. Predates the AI and burst fixes.
- [Zero-added-delay controls](latency-six-racer-zero-delay-controls.json): Shore, Lagoon and Starfall timing comparisons; also exposed Starfall's blue-AI detour stall.
- [Failed burst sweep](latency-six-racer-stress-before-packet-fix.json): Glacier's stressed guest received 11 corrections from the old cumulative 40-block ceiling. Interrupted and explicitly not a full pass.
- [Failed AI-line sweep](latency-six-racer-stress-before-line-fix.json): the packet fix passed Glacier; Temple exposed one uncredited black-AI lap. Interrupted and explicitly not a full pass.

The final code passes 162 JUnit tests, 27 required Minecraft GameTests, and the build. GameTests include an actual vehicle-handler backlog with oversized-move/wall rejection, landing validation, boost footprints, four physical detour traversals, and a full black-AI Temple lap checked for legal course position and lap credit. Harness and GameTest classes are excluded from the mod jar.

[Dedicated-server report](latency-dedicated-five-profiles.json): all five profiles (baseline, 120 ms, 300 ms, jitter, 600 ms bursts) finished with complete client telemetry and zero correction packets. Raw logs, client counters, positions, stamina, FPS, screenshots and field progress remain under `build/latency-pair` and `build/latency`. The normal race ends shortly after all humans finish; slower AI need not finish, but they must continue progressing and earn valid laps when they cross the line.
