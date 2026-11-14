package com.renanpontes.isekaimahou.feature.world_events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.fx.FxBatch;
import com.renanpontes.isekaimahou.feature.cities.CitiesContent;
import com.renanpontes.isekaimahou.feature.cities.CityPlaces;
import com.renanpontes.isekaimahou.feature.cities.CityRealm;
import com.renanpontes.isekaimahou.feature.cities.CityRumors;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * <b>Encontros na estrada</b> (v1.1, "mundo vivo"): de vez em quando, quem anda pelo ermo cruza com gente e coisas
 * pequenas — sem nada na tela, só o que se vê e ouve:
 * <ul>
 *   <li><b>Viajantes</b> (de dia): uma caravana (mercador do reino + 1–2 guardas de escolta) ou um grupo de peregrinos
 *       (às vezes com um bardo tocando) atravessando o mapa rumo à cidade mais próxima. O mercador vende como na cidade.
 *       Param quando você chega perto e cumprimentam.</li>
 *   <li><b>Aventureiro ferido</b>: alguém caído na beira do caminho pedindo ajuda. Dê comida ou uma poção (clique
 *       direito com ela na mão) e ele agradece com moedas, XP e o rumor de um lugar perigoso por perto.</li>
 *   <li><b>Círculo de fadas</b> (noites limpas, campos e bosques): um anel de luz com fadas dançando. Fique dentro
 *       dele por uns segundos e ganhe a Bênção das Fadas (sorte e um pouco de regeneração).</li>
 * </ul>
 * Regras (leve e respeitando as cidades): no máximo 3 encontros no mundo e 1 por jogador; cada jogador é sorteado
 * a cada 4–8 min (só no ermo, na superfície, fora de lugares e estruturas); nunca nasce dentro/perto de cidades;
 * nada quebra blocos; tudo some sozinho (e quem sobrar de um servidor reiniciado é removido ao carregar). Segue a
 * chave {@code /mahou world_events auto} (mesmo sorteio dos eventos). Teste: {@code /mahou encounter <tipo>|clear}.
 */
public final class WeEncounters {
    /** Marca (persistentData) dos NPCs/fadas de encontro. */
    public static final String TAG = "isekai_mahou.encounter";

    public enum Kind {
        TRAVELLERS, WOUNDED, FAIRY_RING;

        final String id = name().toLowerCase(Locale.ROOT);

        @Nullable
        static Kind byId(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }
    }

    private static final int MAX_ACTIVE = 3;
    private static final int ROLL_EVERY = 200;
    private static final int GAP_MIN = 20 * 60 * 4, GAP_SPREAD = 20 * 60 * 4;
    private static final int FIRST_DELAY = 20 * 60 * 3;
    private static final float ROLL_CHANCE = 0.45F;
    private static final Profession[] TRAVEL_TRADES = {Profession.GROCER, Profession.ALCHEMIST, Profession.TAILOR,
            Profession.JEWELER, Profession.BLACKSMITH};

    /** Um encontro em andamento (só em memória: se o servidor reinicia, os participantes somem ao carregar). */
    private static final class Active {
        final Kind kind;
        final UUID player;
        final List<UUID> members = new ArrayList<>();
        final BlockPos anchor;
        final long start;
        long until;
        Vec3 dir = Vec3.ZERO;
        @Nullable UUID bard;
        boolean greeted;
        boolean helped;
        boolean pilgrims;
        long leaveAt;
        final Map<UUID, Integer> standing = new HashMap<>();

