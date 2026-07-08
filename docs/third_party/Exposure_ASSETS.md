# Exposure assets copied for private Photography fork

This private Photography fork includes assets copied/adapted from Exposure:

- Source: `/Users/sasha/Downloads/Exposure-1.21.1`
- Public repository: `https://github.com/mortuusars/Exposure`
- Copyright: Copyright (c) 2025 mortuusars
- License: MIT
- Full license text: `docs/third_party/Exposure_LICENSE.md`

Copied/adapted asset groups:

- Viewfinder mask and status textures:
  - `src/main/resources/assets/photography/textures/gui/viewfinder/`
- Composition guide textures:
  - `src/main/resources/assets/photography/textures/gui/viewfinder/composition_guide/`
- Camera controls sprites:
  - `src/main/resources/assets/photography/textures/gui/sprites/camera_controls/`
- Camera/selfie/camera stand textures:
  - `src/main/resources/assets/photography/textures/item/exposure/`
  - `src/main/resources/assets/photography/textures/block/exposure/`
- Camera/selfie/camera stand models staged under the Photography namespace:
  - `src/main/resources/assets/photography/models/item/exposure/`
  - `src/main/resources/assets/photography/models/block/exposure/`

Model JSON references were mechanically rewritten from the `exposure` namespace to staged Photography paths such as `photography:item/exposure/...` and `photography:block/exposure/...`.
