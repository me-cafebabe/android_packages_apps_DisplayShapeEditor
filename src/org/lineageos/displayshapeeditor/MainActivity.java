package org.lineageos.displayshapeeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int OPEN_XML = 1;
    private static final int SAVE_XML = 2;
    private ShapeConfig config;
    private ShapePreview preview;
    private TextView status;
    private EditText topField;
    private EditText bottomField;
    private EditText pathField;
    private EditText approximationField;
    private EditText widthField;
    private EditText heightField;
    private EditText xField;
    private EditText yField;
    private boolean updatingPath;
    private boolean editApproximation;
    private TextView pointLabel;
    private int lastPresetX, lastPresetY;
    private boolean rotated;
    private boolean expandedCanvas;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        config = new ShapeConfig();
        config.width = getWindowManager().getDefaultDisplay().getMode().getPhysicalWidth();
        config.height = getWindowManager().getDefaultDisplay().getMode().getPhysicalHeight();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        config.densityDpi = metrics.densityDpi;
        String saved = getPreferences(MODE_PRIVATE).getString("draft", null);
        expandedCanvas = getPreferences(MODE_PRIVATE).getBoolean("expandedCanvas", false);
        if (saved != null) {
            try { config = ShapeConfig.fromJson(saved); }
            catch (JSONException ignored) { /* Start fresh if the draft is damaged. */ }
        }
        showEditor();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String content, int size) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(24, 42, 59));
        view.setPadding(dp(2), dp(8), dp(2), dp(5));
        return view;
    }

    private void heading(LinearLayout box, String title) { box.addView(text(title, 19)); }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row);
        return row;
    }

    private void addHalf(LinearLayout row, View view) {
        row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private EditText field(LinearLayout parent, String label, String value,
            Consumer<String> change) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(3), 0, dp(3), 0);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(text(label, 13));
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setText(value);
        box.addView(edit);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                change.accept(s.toString());
                changed();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return edit;
    }

    private void showEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        setContentView(root);

        TextView title = text("DISPLAY / SHAPE", 23);
        title.setTextColor(Color.rgb(0, 93, 120));
        title.setPadding(dp(18), dp(14), dp(18), dp(10));
        root.addView(title);
        preview = new ShapePreview(this, config, (top, pixels) -> {
            if (top) topField.setText(pixels + "px");
            else bottomField.setText(pixels + "px");
        });
        preview.setShapeListener((width, height, x, y) -> {
            widthField.setText(Integer.toString(width));
            heightField.setText(Integer.toString(height));
            xField.setText(Integer.toString(x));
            yField.setText(Integer.toString(y));
        });
        preview.setEditApproximation(editApproximation);
        preview.setPathListener(new ShapePreview.PathListener() {
            @Override public void onPointSelected(int index, float x, float y) {
                pointLabel.setText(String.format(java.util.Locale.ROOT,
                        "Point %d: %.1f, %.1f px (tap coordinates for exact values)",
                        index + 1, x, y));
            }

            @Override public void onPathChanged(boolean bounds, String path) {
                if (bounds) {
                    approximationField.setText(path);
                } else {
                    String old = config.cutout;
                    if (config.linkApproximation && !config.approximation.isEmpty()) {
                        int selected = preview.getSelectedPoint();
                        try {
                            List<PathEditor.Point> before = PathEditor.points(old, config.width,
                                    config.height, config.densityDpi);
                            List<PathEditor.Point> after = PathEditor.points(path, config.width,
                                    config.height, config.densityDpi);
                            syncApproximation(old, path,
                                    after.get(selected).x - before.get(selected).x,
                                    after.get(selected).y - before.get(selected).y);
                        } catch (RuntimeException ignored) { /* Keep an independently edited bound. */ }
                    }
                    updatingPath = true;
                    pathField.setText(path);
                    updatingPath = false;
                }
                changed();
            }
        });
        root.addView(preview, new LinearLayout.LayoutParams(-1, canvasHeight()));
        LinearLayout actions = row(root);
        addHalf(actions, button("Rotate", () -> {
            rotated = !rotated;
            preview.setRotated(rotated);
        }));
        Button resize = new Button(this);
        resize.setText(expandedCanvas ? "Smaller" : "Larger");
        resize.setAllCaps(false);
        resize.setContentDescription("Toggle larger editor canvas");
        addHalf(actions, resize);
        resize.setOnClickListener(v -> {
            expandedCanvas = !expandedCanvas;
            getPreferences(MODE_PRIVATE).edit().putBoolean("expandedCanvas", expandedCanvas).apply();
            preview.getLayoutParams().height = canvasHeight();
            preview.requestLayout();
            resize.setText(expandedCanvas ? "Smaller" : "Larger");
        });
        addHalf(actions, button("Full screen", this::showFullScreenPreview));
        LinearLayout zoomControls = row(root);
        addHalf(zoomControls, button("1x", () -> preview.setZoom(1)));
        addHalf(zoomControls, button("2x", () -> preview.setZoom(2)));
        addHalf(zoomControls, button("4x", () -> preview.setZoom(4)));

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(8), dp(14), dp(28));
        scroll.addView(form);
        status = text("", 14);
        form.addView(status);
        heading(form, "Target display");
        LinearLayout dimensions = row(form);
        field(dimensions, "Native width (px)", Integer.toString(config.width),
                s -> config.width = integer(s));
        field(dimensions, "Native height (px)", Integer.toString(config.height),
                s -> config.height = integer(s));
        LinearLayout density = row(form);
        field(density, "Density (dpi)", Integer.toString(config.densityDpi),
                s -> config.densityDpi = integer(s));
        addHalf(density, button("This device", () -> {
            config.width = getWindowManager().getDefaultDisplay().getMode().getPhysicalWidth();
            config.height = getWindowManager().getDefaultDisplay().getMode().getPhysicalHeight();
            config.densityDpi = getResources().getDisplayMetrics().densityDpi;
            showEditor();
            changed();
        }));

        heading(form, "Rounded corners");
        LinearLayout radii = row(form);
        field(radii, "Default radius", config.radius, s -> config.radius = s);
        topField = field(radii, "Top radius (drag dot)", config.top, s -> config.top = s);
        LinearLayout bottom = row(form);
        bottomField = field(bottom, "Bottom radius (drag dot)", config.bottom,
                s -> config.bottom = s);
        field(bottom, "Default adjustment", config.adjustment, s -> config.adjustment = s);
        LinearLayout adjust = row(form);
        field(adjust, "Top adjustment", config.topAdjustment,
                s -> config.topAdjustment = s);
        field(adjust, "Bottom adjustment", config.bottomAdjustment,
                s -> config.bottomAdjustment = s);
        form.addView(text("Zero top/bottom radius uses the default. Adjustments affect reported "
                + "corner radius, not the outline shown here.", 13));

        heading(form, "Display cutout");
        LinearLayout presets = row(form);
        addHalf(presets, button("Notch", () -> setPreset(1)));
        addHalf(presets, button("Pill", () -> setPreset(2)));
        LinearLayout other = row(form);
        addHalf(other, button("Hole punch", () -> setPreset(3)));
        addHalf(other, button("Left notch", () -> setPreset(4)));
        addHalf(row(form), button("No cutout", () -> {
            config.preset = 0;
            config.cutout = "";
            config.approximation = "";
            showEditor();
        }));
        if (config.preset != 0) {
            form.addView(text("Drag orange dots to move or resize; enter native pixels for precision.", 13));
            LinearLayout size = row(form);
            widthField = field(size, "Width (px)", Integer.toString(config.shapeWidth),
                    s -> { config.shapeWidth = integer(s); updatePreset(); });
            heightField = field(size, "Height (px)", Integer.toString(config.shapeHeight),
                    s -> { config.shapeHeight = integer(s); updatePreset(); });
            LinearLayout position = row(form);
            xField = field(position, "X offset (px)", Integer.toString(config.offsetX),
                    s -> { config.offsetX = integer(s); updatePreset(); });
            yField = field(position, "Y offset (px)", Integer.toString(config.offsetY),
                    s -> { config.offsetY = integer(s); updatePreset(); });
            field(row(form), "Corner curve (px)", Integer.toString(config.curve),
                    s -> { config.curve = integer(s); updatePreset(); });
        }
        pathField = field(row(form), "Cutout path (editing switches to custom mode)", config.cutout,
                s -> {
                    config.cutout = s;
                    if (!updatingPath) {
                        config.preset = 0;
                        preview.invalidate();
                    }
                });
        pathField.setSingleLine(false);
        pathField.setMinLines(2);
        approximationField = field(row(form), "Bounding approximation (optional)",
                config.approximation, s -> config.approximation = s);
        approximationField.setSingleLine(false);
        approximationField.setMinLines(2);
        LinearLayout pathModes = row(form);
        addHalf(pathModes, button("Edit visible path", () -> {
            editApproximation = false;
            if (config.preset != 0) {
                config.preset = 0;
                showEditor();
            } else {
                preview.setEditApproximation(false);
                pointLabel.setText("Tap a path point to edit it");
            }
        }));
        addHalf(pathModes, button("Edit bounds", () -> {
            if (config.cutout.isEmpty()) { message("Create a cutout first"); return; }
            if (config.approximation.isEmpty()) approximationField.setText(config.cutout);
            config.linkApproximation = false;
            editApproximation = true;
            preview.setEditApproximation(true);
            pointLabel.setText("Tap a bounding path point to edit it");
            changed();
        }));
        CheckBox linkBounds = new CheckBox(this);
        linkBounds.setText("Move approximation with visible path");
        linkBounds.setChecked(config.linkApproximation);
        linkBounds.setOnCheckedChangeListener((v, checked) -> {
            config.linkApproximation = checked;
            changed();
        });
        form.addView(linkBounds);
        pointLabel = text("Tap a path point to edit it", 13);
        form.addView(pointLabel);
        LinearLayout nudges = row(form);
        addHalf(nudges, button("X -1", () -> nudge(-1, 0)));
        addHalf(nudges, button("X +1", () -> nudge(1, 0)));
        addHalf(nudges, button("Y -1", () -> nudge(0, -1)));
        addHalf(nudges, button("Y +1", () -> nudge(0, 1)));
        addHalf(row(form), button("Point coordinates...", this::editPointCoordinates));
        CheckBox showBounds = new CheckBox(this);
        showBounds.setText("Show approximation in cyan");
        showBounds.setChecked(true);
        showBounds.setOnCheckedChangeListener((v, checked) -> preview.setApproximationVisible(checked));
        form.addView(showBounds);
        CheckBox fill = new CheckBox(this);
        fill.setText("Fill cutout black in software");
        fill.setChecked(config.fill);
        fill.setOnCheckedChangeListener((v, checked) -> { config.fill = checked; changed(); });
        form.addView(fill);
        CheckBox mask = new CheckBox(this);
        mask.setText("Mask cutout by shrinking display area");
        mask.setChecked(config.mask);
        mask.setOnCheckedChangeListener((v, checked) -> { config.mask = checked; changed(); });
        form.addView(mask);
        form.addView(text("Path origin is top center in native pixels. Supported: M L H V Q C Z "
                + "(and lowercase relatives); @left, @right, @bottom, @cutout and @dp.", 13));

        heading(form, "Device-tree output");
        form.addView(text("Import an existing values XML file, or save a standalone "
                + "values/display_shape.xml for a framework-res overlay. No system settings "
                + "are changed by this app.", 13));
        LinearLayout files = row(form);
        addHalf(files, button("Import XML", this::importXml));
        addHalf(files, button("Export XML", this::exportXml));
        addHalf(row(form), button("View XML", this::viewXml));
        lastPresetX = config.offsetX;
        lastPresetY = config.offsetY;
        changed();
    }

    private void nudge(int dx, int dy) {
        List<PathEditor.Point> points = selectedPoints();
        int selected = preview.getSelectedPoint();
        if (selected < 0 || points == null || selected >= points.size()) return;
        PathEditor.Point point = points.get(selected);
        changeSelectedPoint(point.x + dx, point.y + dy);
    }

    private List<PathEditor.Point> selectedPoints() {
        String spec = editApproximation ? config.approximation : config.cutout;
        if (spec.isEmpty()) return null;
        try { return PathEditor.points(spec, config.width, config.height, config.densityDpi); }
        catch (IllegalArgumentException e) { message(e.getMessage()); return null; }
    }

    private void changeSelectedPoint(float x, float y) {
        int index = preview.getSelectedPoint();
        List<PathEditor.Point> points = selectedPoints();
        if (index < 0 || points == null || index >= points.size()) return;
        String spec = editApproximation ? config.approximation : config.cutout;
        String updated = PathEditor.movePoint(spec, config.width, config.height,
                config.densityDpi, index, x, y);
        if (editApproximation) approximationField.setText(updated);
        else {
            syncApproximation(spec, updated, x - points.get(index).x, y - points.get(index).y);
            updatingPath = true;
            pathField.setText(updated);
            updatingPath = false;
        }
        pointLabel.setText(String.format(java.util.Locale.ROOT,
                "Point %d: %.1f, %.1f px", index + 1, x, y));
        changed();
    }

    private void editPointCoordinates() {
        List<PathEditor.Point> points = selectedPoints();
        int selected = preview.getSelectedPoint();
        if (selected < 0 || points == null || selected >= points.size()) {
            message("Tap a path point first");
            return;
        }
        PathEditor.Point point = points.get(selected);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        EditText x = new EditText(this);
        EditText y = new EditText(this);
        x.setHint("X in native pixels");
        y.setHint("Y in native pixels");
        x.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        y.setInputType(x.getInputType());
        x.setText(Float.toString(point.x));
        y.setText(Float.toString(point.y));
        fields.addView(x);
        fields.addView(y);
        new AlertDialog.Builder(this).setTitle("Point " + (selected + 1))
                .setView(fields).setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try { changeSelectedPoint(Float.parseFloat(x.getText().toString()),
                            Float.parseFloat(y.getText().toString())); }
                    catch (NumberFormatException e) { message("Enter valid coordinates"); }
                }).show();
    }

    private void syncApproximation(String previous, String updated, float dx, float dy) {
        if (!config.linkApproximation || config.approximation.isEmpty()) return;
        try {
            approximationField.setText(config.approximation.equals(previous) ? updated
                    : PathEditor.translate(config.approximation, config.width, config.height,
                            config.densityDpi, dx, dy));
        } catch (IllegalArgumentException ignored) { /* Preserve invalid text for correction. */ }
    }

    private int canvasHeight() {
        if (!expandedCanvas) return dp(320);
        return Math.max(dp(320), Math.min(dp(560),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.68f)));
    }

    private void showFullScreenPreview() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        ShapePreview large = new ShapePreview(this, config, null);
        large.setRotated(rotated);
        large.setFullScreen(true);
        large.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) dialog.dismiss();
            return true;
        });
        dialog.setContentView(large);
        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        dialog.show();
        if (window != null) window.setLayout(-1, -1);
    }

    private int integer(String input) {
        try { return Integer.parseInt(input); }
        catch (NumberFormatException e) { return 0; }
    }

    private void setPreset(int preset) {
        editApproximation = false;
        config.preset = preset;
        config.shapeWidth = preset == 3 ? 60 : 220;
        config.shapeHeight = preset == 3 ? 60 : 80;
        config.offsetX = 0;
        config.offsetY = preset == 3 ? 30 : 0;
        config.curve = preset == 2 ? 30 : 0;
        config.updatePresetPath();
        config.approximation = "";
        showEditor();
    }

    private void updatePreset() {
        if (config.shapeWidth < 1 || config.shapeHeight < 1 || config.curve < 0) return;
        String old = config.cutout;
        config.updatePresetPath();
        if (pathField != null) {
            if (approximationField != null) syncApproximation(old, config.cutout,
                    config.offsetX - lastPresetX, config.offsetY - lastPresetY);
            updatingPath = true;
            pathField.setText(config.cutout);
            updatingPath = false;
        }
        lastPresetX = config.offsetX;
        lastPresetY = config.offsetY;
        preview.invalidate();
    }

    private void changed() {
        preview.invalidate();
        try {
            config.validate();
            status.setText("Ready to export · local preview only");
            status.setTextColor(Color.rgb(0, 105, 96));
        } catch (IllegalArgumentException e) {
            status.setText(e.getMessage());
            status.setTextColor(Color.rgb(164, 41, 43));
        }
        try {
            getPreferences(MODE_PRIVATE).edit().putString("draft", config.toJson().toString()).apply();
        } catch (JSONException ignored) { /* JSON only contains local primitive fields. */ }
    }

    private void importXml() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/xml");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, OPEN_XML);
    }

    private void exportXml() {
        try { config.validate(); }
        catch (IllegalArgumentException e) { message(e.getMessage()); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/xml");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "display_shape.xml");
        startActivityForResult(intent, SAVE_XML);
    }

    private void viewXml() {
        try {
            config.validate();
            TextView code = text(OverlayXml.write(config), 12);
            code.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(code);
            new AlertDialog.Builder(this).setTitle("Overlay XML")
                    .setView(scroll).setPositiveButton("Close", null).show();
        } catch (Exception e) { message(e.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == OPEN_XML) {
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if (in == null) throw new IllegalArgumentException("Cannot open file");
                    config = OverlayXml.read(in, config);
                }
                showEditor();
                message("Imported overlay values");
            } else if (request == SAVE_XML) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IllegalArgumentException("Cannot save file");
                    out.write(OverlayXml.write(config).getBytes(StandardCharsets.UTF_8));
                }
                message("Exported display_shape.xml");
            }
        } catch (Exception e) { message("XML: " + e.getMessage()); }
    }

    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
