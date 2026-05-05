package com.phonemyat.midnightharbor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SolsticeMarkerSyntax {
    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "\\bSolstice\\.(BUG|TODO|IDEA|REVIEW)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT_PREFIX = Pattern.compile("^\\s*(//+|/\\*+|\\*+|#+|<!--)\\s*");
    private static final Pattern COMMENT_SUFFIX = Pattern.compile("\\s*(\\*/|-->)\\s*$");
    private static final Pattern MARKER_PREFIX = Pattern.compile(
            "^\\s*Solstice\\.(BUG|TODO|IDEA|REVIEW)\\b\\s*[:\\-]?\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private SolsticeMarkerSyntax() {
    }

    static String token(String type) {
        return "Solstice." + type;
    }

    static String typeIn(String text) {
        Matcher matcher = MARKER_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    static int markerOffset(String text) {
        Matcher matcher = MARKER_PATTERN.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    static boolean isComment(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("//")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*")
                || trimmed.startsWith("#")
                || trimmed.startsWith("<!--");
    }

    static String cleanMarkerText(String text) {
        String cleaned = COMMENT_PREFIX.matcher(text).replaceFirst("");
        cleaned = COMMENT_SUFFIX.matcher(cleaned).replaceFirst("").trim();
        cleaned = MARKER_PREFIX.matcher(cleaned).replaceFirst("").trim();
        return cleaned.isEmpty() ? "selected code" : cleaned;
    }
}
