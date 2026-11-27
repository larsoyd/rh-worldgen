# RHWorld Engine

RHWorld is a procedural world engine whose guarantees are theorems, not tuning. It keeps a world's arithmetic
structure separate from its look, and it writes a machine-readable certificate saying exactly what is guaranteed,
which theorem guarantees it, and what a third party can recheck from public data alone. The engine is game
independent. A Minecraft Fabric mod, *The Warden's Key*, is its reference game.

The guarantees rest on the Generalized Riemann Hypothesis (GRH) for Dirichlet L-functions, which has a
machine-checked Lean proof:
[leanprover-community/riemann-hypothesis](https://github.com/leanprover-community/riemann-hypothesis), used at main
7c4ccbd64d02774b0a5b40a5f923f5b7a18e8abc.

## What GRH buys here, and what it does not

A possibly hostile server operator (the **Warden**) publishes a key `n = p q` and keeps its factors. The world has a
public layer that anyone computes from `n`, and a hidden layer that needs `p`. Under GRH, through an explicit
published bound (Lamzouri, Li and Soundararajan 2015, Theorem 1.1), the primes up to a budget `B` that depends only on
the bit length of `n` reach every state of both layers, for **every** key that passes the client checks and, for the
hidden layer, the reveal check that `p` and `n/p` are distinct primes. In the game that means:

- **No key can hide a realm.** A Wild Gate ride lands on the colour you asked for, and the hidden half is exactly
  uniform given that colour. So the Warden can never honestly say "my key just never leads to Ember". A realm that
  stops appearing is evidence of cheating during the season: missing from `t` rides of its colour has probability
  `2^-t` under any admissible key.
- **A canonical public landmark.** The least `x <= B` with Jacobi symbol `(x/n) = -1` always exists, so every client
  finds the same small witness within a fixed budget.

What it does not buy:

- **No change in output.** On every key anyone knows how to build, the certified gate behaves exactly like a gate
  over the first thousand primes, or a gate built from hashed numbers. GRH replaces an assumption about how the
  Warden chose the key with a theorem. It does not make the game faster or play differently.
- **Existence only, and loose.** Real witnesses are tiny (at most 11 at 1024 bits and 7 at 2048 bits over 20 random
  keys of each size, 349 for the rigged sample) against `B = 503,791` at 1024 bits. Nothing is claimed about density, fairness or any window other
  than `1..B`.
- **Only what is checked.** Claims about the hidden factors hold only after the Warden reveals `p` and the reveal
  check passes. Hiding rests on factoring being hard, not on RH. Uniformity is exact for uniform dice, and the shipped
  dice come from SHA-256.

The exact statements, preconditions and derivations are in [Contracts](#contracts).

## Components

| part | language | what it is |
|---|---|---|
| `engine/` | Java, no dependencies | arithmetic, contracts RHW-C1 v1 and RHW-C3 v1, public and private-key worlds, certificates, the `rhworld` CLI |
| `mechanics/` | Java | game-independent rules: Wild Gate, realms, dice chain, ride log, audit, season, replay |
| `fabric/` | Java, Fabric | the Minecraft 26.3 mod: world preset, realms, hubs, gates, `/rhworld` commands |
| `verify/` | Python | independent checkers that share no code with the above: certificates, ride logs, region files, the three-arm benchmark |
| `rhworld/` | Python | the original reference generator, kept as the mathematical oracle (see below) |

Requirements: a JDK 25 that Gradle can find (the build runs its daemon on Java 25 and the engine targets Java 21), Python 3.14 with numpy, Pillow and mpmath, Minecraft 26.3 with Fabric
Loader 0.20.1 or newer and Fabric API 0.167.0+26.3 for the mod.

## Engine command line

Build once:

    timeout 900 ./gradlew :engine:installDist --no-daemon

This builds `engine/build/install/rhworld/bin/rhworld`.

    rhworld certify --n-file key.n --out world.json                     # public modulus, no factors
    rhworld certify --gm samples/rigged-1024/gm-key.json --out world.json
    rhworld certify --n <decimal> --p <decimal> [--proofs proofs.json] --out world.json
    rhworld verify world.json                                           # rebuilds every checked field from n
    rhworld bench --moduli moduli.json --repeat 5

`--proofs` takes Pocklington certificates for both factors. With valid proofs for both the factors are marked
`proved`, and invalid or partial proofs are refused. Without `--proofs` they are marked `mr25-probable`. Exit codes: 0 accepted or verified, 2 refused, 1 usage or input error. Diagnostics go to
stderr as `[tag] key=value` lines, and the last line of a refusal is `refused: <reason>`. `--telemetry json` gives one
JSON object per line instead. A certify run on the rigged sample prints:

    [c1.admit] bits=1024 budget=503791 trial_limit=504100 primes_tested=41840 us=43253
    [c1.g2] witness=349 bound=503791
    [c3.basis] layers=1 rank=1 steps=70 last=349 bound=503791
    [reveal] factors_status=mr25-probable
    [rhworld] verdict=accepted world_id=12a2848db86507d770dc9ae402b50e03688a1f01600e34e116953f9e58f6fbf5 revealed=true

A certificate (`rhworld.cert/1`) is canonical JSON with four sections: what any client can recheck (`recomputed`),
the theorem chain and its hypotheses (`theorem`), what becomes checkable at the reveal (`deferred`), and what it does
not prove (`not_proved`). Any other bytes are refused as `refused: certificate not canonical`, and an edited field as
`refused: field mismatch <field>`. `rhworld verify` is the engine rechecking itself. The independent check is
`verify.certverify` in Python.

## Playing The Warden's Key

The server operator is the **Warden**. Before the season they publish a **modulus**, a large odd number `n`. The world
refuses to load without a modulus that passes its checks. The two prime factors of `n` are the **operator key**, which
the Warden keeps secret.

**Realms.** There are four realms: the Overworld, called **Hearth**, and three realm dimensions, **Ember**, **Mist**
and **Frost**. Terrain is ordinary Minecraft terrain. Each realm has a **hub** at block 0,0 with a Sigil pedestal and
two **Wild Gates**. Prime shrine signs ring the hub. In this version they are decoration and cannot be entered.

**Attunement and colours.** Every player carries an attunement, a number below `n` that starts at 1. Your
attunement decides which realm you stand in. Anyone can compute half of it from `n` alone, the **colour**: Hearth
and Ember are **warm**, Mist and Frost are **cold**. Which warm realm or which cold realm you are in depends on the
Warden's secret factors.

**Riding a Wild Gate.** Step into the Warm gate and you land in Hearth or Ember. Step into the Cold gate and you land
in Mist or Frost. Each ride multiplies your attunement by a product of tens of thousands of small primes, picked by
your dice, and you arrive at the hub of your new realm.

**Dice.** The dice come from a hash chain you commit to before your first ride. `/rhworld commit <hex>` publishes the
commitment, and each ride uses the next link, which you queue with `/rhworld open <hex>`. A gate with no queued link
refuses with `refused: no dice opening queued`. The server checks each link before the ride. It learns a link when you
queue it, never earlier, and the chain was fixed before play. You choose the chain's top, so you could try many tops
offline, but without the hidden layer that cannot steer the hidden realm (this rests on quadratic residuosity
being hard without the factors, not on RH). Once players have worked out the hidden layer
(see the codex limit), grinding tops does steer it. A player without a client tool can let the server hold the
chain. Those rides are marked `dice=server-held` everywhere and carry no promise about the dice.

**Sigils and the Concord.** Each realm's pedestal gives that realm's Sigil, once per player. Hold all four and you earn
the **Concord** advancement, in about five rides on average if you ride the colour you still need.

**Death** keeps your attunement, your ride log and your Sigils. You respawn at the hub of the realm you were in.

**Audit and reveal.** `/rhworld audit` counts your rides per colour and the realms you arrived in, flags any report
whose colour is wrong, and gives the chance that an honest server would show a gap that large. At the end of the season
the Warden runs `/rhworld reveal <p>`. The server checks the factors through the engine, replays the logs of the
players online at that moment against them, names the first false report if there is one, and writes the revealed
certificate. Offline players can export their log with `/rhworld log` beforehand and replay it themselves.

What the guarantee does not give a player:

- A single ride can still be misreported between the two realms of one colour. Only statistics during the season, or
  the reveal, catch that. A design with per-ride proofs (a verifiable random function) would audit single rides better.
- It holds for keys that pass the client checks **and** later pass the reveal check. Keys shaped like `p^3` or
  `p r^2` pass every client check, and only the reveal catches them.
- After roughly 41,816 rides against one 1024-bit key, the community can work out the hidden half for every small
  prime, so the hidden layer becomes public, although the factors do not. End a season well before that.
- It reaches only players who replay their rides. A vanilla client plays with no check.

### Checking your rides

Export your log with `/rhworld log` and save it, for example as `rides.json`. Build the launcher once, then replay:

    timeout 900 ./gradlew :mechanics:installDist --no-daemon
    mechanics/build/install/mechanics/bin/mechanics replay --log rides.json

The replay recomputes every ride from the public modulus and your dice links. It checks that each link follows the last,
that every arrival is what the dice produce, and that every reported realm has the right colour. It ends with
`[rhworld] verdict=ok rides=<k>` and exit code 0, or prints `refused: <reason>` and exits 2. The same launcher passes
every other subcommand to the engine CLI. Run through `./gradlew :mechanics:run` instead, relative paths resolve
from `mechanics/` and any failure shows as Gradle's exit code 1. Replay proves the rides shown are consistent. It cannot prove the log is
complete, so keep count of your own rides.

## Running the mod

Development client:

    ./gradlew :fabric:runClient -Prhworld.key=$PWD/samples/random-1024/gm-key.json

Then Create World, World Type **RHWorld: Wild Gates**. That preset carries the `random-1024` sample `n`, so it needs
that key. **RHWorld: Wild Gates (rigged key)** uses `samples/rigged-1024/gm-key.json`. Without an operator key the world loads
public: gates and sigils refuse `refused: no operator key on this server`, while `verify`, `contracts` and
`resources` work. The sample keys are public test data, not secrets.

Development server: put `level-type=rhworld\:wild` in `fabric/run/server.properties` and `eula=true` in
`fabric/run/eula.txt`, then run `./gradlew :fabric:runServer -Prhworld.key=<absolute path to key json>`.

Installable jar: `./gradlew :fabric:build` writes `fabric/build/libs/rhworld-0.1.0.jar`, with the engine and
mechanics nested inside. A server needs the Fabric server launcher for Minecraft 26.3 and Loader 0.20.1, Fabric API
0.167.0+26.3 and the jar in `mods/`, `level-type=rhworld\:wild`, and the key at `config/rhworld/key.json` (operator
only, never published). Clients need the mod too. A bad modulus refuses to start with the engine's reason, for
example `refused: small factor`.

| command | what it shows |
|---|---|
| `/rhworld inspect` | your attunement, public colour, realm, rides, sigils and dice custody |
| `/rhworld contracts` | the world id, the engine contracts with their theorem chains and hypotheses, then the adapter's own items, each marked `kind=adapter, not an engine contract` |
| `/rhworld verify` | the recomputed certificate fields, the engine self-check and the exported certificate path |
| `/rhworld neighbours` | shrine signs within 64 blocks |
| `/rhworld route <realm>` | which gate to ride |
| `/rhworld resources` | the hub and sigil of each realm |
| `/rhworld audit` | per-colour counts, mismatches, the weakest realm's probability and the custody marker |
| `/rhworld log` | the public replay verdict and the ride log as JSON |
| `/rhworld commit <hex>`, `/rhworld open <hex>` | your own dice chain and its next opening |
| `/rhworld reveal <p>` | operator only: the engine reveal, a replay of every online player's log, the revealed certificate |

## Tests and benchmarks

Everything runs headless, every command under a timeout.

| command | verdict |
|---|---|
| `timeout 1500 ./gradlew build --no-daemon` | JUnit for engine and mechanics, Fabric game tests `All 17 required tests passed :)` |
| `./run_tests.sh` | the Python oracle and the independent checkers |
| `fabric/tools/allworld.sh` | every dedicated-server scenario, `[rhworld-all] verdict=pass` |
| `fabric/tools/ordertest.sh rhworld:wild` | two chunk orders and thread counts, `chunks=116 differ=0` |
| `fabric/tools/clienttest.sh` | a real player to the Concord on a private virtual display with audio muted |
| `python3 -m verify.mca <world> <hashdump log>` | placed blocks recomputed from region files |
| `python3 -m verify.crosscheck engine/build/install/rhworld/bin/rhworld` | engine, oracle and independent verifier agree |
| `python3 -m verify.gate_bench` | the three-arm comparison: hashed gate, first thousand primes, certified gate |
| `timeout 900 ./gradlew :engine:bench --no-daemon` | `engine/build/bench/report.json` for the pinned `samples/bench-moduli.json` |

At 2048 bits on the development machine, opening and verifying a world takes about 50 ms and a certified ride about
11 ms. Rigged keys cost the same as random ones.

## Contracts

Every certificate names the contract it relies on and its version. A version changes whenever a formula, a constant or a
precondition changes. Both contracts below rest on one machine-checked theorem and one published theorem. The last step
is our own short argument, given here in full.

Labels: **Lean** means machine-checked in the riemann-hypothesis repository at main
7c4ccbd64d02774b0a5b40a5f923f5b7a18e8abc. **Published** means stated and proved in a paper we read. **Ours** means the
derivation written below, which nobody has machine-checked.

### The shared theorem chain
1. Lean: `LFunction_ne_zero_of_half_lt_re` (src/Main.lean, statement `DirichletLNonvanishingRightOfHalf` in
   src/Statements.lean). For every modulus N, every Dirichlet character chi mod N (primitive or not) and every s with
   Re s > 1/2, L(s, chi) is not zero, except for the trivial character at s = 1. This is the generalized Riemann
   hypothesis for Dirichlet L-functions.
2. Published: Lamzouri, Li, Soundararajan, "Conditional bounds for the least quadratic non-residue and related problems",
   Math. Comp. 84 (2015), arXiv 1309.3595v3, Theorem 1.1, as stated there: "Assume GRH. Let q ≥ 3000 be an integer, and
   let H be a proper subgroup of G = (ℤ/qℤ)*. ... (2) Suppose q is not divisible by any prime below (log q)². Then there
   is a prime ℓ ≤ (log q)² with ℓ not lying in the subgroup H." Here log is the natural log. The 2017 corrigendum was not read.
   Per the note in arXiv v3, its correction touches only the asymptotic Theorems 1.2 and 1.3.
3. Budget. For a number of bit length k, B(k) = floor((k ln 2)^2), computed exactly in integer arithmetic. It depends only
   on k, so it is fixed before the number is seen. B(1024) = 503791, B(2048) = 2015165. Since ln q < k ln 2, every prime
   below (ln q)^2 is at most B, and the prime l from step 2 is an integer at most B.

### RHW-C1 v1
Least canonical witness and hidden layer.

Input: an odd modulus n of k >= 1024 bits published by the game master, who keeps its factors.
Refusals, checked in this order:
1. `refused: modulus too small or even`: n is even or shorter than 1024 bits.
2. `refused: jacobi character is principal`: n is a perfect square.
3. `refused: no hidden layer`: n is a probable prime.
4. `refused: small factor`: some d from 3 to max(B, s^2) divides n, where s = isqrt(B - 1) + 1. The theorem needs only
   the range up to B. The s^2 term (at most about 2 sqrt(B) more) matches the reference oracle, which lays the prefix out
   as a square of side s.

Statement, for every n that passes:
- Public: the least x <= B with Jacobi symbol (x/n) = -1 exists. Any client finds it in at most B steps. A scan that
  goes past B stops with `refused: budget exceeded`. That outcome would contradict the theorem chain.
- Hidden: for every prime factor p of n, the least x <= B with Legendre symbol (x/p) = -1 exists. So the hidden layer is
  not constant on the prefix 1..B.

Derivation (ours): take q = n in step 2. The hypotheses hold by the client checks: q >= 3000 because n has at least 1024
bits (refusal 1), and no prime below (ln n)^2 divides n because refusal 4 has trial-divided by everything up to B. H is the
kernel of (x/n), which is proper because n is not a square, or the kernel of (x/p) read mod n, which is proper by the
Chinese remainder theorem. The prime from step 2 is at most B and does not divide n.

### RHW-C3 v1
Prime basis, routes to states and the gate.

Input: layers n_1, ..., n_r. These are public moduli, or after a reveal the game master's factors p and q. The state of a
tile x is r bits, and bit i is 1 when (x/n_i) = -1. States multiply: the state of x y is the XOR of the states of x and y.
M is the least common multiple of the layers, k is its bit length and B = B(k). The alphabet S is all primes up to B.
Refusals, checked in this order:
1. `refused: no layers`: r = 0.
2. `refused: too many layers`: r > 8.
3. `refused: modulus too small or even`: some layer is below 3 or even. In this contract the string has no 1024-bit
   floor (the layers p and q are about 512 bits each). It is the same text as in RHW-C1 with a different test.
4. `refused: dependent layers`: the product of some nonempty set of layers is a perfect square.
5. `refused: lcm below 3000`: M < 3000.
6. `refused: small factor`: some prime up to B divides some layer.
7. `refused: budget exceeded`: raised at run time if the scan below passes B before it finds r primes. This would
   contradict the theorem chain.

Scan: test primes 2, 3, 5, ... in increasing order. Keep a prime when its state is not an XOR of the states already kept.
Stop once r primes are kept.

Statement, for every input that passes refusals 1 to 6:
- Basis: the scan keeps r primes, all at most B.
- Routes: from every tile x coprime to M to every target state there is a route of at most r multiplications by kept
  primes. It is found by solving r linear equations over the two-element field. No discrete logarithm is needed. Only
  someone who knows the states of the kept primes can compute it: anyone for public layers, but for hidden layers only
  the game master until the reveal. A player cannot plan a route to a hidden state, only learn that one exists.
- Plain gate: multiply x by the product of a random subset of S, each prime taken with probability 1/2 independently.
  The resulting state is exactly uniform over all 2^r states, from any start.
- Steered gate (the one the game uses, layers n and p): draw the subset as above. If the visible colour (x'/n) of the
  result is not the colour the player asked for, toggle the G2 witness w in the subset. The colour is then always the one
  asked for, and the hidden bit (x'/p) is exactly uniform given that colour, from any start. This needs the reveal
  condition below (p and n/p distinct primes).
- Exactness is relative to uniform dice. The shipped dice are SHA-256 outputs of the player's seed, so the uniformity
  holds as far as those outputs are indistinguishable from uniform bits.

Derivation (ours):
- The layers reach every state. A product of layers gives a character mod M that is trivial exactly when the product is a
  square. If a prime divides the product an odd number of times, the Chinese remainder theorem gives a unit where the
  character is -1. Refusal 4 makes every such character nontrivial. Hence the state map from the units mod M onto the r
  bits is onto.
- In two lines: if the primes up to B had states inside a proper subspace V, the units with state in V would be a proper
  subgroup containing every prime up to B, which step 2 forbids. So the primes up to B span all states.
- The scan in detail. If the kept states span only a proper subspace V, the units whose state lies in V form a proper
  subgroup. Step 2 with q = M (refusals 5 and 6 give its hypotheses) yields a prime at most B outside that subgroup. The
  least such prime is the next one the scan keeps, because every smaller prime lies in V. After r rounds the kept states
  are a basis.
- The plain gate is uniform. The map from subsets of S to states is linear over the two-element field and onto, by the
  previous point. So every state has exactly 2^(|S| - r) subsets mapping to it.
- The steered gate. Toggling w maps every subset onto one with the asked colour, two subsets to each, so the result is
  uniform on the subsets E with that colour. On E the hidden bit is an affine function of the subset. It is not constant
  there: otherwise the hidden form would be zero or equal to the colour form on S, so (./p) or (./q) = (./n)(./p) would
  be trivial on every prime up to B, against the span argument for the layers p and q. A non-constant affine function on
  an affine subspace takes both values equally often.

### What is not proved
- Existence only. The contracts say a witness, a basis and a route exist within the budget. They say nothing about how
  many tiles of each kind there are or how they are spread.
- Prefix only. Every statement is about the integers 1 to B (and products of primes up to B). Nothing is claimed for a
  window starting elsewhere.
- Nothing here is machine-checked beyond the Lean theorem. The step to explicit bounds is the published Theorem 1.1, and
  the wiring is ours.
- The guarantees are worst-case bounds. Observed values are tiny: the least G2 witness was at most 41 over 5000 random
  2048-bit n, and basis primes reached at most 19 over 72 random keys at 1024 and 2048 bits. The rigged 1024-bit sample
  key in samples/rigged-1024 has basis primes 349 and 359 (72 primes scanned), against a budget of 503791.
- Claims about p and q = n/p hold only after a reveal shows that p and n/p are distinct primes. The certificate states
  which test was used: a Pocklington certificate gives "proved", and a probabilistic test gives "probable". Without such
  a reveal, a key like p^3 or p r^2 passes every client check, and its visible layer equals the hidden one.
- No statement is made about windows away from the origin, about distances on the map, about routes to a given tile, or
  about how fast any walk other than the gate mixes.
- Hiding the hidden layer rests on the hardness of factoring, not on the Riemann hypothesis.

### The codex limit
Each gate ride lets players learn one linear equation in the hidden bits (l/p) of the primes l up to B. Shrines are inert in v1: no
portal multiplies by a single prime. If one did, each ride would reveal one bit (l/p) directly, and 25 rides would give
every l up to 97. After about
|S| independent equations (41816 at 1024 bits) players know all of them. Then the hidden bit of every tile whose prime
factors are all at most B is public, and that includes every tile of the prefix 1..B. Recovering p itself from these bits is an open
problem. We know no algorithm for it, but we do not claim it is impossible. A season should end well before |S| rides
have been made against one key.

## The Python reference generator

The original fog-of-war generator stays in `rhworld/` as the mathematical oracle. The engine is tested against its
outputs, and its maps and reports are reproducible from the samples. It uses a spawn patch of tiles, which the
Minecraft game does not.

A fog-of-war world generator whose one hard promise rests on the Generalized Riemann Hypothesis (GRH) for Dirichlet
L-functions, which has a machine-checked Lean proof:
[leanprover-community/riemann-hypothesis](https://github.com/leanprover-community/riemann-hypothesis).

### What it does

A game master (GM) publishes an odd number `n` of at least 1024 bits and keeps its two prime factors `n = p q` secret.
Every tile of the world is a positive integer `x`. The spawn patch is a square of side `S`. Tile `x` sits at row
`(x - 1) div S`, column `(x - 1) mod S`, so tile 1 is the top-left corner.

- **Visible layer** (anyone can compute it from `n`): the Jacobi symbol `(x/n)`, which is +1 or -1. Players see
  **land** where it is +1 and **water** where it is -1.
- **Hidden layer** (only the GM can compute it, because it needs `p`): the Legendre symbol `(x/p)`. It splits land
  into plains and forest, and water into shallows and deep water. Players learn it when the GM reveals `p`.

The GM chooses `n` and could try to rig it. For example, a GM could choose `n` so that the hidden layer is the same
everywhere near spawn, which would make the fog hide nothing. The generator refuses any `n` that fails its checks.
For every `n` that passes them, GRH guarantees the following, where G1 needs `n = p q` with `p`, `q` distinct primes. The
reference generator cannot check that before the reveal: `n = p^3` or `n = p r^2` passes its checks, and then the
visible layer equals the hidden one.

> **G1.** Some tile `x <= B(n)` has `(x/p) = -1`, and some tile `y <= B(n)` has `(y/q) = -1`. Tile 1 always has
> `(1/p) = +1`, so the hidden layer is never constant on the spawn patch.
>
> **G2.** Some tile `x <= B(n)` has `(x/n) = -1`. The smallest such tile is a canonical public landmark, and every
> player can find it in at most `B(n)` steps.

Here `B(n) = floor((k ln 2)^2)`, where `k` is the bit length of `n`. It is at least `floor((ln n)^2)` for every `n` of
that length, and it is public before `n` exists. For `k = 1024`, `B = 503,791` and the spawn patch side is
`S = ceil(sqrt(B)) = 710`. For `k = 2048`, `B = 2,015,165` and `S = 1420`. The budget is fixed before any scanning.
If a scan ever ran past it, the generator would return `refused: budget exceeded`. Under GRH that cannot happen.

### How this uses the proof

1. **Machine-checked.** The Lean repository proves `LFunction_ne_zero_of_half_lt_re`, whose statement
   `DirichletLNonvanishingRightOfHalf` says: for every modulus `N` and every Dirichlet character `χ` mod `N`,
   `L(s, χ) ≠ 0` whenever `re s > 1/2` (the one excluded point is the principal character mod `N` at `s = 1`, where nothing is claimed). This is GRH for
   Dirichlet L-functions, with no explicit constants.
   The paper below needs GRH only for primitive characters, which the repository also states as
   `LFunction_eq_zero_re_of_isPrimitive`.
2. **Published, not machine-checked.** Y. Lamzouri, X. Li and K. Soundararajan, *Conditional bounds for the least
   quadratic non-residue and related problems*, Math. Comp. 84 (2015), Theorem 1.1, part (2). Assume GRH, let
   `m >= 3000`, and let `H` be a proper subgroup of the units mod `m`. If `m` has no prime factor below `(log m)^2`,
   then some prime `l <= (log m)^2` is not in `H`. (The paper calls the modulus `q`. It is renamed here because `q` is
   a factor of `n`.) A 2017 corrigendum to the paper was not consulted here. The arXiv
   version (v3) says its correction touches only Theorems 1.2 and 1.3. Bach (1990) proved the first explicit bound of this kind. Its
   constants are quoted in this paper and are not used here.
3. **Applied here,** with `m = n`. The client checks guarantee `n >= 3000` and that `n` has no prime factor up to
   `B >= floor((ln n)^2)`. This is the theorem's hypothesis, and it also makes every tile up to `B` coprime to `n`. The
   residues mod `n` with `(x/p) = +1` form a proper subgroup of the units mod `n`. So do those with `(x/q) = +1`, and
   those with `(x/n) = +1` (this needs `n` not to be a square). Each subgroup misses a prime `l <= (ln n)^2`. Since `l`
   is an integer, `l <= floor((ln n)^2) <= B`, and tile `l` has symbol -1. That gives G1 and G2.

Without GRH the best bounds are Burgess-type. Asymptotically the bound is `p^(1/(4 sqrt e) + eps)`, about `e^54` for a
512-bit `p`, but its constants are not explicit. In fully explicit form it is about `p^(1/4) ln p`, roughly `e^95`.
Both are far larger than any map.

### Honest limits

- **Existence only.** G1 promises at least one hidden -1 tile among about half a million. A spawn patch that is almost
  uniform is still allowed. The guarantee is "the GM cannot make the fog empty", not "the fog is fair".
- **The guarantee is loose.** The best rigging we know of (see `--rigged`) pushes the first hidden -1 tile to
  about `ln p`. That is 349 in the rigged sample, against a budget of 503,791, about 1,400 times smaller. So in
  practice GRH constrains nothing a GM can reach today. It forbids only a hypothetical, far stronger rigging. For random 2048-bit
  `n`, the first visible -1 tile is usually below 20 (the largest seen in 5000 samples was 41). GRH protects the worst
  case. It does not shape the typical world.
- **Rigging can also be stopped without GRH, by changing who picks the map.** Commit-reveal or jointly generated
  randomness, with the map committed in a Merkle tree, gives hidden terrain that no dealer can rig, unconditionally.
  This design is worth it only when the hidden layer must be multiplicative (so that players can deduce it, for example
  from `(x/q) = (x/n)(x/p)`). In that case the dealer necessarily holds a trapdoor.
- **Not the only way to get this.** An interactive protocol, where the GM proves in zero knowledge that a hidden -1
  tile exists below an agreed bound, gives the same promise without GRH. GRH's advantage is that it needs no
  communication and no trust in the GM. Likewise, a hash chain (first `i` with `(H(n, i)/n) = -1`) gives a canonical
  visible landmark without GRH. Its bound, though, relies on treating the hash as a random oracle, and its landmark is
  a huge number rather than a small tile near spawn. The report records the hash-chain index next to the GRH witness.
  See `hash_chain_index` and `hash_value` in `rhworld/fog.py`; the hash is SHAKE-256 of the big-endian bytes of `n`
  followed by the 8-byte index, reduced mod `n`.
- **Hiding is not RH.** Players cannot read the hidden layer because factoring `n` is hard. That has nothing to do
  with RH.
- **Only part of the chain is machine-checked.** The Lean proof gives GRH without explicit constants. The explicit
  bound used here, LLS 2015 Theorem 1.1 part (2), is a published consequence and is not machine-checked here.
- **Two-factor rigging shows. One-factor rigging does not.** Rigging both factors forces small tiles to `+1` in both,
  so the player view gets excess land: 62% instead of 50%, which players can see from `n` alone. Rigging only `p`,
  which is enough to attack G1 for `p`, leaves the player view balanced (measured 50.05% land, against 50.02% for a
  random GM).
- **Checkable only at the reveal.** Players can confirm G1 only after the GM publishes `p`. By the theorem it always
  holds.
- **The maps look like noise.** Neighbouring tiles are unrelated integers and Jacobi symbols behave like coin flips,
  so the full maps look like static: a game board, not terrain. The zoomed views make individual tiles readable. The
  prime ledger shows what actually decides the map. Rigged worlds lean toward plains (about 43% instead of
  25%), because every tile built from small numbers is forced to +1 in both factors.
- **Tests cannot test GRH.** The test suite checks the guarantee on many random and rigged worlds, and on every
  two-prime world `3000 <= n < 10^6` that passes the checks. Beyond that, the guarantee is the theorem chain above and nothing else.

### Client checks

The generator returns `refused: <reason>` and exit code 2, and writes no maps, when:

| condition | reason |
|---|---|
| `n` even or shorter than 1024 bits | `modulus too small or even` |
| `n` a perfect square | `jacobi character is principal` |
| `n` a probable prime | `no hidden layer` |
| a prime factor of `n` at most `max(B, S^2)` (this covers the theorem's hypothesis and makes every tile coprime to `n`) | `small factor` |
| a key file whose `p q` is not `n` | `gm file factors do not match n` |
| a key file factor that is not a probable prime | `gm factor not prime` |
| a witness scan passing `B(n)` without a -1 tile | `budget exceeded` |

### Map legend

| GM view | colour | visible `(x/n)` | hidden `(x/p)` |
|---|---|---|---|
| plains | `#c4d278` | +1 | +1 |
| forest | `#2e6e34` | +1 | -1 |
| shallows | `#78b4dc` | -1 | +1 |
| deep water | `#183c82` | -1 | -1 |

| player view | colour | visible `(x/n)` |
|---|---|---|
| land | `#b0a88c` | +1 |
| water | `#607896` | -1 |

Rings mark landmarks. **Yellow** (radius 5) is the G2 witness: the first water tile, which players can see. **Red**
(radius 3, GM view only) is the G1 witness: the first forest or deep-water tile. Witnesses usually sit in the top row,
so their rings are clipped at the map edge. The terrain names and colours are flavour. Only the four classes carry
meaning.

**Prime ledger** (`ledger.png`, GM only): the primes `2, 3, 5, ...` in order, each coloured by its class. The symbols
are multiplicative, so a tile's class follows from the classes of its prime factors, and the ledger decides the whole
map. The least witness is always a prime. The red bar under the strip marks the G1 witness and the yellow bar marks
the G2 witness. In the rigged sample the ledger is solid plains up to 331, then shows the bars at 349.

### Usage

Requires Python 3.14 with numpy, Pillow and mpmath (mpmath is used only by the tests).

```
python3 -m rhworld gm --bits 512 --seed 1 --out gm-key.json            # sample GM, two 512-bit primes
python3 -m rhworld gm --bits 512 --seed 1 --rigged --out rigged.json   # adversarial GM
python3 -m rhworld world --gm gm-key.json --out out/                   # GM view, player view and report
python3 -m rhworld player --n <decimal n> --out out/                   # player view from n alone
./run_tests.sh                                                         # full suite, under a timeout
```

`world` writes `player.png`, `gm.png`, 8x zooms around the witness (`player_zoom.png`, `gm_zoom.png`), the prime
ledger `ledger.png` and `report.json`. The report holds the bit length of `n`, `B`, `S`, the G1 witnesses for `p` and `q`, the G2 witness,
the hash-chain index and tile counts per class. It never includes `p` or `q`. The player view is byte-identical
whether it comes from the key file or from `n` alone.

The `gm` command uses Python's seeded `random` module so that samples can be reproduced. It is **not** a secure key
generator. A real game needs factors from a cryptographic source.

### Samples

`samples/random-1024` and `samples/rigged-1024` each hold a key file, both maps, both zooms, the ledger and a report.

- **Random GM:** G1 witnesses 2 and 3, G2 witness 2.
- **Rigged GM:** every prime up to 331 is a residue mod both factors. The witnesses are G1 = 349 and 359 and G2 = 349,
  still far below `B = 503,791`.

These key files are public test data, not secrets.
