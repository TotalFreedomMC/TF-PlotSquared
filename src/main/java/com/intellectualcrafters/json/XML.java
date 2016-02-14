package com.intellectualcrafters.json;

import java.util.Iterator;

/**
 * This provides static methods to convert an XML text into a JSONObject, and to covert a JSONObject into an XML text.
 *
 * @author JSON.org
 * @version 2014-05-03
 */
public class XML {
    public static final Character AMP = '&';
    public static final Character APOS = '\'';
    public static final Character BANG = '!';
    public static final Character EQ = '=';
    public static final Character GT = '>';
    public static final Character LT = '<';
    public static final Character QUEST = '?';
    public static final Character QUOT = '"';
    public static final Character SLASH = '/';
    
    public static String escape(final String string) {
        final StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            final char c = string.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * Throw an exception if the string contains whitespace. Whitespace is not allowed in tagNames and attributes.
     *
     * @param string A string.
     *
     * @throws JSONException
     */
    public static void noSpace(final String string) throws JSONException {
        int i;
        final int length = string.length();
        if (length == 0) {
            throw new JSONException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new JSONException("'" + string + "' contains a space character.");
            }
        }
    }
    
    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x       The XMLTokener containing the source string.
     * @param context The JSONObject that will include the new material.
     * @param name    The tag name.
     *
     * @return true if the close tag is processed.
     *
     * @throws JSONException
     */
    private static boolean parse(final XMLTokener x, final JSONObject context, final String name) throws JSONException {
        char c;
        int i;
        JSONObject jsonobject = null;
        String string;
        String tagName;
        Object token;
        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<
        token = x.nextToken();
        // <!
        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (!string.isEmpty()) {
                            context.accumulate("content", string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {
            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {
            // Close tag </
            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;
        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");
            // Open tag <
        } else {
            tagName = (String) token;
            token = null;
            jsonobject = new JSONObject();
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }
                        jsonobject.accumulate(string, XML.stringToValue((String) token));
                        token = null;
                    } else {
                        jsonobject.accumulate(string, "");
                    }
                    // Empty tag <.../>
                } else if (token == SLASH) {
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (jsonobject.length() > 0) {
                        context.accumulate(tagName, jsonobject);
                    } else {
                        context.accumulate(tagName, "");
                    }
                    return false;
                    // Content, between <...> and </...>
                } else if (token == GT) {
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (!string.isEmpty()) {
                                jsonobject.accumulate("content", XML.stringToValue(string));
                            }
                            // Nested element
                        } else if (token == LT) {
                            if (parse(x, jsonobject, tagName)) {
                                if (jsonobject.length() == 0) {
                                    context.accumulate(tagName, "");
                                } else if ((jsonobject.length() == 1) && (jsonobject.opt("content") != null)) {
                                    context.accumulate(tagName, jsonobject.opt("content"));
                                } else {
                                    context.accumulate(tagName, jsonobject);
                                }
                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }
    
    /**
     * Try to convert a string into a number, boolean, or null. If the string can't be converted, return the string.
     * This is much less ambitious than JSONObject.stringToValue, especially because it does not attempt to convert plus
     * forms, octal forms, hex forms, or E forms lacking decimal points.
     *
     * @param string A String.
     *
     * @return A simple JSON value.
     */
    public static Object stringToValue(final String string) {
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }
        // If it might be a number, try converting it, first as a Long, and then
        // as a
        // Double. If that doesn't work, return the string.
        try {
            final char initial = string.charAt(0);
            if ((initial == '-') || ((initial >= '0') && (initial <= '9'))) {
                final Long value = new Long(string);
                if (value.toString().equals(string)) {
                    return value;
                }
            }
        } catch (final Exception ignore) {
            try {
                final Double value = new Double(string);
                if (value.toString().equals(string)) {
                    return value;
                }
            } catch (final Exception ignoreAlso) {}
        }
        return string;
    }
    
    public static JSONObject toJSONObject(final String string) throws JSONException {
        final JSONObject jo = new JSONObject();
        final XMLTokener x = new XMLTokener(string);
        while (x.more() && x.skipPast("<")) {
            parse(x, jo, null);
        }
        return jo;
    }
    
    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object A JSONObject.
     *
     * @return A string.
     *
     * @throws JSONException
     */
    public static String toString(final Object object) throws JSONException {
        return toString(object, null);
    }
    
    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object  A JSONObject.
     * @param tagName The optional name of the enclosing tag.
     *
     * @return A string.
     *
     * @throws JSONException
     */
    public static String toString(Object object, final String tagName) throws JSONException {
        final StringBuilder sb = new StringBuilder();
        int i;
        JSONArray ja;
        JSONObject jo;
        String key;
        Iterator<String> keys;
        int length;
        String string;
        Object value;
        if (object instanceof JSONObject) {
            // Emit <tagName>
            if (tagName != null) {
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
            }
            // Loop thru the keys.
            jo = (JSONObject) object;
            keys = jo.keys();
            while (keys.hasNext()) {
                key = keys.next();
                value = jo.opt(key);
                if (value == null) {
                    value = "";
                }
                string = value instanceof String ? (String) value : null;
                // Emit content in body
                if ("content".equals(key)) {
                    if (value instanceof JSONArray) {
                        ja = (JSONArray) value;
                        length = ja.length();
                        for (i = 0; i < length; i += 1) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            sb.append(escape(ja.get(i).toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }
                    // Emit an array of similar keys
                } else if (value instanceof JSONArray) {
                    ja = (JSONArray) value;
                    length = ja.length();
                    for (i = 0; i < length; i += 1) {
                        value = ja.get(i);
                        if (value instanceof JSONArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(value));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(value, key));
                        }
                    }
                } else if ("".equals(value)) {
                    sb.append('<');
                    sb.append(key);
                    sb.append("/>");
                    // Emit a new tag <k>
                } else {
                    sb.append(toString(value, key));
                }
            }
            if (tagName != null) {
                // Emit the </tagname> close tag
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
            }
            return sb.toString();
            // XML does not have good support for arrays. If an array appears in
            // a place
            // where XML is lacking, synthesize an <array> element.
        } else {
            if (object.getClass().isArray()) {
                object = new JSONArray(object);
            }
            if (object instanceof JSONArray) {
                ja = (JSONArray) object;
                length = ja.length();
                for (i = 0; i < length; i += 1) {
                    sb.append(toString(ja.opt(i), tagName == null ? "array" : tagName));
                }
                return sb.toString();
            } else {
                string = (object == null) ? "null" : escape(object.toString());
                return (tagName == null) ? "\"" + string + "\"" :
                        (string.isEmpty()) ? "<" + tagName + "/>" : "<" + tagName + ">" + string + "</" + tagName + ">";
            }
        }
    }
}
