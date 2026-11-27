package rhworld.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public final class WorldTestHook implements ModInitializer {
    static final String SCENARIO = System.getProperty("rhworld.worldtest", "none");

    @Override
    public void onInitialize() {
        if (SCENARIO.equals("none")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(WorldTestHook::run);
    }

    static void run(MinecraftServer server) {
        long start = System.nanoTime();
        var report = new Report();
        try {
            scenario(SCENARIO).accept(new Ctx(server, report));
        } catch (RuntimeException e) {
            report.fail("exception", e.toString());
        }
        System.out.printf("[rhworld-test] verdict=%s scenario=%s checked=%d failed=%d ms=%d%n",
                report.failed.isEmpty() && report.checked > 0 ? "pass" : "fail", SCENARIO,
                report.checked, report.failed.size(), (System.nanoTime() - start) / 1_000_000);
        server.halt(false);
    }

    static Consumer<Ctx> scenario(String name) {
        return switch (name) {
            case "terrain" -> WorldScenarios::terrain;
            case "realms" -> WorldScenarios::realms;
            case "vanilla" -> WorldScenarios::vanilla;
            case "hubs" -> WorldScenarios::hubs;
            case "hashdump" -> ChunkHashes::dump;
            case "rides" -> RideScenario::rides;
            case "concord" -> LifeScenario::concord;
            case "persist1" -> LifeScenario::persist1;
            case "persist2" -> LifeScenario::persist2;
            default -> ctx -> ctx.report().fail("scenario", "unknown " + name);
        };
    }

    record Ctx(MinecraftServer server, Report report) {}

    static final class Report {
        int checked;
        final List<String> failed = new ArrayList<>();

        void check(boolean ok, String what, String detail) {
            checked++;
            System.out.printf("[rhworld-test] check=%s ok=%s %s%n", what, ok, detail);
            if (!ok) failed.add(what);
        }

        void fail(String what, String detail) {
            check(false, what, detail);
        }
    }
}
