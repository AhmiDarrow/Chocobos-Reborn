# Latency improvement tracker

Scope: riding, race starts, input synchronization, movement validation, and finish ordering.
Work started 2026-09-26. This is a development record; it does not claim internet latency can be eliminated.

## Final six-racer verification

All 24 tracks passed on the final build with three human clients and three normal AI: 72 human finishes, zero corrections, zero sampled stamina/dash-lock differences, no AI stalls and no uncredited AI laps. Guests used 150 ms RTT and 300 ms RTT plus jitter/600 ms stalls. Median client FPS was at least 58; worst sampled server-tick p95 was 8.96 ms. See [QA evidence](qa/README.md) and [the complete report](qa/latency-six-racer-stress-all-tracks.json). Timing review flags are preserved; this does not claim physical network delay or every possible finish-time difference has disappeared.

## Implemented

- **NET-01 — Movement allowance without changing physics.** The old mixin ran at the head of the vehicle packet handler, before Minecraft's server-thread dispatch, and set physical velocity to an entire packet burst. The replacement changes only the velocity-squared value used by the speed comparison, after thread, teleport and controlling-vehicle validation. Collision checks remain. NET-06 below supersedes the intermediate 40-block cumulative ceiling with per-packet validation.
- **NET-02 — Mount-bound dash input.** Dash packets include the entity ID, are accepted only for the sender's current controlled bird, and remember the rider UUID. Every mount sends its initial state, including false. Dismounting no longer sends an unqualified release that could stop a different mount. Changes transmit immediately; held-dash heartbeats run every five ticks with a twenty-tick expiry; idle riders send no redundant refreshes.
- **NET-03 — Local countdown hold.** Server-synchronized grid hold suppresses local movement, jumping, and stamina expenditure. GO releases it; finish/forfeit clears it. The server still corrects a rider who drifts outside the stall.
- **NET-04 — Separate start and finish latency.** Finish credit uses half the bounded RTT at GO plus half at the finish. A late spike no longer retroactively changes the start delay. Maximum total credit stays at 300 ms and finish settlement retains its bounded wait.
- **NET-05 — Fresh race RTT.** Live racers receive one nonce probe per second, echoed on the network thread. A median of five recent, matched round trips replaces Minecraft's slow-moving ping for race compensation. Duplicates, unsolicited replies and expired probes are ignored. Stale/unavailable measurements fall back to vanilla ping. Probe state is removed when the race/player leaves.
- **RIDE-01 — Gold dash while racing.** Being airborne only changes dash into dive when flight is permitted. Gold birds can keep dashing over race terrain where flight is disabled.
- **RIDE-02 — Jump bounds.** Client and server clamp jump charge to the supported 0–100 range.

## Real-client harness

Run `python tools/latency_harness.py`, then `python tools/analyze_latency.py`.
Use `--track C_SHORE` (or another `RaceTrack` enum name) to change the course.

The launcher compiles the development harness and starts an actual rendered Minecraft client and dedicated server. It binds an offline test server to loopback only, using isolated worlds under `build/latency`. A TCP proxy applies symmetric delay, ordered jitter, or periodic stalls. The autopilot changes ordinary movement input and facing; it never sets race progress or teleports along the course. Course entry/exit uses the normal race system. Harness classes are excluded from the distributable jar.

Scenarios: near-zero-delay remote baseline, 120 ms added RTT, 300 ms added RTT, jitter around 120 ms, and recurring 600 ms stalls over a 120 ms baseline. The latter intentionally tests beyond the finish-credit ceiling. These are added delays, not promises of exact measured RTT.

Outputs: `build/latency/results/events.jsonl`, `server.csv`, `client.csv`, `summary.json`; screenshots in `build/latency/client/screenshots`. Previous results, logs and screenshots are archived automatically before another run. Server samples include ordinary ping and the fresh race probe. Telemetry is sampled every ten ticks, so sampled finish comparisons have 500 ms resolution.

## Findings from the first actual race sweep

All four initial Meadow races completed normally with a Gold rider against the AI field. Session durations were 879 / 881 / 885 / 882 ticks for baseline / 120 ms / 300 ms / jitter. These include countdown and post-finish grace, not just driving time.

