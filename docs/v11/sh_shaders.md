# v1.1 — Shaders (Iris + one light "RT-looking" pack)

Owner request: "add a simple raytracing shader, always light".

**Honest note:** real path-traced RT in Minecraft (SEUS PTGI, Rethinking Voxels, Bliss/Photon RT modes) is heavy. It does not fit "always light". What we ship is a very light pack whose "RT look" comes from **raymarched screen-space reflections** on water and glass, refraction, and sun reflection. On its low profile it has no shadow pass.

## Versions chosen

| Piece | Version | Modrinth id | Why |
|---|---|---|---|
| Iris (NeoForge 1.21.1) | **1.8.14-beta.1+mc1.21.1** | `iris:KduFYu4t` | This is the only Iris build made for **Sodium 0.8**. Its changelog says "Updates to Sodium 0.8". Modrinth lists its required dependency as exactly `sodium:uMOpc5uV`, which is the Sodium 0.8.13 we already bundle. All older Iris builds (≤1.8.12) target Sodium 0.6 and would not work. Sodium is **unchanged**. |
| Shaderpack | **MakeUp - Ultra Fast 9.6** | `makeup-ultra-fast-shaders:t4YRxQug` (zip, sha1 `e2a930f1…`) | The lightest mainstream pack. Its `REFLECTION_SLIDER=2` setting is described in the pack as "Raytraced Screen Space Reflection". It has `shadowless_*` profiles, ships DH programs for overworld/nether/end, and has a "basic shader" fallback for modded dimensions. The license allows bundling (see below). |

Checks done on the jars (not just the API):
- Iris `neoforge.mods.toml`: it marks only Embeddium as incompatible and does not declare a Sodium version range. Sodium 0.8.13's own toml does not mark Iris as incompatible. Iris bundles its libraries (glsl-transformer, jcpp, fabric-renderer-api 3.4.0) through jar-in-jar. Sodium bundles fabric-renderer-api 3.4.1, and JarJar picks the newer one, so there is no conflict.
- Iris declares `minecraft [1.21,1.21.1)`. That range looks like it excludes 1.21.1, but FML's `VersionSupportMatrix` (loader 4.0.45) also accepts `1.21` when the game is 1.21.1, so the range passes. Iris 1.8.12 ships the same range.
- **Distant Horizons 3.3.3 + Iris:** Iris's DH compatibility code references 43 DH API classes, and all 43 exist in the DH 3.3.3 jar. The DH 3.3.3 changelog also contains Iris fixes. Iris disables its DH integration gracefully if API methods are missing. MakeUp ships `dh_terrain`/`dh_water` programs for world0/1/-1 and sets `dhShadow.enabled=false`, which keeps it light. No conflict was found.

## License check

| Pack | License | Can we put it in the friends zip? |
|---|---|---|
| **MakeUp Ultra Fast** | LGPL-3.0. The README says "You can use the shader without restrictions" and asks for credit and a link. | **Yes.** We ship it unmodified, with the LGPL and GPL-3.0 texts and a credits file. |
| Complementary Reimagined / Rethinking Voxels | Custom license. §1.2.d: may only be added "using Modrinth or CurseForge's existing systems… not… a direct file upload" | No (our zip is a direct upload) |
| BSL, Sildur's Vibrant, Solas | All Rights Reserved | No |
| Miniature (MIT) | OK | Allowed, but it has no reflections, so there is no RT feel |

Iris itself is LGPL-3.0, so it can be redistributed like the other bundled mods.

## What is bundled where

- `build.gradle`
  - `qolMods` gets `maven.modrinth:iris:KduFYu4t`. It goes into `deployLibs`, so it reaches the TLauncher mods folder and the friends zip, and runs with `-PwithQol`.
  - New `configurations.shaderPacks` with `ext.shaderPackDep = "maven.modrinth:makeup-ultra-fast-shaders:t4YRxQug@zip"` and `ext.shaderPackFile = "MakeUp-UltraFast-9.6.zip"`. The Modrinth maven serves the zip.
  - `deployToTLauncher` preserve-excludes now include `iris-*.jar` and `oculus-*.jar`, so old copies get replaced or removed.
  - New task `deployShadersToTLauncher`, run via `deployToTLauncher.finalizedBy`, so `build` runs it too. It writes to `<TLauncher>/versions/Neo Forge - 1.21.1/`:
    - `shaderpacks/MakeUp-UltraFast-9.6.zip`, plus the license and credit texts. Older `MakeUp-UltraFast-*.zip` files are removed; other packs are not touched.
    - `shaderpacks/MakeUp-UltraFast-9.6.zip.txt`, the light profile, **only if it is missing**. Iris rewrites this file when the player changes options.
    - `config/iris.properties`: **seeded once**, guarded by the marker `config/isekai_mahou_shaders.txt`. If no pack is selected yet, it sets `shaderPack=MakeUp-UltraFast-9.6.zip`, `enableShaders=true` and `disableUpdateMessage=true`, and keeps any other keys. On later deploys it only follows our pack's version bumps. If the owner turns shaders off, they stay off.
  - `clientOnlyMods` gets `iris`, so the server zip does not include it.
  - `packFriends` adds a `shaderpacks/` folder: the pack zip, the light-profile `.txt`, the licenses and the credits. **No `iris.properties`**, so shaders are OFF for friends until they pick the pack.
