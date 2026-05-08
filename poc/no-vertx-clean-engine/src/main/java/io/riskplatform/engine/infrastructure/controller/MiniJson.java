package io.riskplatform.engine.infrastructure.controller;

import java.util.*;

/**
 * PoC-only hand-rolled JSON parser (~80 lines). Not production-grade.
 * Supports flat objects with string, number, boolean, and null values.
 * For nested objects or arrays, extend or replace with a real library.
 */
final class MiniJson {
    private MiniJson() {}

    /** Parse a flat JSON object into Map<String,Object>. Throws IllegalArgumentException on parse error. */
    static Map<String, Object> parse(String json) {
        if (json == null) throw new IllegalArgumentException("null input");
        String s = json.strip();
        if (!s.startsWith("{") || !s.endsWith("}")) throw new IllegalArgumentException("not a JSON object");
        s = s.substring(1, s.length() - 1).strip();
        var result = new LinkedHashMap<String, Object>();
        if (s.isEmpty()) return result;

        int i = 0;
        while (i < s.length()) {
            // skip whitespace and commas
            while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
            if (i >= s.length()) break;

            // parse key (must be quoted string)
            if (s.charAt(i) != '"') throw new IllegalArgumentException("expected '\"' at pos " + i);
            int keyEnd = nextQuote(s, i + 1);
            String key = unescape(s.substring(i + 1, keyEnd));
            i = keyEnd + 1;

            // colon
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= s.length() || s.charAt(i) != ':') throw new IllegalArgumentException("expected ':' after key");
            i++;
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;

            // parse value
            Object value;
            char c = s.charAt(i);
            if (c == '"') {
                int valEnd = nextQuote(s, i + 1);
                value = unescape(s.substring(i + 1, valEnd));
                i = valEnd + 1;
            } else if (c == 't' && s.startsWith("true", i)) {
                value = Boolean.TRUE; i += 4;
            } else if (c == 'f' && s.startsWith("false", i)) {
                value = Boolean.FALSE; i += 5;
            } else if (c == 'n' && s.startsWith("null", i)) {
                value = null; i += 4;
            } else if (c == '-' || Character.isDigit(c)) {
                int start = i;
                if (s.charAt(i) == '-') i++;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                String num = s.substring(start, i);
                value = num.contains(".") ? Double.parseDouble(num) : Long.parseLong(num);
            } else {
                throw new IllegalArgumentException("unexpected char '" + c + "' at pos " + i);
            }
            result.put(key, value);
        }
        return result;
    }

    /** Serialize a Map or simple value to JSON string. */
    static String stringify(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Map<?,?> map) {
            var sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                sb.append(stringify(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (obj instanceof String s) return '"' + escape(s) + '"';
        if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
        return '"' + escape(obj.toString()) + '"';
    }

    private static int nextQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        throw new IllegalArgumentException("unterminated string starting at " + (from - 1));
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }
}
