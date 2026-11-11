# Isekai Mahou — Arquitetura e contrato dos módulos

Mod NeoForge **1.21.1** (NeoForge 21.1.265, Java 21, mapeamentos oficiais Mojang + Parchment).
Mod id `isekai_mahou`, pacote base `com.renanpontes.isekaimahou`. Design completo: `C:\Users\renan\.claude\plans\poh-vamos-l-eu-gentle-glade.md`.
Bibliotecas disponíveis no classpath (já instaladas): **GeckoLib 4.9.3**, **Curios API 9.5.1**, **SmartBrainLib 1.16.11**, **playerAnimator 2.0.4** (KosmX). NÃO adicione outras dependências.

---------------------------------------------------------------------------------------------------
## 1. Regras para quem implementa um módulo (LEIA TUDO)

1. **Você só cria/edita arquivos do SEU módulo:**
   - Java: `src/main/java/com/renanpontes/isekaimahou/feature/<modulo>/**` (o esqueleto `<X>Module.java` e `client/<X>Client.java` já existe — preencha).
   - Traduções: `src/main/lang/<modulo>/pt_br.json` e `en_us.json` (são juntadas no build; chave duplicada com texto diferente = erro de build). PT-BR é o idioma principal; escreva as duas.
   - Recursos com nomes ÚNICOS do seu módulo em `src/main/resources/assets/isekai_mahou/...` e `src/main/resources/data/isekai_mahou/...` (use os ids do seu módulo no nome dos arquivos).
   - Arquivos de dados COMPARTILHADOS (tags do Minecraft/NeoForge/Curios, tags de bioma, `mineable/pickaxe` etc.): coloque em `src/main/merge/<modulo>/<caminho normal>` — ex.: `src/main/merge/creatures_a/data/minecraft/tags/entity_type/undead.json`. O build junta as listas `"values"` de todos os módulos.
   - Scripts de geração de assets do seu módulo: `tools/gen/<modulo>.py` (opcional).
2. **NUNCA edite** arquivos do núcleo (`core/`, `glyph/`, `network/`, `client/`, `module/`, `IsekaiMahou.java`), `build.gradle`, `gradle.properties`, nem arquivos de outros módulos. Se precisar de algo do núcleo que não existe, implemente dentro do seu módulo ou reporte em `followups`.
3. **Não dependa de classes Java de outros módulos** (eles estão sendo escritos ao mesmo tempo). Referências cruzadas são por **id em texto** (`ResourceLocation`), resolvidas em tempo de execução:
   `BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("goblin"))`, `BuiltInRegistries.ITEM.get(...)`, `BuiltInRegistries.BLOCK.get(...)`, `GlyphRegistry.get(IsekaiMahou.id("ignite"))`. Use o catálogo de ids (seção 6). Sempre trate o caso "não existe" sem crashar.
4. **Não rode o Gradle** (`./gradlew`) — vários agentes trabalham ao mesmo tempo. Para verificar, use:
   `bash tools/check-module.sh <modulo>` → compila SÓ o seu módulo com javac contra a fundação e valida os JSON. **Precisa terminar com "TUDO OK".** Rode quantas vezes quiser.
5. Ids novos (itens, blocos, entidades, glifos, pacotes, attachments): minúsculos `a-z0-9_`. Para coisas que não estão no catálogo, use nomes específicos (ex.: `goblin_dagger`, não `dagger`) para não colidir.
6. Código e comentários: Java limpo, comentários curtos em português explicando o "porquê". Nada de código morto/TODO vazio — se algo ficar de fora, liste em `followups`.
7. Texto visível ao jogador: SEMPRE por tradução (`Component.translatable`), nunca texto fixo em Java.
8. Arte: gere texturas por código com `tools/pixelart.py` (Canvas: circle, rect, line, shade_sphere, outline, noise, from_rows, save). Modelos 3D GeckoLib: escreva `.geo.json` (formato Bedrock 1.12.0) e `.animation.json` à mão. Capriche: formas reconhecíveis, sombreamento, 2–3 tons por cor.
9. **Compatibilidade com servidor dedicado:** nada de classes de cliente (`net.minecraft.client.*`, `com.mojang.blaze3d.*`, GeckoLib renderers) fora de `client/`. Handlers de pacote para o cliente devem chamar código de cliente via uma classe em `client/` acessada só dentro do lambda.

