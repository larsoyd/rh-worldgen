import re
import sys
from pathlib import Path

CONTRACTS = ("RHW-C1 v1", "RHW-C3 v1")


def readme_contracts(text):
    match = re.search(r"^## Contracts[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match:
        return "refused: README has no Contracts section"
    for name in CONTRACTS:
        body = re.search(rf"^### {re.escape(name)}(?::[^\n]*)?[ \t]*\n(.*?)(?=^### |\Z)", match[1], re.M | re.S)
        if not body or not body[1].strip():
            return f"refused: Contracts section lacks {name}"
    return "ok"


if __name__ == "__main__":
    verdict = readme_contracts(Path(sys.argv[1]).read_text())
    print(f"[release] check=readme_contracts verdict={verdict}")
    sys.exit(0 if verdict == "ok" else 2)