Minecraft reported only 53–86 ms during the 120 ms race and 183–238 ms during the 300 ms race. After returning to jitter around 120 ms, it still reported 178–208 ms. This measured under-credit/over-credit motivated NET-05 and a second full race sweep.

## Remaining coverage and limits

- Additional scenarios include low-cooperation birds, fluid terrain outside races, dismount/remount under delay, and remote terrain-loading stalls. The initial sweep was a controlled Meadow circuit with one real client and an AI field; the paired all-track sweep below extends this coverage.
- Passenger packets for not-yet-tracked entities still appear during course transfers/field seating. The tested rider races completed; these warnings deserve a separate tracking-order investigation.
- Per-packet movement validation is not a complete movement anti-cheat or a per-player movement-time budget. Sustained malicious movement and extremely long stalls need separate treatment.
- Race stamina now uses acknowledged input prediction (RIDE-06). Stamina authority remains on the server; the complete 24-course six-racer sweep recorded zero sampled host/guest stamina or dash-lock differences. Off-course riding retains the mount-bound dash heartbeat.
- RTT assumes roughly symmetric travel. Very asymmetric routes, packet loss, slow server ticks and delays above the credit ceiling can still affect fairness. The dedicated-server sweep uses a remote baseline; the paired suite separately measures an actual integrated host.

## Validation

Unit coverage includes changing start/finish ping, finish settlement, burst limits, matched/replayed/expired RTT replies and median recovery after delay changes. A GameTest checks grid-hold stamina and release. Real-client race findings and final validation counts are recorded below after the second sweep.

## Compatibility

Dash input and entity synchronization changed. Install the matching build on the server and every client; the required version-2 dash/probe and version-3 race-input channels reject incompatible peers instead of silently giving them different behavior.

## Paired integrated-host / guest suite

Install the paired launcher's small NBT dependency with `python -m pip install -r tools/requirements-latency.txt`. The dedicated harness above creates its isolated seed world on first use.

`python tools/paired_race_harness.py --rtt 150 --rtt2 300` runs all 24 tracks in enum order (C, B, A, S), with their normal lap counts. Three rendered clients join an ordinary six-slot race: LatencyHost owns the integrated server, LatencyGuest and LatencyGuest2 connect through independent ordered TCP delay proxies, and the normal race session creates three AI opponents. The human birds match (Gold/Wonderful, 50 speed, 100 stamina, 0 intelligence, 100 cooperation), with each bird set to the course class. The AI uses the normal field roster. Racing birds do not push each other, so this measures real field simulation, entity updates, and shared server load rather than inventing pack collisions.

Every human records positions, stamina, dash lock, boost state, running ticks, screenshots, and actual incoming vehicle-correction packet counts. Server telemetry includes RTT, precise finish times, sampled tick work duration, and progress of the whole field. The analyzer fails on missing tracks, incomplete human finishes, or an incorrect field composition. AI finish counts are reported separately: the normal race ends shortly after all humans finish, so slower AI can legitimately remain unfinished. Finish differences over 150 ms are review flags, not proof that latency alone caused the difference; six-slot starting positions also differ. Any vehicle correction fails the check. New runs also fail when an AI makes no forward progress for over 30 seconds.

Use `--tracks B_FORD,S_KEEP`, `--jitter2 40`, or `--burst2 600` for targeted/stressed runs. `--duel` preserves the two-player/no-AI comparison. A 30-second lack of human forward progress ends a stuck heat and preserves the failure rather than warping the racer through it. World preparation and transfer to/from stalls use normal game APIs; traversal uses ordinary client input. Results and all three logs are archived under `build/latency-pair` before each run. Do not close test windows during a suite.

## All-course sweep findings (development history)

The initial integrated-host attempt completed Meadow and Orchard, then the integrated server paused during a between-course menu and disconnected the guest. This was a harness setup failure: the loopback listener now also marks the integrated server as published, preserving normal LAN no-pause behavior without opening a wildcard listener.

