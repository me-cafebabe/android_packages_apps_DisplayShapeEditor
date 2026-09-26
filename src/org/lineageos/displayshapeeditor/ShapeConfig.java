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
        return c;
    }
}