---------------------------------------------------------------------------------------------------
## 2. Estrutura do núcleo (o que já existe e você pode usar)

| Classe | Para quê |
|---|---|
| `IsekaiMahou.id(path)` | `ResourceLocation` do mod |
| `core.Element` | ARCANE, 6 base (FIRE, WATER, EARTH, WIND, LIGHT, SHADOW), 15 fusões. `color`, `argb()`, `parentA/B`, `matches(base)`, `canUseGlyphOf(e)`, `displayName()`, `byId`, `fusionOf(a,b)`, `BASE`, `FUSIONS` |
| `core.MagicPath` | Classes: NONE, MAGE, WITCH, WARLOCK, DRUID, NECROMANCER (`PLAYABLE`). `affinity(Element)` → `Affinity` (AFFINITY ×1.5 xp / feitos ×0.5; NEUTRAL; HARD ×0.5 / ×2) |
| `core.Progression` | Fórmulas: `xpToNext`, `levelCap(circle)` (=circle×10), `maxMana`, `maxGlyphs(circle)` (=circle+2), `wheelSize`, `loadoutsPerElement`, `ultimatesAllowed(c≥4)`, `domainAllowed(c≥6)`, `elementLevel(xp)`, consts `SLOTS=4`, `ULTIMATE_SLOT=3`, `MAX_CHARGE=100` |
| `core.MagicData` | Dados do jogador (attachment). Leitura: `level() circle() path() mana() maxMana() isAwakened(e) elementLevel(e) hasNode(e,node) knowsGlyph(id) glyphs() wheel() activeElement() counter(key) hasFlag(f) charge(e)`. Escrita direta permitida: `setFlag`, `addNode(e,node)`, `setCharge/addCharge`, `fillMana/addMana/consumeMana`, `markDirty()` |
| `core.MagicApi` | **Use para mudar progresso** (dispara eventos e mensagens): `get(player)`, `giveXp(sp, n, source)`, `setLevel`, `setCircle`, `awakenElement(sp, e)`, `giveElementXp(sp, e, n)`, `learnGlyph(sp, id)`, `learnGlyphSilently`, `addCounter(sp, key, n)` |
| `core.event.MagicProgressEvent` | Eventos (NeoForge.EVENT_BUS): `LevelUp`, `CircleChanged`, `ElementAwakened`, `ElementXp`, `GlyphLearned`, `Counter`, `PathChosen` |
| `core.event.SpellCastEvent` | `Pre` (cancelável) / `Post` — toda conjuração (jogador ou mob). `getCaster()`, `getContext()` |
| `core.CoreRegistries` | Efeitos de status: `SOAKED, FROZEN, STUNNED, ROOTED, CORRUPTED, EXPOSED, MAGIC_EXHAUSTION, SILENCED` (Holder<MobEffect>). Itens: `ESSENCES.get(Element)` (arcane + 6 base), `GUARDIAN_CORES.get(Element)` (6 base), `MANA_CRYSTAL`, `GLYPH_SCROLL`, `SPELL_SCROLL`. Componentes `GLYPH`, `SPELL`. Atributos do jogador `MAX_MANA`, `MANA_REGEN`, `SPELL_POWER`. Abas criativas `TAB_MAGIC, TAB_CREATURES, TAB_RITUALS, TAB_WORLD` (adicione itens via `BuildCreativeModeTabContentsEvent` checando `event.getTabKey() == CoreRegistries.TAB_X.getKey()`). Dano `SPELL_DAMAGE` (ResourceKey). Entidade `SPELL_PROJECTILE` |
| `core.ElementFx` | Visual/som padrão por elemento: `dust(e,size)`, `signature(e)`, `burst(level,pos,e,count,spread)`, `ring`, `line`, `channeling`, `clientTrail`, `castSound` |
| `core.ElementReactions` | Reações elementais (automáticas no dano de magia). `setMultiplier(entityTypeId, baseElement, mult)` para resistências/fraquezas das SUAS criaturas (0 = imune, 0.5 resiste, 2 fraco). `lastElement(entity, now, maxAge)` |
| `core.ClassKits` | Kit inicial: `addItem(path, () -> stack)`, `setArmor(path, EquipmentSlot, () -> stack)` |
| `core.MahouCommands` | `extend((root, buildCtx) -> root.then(MahouCommands.op("meusub")...))` — subcomandos `/mahou` |
| `core.MahouConfig` | Config: `ANNOUNCE_RADIUS`, `MAX_PERSISTENT_SPELLS`, `DOMAIN_GRIEFING`, cliente: `CUTSCENES`, `OWN_CUTSCENES_ONLY`, `OTHERS_ANNOUNCEMENTS`, `REDUCED_PARTICLES` |
| `core.api.Announce` | `spell(caster, recipe, element, ultimate)`, `send(source, Component, rgb, style)` (STYLE_SPELL/ULTIMATE/BOSS/SYSTEM), `chant(caster, text, ticks)` — envia; o módulo cinematic desenha |
| `core.api.Cutscenes` | `play(viewer, kind, focus, title, subtitle, rgb, ticks, lock)`, `playNear(focus, radius, kind, title, subtitle, rgb, ticks, owner)`, `ultimate(...)`. kinds: ultimate, domain, boss_intro, boss_phase, patron, ascension, portal |
| `core.CoreEvents.bookshelfPower(level,pos)` | Poder de estantes num raio de 4 |
| `glyph.*` | Sistema de glifos (seção 3) |
| `network.ClientMagic` | Cópia dos dados no cliente: `data()` (MagicData), `mana`, `maxMana`, `meditating`, `cooldownLeft/Total` (por chave de slot), `castSlot/castTotal/castElapsed/castChant`, `syncVersion` |
| `network.ClientHooks` | Ganchos de cliente substituíveis: `announce`, `chant`, `cutscene`, `castState` (Consumers dos pacotes do núcleo) |
| `network.CorePayloads` | Pacotes do núcleo (C→S: `CastKey, SelectWheel, SelectLoadout, SetSlot(element, loadout, slot, trigger, Optional<recipe>), WheelEdit, ChoosePath, LibraryEdit(op 0 salvar /1 apagar /2 gravar pergaminho)`) |
| `client.CoreKeys` | Teclas Z X C G (`SLOTS[0..3]`), `MENU` (B), `WHEEL_MODIFIER` (Mouse 5), `LOADOUT_MODIFIER` (Alt) |
| `client.CoreHud` | HUD básico; `CoreHud.enabled=false` desliga (o módulo ui_hud faz isso). `abbreviation(recipe)` |
| `client.menu.MenuTabs / MenuTab / StatusScreen` | Abas do menu B: `MenuTabs.register(id, order, title, Factory)`; `StatusScreen.open(id)`; na aba use `screen.addTabWidget(widget)`. Ordem: status 0, paths 10, grimoire 20, loadouts 30, glyphs 40, journal 50, guild 60, bestiary 70 |
| `module.Module / ClientModule` | Seu `init(modBus)` roda no construtor do mod; `initClient(modBus)` só no cliente |