The corrected-speed, class-matched sweep uses 150 ms added guest RTT (typically roughly 155-168 ms measured) against a 0 ms host. Its first three sprint finish gaps after credit were -15.5 / +37.0 / -10.5 ms. Both sides received zero vehicle-correction packets. Downs then differed by +768.5 ms, Cider by -1656.7 ms and Lagoon by -1420.8 ms; all six races completed. The sign is guest minus host. These longer-course differences remain flagged for pad-fix reruns, not counted as proof of equal outcomes.

The speed fix changes actual ridden pace substantially because previously clipped bonuses now work. Matching bird stats, normal lap counts and ordinary client input are used on both sides; this is a controlled traversal/latency test, not an assessment of beginner difficulty or every breed/build combination.

The first full class-matched sweep completed 23/24 courses. Maelstrom stopped at the same landing for both clients: 591 sampled host corrections and 588 guest corrections. Its failed result is retained; it is not counted as a pass. RIDE-05 addresses this before the final full sweep.

The focused Maelstrom rerun after RIDE-04/RIDE-05 completed all three laps on both actual clients, with **zero vehicle corrections on both sides**, confirmed by end-of-race event counters. Guest measured RTT was 168 ms at GO and 155 ms at finish; host RTT was 0 ms. The guest's credited finish was 321.9 ms earlier, still flagged for parity review. Both completion and the remaining timing difference are retained in the report. The full 24-course verification sweep then began with these fixes.

The old boost sample-count unit check was replaced by an actual in-game voxel traversal regression. At that stage, **153 unit tests and 21 required GameTests passed**. The landing regression reproduces the native failure before exercising the corrected replay and a solid-wall rejection.

## RIDE-06: race stamina input prediction

The six-racer baseline completed Meadow, Downs and Maelstrom with all three humans finishing and zero correction packets. Added guest RTTs were 150/300 ms. Both long courses still favored the delayed guests by roughly 0.45–0.55 seconds after credit. Delayed server stamina/lock updates are a reproducible source of different local dash decisions.

Race input now carries a race epoch and sequence. Both ends run the same stamina transition and intelligence roll; the server acknowledges processed inputs, and the client rebases and replays pending input. Duplicate inputs never drain twice, stale epochs cannot affect the next race, history is bounded to 256 frames, and server processing is limited by elapsed time. Missing initialization freezes acceleration until the authoritative snapshot arrives. Ordinary non-race dash input remains unchanged. Required new channels prevent mixed clients silently using different stamina rules.

Pure tests cover 3 intelligence levels, 0/3/6/12-tick ACK delays, ordered 600 ms stalls, duplicate/gapped input, stale acknowledgements, authority corrections, queue limits, elapsed-time budgets and the empty-bar recovery cycle. These tests do not substitute for the real six-racer rerun.

## RIDE-07: boost contact uses the physical footprint

The post-prediction Cider heat still separated by roughly 1.5 seconds despite identical client stamina histories. A repeat with both guest proxies at **0 ms added RTT** reproduced the same missed pad. Per-tick geometry showed the host's center crossing just outside the corner of pad (1361,69,825), while the bird's physical footprint overlapped it. This was a spatial contact problem, independent of injected latency.

Boost traversal now checks the swept footprint against nearby pad voxels, retaining the original vertical contact range, mud/slab support, and transfer-distance limit. It does not award a boost merely because a pad is nearby: the footprint's swept segment must intersect it. The GameTest now covers both an edge contact and a near miss just outside the footprint. The full six-racer sweep will restart after validation because this changes production traversal.

## AI-01: detour approach

The six-racer suite caught Broden stationary for 1,310 ticks on B_GLACIER, at about 31% of lap one. Visible-field telemetry located the bird jumping in place near (2181.5, 72, 1635.5). The AI's old 0.03-lap early swerve selected the full outer detour before the builder's 0.012-lap connector had opened the rail. Steering now shares the builder's connector interval and blends the target lane through its entrance and exit; off-road recovery uses the lane at the current progress.

New tests check the original off-road target, connector routes across every course, color/bog choices, and an actual yellow AI bird traversing Glacier's entrance and exit. These changes are not in the already-running latency sweep; that run continues on its frozen compiled build. The AI fix will get its own build and live validation afterward.

