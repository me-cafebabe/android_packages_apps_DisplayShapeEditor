package org.lineageos.displayshapeeditor;

import android.graphics.Path;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SVG path subset used by the editor. Unsupported commands are rejected, never silently lost. */
final class CutoutPath {
    private static final Pattern TOKEN = Pattern.compile(
            "[MmLlHhVvCcQqZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?");

    static Path parse(String spec, int width, int height, int densityDpi) {
        Path result = new Path();
        String trimmed = spec.trim();
        boolean dp = trimmed.endsWith("@dp");
        if (dp) trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        for (String segment : trimmed.split("@cutout", -1)) {
            boolean bottom = segment.contains("@bottom");
            boolean left = segment.contains("@left") || segment.contains("@bind_left_cutout");
            boolean right = segment.contains("@right") || segment.contains("@bind_right_cutout");
            if (left && right) throw new IllegalArgumentException("Conflicting left/right markers");
            String data = segment.replaceAll("@(?:bottom|left|right|bind_left_cutout|bind_right_cutout)", "").trim();
            if (data.isEmpty()) throw new IllegalArgumentException("Empty cutout path");
            Path piece = pathData(data);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            float scale = dp ? densityDpi / 160f : 1f;
            matrix.setScale(scale, scale);
            piece.transform(matrix);
            matrix.setTranslate(left ? 0 : right ? width : width / 2f, bottom ? height : 0);
            piece.transform(matrix);
            result.addPath(piece);
        }
        return result;
    }

    private static Path pathData(String data) {
        Matcher matcher = TOKEN.matcher(data);
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        int end = 0;
        while (matcher.find()) {
            if (!data.substring(end, matcher.start()).matches("[\\s,]*")) {
                throw new IllegalArgumentException("Unsupported path syntax near: " + data.substring(end));
            }
            tokens.add(matcher.group());
            end = matcher.end();
        }
        if (tokens.isEmpty() || !data.substring(end).matches("[\\s,]*")) {
            throw new IllegalArgumentException("Invalid cutout path");
        }
        Path path = new Path();
        float x = 0, y = 0, startX = 0, startY = 0;
        char command = ' ';
        int i = 0;
        boolean started = false;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                i++;
                if (command == 'Z' || command == 'z') {
                    path.close();
                    x = startX;
                    y = startY;
                    command = ' ';
                    continue;
                }
            }
            int count;
            switch (Character.toUpperCase(command)) {
                case 'M': case 'L': count = 2; break;
                case 'H': case 'V': count = 1; break;
                case 'Q': count = 4; break;
                case 'C': count = 6; break;
                default: throw new IllegalArgumentException("Unsupported SVG command: " + command);
            }
            if (i + count > tokens.size()) throw new IllegalArgumentException("Incomplete SVG command");
            float[] v = new float[count];
            for (int j = 0; j < count; j++) {
                if (Character.isLetter(tokens.get(i + j).charAt(0))) {
                    throw new IllegalArgumentException("Incomplete SVG command");
                }
                v[j] = Float.parseFloat(tokens.get(i + j));
                if (!Float.isFinite(v[j])) throw new IllegalArgumentException("Invalid coordinate");
            }
            i += count;
            boolean relative = Character.isLowerCase(command);
            switch (Character.toUpperCase(command)) {
                case 'M':
                    x = v[0] + (relative ? x : 0);
                    y = v[1] + (relative ? y : 0);
                    path.moveTo(x, y);
                    startX = x; startY = y;
                    started = true;
                    command = relative ? 'l' : 'L';
                    break;
                case 'L':
                    requireStart(started);
                    x = v[0] + (relative ? x : 0);
                    y = v[1] + (relative ? y : 0);
                    path.lineTo(x, y);
                    break;
                case 'H':
                    requireStart(started);
                    x = v[0] + (relative ? x : 0);
                    path.lineTo(x, y);
                    break;
                case 'V':
                    requireStart(started);
                    y = v[0] + (relative ? y : 0);
                    path.lineTo(x, y);
                    break;
                case 'Q':
                    requireStart(started);
                    path.quadTo(v[0] + (relative ? x : 0), v[1] + (relative ? y : 0),
                            v[2] + (relative ? x : 0), v[3] + (relative ? y : 0));
                    x = v[2] + (relative ? x : 0);
                    y = v[3] + (relative ? y : 0);
                    break;
                case 'C':
                    requireStart(started);
                    path.cubicTo(v[0] + (relative ? x : 0), v[1] + (relative ? y : 0),
                            v[2] + (relative ? x : 0), v[3] + (relative ? y : 0),
                            v[4] + (relative ? x : 0), v[5] + (relative ? y : 0));
                    x = v[4] + (relative ? x : 0);
                    y = v[5] + (relative ? y : 0);
                    break;
            }
        }
        requireStart(started);
        return path;
    }

    private static void requireStart(boolean started) {
        if (!started) throw new IllegalArgumentException("SVG path must begin with M");
    }

    private CutoutPath() {}
}