### Contadores (MagicData.counters) já mantidos pelo núcleo
`cast.<elemento>`, `kill.<ns>.<mob>` (ex.: `kill.isekai_mahou.goblin`, `kill.minecraft.zombie`), `kill.spell.<elemento>`, `kill.total`, `harvest`, `breed`, `brew`, `study`, `meditate`.
Convenções para módulos: `ritual_rank` (+1 a cada "subida" de ritual de classe, qualquer classe), `boss.<mob>` (+1 para jogadores a ≤48 blocos quando um chefe morre), `shrine.<elemento>` (prova concluída), `quest.<id>`, `visit.<dimensão>`, `feat.<...>`. Flags: `kit_given`, `fusion.<id>`, `journal.<capítulo>`...

### Nós dos caminhos (MagicData.hasNode(element, node))
`awakening`, `apprentice`, `adept`, `fluidity`, `master`, `cataclysm`, `domain`, `archmage`.

---------------------------------------------------------------------------------------------------
## 3. Sistema de glifos

Magia = `SpellRecipe(glyphs, name, color, chant)`. Gramática: `[TRIGGER...] FORM [AUGMENT...] (EFFECT [AUGMENT...])* [LAW...]`.
`SpellParser.parse(recipe, element, data)` valida (conhecimento, elemento, Círculo, complexidade) e gera `ParsedSpell(triggers, form, formStats, effects[EffectEntry(effect, stats)], laws, manaCost, castTicks, cooldown, glyphCount)`.
`SpellCaster` cuida das teclas, mana, conjuração segurando, recarga, XP, carga de ultimate, anúncio e cutscene. `SpellCaster.castAsMob(mob, element, recipe, power)` faz MOBS conjurarem (cultistas, chefes).

