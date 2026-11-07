package studentperformance;

import java.io.IOException;
import java.util.*;

/**
 * Lightweight, zero-dependency JSON parser for OpenAI API responses.
 * Parses objects, arrays, strings, numbers, booleans, and nulls.
 */
public class SimpleJsonParser {
    private final String src;
    private int pos = 0;

    public SimpleJsonParser(String src) {
        this.src = src == null ? "" : src.trim();
    }

    public static Map<String, Object> parseObject(String json) throws IOException {
        Object result = new SimpleJsonParser(json).parseValue();
        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        throw new IOException("Expected JSON object at root, but found: " + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    public Object parseValue() throws IOException {
        skipWhitespace();
        if (pos >= src.length()) throw new IOException("Unexpected end of JSON input");
        char c = src.charAt(pos);
        if (c == '{') return parseJsonObject();
        if (c == '[') return parseJsonArray();
        if (c == '"') return parseJsonString();
        if (c == 't' || c == 'f') return parseJsonBoolean();
        if (c == 'n') return parseJsonNull();
        if (c == '-' || Character.isDigit(c)) return parseJsonNumber();
        throw new IOException("Unexpected character '" + c + "' at position " + pos);
    }

    private Map<String, Object> parseJsonObject() throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // consume '{'
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (pos < src.length()) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != '"') {
                throw new IOException("Expected string key in JSON object at position " + pos);
            }
            String key = parseJsonString();
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != ':') {
                throw new IOException("Expected ':' after key in JSON object at position " + pos);
            }
            pos++; // consume ':'
            Object val = parseValue();
            map.put(key, val);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            throw new IOException("Expected ',' or '}' in JSON object at position " + pos);
        }
        throw new IOException("Unterminated JSON object");
    }

    private List<Object> parseJsonArray() throws IOException {
        List<Object> list = new ArrayList<>();
        pos++; // consume '['
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (pos < src.length()) {
            Object val = parseValue();
            list.add(val);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            throw new IOException("Expected ',' or ']' in JSON array at position " + pos);
        }
        throw new IOException("Unterminated JSON array");
    }

    private String parseJsonString() throws IOException {
        pos++; // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) throw new IOException("Unterminated escape sequence in string");
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw new IOException("Incomplete unicode escape");
                        String hex = src.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        } catch (NumberFormatException e) {
                            throw new IOException("Invalid unicode escape: " + hex);
                        }
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IOException("Unterminated JSON string");
    }

    private Number parseJsonNumber() throws IOException {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean isDouble = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isDouble = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isDouble = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String numStr = src.substring(start, pos);
        try {
            if (isDouble) return Double.parseDouble(numStr);
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
            return l;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid JSON number: " + numStr);
        }
    }

    private Boolean parseJsonBoolean() throws IOException {
        if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new IOException("Invalid boolean literal at position " + pos);
    }

    private Object parseJsonNull() throws IOException {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw new IOException("Invalid null literal at position " + pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }
}
