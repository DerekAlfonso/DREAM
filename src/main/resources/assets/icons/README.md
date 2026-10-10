# D.R.E.A.M application icon

The icon takes its charcoal screen, curved scanlines, green prompt and bundled
Perfect DOS VGA 437 typeface from the game's terminal. The large `>_` remains
visible in the taskbar; sizes below 128 px omit the title and decorative text.
The area outside the CRT bezel is transparent.

Assets:

- `dream-1024.png`: full-resolution preview/master.
- `dream.ico`: Windows launcher and installer (16, 32, 48, 64, 128, 256 px).
- `dream.icns`: macOS app and disk image (16 through 1024 px, including Retina).
- `dream.iconset/`: standard macOS source set for `iconutil`.
- `dream-*.png`: embedded Swing window icons and previews.

Regenerate from the repository root with JDK 11 or later:

```sh
java "-Djava.awt.headless=true" tools/GenerateIcons.java
```

Artwork is drawn deterministically in Java using the existing font; no image
generation service, image editor, or additional library is required.
Edit `tools/GenerateIcons.java` to change the design, then regenerate and commit
the assets together. Normal Maven/installer builds use the committed assets.

On macOS the ICNS can also be recreated with Apple's native tool:

```sh
iconutil -c icns src/main/resources/assets/icons/dream.iconset
```

The desktop workflow supplies these icons to
[`jpackage --icon`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).
`jpackage` creates a private Java runtime from the build's Temurin JDK 21;
the icon does not change runtime selection. Build each installer on its target
OS; the macOS workflow produces separate Apple Silicon and Intel builds.
