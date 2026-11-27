# RHWorld

A procedural world engine whose fairness guarantees are theorems. The server operator publishes a key, and every
client can check from public data alone that the key cannot hide any part of the world. Each world comes with a
machine-readable certificate that states what is guaranteed, which theorem guarantees it, and what is not proved.

The engine does not depend on any game. **The Warden's Key**, a Minecraft Fabric mod, is the reference game.

The guarantees assume the Generalized Riemann Hypothesis (GRH) for Dirichlet L-functions. They use the Lean statement in
[leanprover-community/riemann-hypothesis](https://github.com/leanprover-community/riemann-hypothesis) (main
`7c4ccbd`) and an explicit bound from Lamzouri, Li and Soundararajan (2015).

## How it works

The operator, called the **Warden**, publishes a key `n = p q` and keeps its factors secret. The world has two layers:

- a **public layer** that anyone computes from `n`
- a **hidden layer** that needs `p`

For every key that passes the client checks, the primes up to a fixed budget `B` reach every state of both layers. For
the hidden layer the key must also pass the check made when the factors are revealed. The budget depends only on the bit
length of `n`: `B = 503,791` at 1024 bits. In the game this means:

- **No key can hide a realm.** A gate ride always lands on the colour you asked for. The hidden realm is exactly uniform
  given that colour. A realm that is missing from `t` rides of its colour has probability `2^-t` under an honest key.
- **One public landmark.** Every client finds the same small witness within the budget.

## Requirements

- JDK 25 (the engine targets Java 21)
- Python 3.14 with numpy, Pillow and mpmath
- For the mod: Minecraft 26.3, Fabric Loader 0.20.1 or newer and Fabric API 0.167.0+26.3

## Project layout

| path | language | contents |
|---|---|---|
| `engine/` | Java, no dependencies | arithmetic, contracts, worlds, certificates, the `rhworld` CLI |
| `mechanics/` | Java | game rules: Wild Gate, realms, dice chain, ride log, audit, season, replay |
| `fabric/` | Java, Fabric | the Minecraft mod |
| `verify/` | Python | independent checkers that share no code with the Java side |
| `rhworld/` | Python | the reference generator, used as a test oracle |
| `samples/` | | public test keys and their outputs |

## Engine CLI

```sh
./gradlew :engine:installDist --no-daemon
engine/build/install/rhworld/bin/rhworld <command>
```

```sh
rhworld certify --n-file key.n --out world.json            # public key only
rhworld certify --gm samples/rigged-1024/gm-key.json --out world.json
rhworld certify --n <n> --p <p> [--proofs proofs.json] --out world.json
rhworld verify world.json                                  # recheck every field from n
rhworld bench --moduli moduli.json --repeat 5
```

`--proofs` takes Pocklington certificates for both factors. With them the factors are marked `proved`, otherwise
`mr25-probable`.

Exit codes are `0` for accepted or verified, `2` for refused, and `1` for a usage error. Diagnostics go to stderr as
`[tag] key=value` lines, and the last line of a refusal is `refused: <reason>`. Use `--telemetry json` to get JSON lines
instead.

A certificate (`rhworld.cert/1`) is canonical JSON with four sections:

- `recomputed`: what any client can recheck
- `theorem`: the theorem chain and its hypotheses
- `deferred`: what becomes checkable once the factors are revealed
- `not_proved`: what the certificate does not claim

## The Warden's Key

There are four realms. **Hearth** (the Overworld) and **Ember** are warm, and **Mist** and **Frost** are cold. Each realm
has a hub at 0,0 with a Sigil pedestal and two **Wild Gates**, one warm and one cold.

Every player has an **attunement**, a number below `n` that starts at 1 and decides which realm they are in. The colour
is public. Which realm of that colour the player is in depends on the secret factors. A ride through a gate multiplies
the attunement by primes picked by the player's dice, and the player lands in a realm of the gate's colour.

- **Dice** come from a hash chain the player commits to before their first ride. Players can also let the server hold
  the chain. Those rides are marked `dice=server-held` and carry no promise about the dice.
- **Concord**: collect the Sigil of all four realms. It takes about five rides on average.
- **Death** keeps your attunement, ride log and Sigils.
- **Audit and reveal**: `/rhworld audit` shows per-colour statistics during the season. At the end the Warden runs
  `/rhworld reveal <p>`. The server checks the factors and replays the ride log of every online player.

To check your own rides, export the log with `/rhworld log` and replay it:

```sh
./gradlew :mechanics:installDist --no-daemon
mechanics/build/install/mechanics/bin/mechanics replay --log rides.json
```

A replay proves that the rides in the log are consistent. It cannot prove that the log is complete.

### Running the mod

```sh
./gradlew :fabric:runClient -Prhworld.key=$PWD/samples/random-1024/gm-key.json
```

Create a world with the world type **RHWorld: Wild Gates**. The type **RHWorld: Wild Gates (rigged key)** uses
`samples/rigged-1024/gm-key.json` instead. Without an operator key the world loads in public mode, and gates refuse to
work. The sample keys are public test data.

For a server, `./gradlew :fabric:build` writes `fabric/build/libs/rhworld-0.1.0.jar`, which includes the engine and
mechanics. Put the jar and Fabric API in `mods/`, set `level-type=rhworld\:wild`, and put the operator key at
`config/rhworld/key.json`. Never publish that key. Clients need the mod as well.

