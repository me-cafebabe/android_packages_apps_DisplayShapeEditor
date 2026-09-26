# Display Shape Editor

An on-device editor for framework display cutout and rounded-corner overlay values.
The app draws a local preview; it does **not** change the running system's display
configuration and needs no root access.

## Use

1. Open the app on a device. The current display mode and density are the starting
   values; enter the target panel's **native** width, height and density when
   preparing an overlay for a different device.
2. Drag either cyan corner handle to adjust the top or bottom radius, or enter
   values with `px`/`dp` units. A zero top/bottom radius inherits the default.
3. Choose a notch, pill, or hole-punch preset. Drag its orange handles to move
   it or adjust width and height, or drag inside the dark cutout to move the
   entire shape. The square grip just beyond the lower-right corner of its
   bounding box resizes the whole cutout from the opposite, upper-left corner.
   This works for presets and custom or imported paths (including curves); zoom
   in when working with a very small cutout. The width, height, and curvature
   fields control the preset.
   The position inputs use the selected shape's center X (from the display's
   left edge) and top Y (from its top edge), in native pixels. Editing the SVG
   text switches to custom mode; there is no automatic conversion back to a
   preset. These same position inputs work for custom and imported paths; in
   custom mode, drag inside the cutout to move all its points, or
   tap an anchor or curve-control point and drag it, nudge it by one pixel, or
   enter exact coordinates. Pinch to zoom and drag
   empty canvas to pan; the `1x` and `2x` buttons reset the view.
4. Use **Edit bounds** to create and separately edit the cutout's bounding
   approximation. Its position inputs then move the approximation rather than
   the visible cutout. Its square resize grip scales just the approximation.
   Drag inside it to move it independently;
   its outline is cyan. If the approximation is empty, the framework uses the
   visible path; enabling **Move approximation with visible path** also moves
   and scales a refined approximation when resizing the visible cutout. Check
   both outlines after changing size.
5. Paths start at the top center by default. `@left`, `@right`, `@bottom`,
   `@cutout`, and a trailing `@dp` are supported. The supported path commands
   are `M`, `L`, `H`, `V`, `Q`, `C`, `Z` and their relative lowercase equivalents.
   Point edits normalize the path to absolute commands. Other SVG commands are
   rejected rather than exported with a misleading preview.
6. Tap **Larger** to expand the in-editor canvas while keeping the fields
   available below it; tap **Smaller** to restore its size. This choice is
   remembered between launches. Rotate the preview or open **Full screen**
   to see the drawing edge-to-edge without guides or corner handles. Tap
   anywhere or press Back to return to the editor. If the target display's
   aspect ratio differs from this device, unused space remains around the
   drawing. **Calibrate** opens an immersive editing canvas with an explicit
   Exit button and togglable guides. Its edits update the same draft. The
   drawing is an approximation, not the framework's actual insets calculation.
7. Use **View XML** to inspect the output. **Export XML** saves
   `display_shape.xml` through Android's document picker. The editable draft is
   also saved locally between launches. **Import XML** accepts a values XML
   file and reads supported resources; dimensions, density and unknown resources
   are not inferred from the file.
   **Reset** asks for confirmation before starting a fresh draft with this
   device's display dimensions; it does not delete previously exported files.

## Device-tree integration

For a conventional `framework-res` product resource overlay, put the exported
file at:

```
device/<vendor>/<device>/overlay/frameworks/base/core/res/res/values/display_shape.xml
```

Ensure the device product includes that overlay root in
`PRODUCT_PACKAGE_OVERLAYS` (or integrate the resource entries into an existing
framework overlay). The output is a single valid `<resources>` file containing
both cutout strings/bools and corner dimensions; Android does not require
`config.xml` and `dimens.xml` to be separate files. Build the target ROM and
check the result on-device before shipping it.

This first version targets the main built-in display. Multi-display unique-ID
arrays, waterfall insets, side overrides and SystemUI-specific corner drawables
are not generated. Preview rotation does not change the saved native-orientation
coordinates. Import preserves the existing draft for fields absent from the
selected XML; importing a new device's overlay is best done after setting that
device's display dimensions and density.