### Criando glifos
```java
// Efeito
public class IgniteEffect extends EffectGlyph {
    public IgniteEffect() { super("ignite", 8); element(Element.FIRE); tier(1); }   // id, custo de mana
    @Override public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ctx.damage(target, 3F, stats, null);                               // dano mágico do elemento (reações, crit, vampírico...)
        target.igniteForSeconds(stats.duration(80) / 20F);                 // duração ajustada pelos aumentos
    }
    @Override public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) { ... } // só com wantsBlocks() ou Sensível
}
// registrar no init do módulo:
GlyphRegistry.register(new IgniteEffect());
```
- `EffectGlyph`: `onEntity`, `onBlock`, `isBeneficial()` (cura/escudo: afeta aliados; inimigos se Inverter), `wantsBlocks()`.
- `FormGlyph`: `cast(ctx)`, `baseCastTicks()`, `baseCooldown()`, `isUltimate()` (só slot G, usa carga), `acceptsLaws()`, `isPersistent()`.
  Formas aplicam efeitos com `ctx.resolveOnEntity(target, directEntity)`, `ctx.resolveOnBlock(hit, direct)`, `ctx.resolveHit(hitResult, direct)`, `ctx.resolveArea(center, radius, direct, centerBlock)`, `ctx.resolveOnSelf()`.
  Entidades que guardam a magia (zonas, torres, runas): salve `ctx.save()` e recarregue com `SpellContext.load(serverLevel, tag)`. Limite de entidades persistentes por jogador: `MahouConfig.MAX_PERSISTENT_SPELLS`.
- `AugmentGlyph`: todos os aumentos do plano JÁ EXISTEM no núcleo (ids: amplify, dampen, concentrate, critical, vampiric, overload, efficient, expand, extend, shorten, persistent, delay, echo, accelerate, decelerate, split, scatter, pierce, ricochet, chain, homing, phantom, arc, sensitive, enemies_only, allies_only, invert, charge, channel, silent, fortune, silk_touch). Formas/efeitos LEEM os `SpellStats`:
  `power()` (multiplicador), `radius(base)`, `duration(baseTicks)`, `speedMult()`, `count()` (1+split), `delayTicks()`, campos `pierce ricochet chain homing ghost arc sensitive spread inverted silent fortune silkTouch persistent concentrate charge channel`, `custom(id)` para aumentos de módulos.
  Atraso/Repetir/Encadear/Vampírico/Crítico/aliados são tratados AUTOMATICAMENTE pelo núcleo (SpellContext/SpellCaster).
