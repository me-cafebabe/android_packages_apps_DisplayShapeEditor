package org.lineageos.displayshapeeditor;

import android.util.DisplayMetrics;

import org.json.JSONException;
import org.json.JSONObject;

final class ShapeConfig {
    int width = 1080;
    int height = 2400;
    int densityDpi = 420;
    String cutout = "";
    String approximation = "";
    boolean fill;
    boolean mask;
    String radius = "0px";
    String top = "0px";
    String bottom = "0px";
    String adjustment = "0px";
    String topAdjustment = "0px";
    String bottomAdjustment = "0px";
    int preset;
    int shapeWidth = 220;
    int shapeHeight = 80;
    int offsetX;
    int offsetY;
    int curve;
    boolean linkApproximation = true;

    void updatePresetPath() {
        if (preset == 0) return;
        int left = offsetX - shapeWidth / 2;
        int right = left + shapeWidth;
        int top = offsetY;
        int bottom = top + shapeHeight;
        if (preset == 4) {
            left = offsetX;
            right = left + shapeWidth;
        }
        int rounding = Math.max(0, Math.min(curve, Math.min(shapeWidth, shapeHeight) / 2));
        if (preset == 3) {
            float cx = (left + right) / 2f;
            float cy = (top + bottom) / 2f;
            float rx = shapeWidth / 2f;
            float ry = shapeHeight / 2f;
            float k = 0.55228475f;
            cutout = String.format(java.util.Locale.ROOT,
                    "M %.2f,%.2f C %.2f,%.2f %.2f,%.2f %.2f,%.2f "
                    + "C %.2f,%.2f %.2f,%.2f %.2f,%.2f "
                    + "C %.2f,%.2f %.2f,%.2f %.2f,%.2f "
                    + "C %.2f,%.2f %.2f,%.2f %.2f,%.2f Z",
                    cx, top, cx - rx * k, top, left, cy - ry * k, left, cy,
                    left, cy + ry * k, cx - rx * k, bottom, cx, bottom,
                    cx + rx * k, bottom, right, cy + ry * k, right, cy,
                    right, cy - ry * k, cx + rx * k, top, cx, top);
        } else if (preset == 2) {
            cutout = "M " + (left + rounding) + "," + top
                    + " L " + (right - rounding) + "," + top
                    + " Q " + right + "," + top + " " + right + "," + (top + rounding)
                    + " L " + right + "," + (bottom - rounding)
                    + " Q " + right + "," + bottom + " " + (right - rounding) + "," + bottom
                    + " L " + (left + rounding) + "," + bottom
                    + " Q " + left + "," + bottom + " " + left + "," + (bottom - rounding)
                    + " L " + left + "," + (top + rounding)
                    + " Q " + left + "," + top + " " + (left + rounding) + "," + top + " Z";
        } else {
            cutout = "M " + left + "," + top + " L " + left + "," + (bottom - rounding)
                    + " Q " + left + "," + bottom + " " + (left + rounding) + "," + bottom
                    + " L " + (right - rounding) + "," + bottom
                    + " Q " + right + "," + bottom + " " + right + "," + (bottom - rounding)
                    + " L " + right + "," + top + " Z" + (preset == 4 ? " @left" : "");
        }
    }

    float pixels(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.endsWith("dip")) v = v.substring(0, v.length() - 3) + "dp";
        float multiplier = v.endsWith("dp") ? densityDpi / (float) DisplayMetrics.DENSITY_DEFAULT : 1;
        if (v.endsWith("dp") || v.endsWith("px")) v = v.substring(0, v.length() - 2);
        return Float.parseFloat(v) * multiplier;
    }

    float topRadius() {
        float value = pixels(top);
        return value > 0 ? value : pixels(radius);
    }

    float bottomRadius() {
        float value = pixels(bottom);
        return value > 0 ? value : pixels(radius);
    }

    void validate() {
        if (width < 1 || height < 1 || densityDpi < 1) {
            throw new IllegalArgumentException("Resolution and density must be positive");
        }
        if (preset != 0 && (shapeWidth < 1 || shapeHeight < 1 || curve < 0)) {
            throw new IllegalArgumentException("Preset size must be positive; curvature cannot be negative");
        }
        for (String value : new String[]{radius, top, bottom, adjustment,
                topAdjustment, bottomAdjustment}) {
            if (!value.matches("[0-9]+(\\.[0-9]+)?(px|dp|dip)")) {
                throw new IllegalArgumentException("Use dimensions such as 24px or 12dp");
            }
        }
        if (!cutout.isEmpty()) CutoutPath.parse(cutout, width, height, densityDpi);
        if (!approximation.isEmpty()) CutoutPath.parse(approximation, width, height, densityDpi);
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("width", width).put("height", height).put("densityDpi", densityDpi)
                .put("cutout", cutout).put("approximation", approximation)
                .put("fill", fill).put("mask", mask).put("radius", radius)
                .put("top", top).put("bottom", bottom).put("adjustment", adjustment)
                .put("topAdjustment", topAdjustment).put("bottomAdjustment", bottomAdjustment);
        json.put("preset", preset).put("shapeWidth", shapeWidth)
                .put("shapeHeight", shapeHeight).put("offsetX", offsetX)
                .put("offsetY", offsetY).put("curve", curve)
                .put("linkApproximation", linkApproximation);
        return json;
    }

    static ShapeConfig fromJson(String text) throws JSONException {
        JSONObject json = new JSONObject(text);
        ShapeConfig c = new ShapeConfig();
        c.width = json.optInt("width", c.width);
        c.height = json.optInt("height", c.height);
        c.densityDpi = json.optInt("densityDpi", c.densityDpi);
        c.cutout = json.optString("cutout", c.cutout);
        c.approximation = json.optString("approximation", c.approximation);
        c.fill = json.optBoolean("fill");
        c.mask = json.optBoolean("mask");
        c.radius = json.optString("radius", c.radius);
        c.top = json.optString("top", c.top);
        c.bottom = json.optString("bottom", c.bottom);
        c.adjustment = json.optString("adjustment", c.adjustment);
        c.topAdjustment = json.optString("topAdjustment", c.topAdjustment);
        c.bottomAdjustment = json.optString("bottomAdjustment", c.bottomAdjustment);
        c.preset = json.optInt("preset");
        c.shapeWidth = json.optInt("shapeWidth", c.shapeWidth);
        c.shapeHeight = json.optInt("shapeHeight", c.shapeHeight);
        c.offsetX = json.optInt("offsetX");
        c.offsetY = json.optInt("offsetY");
        c.curve = json.optInt("curve");
        c.linkApproximation = json.optBoolean("linkApproximation", true);
        return c;
    }
}
