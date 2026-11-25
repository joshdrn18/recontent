# Firstlight environments

The Android assets in `app/src/main/assets/firstlight` are original Firstlight Gate, Arena and Weekly Tower scenes. Filament renders the GLB models under a shared dusk sky. Each WebP image shows the same scene and appears while assets load, when 3D scenes are disabled, or when the device cannot initialize the renderer.

Generate the stone textures and sky with `python3 make_textures.py` using NumPy and Pillow. Export each scene with Blender 4.3 or later using `blender -b --python build_realm.py -- output gate --preview`. Use `arena` and `tower` to generate the remaining assets. Convert the rendered PNG images to WebP. Generate the environment textures with Filament 1.56's `cmgen -s 128 -f ktx -x output firstlight_sky.png`. Copy the generated IBL and skybox to `ambient.ktx` and `sky.ktx` in the Android assets.