- `LawGlyph` (Domínio), `TriggerGlyph(path, cost, placeable)` (gatilhos condicionais/sequência; os de movimento já existem: trigger_sneak/sprint/air/water).
- Configuração fluente: `element(e)`, `origin(path)` (Magia de Origem: usável em qualquer elemento), `tier(1..5)`, `minCircle(n)`, `legendary()`.
  **Tier** = em que nó do caminho o módulo Caminhos libera o glifo: 1 Despertar (2 glifos), 2 Aprendiz (3), 3 Adepto (3 + invocação), 4 Mestre (3–4), 5 Arquimago (1 lendário). Glifos ARCANOS: tier = Círculo (1→C1, 2→C2, 3→C3, 4→C5, 5→C8).
- Custos de referência: efeito simples 6–12, forte 15–30, lendário 40–80; formas 2–20 (ultimates 60–120).
- Glifos já existentes no núcleo: formas `projectile`, `touch`, `self`; efeitos `arcane_damage`, `push`.
- Tradução obrigatória para cada glifo: `glyph.isekai_mahou.<id>` e `glyph.isekai_mahou.<id>.desc` (pt_br e en_us).

### Helpers do SpellContext
`level() caster() casterPlayer() element() recipe() spell() formStats() powerMult() random() color() eyePos() look()`
`damage(target, base, stats, direct[, element])` · `heal(living, base, stats)` · `status(living, Holder<MobEffect>, baseTicks, amp, stats)` · `isAlly(e)` · `enemiesAround(center, r)` · `damageSource(direct)` · `casterSpellPower()` · `save()/load()`.

---------------------------------------------------------------------------------------------------
## 4. Padrões NeoForge 1.21.1 (cuidado com versões)

