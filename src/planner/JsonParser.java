package planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser. Produces Map (objects), List (arrays), String,
 * Double (numbers), Boolean, and null.
 */
final class JsonParser {
    private final String s;
    private int i;

    private JsonParser(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        JsonParser p = new JsonParser(text);
        p.skipWhitespace();
        Object value = p.value();
        p.skipWhitespace();
        if (p.i != p.s.length()) throw p.error("Unexpected trailing data");
        return value;
    }

    private Object value() {
        if (i >= s.length()) throw error("Unexpected end of input");
        return switch (s.charAt(i)) {
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
        i++; // {
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            i++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) throw error("Expected string key");
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, value());
            skipWhitespace();
            if (peek(',')) {
                i++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        i++; // [
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            i++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value());
            skipWhitespace();
            if (peek(',')) {
                i++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        i++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw error("Unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) throw error("Unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("Bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw error("Bad escape \\" + e);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("Unexpected character '" + s.charAt(i) + "'");
        return Double.valueOf(s.substring(start, i));
    }

    private Object literal(String word, Object result) {
        if (!s.startsWith(word, i)) throw error("Expected " + word);
        i += word.length();
        return result;
    }

    private void skipWhitespace() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("Expected '" + c + "'");
        i++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + i);
    }
}
