# Fetch appearance assets

The six palettes use Material Color Utilities HCT tonal palettes, with dedicated seeds for Cobalt, Tidal, Amber, Forest, Orchid and Rose. Primary, secondary, tertiary, neutral and semantic roles are generated at build-design time; there is no JavaScript runtime dependency in the UI.

Regenerate from the repository's `multiplatform` directory:

```sh
cd tools/design
npm install
node generate.mjs
cd ../..
iconutil -c icns desktopApp/icons/Fetch.iconset -o desktopApp/icons/Fetch.icns
```

The editable icon is `desktopApp/icons/Fetch.svg`. The same SVG is loaded directly inside the app; generated PNG/ICO/ICNS files are used by native packaging. All sizes are rendered from vectors, including 1024px Retina artwork.

Official color implementation: https://github.com/material-foundation/material-color-utilities