- `ResourceLocation.fromNamespaceAndPath(ns, path)` / `IsekaiMahou.id(path)`. Nada de `new ResourceLocation`.
- Registros: `DeferredRegister.createItems(MODID)`, `DeferredRegister.createBlocks(MODID)`, `DeferredRegister.create(Registries.ENTITY_TYPE, MODID)`, `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID)`, `DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID)`, `Registries.MOB_EFFECT`, `Registries.SOUND_EVENT`, `Registries.PARTICLE_TYPE`, `Registries.BLOCK_ENTITY_TYPE`, `Registries.MENU`, `Registries.ARMOR_MATERIAL`. Registre no `modBus` dentro do seu `init`. Pode haver vários DeferredRegister do mesmo tipo (um por módulo).
- `EntityType.Builder.of(Factory, MobCategory).sized(w,h).build("id")` (1.21.1 recebe String). Atributos: `EntityAttributeCreationEvent` (mod bus). Spawn: `RegisterSpawnPlacementsEvent` (mod bus) + biome modifier JSON `data/isekai_mahou/neoforge/biome_modifier/<id>.json` com `"type": "neoforge:add_spawns"`. Ovo: `new DeferredSpawnEggItem(() -> TYPE.get(), corFundo, corPintas, new Item.Properties())`, modelo `{"parent":"minecraft:item/template_spawn_egg"}`.
- Pastas de dados 1.21 são no SINGULAR: `loot_table/`, `recipe/`, `advancement/`, `structure/` (nbt), `tags/item`, `tags/block`, `tags/entity_type`, `tags/worldgen/biome`. Receita: `"result": {"id": "...", "count": 1}`.
- Itens: `new Item.Properties()`, componentes via `.component(DataComponents.X, v)`; armadura `ArmorItem(Holder<ArmorMaterial>, ArmorItem.Type, props)`; espada `new SwordItem(Tier, props.attributes(SwordItem.createAttributes(Tier, dmg, speed)))`.
- `Item.use` retorna `InteractionResultHolder<ItemStack>`; `appendHoverText(stack, Item.TooltipContext, List<Component>, TooltipFlag)`; `getUseDuration(stack, entity)`.
- Efeitos: `MobEffect` (construtor protegido → subclasse), `applyEffectTick(LivingEntity, amp)` retorna boolean, `shouldApplyEffectTickThisTick(duration, amp)`. Atributos: `addAttributeModifier(Holder<Attribute>, ResourceLocation id, double, Operation)`.
- Entidades: `defineSynchedData(SynchedEntityData.Builder)`, `getDefaultGravity()`, `igniteForSeconds(float)`, `hurt(DamageSource, float)`, `Attributes.SCALE` (tamanho — use para Encolher/Crescer/Metamorfose), `Attributes.JUMP_STRENGTH`, `Attributes.GRAVITY`, `Attributes.SAFE_FALL_DISTANCE`.
- Teleporte entre dimensões: `entity.changeDimension(new DimensionTransition(level, pos, Vec3.ZERO, yRot, xRot, DimensionTransition.DO_NOTHING))`.
- Pacotes: `record X(...) implements CustomPacketPayload` com `Type` (id `IsekaiMahou.id("<modulo>_<nome>")`) e `StreamCodec` (`StreamCodec.composite` aceita no máx. 6 campos; acima disso use `StreamCodec.of`). Registre no seu init: `modBus.addListener((RegisterPayloadHandlersEvent e) -> { var r = e.registrar("<modulo>-1"); r.playToServer(...); r.playToClient(...); })`. Enviar: `PacketDistributor.sendToPlayer/sendToServer/sendToPlayersNear`.
- Telas com container: `MenuType` via `IMenuTypeExtension.create(...)`, abrir com `player.openMenu(provider, buf -> ...)`, registrar tela no cliente com `RegisterMenuScreensEvent`.
- Cliente: `EntityRenderersEvent.RegisterRenderers`, `RegisterGuiLayersEvent`, `RegisterKeyMappingsEvent`, `RegisterParticleProvidersEvent`, `RegisterColorHandlersEvent.Item`, `ClientTickEvent.Post`, `RenderLevelStageEvent`, `ViewportEvent.ComputeCameraAngles`, `RenderGuiEvent`. Listeners de mod bus no `initClient(modBus)`; de jogo via `NeoForge.EVENT_BUS.addListener`.
- GeckoLib 4.9.3 (`software.bernie.geckolib`): entidade implementa `GeoEntity` (`AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this)`, `registerControllers(AnimatableManager.ControllerRegistrar)`, `AnimationController`, `RawAnimation.begin().thenLoop("walk")`, `PlayState`), renderer `GeoEntityRenderer<T>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("goblin")))` → arquivos `assets/isekai_mahou/geo/entity/goblin.geo.json`, `animations/entity/goblin.animation.json`, `textures/entity/goblin.png`. Armaduras: `GeoItem` + `GeoArmorRenderer` + `createGeoRenderer(Consumer<GeoRenderProvider>)`. **Confirme as assinaturas no jar** antes de usar: `javap -cp <jar> <classe>` (jars em `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/<lib>/`).
- SmartBrainLib: opcional — a IA de `Goal` do vanilla (`goalSelector`/`targetSelector`) é preferível pela simplicidade; use SBL só se precisar.
- Curios 9.5.1: `top.theillusivec4.curios.api` (`ICurioItem`, `CuriosApi`), slots por dados em `data/isekai_mahou/curios/slots/<slot>.json` e `data/isekai_mahou/curios/entities/<x>.json`; tags de item de slot em `src/main/merge/<modulo>/data/curios/tags/item/<slot>.json`.
- playerAnimator 2.0.4: `dev.kosmx.playerAnim.*` (confirme no jar).

---------------------------------------------------------------------------------------------------
## 5. Assets

