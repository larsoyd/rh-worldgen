package rhworld.engine.json;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        Object v = p.value();
        p.skipSpace();
        if (p.pos != text.length()) throw p.fail("trailing input");
        return v;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object v, StringBuilder out) {
        switch (v) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case BigInteger i -> out.append(i);
            case Long i -> out.append(i);
            case Integer i -> out.append(i);
            case String s -> quote(s, out);
            case Map<?, ?> m -> {
                Map<String, Object> sorted = new TreeMap<>();
                m.forEach((k, x) -> sorted.put((String) k, x));
                out.append('{');
                String sep = "";
                for (var e : sorted.entrySet()) {
                    out.append(sep);
                    quote(e.getKey(), out);
                    out.append(':');
                    write(e.getValue(), out);
                    sep = ",";
                }
                out.append('}');
            }
            case List<?> l -> {
                out.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) out.append(',');
                    write(l.get(i), out);
                }
                out.append(']');
            }
            default -> throw new JsonException("cannot write " + v.getClass().getName());
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private Object value() {
        skipSpace();
        if (pos >= text.length()) throw fail("unexpected end");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        pos++;
        Map<String, Object> m = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) return Collections.unmodifiableMap(m);
        do {
            skipSpace();
            if (pos >= text.length() || text.charAt(pos) != '"') throw fail("expected key");
            String k = string();
            skipSpace();
            expect(':');
            if (m.containsKey(k)) throw fail("duplicate key " + k);
            m.put(k, value());
            skipSpace();
        } while (peek(','));
        expect('}');
        return Collections.unmodifiableMap(m);
    }

    private List<Object> array() {
        pos++;
        List<Object> l = new ArrayList<>();
        skipSpace();
        if (peek(']')) return Collections.unmodifiableList(l);
        do {
            l.add(value());
            skipSpace();
        } while (peek(','));
        expect(']');
        return Collections.unmodifiableList(l);
    }

    private String string() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw fail("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw fail("control character in string");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) throw fail("unterminated escape");
            char e = text.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw fail("short unicode escape");
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw fail("bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw fail("bad escape " + e);
            }
        }
    }

    private BigInteger number() {
        int start = pos;
        peek('-');
        int digits = pos;
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        if (pos == digits) throw fail("expected value");
        if (text.charAt(digits) == '0' && pos - digits > 1) throw fail("leading zero");
        if (pos < text.length() && ".eE".indexOf(text.charAt(pos)) >= 0) throw fail("only integers are allowed");
        return new BigInteger(text.substring(start, pos));
    }

    private Object literal(String word, Object v) {
        if (!text.startsWith(word, pos)) throw fail("bad literal");
        pos += word.length();
        return v;
    }

    private boolean peek(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw fail("expected " + c);
    }

    private void skipSpace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) pos++;
    }

    private JsonException fail(String why) {
        return new JsonException(why + " at offset " + pos);
    }
}