## Completed full six-racer baseline

docs/qa/latency-six-racer-all-tracks.json records all 24 courses at 150/300 ms added guest RTT: 72 human finishes, zero vehicle corrections, zero matched-running-tick stamina differences and zero sampled dash-lock mismatches. The lowest per-client/course median FPS was 56; the worst sampled server-tick p95 was 14.46 ms. Timing differences remain flagged on Shore, Lagoon, Deeps and Starfall; grid/path controls follow. AI stagnation was observed on Glacier, Deeps, Temple, Inferno, Starfall and Citadel. This report predates AI-01 and does not claim the AI passed.

AI-01 passed three physical traversal GameTests (Glacier water, Deeps ridge, Temple mud). Standalone traversal fixtures run in a separate batch from the town-cleanup tests: normal cleanup correctly removes unregistered race NPCs. All 162 unit tests and 24 required GameTests passed; full live AI and stressed-network validation follows.

## AI-02: local steering through long-course detours

A blue AI on Starfall still stalled at the second ridge after AI-01. A new physical GameTest reproduced the exact 0.489-lap stall. A target a whole connector ahead cut the diagonal corner into the barrier; detour steering now caps lookahead at four metres. All four traversal regressions passed together: 162 unit tests, 25 required GameTests, release build and Python syntax checks. The 24-course mixed-delay live rerun uses rotated player stalls, 150 ms on guest one, and 300 ms plus +/-40 ms one-way jitter and periodic 600 ms ordered stalls on guest two.

## No-added-delay controls

`docs/qa/latency-six-racer-zero-delay-controls.json` retains Shore, Lagoon and Starfall controls with both proxies at zero added delay. Shore still differed by about 0.7 seconds, and Starfall guest two by 695 ms versus 696 ms in the delayed run. Lagoon differed by 208/748 ms rather than the earlier 1635/1323 ms; its per-tick trace records the same 12 boost activations for every human, at different arrival ticks. These are path/grid controls, not forced ties or evidence that every network effect has been eliminated. The control also exposed the Starfall blue AI failure fixed by AI-02.

## NET-06: validate ordered packet steps, not the entire arrival burst

The first stressed six-racer sweep passed Citadel and Starfall, then Glacier delivered 11 corrections to the 300 ms/jitter/stall guest. Server logs showed a 600 ms ordered backlog accumulating over 40 blocks in one server tick; the old cumulative allowance rejected it and triggered a cascade of collision corrections. The incomplete sweep is retained in `docs/qa/latency-six-racer-stress-before-packet-fix.json`.

The vehicle comparison now uses displacement from the last accepted packet while retaining native speed limits for individual steps, coordinate clamps, collision replay and final collision rejection. It never mutates physical velocity. This supersedes the old NET-01 40-block cumulative ceiling. A real vehicle-handler GameTest accepts 30 two-block packets in a single tick, then rejects a 12-block single jump and a four-block wall crossing. Regression/build gates pass: 162 unit tests, 26 required GameTests. The complete stressed suite is restarting on this build.

## AI-03: preserve valid laps through long-course bends

After NET-06, Glacier passed with zero corrections and +1.7 ms stressed-guest credited finish difference. Temple exposed a different problem: black AI Seifer completed a circuit without lap credit. A full-lap GameTest reproduced the bird leaving the legal course at progress 0.478, before the ridge. Percentage-based lookahead covered roughly 17 metres and cut across the inside rail; normal steering now caps lookahead at eight metres (four through detours). The full-lap regression now stays on course and earns its lap. All 162 unit tests and 27 required GameTests pass, and the build passes.

The analyzer now tracks geometric movement separately from credited laps, and rejects both stationary AI and uncredited line crossings. `docs/qa/latency-six-racer-stress-before-line-fix.json` records the interrupted run and its one uncredited Seifer lap without mislabelling it as a stationary bird. A new complete 24-course stress run begins with Temple.

## Final dedicated-server verification

