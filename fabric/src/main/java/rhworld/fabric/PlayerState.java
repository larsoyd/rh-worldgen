package rhworld.fabric;

import com.mojang.serialization.Codec;
import java.math.BigInteger;
import java.util.HexFormat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;
import rhworld.mechanics.Concord;
import rhworld.mechanics.RideLog;

public final class PlayerState {
    public static final AttachmentType<String> ATTUNEMENT = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).buildAndRegister(RhWorldMod.id("attunement"));
    public static final AttachmentType<String> DICE = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(RhWorldMod.id("dice"));
    public static final AttachmentType<String> RIDES = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).buildAndRegister(RhWorldMod.id("rides"));
    public static final AttachmentType<String> CONCORD = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(RhWorldMod.id("concord"));

    private PlayerState() {}

    static void init() {}

    public static BigInteger attunement(Player p) {
        return new BigInteger(p.getAttachedOrElse(ATTUNEMENT, "1"));
    }

    public static void setAttunement(Player p, BigInteger x) {
        p.setAttached(ATTUNEMENT, x.toString());
    }

    public static byte[] seed(Player p) {
        String hex = p.getAttached(DICE);
        return hex == null ? null : HexFormat.of().parseHex(hex);
    }

    public static void setSeed(Player p, byte[] seed) {
        p.setAttached(DICE, HexFormat.of().formatHex(seed));
    }

    public static RideLog rides(Player p, BigInteger n) {
        String json = p.getAttached(RIDES);
        return json == null ? new RideLog(n) : RideLog.fromJson(json);
    }

    public static void setRides(Player p, RideLog log) {
        p.setAttached(RIDES, log.toJson());
    }

    public static Concord concord(Player p) {
        String json = p.getAttached(CONCORD);
        return json == null ? new Concord() : Concord.fromJson(json);
    }

    public static void setConcord(Player p, Concord c) {
        p.setAttached(CONCORD, c.toJson());
    }
}