        Active(Kind kind, UUID player, BlockPos anchor, long start, long until) {
            this.kind = kind;
            this.player = player;
            this.anchor = anchor;
            this.start = start;
            this.until = until;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Set<UUID> TRACKED = new HashSet<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** Participantes de encontros antigos (servidor reiniciado) que voltaram ao carregar: somem no próximo tick. */
    private static final List<Entity> ORPHANS = new ArrayList<>();

    private WeEncounters() {}

    static void register() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(LevelTickEvent.Post.class, WeEncounters::onLevelTick);
        bus.addListener(EntityJoinLevelEvent.class, WeEncounters::onJoin);
        bus.addListener(PlayerInteractEvent.EntityInteract.class, WeEncounters::onInteract);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> NEXT.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            ACTIVE.clear();
            TRACKED.clear();
            NEXT.clear();
            ORPHANS.clear();
        });
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("encounter")
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Kind.values()).map(k -> k.id), b))
                        .executes(WeEncounters::startCommand))));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!ORPHANS.isEmpty()) {
            for (Entity o : ORPHANS) if (!o.isRemoved()) o.discard();
            ORPHANS.clear();
        }
        long now = level.getGameTime();
        if (now % 20 == 0 && !ACTIVE.isEmpty()) {
            for (int i = ACTIVE.size() - 1; i >= 0; i--) {
                Active a = ACTIVE.get(i);
                boolean keep = switch (a.kind) {
                    case TRAVELLERS -> tickTravellers(level, a, now);
                    case WOUNDED -> tickWounded(level, a, now);
                    case FAIRY_RING -> tickFairyRing(level, a, now);
                };
                if (!keep) end(level, a, i);
            }
        }
        if (now % ROLL_EVERY == 0) roll(level, now);
    }

    private static void roll(ServerLevel level, long now) {
        if (!WeState.get(level.getServer()).auto || level.players().isEmpty()) return;
        if (WeManager.active(level) == WorldEventType.BLOOD_MOON) return;
        RandomSource r = level.random;
        for (ServerPlayer p : level.players()) {
            Long next = NEXT.get(p.getUUID());
            if (next == null) {
                NEXT.put(p.getUUID(), now + FIRST_DELAY + r.nextInt(GAP_SPREAD));
                continue;
            }
            if (now < next) continue;
            NEXT.put(p.getUUID(), now + GAP_MIN + r.nextInt(GAP_SPREAD));
            if (ACTIVE.size() >= MAX_ACTIVE || hasEncounter(p) || !wild(level, p) || r.nextFloat() > ROLL_CHANCE) continue;
            Kind k = choose(level, p, r);
            if (k != null) start(level, p, k);
        }
    }

    @Nullable
    private static Kind choose(ServerLevel level, ServerPlayer p, RandomSource r) {
        int tod = (int) (level.getDayTime() % 24000L);
        boolean night = tod >= 13000 && tod < 23000;
        float x = r.nextFloat();
        if (night) {
            if (x < 0.5F && fairyBiome(level.getBiome(p.blockPosition())) && !level.isRaining()) return Kind.FAIRY_RING;
            return x < 0.75F ? Kind.WOUNDED : null;
        }
        if (tod < 1000 || tod > 11500) return null;
        return x < 0.65F ? Kind.TRAVELLERS : Kind.WOUNDED;
    }

    private static boolean hasEncounter(ServerPlayer p) {
        for (Active a : ACTIVE) if (a.player.equals(p.getUUID())) return true;
        return false;
    }

    /** Ermo de verdade: superfície, fora de lugares (e longe deles), fora de estruturas. */
    private static boolean wild(ServerLevel level, ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive() || p.isSleeping()) return false;
        BlockPos at = p.blockPosition();
        if (at.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 6) return false;
        if (Places.at(level, at) != null || WeSites.nearPlace(level, at, 48)) return false;
        return level.structureManager().getAllStructuresAt(at).isEmpty();
    }

    private static boolean fairyBiome(Holder<Biome> b) {
        if (b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_DRY)) return false;
        return b.is(Tags.Biomes.IS_FLORAL) || b.is(BiomeTags.IS_FOREST) || b.is(Tags.Biomes.IS_PLAINS) || b.is(Biomes.MEADOW)
                || b.is(Biomes.CHERRY_GROVE) || b.unwrapKey().map(k -> IsekaiMahou.MODID.equals(k.location().getNamespace())
                && (k.location().getPath().startsWith("flat_lands") || k.location().getPath().equals("sakura_fields")
                || k.location().getPath().equals("crystal_forest"))).orElse(false);
    }

    // =====================================================================================
    //  Começo
    // =====================================================================================
    static boolean start(ServerLevel level, ServerPlayer p, Kind kind) {
        return switch (kind) {
            case TRAVELLERS -> startTravellers(level, p);
            case WOUNDED -> startWounded(level, p);
            case FAIRY_RING -> startFairyRing(level, p);
        };
    }

    private static boolean startTravellers(ServerLevel level, ServerPlayer p) {
        RandomSource r = level.random;
        BlockPos spot = findSpot(level, p, 26, 40, false);
        if (spot == null) return false;
        long now = level.getGameTime();
        Active a = new Active(Kind.TRAVELLERS, p.getUUID(), spot, now, now + 20 * 60 * (4 + r.nextInt(3)));
        // Rumo: a cidade mais próxima (até ~2000 blocos); sem cidade, passam de lado pelo jogador
        Places.Place city = nearestCity(level, spot, 2000);
        Vec3 to = city != null ? Vec3.atCenterOf(city.center()).subtract(Vec3.atCenterOf(spot))
                : Vec3.atCenterOf(spot).subtract(p.position()).cross(new Vec3(0, 1, 0));
        a.dir = new Vec3(to.x, 0, to.z).lengthSqr() < 1 ? new Vec3(1, 0, 0) : new Vec3(to.x, 0, to.z).normalize();
        CityKingdom k = city != null && CityRealm.byFaction(city.faction()) != null ? CityRealm.byFaction(city.faction())
                : kingdomOfBiome(level.getBiome(spot));
        a.pilgrims = r.nextFloat() < 0.45F;
        if (a.pilgrims) {
            int n = 2 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                CityNpc c = spawnNpc(level, CitiesContent.CITIZEN.get(), spot.offset(i - 1, 0, (i % 2) * 2 - 1), k, a);
                if (c != null && i == 0 && r.nextFloat() < 0.5F) a.bard = c.getUUID();
            }
        } else {
            CityNpc m = spawnNpc(level, CitiesContent.CITY_MERCHANT.get(), spot, k, a);
            if (m instanceof CityMerchant merchant) merchant.setProfession(TRAVEL_TRADES[r.nextInt(TRAVEL_TRADES.length)]);
            int guards = 1 + r.nextInt(2);
            for (int i = 0; i < guards; i++) spawnNpc(level, CitiesContent.CITY_GUARD.get(), spot.offset(i * 2 - 1, 0, 2), k, a);
        }
        if (a.members.isEmpty()) return false;
        ACTIVE.add(a);
        return true;
    }

    private static boolean startWounded(ServerLevel level, ServerPlayer p) {
        BlockPos spot = findSpot(level, p, 18, 30, false);
        if (spot == null) return false;
        long now = level.getGameTime();
        Active a = new Active(Kind.WOUNDED, p.getUUID(), spot, now, now + 20 * 60 * 6);
        CityNpc c = spawnNpc(level, CitiesContent.CITIZEN.get(), spot, kingdomOfBiome(level.getBiome(spot)), a);
        if (c == null) return false;
        c.setHealth(Math.max(2F, c.getMaxHealth() * 0.25F));
        // "Caído": não sai do lugar (mas ainda olha para quem chega)
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60 * 7, 9, false, false));
        ACTIVE.add(a);
        return true;
    }

    private static boolean startFairyRing(ServerLevel level, ServerPlayer p) {
        BlockPos spot = findSpot(level, p, 18, 30, true);
        if (spot == null) return false;
        long now = level.getGameTime();
        Active a = new Active(Kind.FAIRY_RING, p.getUUID(), spot, now, now + 20 * 60 * 4);
        EntityType<?> fairy = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("fairy")).orElse(null);
        if (fairy != null) {
            for (int i = 0; i < 3; i++) {
                double ang = i * Math.PI * 2 / 3;
                Entity f = fairy.create(level);
                if (f == null) continue;
                f.moveTo(spot.getX() + 0.5 + Math.cos(ang) * 2.5, spot.getY() + 1.2, spot.getZ() + 0.5 + Math.sin(ang) * 2.5,
                        level.random.nextFloat() * 360F, 0);
                if (f instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
                    mob.restrictTo(spot, 4);
                }
                track(f, a);
                level.addFreshEntity(f);
            }
        }
        ACTIVE.add(a);
        // uma linha só, na primeira vez: senão quase ninguém repararia no brilho na grama
        Hints.show(p, "encounter.fairy_ring");
        return true;
    }

    @Nullable
    private static CityNpc spawnNpc(ServerLevel level, EntityType<? extends CityNpc> type, BlockPos at, CityKingdom k, Active a) {
        CityNpc npc = type.create(level);
        if (npc == null) return null;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        npc.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);
        npc.joinKingdom(k);
        npc.setHome(ground);
        track(npc, a);
        level.addFreshEntity(npc);
        return npc;
    }

    private static void track(Entity e, Active a) {
        e.getPersistentData().putBoolean(TAG, true);
        TRACKED.add(e.getUUID());
        a.members.add(e.getUUID());
    }

    // =====================================================================================
    //  Andamento
    // =====================================================================================
    private static boolean tickTravellers(ServerLevel level, Active a, long now) {
        List<CityNpc> party = alive(level, a, CityNpc.class);
        if (party.isEmpty()) return false;
        CityNpc lead = party.get(0);
        Player near = level.getNearestPlayer(lead, 128);
        if (near == null) return false;                                     // ninguém por perto: seguem viagem (somem)
        double d = near.distanceTo(lead);
        if (now > a.until && d > 48) return false;
        if (d < 10 && !a.greeted) {
            a.greeted = true;
            lead.faceFor(near, 80);
            lead.say(line(a.pilgrims ? "pilgrims.greet" : "travellers.greet", 3, level.random));
        }
        if (a.bard != null && now % 60 == 0 && d < 24) bardSong(level, level.getEntity(a.bard));
        boolean arrived = CityPlaces.cityAt(level, lead.blockPosition()) != null;
        if (arrived && d > 32) return false;                                // chegaram à cidade
        if (d < 7 || arrived || now % 60 != 0) {
            if (d < 7) party.forEach(n -> n.getNavigation().stop());
            return true;
        }
        // próximo trecho: 14 blocos à frente; água ou paredão na frente → desvia 45°
        Vec3 next = lead.position().add(a.dir.scale(14));
        BlockPos wp = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next));
        if (!level.isLoaded(wp)) return true;
        if (!level.getFluidState(wp.below()).isEmpty() || wp.getY() - lead.getBlockY() > 5) {
            a.dir = a.dir.yRot((float) (Math.PI / 4) * (level.random.nextBoolean() ? 1 : -1));
            return true;
        }
        lead.setHome(wp);
        lead.getNavigation().moveTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 0.6);
        for (int i = 1; i < party.size(); i++) {
            CityNpc f = party.get(i);
            BlockPos fp = lead.blockPosition().offset((i % 2) * 2 - 1, 0, i > 1 ? 2 : -2);
            f.setHome(fp);
            if (f.distanceTo(lead) > 4) f.getNavigation().moveTo(fp.getX() + 0.5, fp.getY(), fp.getZ() + 0.5, 0.7);
        }
        return true;
    }

    /** Três notas de flauta com notinhas no ar (bardo dos peregrinos). */
    private static void bardSong(ServerLevel level, @Nullable Entity bard) {
        if (bard == null || !bard.isAlive()) return;
        RandomSource r = level.random;
        for (int i = 0; i < 3; i++) {
            float pitch = (float) Math.pow(2.0, (r.nextInt(13) - 6) / 12.0);
            level.playSound(null, bard.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 0.8F, pitch);
        }
        level.sendParticles(ParticleTypes.NOTE, bard.getX(), bard.getY() + 2.2, bard.getZ(), 3, 0.4, 0.2, 0.4, 1.0);
    }

    private static boolean tickWounded(ServerLevel level, Active a, long now) {
        List<CityNpc> list = alive(level, a, CityNpc.class);
        if (list.isEmpty()) return false;
        CityNpc c = list.get(0);
        Player near = level.getNearestPlayer(c, 96);
        if (near == null) return false;
        double d = near.distanceTo(c);
        if (a.helped) return !(now > a.leaveAt && d > 24);
        if (now > a.until && d > 24) return false;
        if (d < 12 && now % 300 == 0) {
            c.faceFor(near, 60);
            c.say(line("wounded.help", 3, level.random));
        }
        if (now % 80 == 0) level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.getX(), c.getY() + 1.0, c.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        return true;
    }

    private static boolean tickFairyRing(ServerLevel level, Active a, long now) {
        int tod = (int) (level.getDayTime() % 24000L);
        if (now > a.until || (now - a.start > 100 && (tod < 12500 || tod >= 23500))) return false;
        if (a.helped) return now < a.leaveAt;
        Vec3 c = Vec3.atBottomCenterOf(a.anchor).add(0, 0.15, 0);
        Player near = level.getNearestPlayer(c.x, c.y, c.z, 64, false);
        if (near == null) return now - a.start < 20 * 60;                   // ninguém viu em 1 min: some
        // o anel: um item só no lote de partículas (o cliente desenha os pontos), + algumas motas subindo
        FxBatch.ring(level, Element.LIGHT, 0xC8FFB0, c, 3.0, 18, 4, 1.0F, false);
        FxBatch.emit(level, ElementFx.fx(SpellFxParticles.MOTE, 0xE8FFC8, 0.8F, 40, -1, 0), c.x, c.y + 0.6, c.z, 4,
                2.0, 0.6, 2.0, 0.01, false);
        if (now % 60 == 0) {
            level.playSound(null, a.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.6F,
                    1.4F + level.random.nextFloat() * 0.4F);
        }
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            boolean in = dx * dx + dz * dz <= 2.8 * 2.8 && Math.abs(p.getY() - c.y) < 2.5;
            int n = in ? a.standing.getOrDefault(p.getUUID(), 0) + 1 : 0;
            a.standing.put(p.getUUID(), n);
            if (n >= 3) bless(level, p, a, now);
        }
        return true;
    }

    private static void bless(ServerLevel level, ServerPlayer p, Active a, long now) {
        p.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 60 * 5, 0));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 15, 0));
        MagicApi.giveXp(p, 30, "encounter");
        p.displayClientMessage(Component.translatable("encounter.isekai_mahou.fairy.blessing").withColor(0xC8FFB0), true);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        ElementFx.flash(level, p.position().add(0, 1, 0), 0xC8FFB0, 1.5F);
        MagicApi.addCounter(p, "encounter.fairy_ring", 1);
        a.helped = true;
        a.leaveAt = now + 20 * 8;
    }

    // =====================================================================================
    //  Ajudar o ferido (clique direito com comida/poção)
    // =====================================================================================
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Entity target = e.getTarget();
        if (!TRACKED.contains(target.getUUID()) || !(target instanceof CityNpc npc)) return;
        Active a = find(target.getUUID());
        if (a == null || a.kind != Kind.WOUNDED) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (a.helped) {
            npc.faceFor(sp, 40);
            npc.say(line("wounded.thanks", 2, sp.getRandom()));
            return;
        }
        ItemStack held = sp.getItemInHand(e.getHand());
        if (!healing(held)) {
            npc.faceFor(sp, 60);
            npc.say(Component.translatable("encounter.isekai_mahou.wounded.need"));
            Hints.show(sp, "encounter.wounded");
            return;
        }
        if (!sp.getAbilities().instabuild) held.shrink(1);
        a.helped = true;
        a.leaveAt = sp.serverLevel().getGameTime() + 20 * 40;
        npc.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        npc.heal(npc.getMaxHealth());
        npc.faceFor(sp, 80);
        npc.say(line("wounded.thanks", 2, sp.getRandom()));
        npc.playAction("bow");
        reward(sp, npc);
    }

    private static boolean healing(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.has(DataComponents.FOOD) || s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.HONEY_BOTTLE);
    }

    private static void reward(ServerPlayer sp, CityNpc npc) {
        ServerLevel level = sp.serverLevel();
        RandomSource r = sp.getRandom();
        Item coin = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("guild_coin")).orElse(Items.EMERALD);
        Rewards.give(sp, new ItemStack(coin, coin == Items.EMERALD ? 1 + r.nextInt(2) : 6 + r.nextInt(7)));
        MagicApi.giveXp(sp, 60, "encounter");
        MagicApi.addCounter(sp, "encounter.wounded_helped", 1);
        level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.3F);
        // "Obrigado... e cuidado: vi um lugar perigoso por aqui." Rumor de masmorra/covil/torre por perto.
        CityRumors.Found f = CityRumors.search(level, npc.blockPosition(), r, WeEncounters::dangerous, 3);
        if (f != null) {
            CityRumors.mark(sp, f);
            int dist = (int) Math.sqrt(npc.blockPosition().distSqr(f.pos()));
            sp.sendSystemMessage(Component.translatable("encounter.isekai_mahou.wounded.rumor", npc.getDisplayName(),
                    CityRumors.rumorName(f.key()), dist, CityRumors.direction(npc.blockPosition(), f.pos())).withColor(0xE8D8A8));
        } else {
            sp.sendSystemMessage(Component.translatable("encounter.isekai_mahou.wounded.reward", npc.getDisplayName())
                    .withColor(0xE8D8A8));
        }
    }

    private static boolean dangerous(String path) {
        return path.contains("tower") || path.contains("crypt") || path.contains("labyrinth") || path.contains("depths")
                || path.endsWith("_den") || path.endsWith("_lair") || path.contains("fortress") || path.contains("camp")
                || path.contains("ruins") || path.contains("nest");
    }

    // =====================================================================================
    //  Fim e limpeza
    // =====================================================================================
    private static void end(ServerLevel level, Active a, int index) {
        for (UUID id : a.members) {
            TRACKED.remove(id);
            Entity e = level.getEntity(id);
            if (e != null && !e.isRemoved()) {
                // some num "puf" só se alguém estiver vendo de perto (senão, só some)
                if (level.getNearestPlayer(e, 24) != null) {
                    level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                }
                e.discard();
            }
        }
        ACTIVE.remove(index);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en.getPersistentData().getBoolean(TAG) && !TRACKED.contains(en.getUUID())) ORPHANS.add(en);
    }

    @Nullable
    private static Active find(UUID member) {
        for (Active a : ACTIVE) if (a.members.contains(member)) return a;
        return null;
    }

    private static <T extends Entity> List<T> alive(ServerLevel level, Active a, Class<T> cls) {
        List<T> out = new ArrayList<>();
        for (UUID id : a.members) {
            Entity e = level.getEntity(id);
            if (cls.isInstance(e) && e.isAlive()) out.add(cls.cast(e));
        }
        return out;
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    /**
     * Chão livre a {@code min}–{@code max} blocos do jogador: chunk carregado, chão firme e seco, longe de lugares e
     * de cidades; {@code grass} = só sobre grama (círculo de fadas). Até 8 tentativas.
     */
    @Nullable
    private static BlockPos findSpot(ServerLevel level, ServerPlayer p, int min, int max, boolean grass) {
        RandomSource r = level.random;
        for (int i = 0; i < 8; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (Math.abs(ground.getY() - p.getBlockY()) > 12) continue;
            BlockPos below = ground.below();
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            if (grass && !level.getBlockState(below).is(Blocks.GRASS_BLOCK)) continue;
            if (Places.isSafe(level, ground) || WeSites.nearPlace(level, ground, 24)) continue;
            return ground;
        }
        return null;
    }

    @Nullable
    private static Places.Place nearestCity(ServerLevel level, BlockPos from, int maxDist) {
        Places.Place best = null;
        double bestD = (double) maxDist * maxDist;
        for (Places.Place p : Places.all(level)) {
            if (!CityPlaces.isCity(p) || p.dim() != level.dimension()) continue;
            double d = p.center().distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static CityKingdom kingdomOfBiome(Holder<Biome> b) {
        if (b.is(Tags.Biomes.IS_DESERT) || b.is(Tags.Biomes.IS_BADLANDS) || b.is(Tags.Biomes.IS_SAVANNA)) return CityKingdom.SAHRA;
        if (b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_COLD_OVERWORLD)) return CityKingdom.NORDHEIM;
        return CityKingdom.ALDORIA;
    }

    private static Component line(String group, int count, RandomSource r) {
        return Component.translatable("encounter.isekai_mahou." + group + "." + r.nextInt(count));
    }

    // =====================================================================================
    //  Comandos de teste (op)
    // =====================================================================================
    private static int startCommand(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        Kind k = Kind.byId(StringArgumentType.getString(c, "kind"));
        if (p == null || k == null || p.serverLevel().dimension() != Level.OVERWORLD) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.encounter.failed"));
            return 0;
        }
        if (!start(p.serverLevel(), p, k)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.encounter.failed"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.encounter.started", k.id), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        int n = ACTIVE.size();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) end(level, ACTIVE.get(i), i);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.encounter.cleared", n), false);
        return n;
    }
}
