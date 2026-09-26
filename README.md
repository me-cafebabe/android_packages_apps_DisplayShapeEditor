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
3. Pick a cutout preset and edit its SVG path to match the physical panel. Paths
   start at the top center by default. `@left`, `@right`, `@bottom`, `@cutout`,
   and a trailing `@dp` are supported. The supported path commands are `M`,
   `L`, `H`, `V`, `Q`, `C`, `Z` and their relative lowercase equivalents.
   Other SVG commands are rejected rather than exported with a misleading
   preview. For complex paths, create an approximation path separately; its
   outline is shown in cyan when enabled.
4. Rotate the preview or open **Full screen** to see the drawing edge-to-edge
   without guides or corner handles. Tap anywhere or press Back to return to
   the editor. If the target display's aspect ratio differs from this device,
   unused space remains around the drawing. The display drawing is an
   approximation, not the framework's actual cutout/insets calculation.
5. Use **View XML** to inspect the output. **Export XML** saves
   `display_shape.xml` through Android's document picker. The editable draft is
   also saved locally between launches. **Import XML** accepts a values XML
   file and reads supported resources; dimensions, density and unknown resources
   are not inferred from the file.

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
