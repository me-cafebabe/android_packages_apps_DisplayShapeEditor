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

public final class MainActivity extends Activity {
    private static final int OPEN_XML = 1;
    private static final int SAVE_XML = 2;
    private ShapeConfig config;
    private ShapePreview preview;
    private TextView status;
    private EditText topField;
    private EditText bottomField;
    private boolean rotated;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        config = new ShapeConfig();
        config.width = getWindowManager().getDefaultDisplay().getMode().getPhysicalWidth();
        config.height = getWindowManager().getDefaultDisplay().getMode().getPhysicalHeight();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        config.densityDpi = metrics.densityDpi;
        String saved = getPreferences(MODE_PRIVATE).getString("draft", null);
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
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(320)));
        LinearLayout actions = row(root);
        addHalf(actions, button("Rotate preview", () -> {
            rotated = !rotated;
            preview.setRotated(rotated);
        }));
        addHalf(actions, button("Full screen", this::showFullScreenPreview));

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
        addHalf(presets, button("Notch", () -> setPath("M -110,0 L -110,80 L 110,80 L 110,0 Z")));
        addHalf(presets, button("Hole punch", () -> setPath("M 0,30 C -17,30 -30,43 -30,60 "
                + "C -30,77 -17,90 0,90 C 17,90 30,77 30,60 C 30,43 17,30 0,30 Z")));
        LinearLayout other = row(form);
        addHalf(other, button("No cutout", () -> setPath("")));
        addHalf(other, button("Left notch", () -> setPath("M 0,0 L 0,80 L 200,80 L 200,0 Z @left")));
        EditText path = field(row(form), "Cutout path (SVG path + optional markers)", config.cutout,
                s -> config.cutout = s);
        path.setSingleLine(false);
        path.setMinLines(2);
        EditText approximation = field(row(form), "Bounding approximation (optional)",
                config.approximation, s -> config.approximation = s);
        approximation.setSingleLine(false);
        approximation.setMinLines(2);
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
        changed();
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

    private void setPath(String value) {
        config.cutout = value;
        config.approximation = "";
        showEditor();
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
