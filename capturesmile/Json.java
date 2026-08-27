package capturesmile;

import java.util.Locale;

/**
 * Minimal, dependency-free JSON <em>writer</em>. There is deliberately no parser anywhere in the
 * app: the image upload is a raw binary body and the showdown request is a trivial tab-delimited
 * body, so nothing untrusted is ever JSON-parsed.
 *
 * <p>Correctness notes baked in here: strings escape {@code "}, {@code \} and all control chars;
 * non-ASCII (emoji, accented names) is emitted raw as UTF-8; and every number is formatted with
 * {@link Locale#ROOT} so a comma-decimal machine locale can never emit {@code 8,7} and break the
 * JSON.
 */
public final class Json {
    private Json() {}

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static String quote(String v) {
        return v == null ? "null" : "\"" + escape(v) + "\"";
    }

    /** Fluent JSON object builder. */
    public static final class Obj {
        private final StringBuilder b = new StringBuilder(96);
        private boolean first = true;
        public Obj() { b.append('{'); }

        private void key(String k) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(escape(k)).append("\":");
        }

        public Obj str(String k, String v) { key(k); b.append(quote(v)); return this; }
        public Obj num(String k, long v) { key(k); b.append(v); return this; }
        public Obj dec(String k, double v, int places) {
            key(k); b.append(String.format(Locale.ROOT, "%." + places + "f", v)); return this;
        }
        public Obj bool(String k, boolean v) { key(k); b.append(v); return this; }
        /** Insert a pre-built object/array (already valid JSON). */
        public Obj raw(String k, String rawJson) { key(k); b.append(rawJson == null ? "null" : rawJson); return this; }

        public String end() { b.append('}'); return b.toString(); }
        @Override public String toString() { return b + "}"; }
    }

    /** Fluent JSON array builder. */
    public static final class Arr {
        private final StringBuilder b = new StringBuilder(96);
        private boolean first = true;
        public Arr() { b.append('['); }

        private void sep() { if (!first) b.append(','); first = false; }

        public Arr add(String rawJson) { sep(); b.append(rawJson == null ? "null" : rawJson); return this; }
        public Arr addStr(String v) { sep(); b.append(quote(v)); return this; }

        public String end() { b.append(']'); return b.toString(); }
    }
}