- `tools/pack/shaders/`: `MakeUp-UltraFast.options.txt`, `CREDITOS-shaders.txt`, `LICENSE-MakeUp-UltraFast-LGPL-3.0.txt`, `LICENSE-GPL-3.0.txt`.
- `tools/pack/friends/LEIA-ME.txt`: now tells friends to copy the `shaderpacks` folder (optional), and has a "Shaders" section explaining how to turn them on, the K toggle, and that shaders always cost FPS.
- Offline: the Iris jar and the pack zip are now in the Gradle cache (`~/.gradle/caches/modules-2/.../maven.modrinth/{iris,makeup-ultra-fast-shaders}`). I verified both resolve with `--offline`, and `./gradlew help --offline` passes.

I tested `deployShadersToTLauncher` against a temporary fake game dir (`-Ptlauncher_mods_dir=<tmp>/mods`). The first run produced the expected files and properties. On a second run, a player's `enableShaders=false` and their option edits were kept. I have not deployed to the real TLauncher; the coordinator's next `build` does that.

## Defaults and profile

The default profile is `shadowless_low` with one change: `REFLECTION_SLIDER=2` (raymarched SSR, the "RT" part). Iris will therefore show the profile as "Custom".
- Off in this profile: no shadow pass (`SHADOW_CASTING=false` disables `program.shadow`), no volumetric clouds, no bloom, no AO, no TAA, no DoF or motion blur.
- On: water reflection and refraction, sun reflection, waving plants.

For a stronger RT feel, the owner can pick these in the pack settings:
- `shadowless_high`: adds bloom, cheap depth-based godrays, AO and gloss. Still no shadows.
- `low`: real shadows and godrays. This is the first step that noticeably costs FPS.

## How to toggle

- **K**: Iris on/off (instant).
- **Options → Video Settings → Shader Packs**: choose a pack or "None". The profile is set in the pack settings on that screen.
- Iris's default keys are **R** (reload shaders) and **O** (pack screen). These clash with Isekai Mahou's R (Hollow, mount) and O (summon order). The existing `KeyConflictFix` moves the *other* mod's key to a free key on first login and announces it in chat, so Iris's R and O will be remapped. That is good: pressing R would otherwise reload shaders and stutter.

## Risks (cannot run the game — coordinator should smoke-test)

1. **Iris is a beta label** (1.8.14-beta.1; Modrinth marks it as "release"). It is the only Iris for Sodium 0.8. If it misbehaves, the fallback is to remove the Iris jar. Do not downgrade Sodium.
2. **Custom dimensions (10 of them, `isekai_mahou:*`)**: MakeUp's `dimension.properties` maps only overworld, nether and end. Modded dimensions use the pack's root programs with `USE_BASIC_SH`, a near-vanilla "basic" shader. Two effects:
   - With shaders on, **DH LODs will probably not render in custom dimensions**, because the root program set has no `dh_terrain`.
   - The custom skies (`MagicSkyEffects`, `TrialSkyEffects`, `RealmSkyEffects` via `DimensionSpecialEffects.renderSky`, drawn with `position_color`/`position_tex_color`) go through the pack's sky programs. They may look tinted or different.
3. **Post-processing effects**: `BossScreenFx` (vanilla `invert.json`), `ClientAfterlife` (`afterlife_spirit.json`) and `TimeStopClient` (`legends_d_time_stop.json`) use `GameRenderer.loadEffect`. These normally still apply after Iris's final pass, but check the Time Stop and afterlife screens with shaders on.
4. **AFTER_LEVEL world overlays**: `OreRevealRenderer`, `FarmOverlay` and `CityPlacer` draw with `RenderSystem.setShader` and sometimes `disableDepthTest`. With Iris they can be hidden by composite passes or drawn unlit. This is the most likely place for something to go "missing". Check ore reveal and the city placer ghost.
5. **AFTER_PARTICLES effects**: about 25 renderers, plus custom `RenderType`s in `*RenderTypes.java` files. They use only vanilla core shaders (`rendertype_lightning`, `position_color`, `position_tex_color`, `rendertype_lines`), `LIGHTNING_TRANSPARENCY` and `PARTICLES_TARGET`. Iris remaps these automatically, so **no crash risk**. They may look slightly dimmer or receive the pack's fog. There are **no custom `ShaderInstance`s or GLSL core shaders** in the mod, which is the main thing that usually breaks under Iris.
6. HUD and GUI code (`ScreenFx`, `BossEntranceFx` depth clear, HUD bars) runs after the world, so it is unaffected.
7. Performance: shaders add GPU cost on top of DH. The mod's own `graphicsPreset` (FxQuality) does not know about Iris. An optional follow-up for gameplay agents: auto-drop FxQuality one step when `ModList.isLoaded("iris")` and shaders are active.

## Coordinator correction (2026-11-13 20:55)
Iris 1.8.14-beta.1 (`KduFYu4t`) declares `minecraft [1.21,1.21.1)` — it EXCLUDES 1.21.1, and in the dev client its Sodium
mixins failed (NoClassDefFoundError). Replaced with the stable pair **Iris 1.8.12 (`t3ruzodq`) + Sodium 0.6.13 (`Pb3OXVqC`)**.
Verified in the dev client with `-PwithQol` + MakeUp enabled: game loads, joins the server, world renders; no isekai errors.
The 1.8.14 jar was deleted from the TLauncher mods folder before redeploy.
