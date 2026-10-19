package ru.mcmon.plugin.client;

import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

final class HostNames {

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    private HostNames() {}

    static boolean same(String first, String second) {
        String a = normalize(first);
        return a != null && a.equals(normalize(second));
    }

    private static String normalize(String host) {
        String text = host.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return null;
        }
        if (text.indexOf(':') >= 0) {
            return ipv6(text);
        }
        try {
            return IDN.toASCII(text).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // A literal with a colon never triggers a DNS lookup in InetAddress; this only canonicalizes the notation.
    private static String ipv6(String text) {
        if (!IPV6_LITERAL.matcher(text).matches()) {
            return text.toLowerCase(Locale.ROOT);
        }
        try {
            return InetAddress.getByName(text).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
