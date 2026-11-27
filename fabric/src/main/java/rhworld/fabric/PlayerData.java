package rhworld.fabric;

import com.mojang.serialization.Codec;
import java.security.SecureRandom;
import java.util.HexFormat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;
import rhworld.mechanics.Custody;
import rhworld.mechanics.Season;
import rhworld.mechanics.SeedChain;

public final class PlayerData {
    public static final String NO_OPENING = "refused: no dice opening queued";
    public static final int CHAIN_LENGTH = 4096;

    public static final AttachmentType<String> STATE = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(RhWorldMod.id("player"));
    public static final AttachmentType<String> CHAIN = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(RhWorldMod.id("chain"));
    public static final AttachmentType<String> OPENING = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(RhWorldMod.id("opening"));

    private static final SecureRandom RANDOM = new SecureRandom();

    private PlayerData() {}

    static void init() {}

    // vanilla clients get a chain the server holds, so their rides carry no promise about the dice
    public static Season.Player state(Player p, Season season) {
        String id = p.getStringUUID();
        if (p.getAttached(STATE) != null) return season.resume(id, () -> p.getAttached(STATE));
        byte[] top = new byte[32];
        RANDOM.nextBytes(top);
        var chain = new SeedChain(top, CHAIN_LENGTH);
        p.setAttached(CHAIN, HexFormat.of().formatHex(top));
        var fresh = season.join(chain.commitment(), Custody.SERVER);
        season.hold(id, fresh);
        save(p, fresh);
        return fresh;
    }

    public static void save(Player p, Season.Player state) {
        p.setAttached(STATE, state.toJson());
    }

    // the live player is written back only when the game saves the player
    public static void flush(Player p) {
        var season = Session.current().flatMap(Session::season);
        if (season.isEmpty() || p.getAttached(STATE) == null) return;
        save(p, season.get().resume(p.getStringUUID(), () -> p.getAttached(STATE)));
    }

    public static void release(Player p) {
        flush(p);
        Session.current().flatMap(Session::season).ifPresent(s -> s.release(p.getStringUUID()));
    }

    public static String commit(Player p, String hex) {
        var session = Session.current();
        if (session.isEmpty() || session.get().season().isEmpty()) return RideCore.NO_KEY;
        if (!lowerHex64(hex)) return "refused: commitment is not 64 lower case hex digits";
        var season = session.get().season().get();
        if (state(p, season).log().size() != 0) return "refused: commitment only before the first ride";
        var committed = season.join(hex, Custody.PLAYER);
        season.hold(p.getStringUUID(), committed);
        save(p, committed);
        p.removeAttached(CHAIN);
        p.removeAttached(OPENING);
        return "committed";
    }

    public static String queue(Player p, String hex) {
        if (!lowerHex64(hex)) return "refused: opening is not 64 lower case hex digits";
        p.setAttached(OPENING, hex);
        return "queued";
    }

    static String opening(Player p, Season.Player state) {
        if (state.log().custody() == Custody.SERVER) {
            String top = p.getAttached(CHAIN);
            if (top == null) return "refused: server held chain missing";
            return new SeedChain(HexFormat.of().parseHex(top), CHAIN_LENGTH).opening(state.log().size());
        }
        String queued = p.getAttached(OPENING);
        return queued == null ? NO_OPENING : queued;
    }

    static void used(Player p) {
        p.removeAttached(OPENING);
    }

    static boolean lowerHex64(String s) {
        return s.length() == 64 && s.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f');
    }
}
