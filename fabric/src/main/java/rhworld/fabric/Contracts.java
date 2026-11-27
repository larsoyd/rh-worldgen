package rhworld.fabric;

import java.util.List;

public final class Contracts {
    public record Contract(String id, String label, boolean grh, String statement, String checked) {
        public String line() {
            return "id=" + id + " label=" + label + " grh=" + (grh ? "yes" : "no") + " statement=\"" + statement + "\" checked=\"" + checked + "\"";
        }
    }

    public static final List<Contract> UNCONDITIONAL = List.of(
            new Contract("PUBLIC", "unconditional", false,
                    "after every ride the public symbol (x/n) equals the gate colour",
                    "anyone recomputes it from n and the log"),
            new Contract("SHRINE", "unconditional", false,
                    "shrines are inert in this version, they make no claim and change nothing",
                    "none needed"),
            new Contract("REVEAL", "unconditional", false,
                    "with p revealed every logged report is replayed and the first false one is named",
                    "reveal command"));

    // the theorem backed contracts are printed from the engine, never restated here
    public static List<String> engine() {
        var lines = new java.util.ArrayList<String>();
        engine(lines, rhworld.engine.contract.C1.ID, rhworld.engine.contract.C1.CHAIN, rhworld.engine.contract.C1.HYPOTHESES);
        engine(lines, rhworld.engine.contract.C3.ID, rhworld.engine.contract.C3.CHAIN, rhworld.engine.contract.C3.HYPOTHESES);
        return lines;
    }

    private static void engine(List<String> out, rhworld.engine.contract.ContractId id,
            List<rhworld.engine.contract.TheoremRef> chain, List<rhworld.engine.contract.Hypothesis> hypotheses) {
        out.add("contract=" + id);
        chain.forEach(t -> out.add("  chain label=" + t.label() + " ref=" + t.ref() + " statement=\"" + t.statement() + "\""));
        hypotheses.forEach(h -> out.add("  hypothesis checked_by=" + h.checkedBy() + " statement=\"" + h.statement() + "\""));
    }

    private Contracts() {}
}