| command | shows |
|---|---|
| `/rhworld inspect` | attunement, colour, realm, rides, sigils, dice custody |
| `/rhworld contracts` | world id and the contracts with their theorem chains |
| `/rhworld verify` | recomputed certificate fields and the engine self-check |
| `/rhworld neighbours` | shrine signs within 64 blocks |
| `/rhworld route <realm>` | which gate to ride |
| `/rhworld resources` | hub and sigil of each realm |
| `/rhworld audit` | per-colour counts, mismatches and their probability |
| `/rhworld log` | replay verdict and the ride log as JSON |
| `/rhworld commit <hex>`, `/rhworld open <hex>` | your dice chain and its next link |
| `/rhworld reveal <p>` | operator only: reveal, replay every online player, write the revealed certificate |

## Reference generator

`rhworld/` holds the original Python generator. It is the oracle that the engine is tested against. It draws the public
and hidden layers as maps.

```sh
python3 -m rhworld gm --bits 512 --seed 1 --out gm-key.json           # sample key, not secure
python3 -m rhworld gm --bits 512 --seed 1 --rigged --out rigged.json  # adversarial key
python3 -m rhworld world --gm gm-key.json --out out/                  # key holder's view, player view, report
python3 -m rhworld player --n <n> --out out/                          # player view from n alone
```

## Testing

All test commands run headless.

| command | checks |
|---|---|
| `./gradlew build --no-daemon` | JUnit and Fabric game tests |
| `./run_tests.sh` | Python oracle and independent checkers |
| `fabric/tools/allworld.sh` | every dedicated server scenario |
| `fabric/tools/ordertest.sh rhworld:wild` | identical chunks across load orders and thread counts |
| `fabric/tools/clienttest.sh` | a real client playing to the Concord |
| `python3 -m verify.mca <world> <log>` | placed blocks recomputed from region files |
| `python3 -m verify.crosscheck <rhworld>` | engine, oracle and verifier agree |
| `./gradlew :engine:bench --no-daemon` | pinned benchmark, written to `engine/build/bench/report.json` |

## Contracts

Each certificate names the contracts it relies on and their versions. A version changes whenever a formula, constant or
precondition changes.

Both contracts use the same theorem chain:

1. **Lean** (`LFunction_ne_zero_of_half_lt_re`): for every Dirichlet character chi mod N and every s with Re s > 1/2,
   L(s, chi) is nonzero, except for the trivial character at s = 1.
2. **Published** (Lamzouri, Li, Soundararajan, Math. Comp. 84 (2015), arXiv 1309.3595v3, Theorem 1.1 (2)): assume GRH.
   Let q ≥ 3000 and let H be a proper subgroup of (ℤ/qℤ)*. If no prime below (log q)² divides q, some prime
   ℓ ≤ (log q)² is not in H.
3. **Budget**: for bit length k, B(k) = floor((k ln 2)²), computed exactly. Since ln q < k ln 2, the prime from step 2
   is at most B. B(1024) = 503791 and B(2048) = 2015165.

The derivations below are this project's own and have not been machine-checked.

### RHW-C1 v1

The least witness and the hidden layer.

Input: an odd key n of k ≥ 1024 bits. The refusals, checked in order:

1. `modulus too small or even`
2. `jacobi character is principal`: n is a perfect square
3. `no hidden layer`: n is a probable prime
4. `small factor`: some d from 3 to max(B, s²) divides n, where s = isqrt(B − 1) + 1

Claim: the least x ≤ B with (x/n) = −1 exists. For each prime factor p of n, the least x ≤ B with (x/p) = −1 exists.

Derivation: apply step 2 with q = n. Refusals 1 and 4 give its hypotheses. Take H to be the kernel of (·/n), which is
proper because n is not a square. Or take H to be the kernel of (·/p) read mod n, which is proper by the Chinese
remainder theorem.

### RHW-C3 v1

The prime basis, routes and the gate.

Input: layers n₁ … n_r (public keys, or p and q after a reveal). The state of a number x is r bits, where bit i is set
when (x/n_i) = −1. States multiply as XOR. M is the lcm of the layers and B = B(bitlen M). The refusals: `no layers`,
`too many layers` (r > 8), `modulus too small or even`, `dependent layers` (some product of layers is a square),
`lcm below 3000`, and `small factor` (a prime ≤ B divides a layer).

Claims:

- Scanning the primes in order and keeping each prime whose state is new yields r primes, all ≤ B.
- Every state is reachable from every x coprime to M in at most r multiplications by kept primes.
- **Plain gate**: multiply by a random subset of the primes ≤ B. The resulting state is exactly uniform.
- **Steered gate** (layers n and p): if the colour is wrong, toggle the public witness. The colour is then always the one
  asked for, and the hidden bit is exactly uniform given it. This needs p and n/p to be distinct primes. Uniformity is
  relative to uniform dice, and the shipped dice are SHA-256 outputs.

Derivation: refusal `dependent layers` makes every nonempty product of layers a nontrivial character, so the states of the
units mod M span all r bits. If the primes ≤ B spanned only a proper subspace V, the units with state in V would form a
proper subgroup that contains every prime ≤ B. Step 2 with q = M rules that out. So the scan finds a basis, and the
subset-to-state map is linear and onto, which makes it exactly uniform. For the steered gate, the hidden bit is an affine
function on the subsets with the right colour. It is not constant there, or (·/p) or (·/q) would be trivial on every
prime ≤ B. A nonconstant affine function takes both values equally often.

### Not proved

- Existence only. Nothing is claimed about density, fairness or any window other than 1..B.
- Only the Lean theorem is machine-checked. The explicit bound is published but not formalised.
- Claims about p and q hold only after a reveal. A key shaped like p³ or p r² passes every client check.
- Hiding the factors rests on factoring being hard, not on RH.
- The bound is loose in practice. Observed witnesses are below 50, and 349 for the rigged sample, against B = 503791.
- Each ride leaks one linear equation about the hidden layer. After about 41,816 rides against one 1024-bit key the
  hidden layer becomes public, although the factors do not. End a season well before that.
