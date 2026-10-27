package com.ringdown.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small strict JSON reader with no dependencies, so the same code runs on Android and in the
 * desktop tests. Objects become Map, arrays List, numbers Double, plus String, Boolean and null.
 * Anything that isn't valid JSON throws {@link Error}; it never throws anything else.
 */
public final class Json {
    public static final class Error extends Exception {
        Error(String m) { super(m); }
    }

    private static final int MAX_DEPTH = 24;
    private static final int MAX_CHARS = 256 * 1024;
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) throws Error {
        if (text == null) throw new Error("no text");
        if (text.length() > MAX_CHARS) throw new Error("too large");
        Json p = new Json(text);
        if (p.s.length() > 0 && p.s.charAt(0) == '﻿') p.i = 1;   // byte order mark
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != p.s.length()) throw new Error("extra text at " + p.i);
        return v;
    }

    private Object value(int depth) throws Error {
        if (depth > MAX_DEPTH) throw new Error("nested too deep");
        if (i >= s.length()) throw new Error("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object(depth);
        if (c == '[') return array(depth);
        if (c == '"') return string();
        if (c == 't') return word("true", Boolean.TRUE);
        if (c == 'f') return word("false", Boolean.FALSE);
        if (c == 'n') return word("null", null);
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw new Error("unexpected '" + c + "' at " + i);
    }

    private Map<String, Object> object(int depth) throws Error {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            if (peek() != '"') throw new Error("expected a \"name\" at " + i);
            String k = string();
            ws();
            if (peek() != ':') throw new Error("expected ':' at " + i);
            i++;
            ws();
            m.put(k, value(depth + 1));
            ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; return m; }
            throw new Error("expected ',' or '}' at " + i);
        }
    }

    private List<Object> array(int depth) throws Error {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; return l; }
            throw new Error("expected ',' or ']' at " + i);
        }
    }

    private String string() throws Error {
        i++;   // opening quote
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new Error("unclosed text");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c < 0x20) throw new Error("line break or control character inside text at " + (i - 1));
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw new Error("unclosed text");
            char e = s.charAt(i++);
            switch (e) {
                case '"': b.append('"'); break;
                case '\\': b.append('\\'); break;
                case '/': b.append('/'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new Error("bad \\u escape");
                    int v = 0;
                    for (int k = 0; k < 4; k++) {
                        int d = Character.digit(s.charAt(i++), 16);
                        if (d < 0) throw new Error("bad \\u escape");
                        v = v * 16 + d;
                    }
                    b.append((char) v);
                    break;
                default:
                    throw new Error("bad escape \\" + e);
            }
        }
    }

    private Double number() throws Error {
        int start = i;
        if (peek() == '-') i++;
        if (peek() == '0') i++;
        else if (isDigit(peek())) while (isDigit(peek())) i++;
        else throw new Error("bad number at " + start);
        if (peek() == '.') {
            i++;
            if (!isDigit(peek())) throw new Error("bad number at " + start);
            while (isDigit(peek())) i++;
        }
        if (peek() == 'e' || peek() == 'E') {
            i++;
            if (peek() == '+' || peek() == '-') i++;
            if (!isDigit(peek())) throw new Error("bad number at " + start);
            while (isDigit(peek())) i++;
        }
        try {
            return Double.valueOf(s.substring(start, i));
        } catch (NumberFormatException ex) {
            throw new Error("bad number at " + start);
        }
    }

    private Object word(String w, Object v) throws Error {
        if (!s.startsWith(w, i)) throw new Error("unexpected text at " + i);
        i += w.length();
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
