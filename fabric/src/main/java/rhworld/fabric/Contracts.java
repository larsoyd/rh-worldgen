package rhworld.fabric;

import java.util.List;

public final class Contracts {
    public record Contract(String id, String label, boolean grh, String statement, String checked) {
        public String line() {
            return "id=" + id + " label=" + label + " grh=" + (grh ? "yes" : "no") + " statement=\"" + statement + "\" checked=\"" + checked + "\"";
        }
    }

    public static final List<Contract> ALL = List.of(
            new Contract("G2", "Lean->Pub", true,
                    "some prime w <= B has (w/n) = -1, the least one steers both gates",
                    "recomputed from n: w found below B, see verify"),
            new Contract("WG-1", "Lean->Pub->New", true,
                    "for every key passing the checks the hidden bits of the gate primes span V4, so a ride is uniform over its colour pair",
                    "hypotheses on n recomputed, distinct prime factors checked at reveal"),
            new Contract("WG-2", "New", true,
                    "rides are independent fair coins, a realm unseen after t rides of its colour has probability 2^-t",
                    "audit reports this probability per player"),
            new Contract("PUBLIC", "unconditional", false,
                    "after every ride the public symbol (x/n) equals the gate colour",
                    "anyone recomputes it from n and the log"),
            new Contract("SHRINE", "unconditional", false,
                    "shrine l multiplies attunement by l mod n, a fact of arithmetic with no theorem behind any guarantee",
                    "none needed"),
            new Contract("REVEAL", "unconditional", false,
                    "with p revealed every logged report is replayed and the first false one is named",
                    "reveal command"));

    private Contracts() {}
}
