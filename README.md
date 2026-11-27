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
