# rh-worldgen

A fog-of-war world generator whose one hard promise rests on the Generalized Riemann Hypothesis (GRH) for Dirichlet
L-functions, which has a machine-checked Lean proof:
[leanprover-community/riemann-hypothesis](https://github.com/leanprover-community/riemann-hypothesis).

## What it does

A game master (GM) publishes an odd number `n` of at least 1024 bits and keeps its two prime factors `n = p q` secret.
Every tile of the world is a positive integer `x`. The spawn patch is a square of side `S`. Tile `x` sits at row
`(x - 1) div S`, column `(x - 1) mod S`, so tile 1 is the top-left corner.

- **Visible layer** (anyone can compute it from `n`): the Jacobi symbol `(x/n)`, which is +1 or -1. Players see
  **land** where it is +1 and **water** where it is -1.
- **Hidden layer** (only the GM can compute it, because it needs `p`): the Legendre symbol `(x/p)`. It splits land
  into plains and forest, and water into shallows and deep water. Players learn it when the GM reveals `p`.

The GM chooses `n` and could try to rig it. For example, a GM could choose `n` so that the hidden layer is the same
everywhere near spawn, which would make the fog hide nothing. The generator refuses any `n` that fails its checks.
For every `n` that passes them, GRH guarantees the following.

> **G1.** Some tile `x <= B(n)` has `(x/p) = -1`, and some tile `y <= B(n)` has `(y/q) = -1`. Tile 1 always has
> `(1/p) = +1`, so the hidden layer is never constant on the spawn patch.
>
> **G2.** Some tile `x <= B(n)` has `(x/n) = -1`. The smallest such tile is a canonical public landmark, and every
> player can find it in at most `B(n)` steps.

Here `B(n) = floor((k ln 2)^2)`, where `k` is the bit length of `n`. It is at least `floor((ln n)^2)` for every `n` of
that length, and it is public before `n` exists. For `k = 1024`, `B = 503,791` and the spawn patch side is
`S = ceil(sqrt(B)) = 710`. For `k = 2048`, `B = 2,015,165` and `S = 1420`. The budget is fixed before any scanning.
If a scan ever ran past it, the generator would return `refused: budget exceeded`. Under GRH that cannot happen.

## How this uses the proof

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

## Honest limits

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

## Client checks

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

## Map legend

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
Each gate ride lets players learn one linear equation in the hidden bits (l/p) of the primes l up to B. A portal that
multiplies by a single prime l reveals (l/p) directly, one bit per ride: 25 such rides give every l up to 97. After about
|S| independent equations (41816 at 1024 bits) players know all of them. Then the hidden bit of every tile whose prime
factors are all at most B is public, and that includes every tile of the prefix 1..B. Recovering p itself from these bits is an open
problem. We know no algorithm for it, but we do not claim it is impossible. A season should end well before |S| rides
have been made against one key.

## Usage

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

## Samples

`samples/random-1024` and `samples/rigged-1024` each hold a key file, both maps, both zooms, the ledger and a report.

- **Random GM:** G1 witnesses 2 and 3, G2 witness 2.
- **Rigged GM:** every prime up to 331 is a residue mod both factors. The witnesses are G1 = 349 and 359 and G2 = 349,
  still far below `B = 503,791`.

These key files are public test data, not secrets.
