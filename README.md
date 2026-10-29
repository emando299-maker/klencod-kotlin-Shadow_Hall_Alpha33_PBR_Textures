# Shadow Hall — Alpha 24 (source project)

Android Kotlin / OpenGL ES 2.0 horror-game prototype. Open the `ShadowHallKotlin` folder in KlencodIDE.

## Alpha 21 changes
- Starts with the monster in a side room, rather than directly in the central hall beside the player, to avoid an immediate opening attack.
- The monster's head/body silhouette has additional raised chest, jaw, limb, rib and claw details. Its facing direction updates toward the player instead of staying fixed in one direction.
- The visual-quality preset now defaults to **High detail**.
- Door interaction ranges are more forgiving so the Interact button is easier to use near the four room doors and the internal door.
- Material UVs are scaled by each object's dimensions, so wood/plaster/stone textures tile over large surfaces instead of stretching one copy across an entire wall or floor.
- Material images are enlarged to 512×512 with restrained sharpening and surface grain. This increases texture detail, but does not turn the existing box-based geometry into professionally sculpted assets.
- Existing puzzle, keys, fuse, flashlight, hiding, audio and game-state code is retained from the supplied Alpha 12 source.

## Important limits and verification
- The only source archive supplied for this session was `Shadow_Hall_MultiRoom_Alpha12.zip`. This Alpha 21 package is based on that archive, not on the later Alpha 20 package mentioned in conversation; changes made only in Alpha 13–20 are therefore not included.
- This environment does not have a configured Android build run for this project. The ZIP was checked for integrity and source edits, but APK compilation, installation and gameplay have **not** been verified. Build it in KlencodIDE before replacing your working copy.
- The renderer is still OpenGL ES 2.0 and uses box meshes. This is a visual improvement pass, not AAA photorealism; true cast shadows, a physically accurate flashlight cone, skeletal animation, and imported high-poly models are not implemented here.


## Alpha 22 changes
- High-quality architectural trim, panel mouldings, ceiling beams and fixture detailing.
- Higher-resolution 1024x1024 aged-material textures with added fine surface variation.
- Monster now starts in and patrols its side room; awakening is delayed unless the player approaches or sprints nearby.
- Fixed patrol target timing so the monster no longer continuously resets its destination.
- Clearer door interaction label and wider proximity checks for doors.
- Added facial shadow sockets, brow details and teeth to the monster placeholder mesh.

Note: the scene is still a custom OpenGL ES 2.0 prototype using box-based geometry. These are source-level improvements; build and on-device behavior must be verified in KlencodIDE.


## Alpha 23 changes
- Replaced the creature's main head, torso, shoulders, upper arms and legs with a generated smooth UV-sphere mesh scaled into ellipsoids. This is a procedural geometry improvement, not an imported professional character model or skeletal animation rig.
- Added smooth-normal shading for the ellipsoid mesh while retaining the existing box mesh for small facial details, ribs and claws.
- Monster parts now bypass environment textures so red eyes, teeth and dark flesh colours are not tinted by wood/plaster textures.
- The existing room layout, puzzle and AI systems are retained from Alpha 22.
- Build and on-device rendering were not verified in this environment; run Build in KlencodIDE and report any errors.


## Alpha 24 — combined graphics/gameplay pass
- Added a per-fragment world-space flashlight cone with distance attenuation and surface-facing response; removed the old floating rectangular floor-light artifact. This is a spotlight approximation, not shadow mapping.
- Corrected smooth-ellipsoid normal scaling so stretched monster forms shade more consistently.
- Added high-detail door mouldings, brass handles, wall frames/canvases, sconces, table books, cabinet details, extra structural ribs and inexpensive fake contact-occlusion shapes.
- Added subtle opposing arm/leg movement and a forward lean when the monster is alerted, plus extra scars and claw detail. These are procedural pose offsets, not skeletal animation.
- Kept the existing room layout, puzzle progression, separate-room opening, hide system and door state logic.
- Source-level change only: Android build/device testing was not available in the authoring environment. The lighting is an approximate ES 2.0 spotlight and the contact-darkening shapes are not physically cast shadows.

