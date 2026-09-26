package org.lineageos.displayshapeeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class ShapePreview extends View {
    interface CornerListener { void onRadius(boolean top, int pixels); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private ShapeConfig config;
    private CornerListener listener;
    private boolean rotated;
    private boolean approximationVisible = true;
    private float scale;
    private int dragCorner = -1;

    ShapePreview(Context context, ShapeConfig config, CornerListener listener) {
        super(context);
        this.config = config;
        this.listener = listener;
        setBackgroundColor(Color.rgb(17, 25, 39));
    }

    void setConfig(ShapeConfig value) { config = value; invalidate(); }
    void setRotated(boolean value) { rotated = value; invalidate(); }
    void setApproximationVisible(boolean value) { approximationVisible = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = Math.max(1, config.width);
        int height = Math.max(1, config.height);
        float viewWidth = rotated ? height : width;
        float viewHeight = rotated ? width : height;
        scale = Math.min((getWidth() - 48f) / viewWidth, (getHeight() - 48f) / viewHeight);
        if (scale <= 0) return;
        float drawnWidth = viewWidth * scale;
        float drawnHeight = viewHeight * scale;
        float left = (getWidth() - drawnWidth) / 2;
        float top = (getHeight() - drawnHeight) / 2;
        bounds.set(left, top, left + drawnWidth, top + drawnHeight);

        canvas.save();
        canvas.translate(left, top);
        if (rotated) {
            canvas.translate(height * scale, 0);
            canvas.rotate(90);
        }
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(236, 243, 250));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.rgb(194, 210, 225));
        paint.setStrokeWidth(Math.max(1, 1 / scale));
        for (int x = 0; x < width; x += 100) canvas.drawLine(x, 0, x, height, paint);
        for (int y = 0; y < height; y += 100) canvas.drawLine(0, y, width, y, paint);

        float upper = radius(true, width, height);
        float lower = radius(false, width, height);
        Path corners = new Path();
        corners.moveTo(0, upper);
        corners.quadTo(0, 0, upper, 0);
        corners.lineTo(width - upper, 0);
        corners.quadTo(width, 0, width, upper);
        corners.lineTo(width, height - lower);
        corners.quadTo(width, height, width - lower, height);
        corners.lineTo(lower, height);
        corners.quadTo(0, height, 0, height - lower);
        corners.close();
        paint.setColor(Color.rgb(17, 25, 39));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2, 3 / scale));
        canvas.drawPath(corners, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, upper, upper, paint);
        canvas.drawRect(width - upper, 0, width, upper, paint);
        canvas.drawRect(0, height - lower, lower, height, paint);
        canvas.drawRect(width - lower, height - lower, width, height, paint);
        paint.setColor(Color.rgb(236, 243, 250));
        canvas.drawPath(corners, paint);

        try {
            if (!config.cutout.trim().isEmpty()) {
                paint.setColor(Color.rgb(17, 25, 39));
                canvas.drawPath(CutoutPath.parse(config.cutout, width, height, config.densityDpi), paint);
            }
            if (approximationVisible && !config.approximation.trim().isEmpty()) {
                paint.setColor(Color.rgb(0, 150, 185));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2, 3 / scale));
                canvas.drawPath(CutoutPath.parse(config.approximation, width, height,
                        config.densityDpi), paint);
            }
        } catch (IllegalArgumentException ignored) {
            // The editor reports the error; preserve the rest of the preview while typing.
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0, 150, 185));
        float handle = Math.max(7, 9 / scale);
        canvas.drawCircle(upper, upper, handle, paint);
        canvas.drawCircle(lower, height - lower, handle, paint);
        canvas.restore();
    }

    private float radius(boolean top, int width, int height) {
        try {
            return Math.min(Math.max(0, top ? config.topRadius() : config.bottomRadius()),
                    Math.min(width, height) / 2f);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (listener == null || rotated || scale <= 0) return false;
        float x = (event.getX() - bounds.left) / scale;
        float y = (event.getY() - bounds.top) / scale;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float upper = radius(true, config.width, config.height);
            float lower = radius(false, config.width, config.height);
            float threshold = 44 * getResources().getDisplayMetrics().density / scale;
            if (Math.hypot(x - upper, y - upper) < threshold) dragCorner = 0;
            else if (Math.hypot(x - lower, y - (config.height - lower)) < threshold) dragCorner = 1;
            else return false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragCorner != -1) {
            int radius = Math.round(dragCorner == 0 ? (x + y) / 2
                    : (x + config.height - y) / 2);
            listener.onRadius(dragCorner == 0,
                    Math.max(0, Math.min(radius, Math.min(config.width, config.height) / 2)));
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragCorner = -1;
            return true;
        }
        return false;
    }
}
