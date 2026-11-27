package rhworld.engine.contract;

public record ContractId(String name, int version) {
    @Override
    public String toString() {
        return name + " v" + version;
    }
}
