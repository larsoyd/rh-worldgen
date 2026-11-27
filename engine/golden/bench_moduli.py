import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from verify import seeds

ORACLE = Path(__file__).resolve().parents[1] / "src/test/resources/golden/oracle.json"


def rows():
    out = []
    for c in json.loads(ORACLE.read_text())["modulus"]:
        row = {"label": c["label"], "n": c["n"]}
        if c["factors"]:
            row["p"] = c["factors"][0]
        out.append(row)
    p, q = 2 ** 521 - 1, 2 ** 607 - 1
    out.append({"label": "mersenne-521x607", "n": str(p * q), "p": str(p)})
    for bits in (1024, 2048):
        s = seeds.both_rigged(bits, 0)
        out.append({"label": f"both-rigged-{bits}-0", "n": str(s["n"]), "p": str(s["factors"][0])})
    return out


Path(sys.argv[1]).write_text(json.dumps(rows(), indent=1) + "\n")