- Itens: `assets/isekai_mahou/models/item/<id>.json` + `textures/item/<id>.png` (16×16; gere com `tools/pixelart.py`, `item_model(...)` cria o modelo).
- Blocos: `blockstates/<id>.json`, `models/block/<id>.json`, `textures/block/<id>.png`, `models/item/<id>.json` (parent no modelo do bloco), loot `data/isekai_mahou/loot_table/blocks/<id>.json`, picareta etc. via `src/main/merge/<modulo>/data/minecraft/tags/block/mineable/pickaxe.json`.
- Entidades GeckoLib: textura 64×64 (ou do tamanho do UV do modelo), modelo `.geo.json` com `"format_version": "1.12.0"`, `minecraft:geometry` → `description` (identifier `geometry.<id>`, texture_width/height) + `bones` (name, pivot, cubes com origin/size/uv). Animações `"format_version": "1.8.0"`, `animations` → `animation.<id>.idle/walk/attack/...`.
- Estruturas: `tools/structgen.py` gera `.nbt` (`data/isekai_mahou/structure/<id>.nbt`). Worldgen JSON em `data/isekai_mahou/worldgen/{structure,structure_set,template_pool}/<id>.json`; tags de bioma `has_structure/<id>` no merge.
- Sons: use os do vanilla (`SoundEvents.*`) — sem arquivos .ogg.

---------------------------------------------------------------------------------------------------
## 6. Catálogo de ids compartilhados (crie EXATAMENTE estes ids quando forem do seu módulo)

**Entidades**
- creatures_a: `slime` (slime "amigável" estilo Tensura, azul), `fire_slime`, `water_slime`, `stone_slime`, `wind_slime`, `poison_jelly`, `king_slime` (minichefe); `goblin`, `goblin_archer`, `goblin_shaman`, `goblin_wolf_rider`, `hobgoblin`, `goblin_king` (chefe)
- creatures_b: `kobold`, `orc`, `orc_berserker`, `orc_lord` (chefe), `ogre`, `troll`, `lizardman`, `minotaur` (chefe), `werewolf`, `cultist`, `wandering_mage` (NPC comerciante)
- creatures_c: `dire_wolf` (domável, montaria), `harpy`, `basilisk`, `mimic`, `gargoyle`, `fairy`
- creatures_d: `salamander`, `ember_imp`, `undine`, `tide_serpent`, `moss_golem`, `crystal_mole`, `sylph`, `storm_hawk`, `lumen_sprite`, `solar_sentinel`, `creeping_shade`, `void_bat`, `wandering_spirit`, `arcane_golem`
- creatures_e: `greater_fire_elemental`, `greater_water_elemental`, `greater_earth_elemental`, `greater_air_elemental`, `lesser_phoenix`, `storm_wyvern`, `void_crawler`, `watcher_eye`, `young_kraken`, `angel_sentinel`, `cherub`, `crystal_golem`
- bosses_a: `ignis`, `tide_guardian` (Maré), `primordial_golem`, `zephyr` — deixam cair `guardian_core_<fire|water|earth|wind>`
- bosses_b: `abyss_sovereign` (→ `guardian_core_shadow`), `seraph` (→ `guardian_core_light`), `twilight_herald` (chefe final)
- bosses_c: `renegade_archmage`, `corrupted_witch`, `imprisoned_patron`, `corrupted_ent`, `lich`
- mounts: `griffin`, `sky_ray`, `pegasus`, `wyvern`
- origins_a: `familiar_crow`, `familiar_cat` · origins_b: `summoned_imp`, `summoned_demon`, `undead_servant`, `bone_warrior`
- ritual_warlock: `patron_ember_lord`, `patron_void_lady`, `patron_drowned_king` · ritual_druid: `animal_spirit`
- guild: `guild_receptionist`, `guild_master`, `adventurer` · journal: `guide_spirit`
- forms_b: `spell_zone`, `spell_wall`, `spell_rune`, `spell_orb`, `spell_turret`, `spell_conjurer`, `spell_illusion`, `spell_portal` · forms_a: `spell_wave`, `spell_falling` · domain: `domain_dome` · fire_water/earth_wind/light_shadow: summons `summoned_salamander`, `summoned_undine`, `summoned_stone_golem`, `summoned_sylph`, `summoned_guardian_fairy`, `living_shadow` · fusions_a: `summoned_ent`

