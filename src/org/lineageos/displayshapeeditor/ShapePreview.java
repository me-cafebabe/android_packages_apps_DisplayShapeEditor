package org.lineageos.displayshapeeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

final class ShapePreview extends View {
    interface CornerListener { void onRadius(boolean top, int pixels); }
    interface ShapeListener { void onShape(int width, int height, int offsetX, int offsetY); }
    interface PathListener {
        void onPointSelected(int index, float x, float y);
        void onPathChanged(boolean approximation, String path);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private ShapeConfig config;
    private CornerListener listener;
    private ShapeListener shapeListener;
    private PathListener pathListener;
    private boolean editApproximation;
    private int selectedPoint = -1;
    private boolean draggingPoint;
    private boolean rotated;
    private boolean fullScreen;
    private boolean calibration;
    private boolean approximationVisible = true;
    private float scale;
    private int dragCorner = -1;
    private int dragShape = -1;
    private boolean draggingCutout;
    private String cutoutAtDown;
    private float cutoutDownX, cutoutDownY;
    private boolean resizingCutout;
    private String cutoutBeforeResize;
    private final RectF originalCutoutBounds = new RectF();
    private float resizeDownX, resizeDownY;
    private int originalWidth, originalHeight, originalOffsetX, originalOffsetY;
    private float downX, downY;
    private int initialX, initialY;
    private boolean panning;
    private float panX, panY;
    private float zoom = 1;
    private float pinchDistance, pinchZoom;
    private float startPanX, startPanY;

    ShapePreview(Context context, ShapeConfig config, CornerListener listener) {
        super(context);
        this.config = config;
        this.listener = listener;
        setBackgroundColor(Color.rgb(17, 25, 39));
    }

    void setConfig(ShapeConfig value) { config = value; invalidate(); }
    void setRotated(boolean value) { rotated = value; resetViewport(); }
    void setFullScreen(boolean value) { fullScreen = value; invalidate(); }
    void setCalibration(boolean value) { calibration = value; invalidate(); }
    void setApproximationVisible(boolean value) { approximationVisible = value; invalidate(); }
    void setShapeListener(ShapeListener value) { shapeListener = value; invalidate(); }
    void setCornerListener(CornerListener value) { listener = value; }
    void setPathListener(PathListener value) { pathListener = value; invalidate(); }
    void setEditApproximation(boolean value) {
        editApproximation = value;
        selectedPoint = -1;
        invalidate();
    }
    void setZoom(float value) { zoom = Math.max(1, Math.min(6, value)); panX = panY = 0; invalidate(); }
    float getZoom() { return zoom; }
    int getSelectedPoint() { return selectedPoint; }
    void resetViewport() { zoom = 1; panX = panY = 0; invalidate(); }

