package rhworld.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    private static final Logger LOG = LoggerFactory.getLogger("rhworld");

    private Log() {}

    public static void line(String tag, Object... kv) {
        var sb = new StringBuilder("[rhworld] ").append("event=").append(tag);
        for (int i = 0; i + 1 < kv.length; i += 2) sb.append(' ').append(kv[i]).append('=').append(kv[i + 1]);
        LOG.info(sb.toString());
    }
}