## Alpha 29 — render fix + end screen
- **Fixed the black-screen render bug:** the fragment shader used `uMV`, which was never declared there, so the shader failed to compile and nothing was drawn. The view-space position is now passed from the vertex shader as `vViewPos`.
- Matched the `uDimensions` uniform precision (`mediump`) in both shaders, which strict GPU drivers require to link.
- Shader compile/link errors are now shown in the status bar (and logged under tag `ShadowHallGL`) instead of failing silently.
- Added a win/lose end screen with a "play again" button.
- Not compiled or run in the authoring environment: build in KlencodIDE / Android Studio and report any errors.

## Alpha 30 — horror-realism lighting pass
- Rewrote the fragment shader: much darker cold ambient, warm flickering point lights for the ceiling lamps and wall sconces, fake ambient occlusion (floor, ceiling, corners).
- Flashlight is now a warm cone with soft penumbra, bright core, wet specular glints and light scattering in the fog (visible beam haze).
- Bump mapping derived from the material textures (cracks, grain, grout catch the light).
- Emissive red eyes / warning lamps, warm lamp bulbs and green "ready" lights.
- Filmic highlight shoulder, desaturation + red vignette as the monster approaches, stronger vignette and dark-weighted film grain.
- Camera head-bob while walking.
- Fragment shader now uses highp where available (fixes texture stepping on large surfaces with mediump).
- New Settings options: brightness slider and an explicit "Ultra" quality entry.
- Still OpenGL ES 2.0 with box geometry: no real cast shadows, no imported models. Built/tested on-device by you only; lighting values may need tuning (use the brightness slider).

## Alpha 31 — real flashlight shadows
- Added shadow mapping for the hand-held flashlight: the scene's depth is rendered from the light into a 1024×1024 RGBA8 texture (depth packed into 4 channels, so no `OES_depth_texture` extension is required), then sampled with 4-tap soft edges in the main shader.
- The flashlight now sits at the player's hand (right/below the eye) so furniture, doors, walls and the monster cast visible shadows.
- The shadow pass skips tiny decoration, big floor slabs and anything out of the light's reach to limit the extra cost. It runs only while the flashlight is on and quality is "More detail" or Ultra.
- Settings has a "real shadows" checkbox; if the device cannot create the shadow framebuffer the game silently runs without shadows.
- Only the flashlight casts shadows; ceiling lamps and sconces still use unshadowed point lighting.
- Not compiled or run in the authoring environment: please build and test on-device.

## Alpha 32 — physically-based material pass
- Cook-Torrance GGX specular for the flashlight and all lamps, with per-material roughness (wet stone, glossy flesh sheen, matte plaster).
- Rounded box edges: normals bend near every edge and worn edges get lighter/smoother, so corners catch light like chamfers instead of razor-sharp cubes.
- Procedural world-space grime, tile-repeat breaking, dripping wall stains and wet floor puddles (low roughness, strong reflections of the flashlight and lamps).
- Hemispheric ambient (cold from above, warm dirty bounce from below) and wrap lighting on the creature's skin.
- Still box/ellipsoid geometry: true photorealism needs real modelled assets (OBJ meshes + PBR albedo/normal/roughness texture sets), which are not included. Not compiled or run in the authoring environment.

## Alpha 33 — full procedural PBR texture sets
- Regenerated all three materials (aged plaster, old wooden planks, dirty stone flagstones) with `tools/gen_textures.py`: 2048² tileable albedo plus 1024² normal+roughness maps.
- Plaster: wandering cracks, water streaks, mould, pits, peeling paint. Wood: fine grain, knots, staggered boards, scratches. Stone: irregular flagstones, grout, cracks, damp patches.
- Normal map is stored in R/G and roughness in B of `*_nr.png` (RGB, no alpha); the shader rebuilds Z and falls back to the old brightness-bump if a map is missing.
- Old textures were backed up outside the project; regenerate with `python3 gen_textures.py [plaster|wood|stone]` (needs numpy, scipy, Pillow).
- GPU memory use rises (~130 MB for textures with mipmaps). If the game crashes on a weaker phone, lower N_ALB in the generator to 1024.
- Not compiled or run in the authoring environment.