**Blocos** (estruturas usam estes ids no .nbt)
- structures_a: `shrine_altar` (altar da prova de Despertar; propriedade/BlockEntity com o elemento), `elemental_brick_<fire|water|earth|wind|light|shadow>` (decoração)
- ascension: `ascension_core`, `ascension_pedestal`, `mana_crystal_block`, `rune_stone`
- ritual_mage: `research_table` · ritual_witch: `witch_cauldron` · ritual_warlock: `ritual_chalk`, `offering_altar` · ritual_druid: `ancestral_sapling`, `heart_tree_core`, `heart_tree_log`, `heart_tree_leaves` · ritual_necro: `ossuary_altar`
- guild: `quest_board`, `guild_counter`
- dimensions: `elemental_portal`, `elemental_portal_frame`, `abyss_portal`, `abyss_portal_frame`, `celestial_portal`, `celestial_beacon`, `twilight_rift`, `convergence_altar`, + blocos de terreno das dimensões (`floating_stone`, `ember_rock`, `sky_grass`, `abyssal_stone`, `void_crystal`, `celestial_marble`, `cloud_block`...)
- forms_b/domain: blocos temporários `spell_barrier`, `domain_barrier`

**Itens com uso cruzado**
- núcleo: `essence_<arcane|fire|water|earth|wind|light|shadow>`, `guardian_core_<base>`, `mana_crystal`, `glyph_scroll` (componente `isekai_mahou:glyph`), `spell_scroll`
- ritual_mage: `arcane_lens`, `research_note` · ritual_witch: `ritual_spoon`, `witch_recipe_page` · ritual_warlock: `ritual_chalk_item` (o item do giz), `ritual_dagger` · ritual_druid: `ancestral_sapling` (item do bloco) · ritual_necro: `phylactery`, `bone_scythe`
- guild: `adventurer_card`, `guild_coin`
- equipment: `focus_<elemento base>`, `mana_ring`, `arcane_amulet`
- creatures: drops livres (ex.: `goblin_ear`, `slime_gel`, `orc_tusk`, `harpy_feather`...)

**Dimensões e biomas** (criados pelo módulo dimensions; creatures_e/bosses usam para spawns e estruturas)
- `isekai_mahou:elemental_plane` — biomas `ember_isles`, `suspended_sea`, `earthen_peaks`, `storm_skies`
- `isekai_mahou:abyss` — biomas `abyssal_depths`, `void_hollows`
- `isekai_mahou:celestial_sanctuary` — biomas `crystal_city`, `cloud_gardens`
- `isekai_mahou:twilight_rift` — bioma `twilight_rift`
- Tags de bioma (no merge do dimensions): `isekai_mahou:is_elemental_plane`, `isekai_mahou:is_abyss`, `isekai_mahou:is_celestial`, `isekai_mahou:is_twilight_rift`
- Estruturas de chefe nas dimensões (dimensions cria, com o chefe dentro do .nbt): `guardian_temple_<fire|water|earth|wind>`, `abyss_throne`, `celestial_cathedral`, `twilight_arena`

**Abas do menu B**: `status` (ui_hud), `paths` (paths), `grimoire` / `loadouts` / `glyphs` (ui_grimoire), `journal` / `bestiary` (journal), `guild` (guild).

**Loot tables de baú** (estruturas): `isekai_mahou:chests/<id_da_estrutura>` — para dar Pergaminhos de Glifo use a função `minecraft:set_components` com `{"isekai_mahou:glyph": "isekai_mahou:<id_do_glifo>"}` no item `isekai_mahou:glyph_scroll`.

---------------------------------------------------------------------------------------------------
## 7. Definição de pronto

- `bash tools/check-module.sh <modulo>` termina com **TUDO OK**.
- Tudo registrado no `init` do módulo (e `initClient` para cliente); traduções pt_br + en_us para todo texto/nome.
- Itens/blocos aparecem numa aba criativa do mod; criaturas têm ovo na aba `TAB_CREATURES`.
- Nada fora da área do seu módulo foi alterado.