The final one-human/five-AI Meadow sweep passed all five profiles with complete end-event telemetry and zero correction packets: baseline 520 ticks, 120 ms 522, 300 ms 526, jitter 523, and recurring 600 ms stalls 523. These are session durations including countdown/grace, not credited driving times. See `qa/latency-dedicated-five-profiles.json`. The launcher now runs its strict analyzer automatically. Final Python syntax checks, diff whitespace checks, source-hash comparison against the completed stress sweep, and release-jar exclusions passed.

## NET-07: the rider drives, the server checks (supersedes NET-01 and NET-06)

Hub race 2026-09-28 (two players and three AI on the live server) was unplayable: the host's bird was sent back 40 to 67 blocks eleven times in three minutes ("moved too quickly"), and small "moved wrongly" corrections snapped it by up to a block and a half. Server ticks were healthy (20 TPS, 7.8 ms a tick, no full GCs); the cause was the vehicle protocol.

- **No handshake for vehicles.** Vanilla numbers a player's own teleports and ignores that player's moves until the client confirms one. A vehicle has nothing of the kind, and a client ignores the server's entity teleport for a bird it drives. After a set-back the client went on driving from where it had been; every move already in flight was refused with its own correction, each correction arrived after the client had moved on, and the bird was snapped back over and over for a whole round trip.
- **Replay and refuse.** Vanilla replays each vehicle packet through the server's physics and refuses it when the result is more than a quarter block from the client's. Client and server never agree exactly on a racing bird (steps, ridges, bumps against birds each side sees in a different place).

`race/RiderAuthority` with `mixin/ChocoboVehicleMoveMixin` now take a player's chocobo moves as sent when they are possible: a speed bank of 6 blocks a tick (the fastest legitimate race bird reaches 4.6 at the 99.9th percentile), banked up to 20 ticks so a backlog after a stall catches up; no single step over 10 blocks; not through a wall (the bird's core, narrowed and lifted by its step height, swept along the step); not ending inside a block. The server still replays each step for its side effects (fall damage, pressure plates, portals, statistics, vanilla's floating check) and keeps the client's result. Every server move of a ridden bird (`RaceSession.moveRidden`: grid, set-backs, fall rescues, the walk back) and every refusal is a numbered `RaceMovePayloads.Teleport`; moves sent before the client's `TeleportAck` are dropped without a correction, so a refusal is one snap, never a chain. Unanswered teleports are resent each second and dropped after ten. Refusals are logged as `move refused #N: <reason>`.

## NET-08: the field played back from tick-stamped frames

Vanilla entity packets carry no time: a client lerps each bird toward whatever arrived last, so every burst or gap in delivery shows as a stutter, and `remoteGlideSteps` (up to eight lerp steps for a long move) left birds trailing.

Every racing bird is now sent each server tick as a `RaceMovePayloads.Frame` with the tick it was taken on, to players within 192 blocks. The client reads frames on the network thread (exact arrival time). `net/PlayoutClock` finds how far behind the server the fastest frames arrive over the last two seconds and shows the field an adaptive 1.5 to 6 ticks behind them (twice the smoothed lateness plus one); the shown tick steps exactly one per client tick, so it moves in lockstep with the game's own frame interpolation, corrected at most 5 % ahead or 20 % back. `net/FrameBuffer` interpolates between frames, carries a bird on at its last velocity for up to three ticks past the newest frame and then holds, and never sweeps across a teleport (frames over 16 blocks apart). Vanilla's position packets are ignored for a bird while its frames arrive; a bird with no frame for a second goes back to vanilla. Racer contact leads the remote field by the round trip plus the playout delay (`RacerContact.leadTicks(rtt, displayTicks)`).

Kill switches: `-Dchocobosreborn.vanillaRiderMovement=true` on the server and `-Dchocobosreborn.vanillaFieldPlayback=true` on a client put each half back on vanilla's handling.

Measurement: the harness client now writes `motion-<name>.csv` (every other racer, every client tick), `frames-<name>.csv` (per-frame p50/p99/max, frames over 33 ms, bird render CPU per frame, playout delay) and the acknowledged-teleport count in the client csv. `tools/analyze_smoothness.py <results>` reports judder (a tick's step against its neighbours, relative to speed), stalls and leaps per thousand ticks, frame pacing, and vanilla corrections / refusals / acknowledged teleports.