    private String editableSpec() {
        return editApproximation ? config.approximation : config.preset == 0 ? config.cutout : "";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = Math.max(1, config.width);
        int height = Math.max(1, config.height);
        float viewWidth = rotated ? height : width;
        float viewHeight = rotated ? width : height;
        float inset = fullScreen ? 0 : 48f;
        scale = Math.min((getWidth() - inset) / viewWidth, (getHeight() - inset) / viewHeight) * zoom;
        if (scale <= 0) return;
        float drawnWidth = viewWidth * scale;
        float drawnHeight = viewHeight * scale;
        float left = (getWidth() - drawnWidth) / 2 + panX;
        float top = (getHeight() - drawnHeight) / 2 + panY;
        bounds.set(left, top, left + drawnWidth, top + drawnHeight);

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.translate(left, top);
        if (rotated) {
            canvas.translate(height * scale, 0);
            canvas.rotate(90);
        }
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(236, 243, 250));
        canvas.drawRect(0, 0, width, height, paint);
        if (!fullScreen || calibration) {
            paint.setColor(Color.rgb(194, 210, 225));
            paint.setStrokeWidth(Math.max(1, 1 / scale));
            for (int x = 0; x < width; x += 100) canvas.drawLine(x, 0, x, height, paint);
            for (int y = 0; y < height; y += 100) canvas.drawLine(0, y, width, y, paint);
        }

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
        if (!fullScreen || calibration) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(0, 150, 185));
            float handle = Math.max(7, 9 / scale);
            canvas.drawCircle(upper, upper, handle, paint);
            canvas.drawCircle(lower, height - lower, handle, paint);
            if (config.preset != 0 && shapeListener != null && !editApproximation) {
                float cx = shapeCenterX();
                float cy = config.offsetY + config.shapeHeight / 2f;
                paint.setColor(Color.rgb(212, 79, 35));
                canvas.drawCircle(cx, cy, handle, paint);
                canvas.drawCircle(cx + config.shapeWidth / 2f, cy, handle, paint);
                canvas.drawCircle(cx, config.offsetY + config.shapeHeight, handle, paint);
            }
            if (pathListener != null && !editableSpec().isEmpty()) {
                try {
                    List<PathEditor.Point> points = PathEditor.points(editableSpec(), width, height,
                            config.densityDpi);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1, 2 / scale));
                    paint.setColor(Color.rgb(243, 112, 32));
                    float anchorX = 0, anchorY = 0;
                    for (PathEditor.Point point : points) {
                        if (point.control) canvas.drawLine(anchorX, anchorY, point.x, point.y, paint);
                        else { anchorX = point.x; anchorY = point.y; }
                    }
                    paint.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < points.size(); i++) {
                        PathEditor.Point point = points.get(i);
                        paint.setColor(i == selectedPoint ? Color.rgb(228, 56, 35)
                                : point.control ? Color.rgb(255, 161, 47) : Color.rgb(10, 145, 145));
                        canvas.drawCircle(point.x, point.y, Math.max(6, 8 / scale), paint);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid paths remain editable as text.
                }
            }
            RectF cutoutBounds = currentCutoutBounds();
            if (cutoutBounds != null) {
                float hx = resizeHandleX(cutoutBounds);
                float hy = resizeHandleY(cutoutBounds);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, 2 / scale));
                paint.setColor(Color.rgb(212, 79, 35));
                canvas.drawRect(cutoutBounds, paint);
                canvas.drawLine(cutoutBounds.right, cutoutBounds.bottom, hx, hy, paint);
                paint.setStyle(Paint.Style.FILL);
                float grip = 9 / scale;
                canvas.drawRect(hx - grip, hy - grip, hx + grip, hy + grip, paint);
            }
        }
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

    private float shapeCenterX() {
        return (config.preset == 4 ? 0 : config.width / 2f) + config.offsetX
                + (config.preset == 4 ? config.shapeWidth / 2f : 0);
    }

    private RectF currentCutoutBounds() {
        String spec = editApproximation ? config.approximation : config.cutout;
        if (spec.trim().isEmpty()) return null;
        try {
            RectF rect = new RectF();
            CutoutPath.parse(spec, config.width, config.height, config.densityDpi)
                    .computeBounds(rect, true);
            return rect.width() > 0 && rect.height() > 0 ? rect : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private float resizeHandleX(RectF rect) {
        return Math.min(rect.right + 16 / scale, config.width - 10 / scale);
    }

    private float resizeHandleY(RectF rect) {
        return Math.min(rect.bottom + 16 / scale, config.height - 10 / scale);
    }

    private boolean insideCutout(float x, float y) {
        String spec = editApproximation ? config.approximation : config.cutout;
        if (spec.trim().isEmpty()) return false;
        try {
            Region region = new Region();
            region.setPath(CutoutPath.parse(spec, config.width, config.height, config.densityDpi),
                    new Region(0, 0, config.width, config.height));
            return region.contains(Math.round(x), Math.round(y));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (scale <= 0 || fullScreen && !calibration) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) {
            pinchDistance = (float) Math.hypot(event.getX(0) - event.getX(1),
                    event.getY(0) - event.getY(1));
            pinchZoom = zoom;
            dragCorner = dragShape = -1;
            draggingCutout = false;
            resizingCutout = false;
            selectedPoint = -1;
            draggingPoint = false;
            panning = false;
            return true;
        }
        if (event.getPointerCount() == 2 && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float distance = (float) Math.hypot(event.getX(0) - event.getX(1),
                    event.getY(0) - event.getY(1));
            if (pinchDistance > 0) {
                zoom = Math.max(1, Math.min(6, pinchZoom * distance / pinchDistance));
                invalidate();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            downX = event.getX(remaining);
            downY = event.getY(remaining);
            startPanX = panX;
            startPanY = panY;
            panning = true;
            return true;
        }
        float x = rotated ? (event.getY() - bounds.top) / scale
                : (event.getX() - bounds.left) / scale;
        float y = rotated ? config.height - (event.getX() - bounds.left) / scale
                : (event.getY() - bounds.top) / scale;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            startPanX = panX;
            startPanY = panY;
            float threshold = 44 * getResources().getDisplayMetrics().density / scale;
            RectF cutoutBounds = currentCutoutBounds();
            if (cutoutBounds != null && Math.hypot(x - resizeHandleX(cutoutBounds),
                    y - resizeHandleY(cutoutBounds)) < 12 / scale) {
                resizingCutout = true;
                selectedPoint = -1;
                cutoutBeforeResize = editApproximation ? config.approximation : config.cutout;
                originalCutoutBounds.set(cutoutBounds);
                resizeDownX = x;
                resizeDownY = y;
                originalWidth = config.shapeWidth;
                originalHeight = config.shapeHeight;
                originalOffsetX = config.offsetX;
                originalOffsetY = config.offsetY;
                return true;
            }
            boolean inside = insideCutout(x, y);
            if (pathListener != null && !editableSpec().isEmpty()) {
                try {
                    List<PathEditor.Point> points = PathEditor.points(editableSpec(),
                            config.width, config.height, config.densityDpi);
                    float pointThreshold = threshold;
                    if (inside) {
                        RectF pathBounds = new RectF();
                        CutoutPath.parse(editableSpec(), config.width, config.height,
                                config.densityDpi).computeBounds(pathBounds, true);
                        pointThreshold = Math.min(threshold,
                                Math.min(pathBounds.width(), pathBounds.height()) / 5f);
                    }
                    for (int i = points.size() - 1; i >= 0; i--) {
                        PathEditor.Point point = points.get(i);
                        if (Math.hypot(x - point.x, y - point.y) < pointThreshold) {
                            selectedPoint = i;
                            draggingPoint = true;
                            pathListener.onPointSelected(i, point.x, point.y);
                            invalidate();
                            return true;
                        }
                    }
                } catch (IllegalArgumentException ignored) { /* Text editor reports invalid paths. */ }
            }
            if (inside && (config.preset == 0 || editApproximation) && pathListener != null) {
                selectedPoint = -1;
                draggingCutout = true;
                cutoutAtDown = editableSpec();
                cutoutDownX = x;
                cutoutDownY = y;
                return true;
            }
            if (!editApproximation && config.preset != 0 && shapeListener != null) {
                float cx = shapeCenterX();
                float cy = config.offsetY + config.shapeHeight / 2f;
                float resizeThreshold = Math.min(threshold,
                        Math.min(config.shapeWidth, config.shapeHeight) / 4f);
                if (Math.hypot(x - (cx + config.shapeWidth / 2f), y - cy) < resizeThreshold) {
                    dragShape = 1;
                } else if (Math.hypot(x - cx, y - (config.offsetY + config.shapeHeight))
                        < resizeThreshold) {
                    dragShape = 2;
                } else if (inside || Math.hypot(x - cx, y - cy) < threshold) {
                    dragShape = 0;
                }
                if (dragShape != -1) {
                    downX = x;
                    downY = y;
                    initialX = config.offsetX;
                    initialY = config.offsetY;
                    return true;
                }
            }
            if (listener != null && !editApproximation) {
                float upper = radius(true, config.width, config.height);
                float lower = radius(false, config.width, config.height);
                if (Math.hypot(x - upper, y - upper) < threshold) dragCorner = 0;
                else if (Math.hypot(x - lower, y - (config.height - lower)) < threshold) dragCorner = 1;
            }
            if (dragCorner == -1) panning = true;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && draggingPoint
                && pathListener != null) {
            try {
                String updated = PathEditor.movePoint(editableSpec(), config.width, config.height,
                        config.densityDpi, selectedPoint, x, y);
                pathListener.onPathChanged(editApproximation, updated);
                pathListener.onPointSelected(selectedPoint, x, y);
            } catch (IllegalArgumentException ignored) { /* Keep the previous valid path. */ }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && draggingCutout
                && pathListener != null) {
            try {
                pathListener.onPathChanged(editApproximation,
                        PathEditor.translate(cutoutAtDown, config.width, config.height,
                                config.densityDpi, x - cutoutDownX, y - cutoutDownY));
            } catch (IllegalArgumentException ignored) { /* Keep the last valid geometry. */ }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && resizingCutout) {
            float newWidth = Math.max(1, originalCutoutBounds.width() + x - resizeDownX);
            float newHeight = Math.max(1, originalCutoutBounds.height() + y - resizeDownY);
            if (!editApproximation && config.preset != 0 && shapeListener != null) {
                int width = Math.max(1, originalWidth + Math.round(x - resizeDownX));
                int height = Math.max(1, originalHeight + Math.round(y - resizeDownY));
                int offsetX = config.preset == 4 ? originalOffsetX
                        : originalOffsetX - originalWidth / 2 + width / 2;
                shapeListener.onShape(width, height, offsetX, originalOffsetY);
            } else if (pathListener != null) {
                RectF resized = new RectF(originalCutoutBounds.left, originalCutoutBounds.top,
                        originalCutoutBounds.left + newWidth, originalCutoutBounds.top + newHeight);
                try {
                    pathListener.onPathChanged(editApproximation, PathEditor.resize(cutoutBeforeResize,
                            config.width, config.height, config.densityDpi,
                            originalCutoutBounds, resized));
                } catch (IllegalArgumentException ignored) { /* Keep the previous valid path. */ }
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragShape != -1) {
            int width = config.shapeWidth, height = config.shapeHeight;
            int offsetX = config.offsetX, offsetY = config.offsetY;
            if (dragShape == 0) {
                offsetX = initialX + Math.round(x - downX);
                offsetY = initialY + Math.round(y - downY);
            } else if (dragShape == 1) {
                width = Math.max(1, Math.round(config.preset == 4
                        ? x - config.offsetX : (x - shapeCenterX()) * 2));
            } else {
                height = Math.max(1, Math.round(y - config.offsetY));
            }
            shapeListener.onShape(width, height, offsetX, offsetY);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragCorner != -1) {
            int radius = Math.round(dragCorner == 0 ? (x + y) / 2
                    : (x + config.height - y) / 2);
            listener.onRadius(dragCorner == 0,
                    Math.max(0, Math.min(radius, Math.min(config.width, config.height) / 2)));
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && panning) {
            panX = startPanX + event.getX() - downX;
            panY = startPanY + event.getY() - downY;
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragCorner = -1;
            dragShape = -1;
            draggingCutout = false;
            resizingCutout = false;
            draggingPoint = false;
            panning = false;
            return true;
        }
        return false;
    }
}
