package org.lineageos.displayshapeeditor;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the supported SVG path subset to absolute commands for point editing. */
final class PathEditor {
    private static final Pattern TOKEN = Pattern.compile(
            "[MmLlHhVvCcQqZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?");
    private static final Pattern MARKER = Pattern.compile(
            "@(?:bottom|left|right|bind_left_cutout|bind_right_cutout)");

    static final class Point {
        final int segment, command, index;
        final float x, y;
        final boolean control;

        Point(int segment, int command, int index, float x, float y, boolean control) {
            this.segment = segment;
            this.command = command;
            this.index = index;
            this.x = x;
            this.y = y;
            this.control = control;
        }
    }

    private static final class Command {
        final char type;
        final float[] coordinates;
        Command(char type, float... coordinates) {
            this.type = type;
            this.coordinates = coordinates;
        }
    }

    private static final class Segment {
        final String markers;
        final float originX, originY;
        final List<Command> commands = new ArrayList<>();
        Segment(String markers, int width, int height) {
            this.markers = markers;
            originX = markers.contains("@left") || markers.contains("@bind_left_cutout")
                    ? 0 : markers.contains("@right") || markers.contains("@bind_right_cutout")
                    ? width : width / 2f;
            originY = markers.contains("@bottom") ? height : 0;
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private final boolean dp;
    private final float factor;

    private PathEditor(String spec, int width, int height, int densityDpi) {
        CutoutPath.parse(spec, width, height, densityDpi);
        String raw = spec.trim();
        dp = raw.endsWith("@dp");
        if (dp) raw = raw.substring(0, raw.length() - 3).trim();
        factor = dp ? densityDpi / 160f : 1f;
        for (String piece : raw.split("@cutout", -1)) {
            Matcher marker = MARKER.matcher(piece);
            StringBuilder markers = new StringBuilder();
            while (marker.find()) markers.append(' ').append(marker.group());
            Segment segment = new Segment(markers.toString(), width, height);
            parseCommands(MARKER.matcher(piece).replaceAll(""), segment.commands);
            segments.add(segment);
        }
    }

    static List<Point> points(String spec, int width, int height, int densityDpi) {
        return new PathEditor(spec, width, height, densityDpi).points();
    }

    static String movePoint(String spec, int width, int height, int densityDpi,
            int pointIndex, float x, float y) {
        PathEditor editor = new PathEditor(spec, width, height, densityDpi);
        List<Point> points = editor.points();
        if (pointIndex < 0 || pointIndex >= points.size()) return spec;
        Point point = points.get(pointIndex);
        Segment segment = editor.segments.get(point.segment);
        float[] coords = segment.commands.get(point.command).coordinates;
        coords[point.index] = (x - segment.originX) / editor.factor;
        coords[point.index + 1] = (y - segment.originY) / editor.factor;
        return editor.serialize();
    }

    static String translate(String spec, int width, int height, int densityDpi, float dx, float dy) {
        if (spec.isEmpty() || dx == 0 && dy == 0) return spec;
        PathEditor editor = new PathEditor(spec, width, height, densityDpi);
        for (Segment segment : editor.segments) {
            for (Command command : segment.commands) {
                for (int i = 0; i < command.coordinates.length; i += 2) {
                    command.coordinates[i] += dx / editor.factor;
                    command.coordinates[i + 1] += dy / editor.factor;
                }
            }
        }
        return editor.serialize();
    }

    static String resize(String spec, int width, int height, int densityDpi,
            RectF oldBounds, RectF newBounds) {
        if (oldBounds.width() <= 0 || oldBounds.height() <= 0
                || newBounds.width() <= 0 || newBounds.height() <= 0) {
            throw new IllegalArgumentException("Cutout needs nonzero width and height to resize");
        }
        PathEditor editor = new PathEditor(spec, width, height, densityDpi);
        float scaleX = newBounds.width() / oldBounds.width();
        float scaleY = newBounds.height() / oldBounds.height();
        for (Segment segment : editor.segments) {
            for (Command command : segment.commands) {
                for (int i = 0; i < command.coordinates.length; i += 2) {
                    float x = segment.originX + command.coordinates[i] * editor.factor;
                    float y = segment.originY + command.coordinates[i + 1] * editor.factor;
                    command.coordinates[i] = (newBounds.left
                            + (x - oldBounds.left) * scaleX - segment.originX) / editor.factor;
                    command.coordinates[i + 1] = (newBounds.top
                            + (y - oldBounds.top) * scaleY - segment.originY) / editor.factor;
                }
            }
        }
        return editor.serialize();
    }

    private List<Point> points() {
        List<Point> result = new ArrayList<>();
        for (int s = 0; s < segments.size(); s++) {
            Segment segment = segments.get(s);
            for (int c = 0; c < segment.commands.size(); c++) {
                Command command = segment.commands.get(c);
                for (int i = 0; i < command.coordinates.length; i += 2) {
                    result.add(new Point(s, c, i,
                            segment.originX + command.coordinates[i] * factor,
                            segment.originY + command.coordinates[i + 1] * factor,
                            i < command.coordinates.length - 2));
                }
            }
        }
        return result;
    }

    private String serialize() {
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < segments.size(); s++) {
            if (s != 0) out.append(" @cutout ");
            Segment segment = segments.get(s);
            for (Command command : segment.commands) {
                if (out.length() != 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
                out.append(command.type);
                for (int i = 0; i < command.coordinates.length; i += 2) {
                    out.append(' ').append(number(command.coordinates[i])).append(',')
                            .append(number(command.coordinates[i + 1]));
                }
            }
            out.append(segment.markers);
        }
        if (dp) out.append(" @dp");
        return out.toString();
    }

    private static String number(float value) {
        if (value == Math.round(value)) return Integer.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void parseCommands(String data, List<Command> output) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(data);
        while (matcher.find()) tokens.add(matcher.group());
        float x = 0, y = 0, firstX = 0, firstY = 0;
        char type = ' ';
        int i = 0;
        while (i < tokens.size()) {
            if (Character.isLetter(tokens.get(i).charAt(0))) {
                type = tokens.get(i++).charAt(0);
                if (type == 'Z' || type == 'z') {
                    output.add(new Command('Z'));
                    x = firstX;
                    y = firstY;
                    type = ' ';
                    continue;
                }
            }
            int length;
            switch (Character.toUpperCase(type)) {
                case 'M': case 'L': length = 2; break;
                case 'H': case 'V': length = 1; break;
                case 'Q': length = 4; break;
                case 'C': length = 6; break;
                default: throw new IllegalArgumentException("Unsupported path command");
            }
            if (i + length > tokens.size()) throw new IllegalArgumentException("Incomplete path command");
            float[] values = new float[length];
            for (int j = 0; j < length; j++) values[j] = Float.parseFloat(tokens.get(i++));
            boolean relative = Character.isLowerCase(type);
            char command = Character.toUpperCase(type);
            if (command == 'H') {
                output.add(new Command('L', values[0] + (relative ? x : 0), y));
            } else if (command == 'V') {
                output.add(new Command('L', x, values[0] + (relative ? y : 0)));
            } else {
                for (int j = 0; j < values.length; j += 2) {
                    if (relative) { values[j] += x; values[j + 1] += y; }
                }
                output.add(new Command(command, values));
            }
            Command last = output.get(output.size() - 1);
            x = last.coordinates[last.coordinates.length - 2];
            y = last.coordinates[last.coordinates.length - 1];
            if (command == 'M') {
                firstX = x;
                firstY = y;
                type = relative ? 'l' : 'L';
            }
        }
    }

}
