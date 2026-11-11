package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.guild.ActiveQuest;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;
import com.renanpontes.isekaimahou.feature.guild.GuildService;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuest;
import com.renanpontes.isekaimahou.feature.guild.quest.QuestObjective;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * <b>Áreas de Caça</b> (v0.9): ao aceitar uma missão de caçar criaturas comuns, a Guilda marca um ponto a 150–500 blocos
 * no bioma das criaturas ({@link HuntLocator}) e cria um lugar {@code Places} tipo {@code hunting_ground} ("Área de Caça:
 * Goblin") com um estandarte ({@link HuntBanner}) e a seta no rastreador.
 * <ol>
 *   <li><b>Viagem</b>: ao chegar perto, o Y do centro é medido no terreno e o estandarte é fincado;</li>
 *   <li><b>Ondas</b>: a ≤ {@link #RADIUS} blocos, as criaturas da missão nascem em ondas perto do caçador — cada uma
 *       anunciada por um CÍRCULO DE INVOCAÇÃO no chão (telegrafia do núcleo) antes de surgir. Tamanho por rank e por número de
 *       jogadores na área; a partir do rank D cada onda (da 2ª em diante) traz um <i>Alfa</i> mais forte. Na área não nasce
 *       nenhum outro monstro;</li>
 *   <li><b>Marcas</b>: os mobs da área levam a marca da área (dados persistentes); deixam cair essências do elemento do
 *       bioma (+ arcanas), abates por aliados/companheiros contam para o dono, e somem se a área acabar;</li>
 *   <li><b>Fim</b>: missão concluída (área limpa → saque e título), entregue, abandonada, vencida ({@link #LIFETIME}) ou
 *       esgotada ({@link #MAX_WAVES} ondas). Sair da área no meio da luta faz as criaturas recuarem (as ondas recomeçam ao
 *       voltar).</li>
 * </ol>
 * Fonte da verdade: {@link HuntRegistry} (SavedData) + o campo {@code hunt} do {@code ActiveQuest}; uma varredura a cada
 * minuto apaga áreas vencidas ou órfãs (inclusive de jogadores offline).
 */
public final class HuntingGrounds {
    public static final String TYPE = "hunting_ground";
    /** Raio (blocos) da área: chegar a esta distância do centro começa as ondas. */
    public static final int RADIUS = 40;
    /** Longe disto (ou noutra dimensão) as criaturas recuam. */
    private static final int LEAVE = 96;
    /** O estandarte é fincado quando o dono está a esta distância. */
    private static final int BANNER_RANGE = 112;
    /** Validade de uma área (tempo de jogo): 3 dias do Minecraft (1 h real). */
    public static final long LIFETIME = 3L * 24000L;
    /** Ondas no máximo por área (depois, a área se esgota e a missão segue como caçada comum). */
    private static final int MAX_WAVES = 12;
    /** Mobs marcados vivos ao mesmo tempo numa área. */
    private static final int MAX_ALIVE = 14;
    /** Áreas vivas por jogador (a mais 3 missões da Guilda + 2 de facção + a prova). */
    private static final int MAX_PER_PLAYER = 6;
    /** Segundos entre uma onda limpa e a próxima. */
    private static final int WAVE_COOLDOWN = 6;

    public static final String TAG = "isekai_mahou_hunt";
    private static final String TAG_ELITE = "isekai_mahou_hunt_elite";
    private static final String TAG_ELEMENT = "isekai_mahou_hunt_el";
    private static final String TAG_RANK = "isekai_mahou_hunt_rank";
    private static final ResourceLocation MOD_HEALTH = IsekaiMahou.id("hunt_health");
    private static final ResourceLocation MOD_DAMAGE = IsekaiMahou.id("hunt_damage");
    private static final ResourceLocation MOD_SCALE = IsekaiMahou.id("hunt_alpha_scale");
    private static final ResourceLocation MOD_SPEED = IsekaiMahou.id("hunt_alpha_speed");
    private static final ResourceLocation MOD_KNOCKBACK = IsekaiMahou.id("hunt_alpha_knockback");
    private static final String KEY = "guild.isekai_mahou.hunt.";
    /** Cor do couro (capacete que protege os mortos-vivos da área do sol). */
    private static final int HUNT_LEATHER = 0x3E6B35;

    /** Quantas áreas existem (atalho barato para os eventos de nascimento natural). Atualizado nas mudanças e na varredura. */
    private static volatile int liveGrounds = -1;
    private static int sweepTimer = 100;

    public enum Reason { CLEARED, TURNED_IN, ABANDONED, EXPIRED, EXHAUSTED, GONE }

    // =====================================================================================
    //  Criar
    // =====================================================================================
    /**
     * Cria a Área de Caça de uma missão recém-aceita (se ela for de caça e houver um bom lugar). Silencioso quando a missão
     * não é de caça; avisa quando não achou lugar.
     */
    public static void create(ServerPlayer sp, ActiveQuest a, GuildQuest q, boolean announce) {
        ServerLevel level = sp.serverLevel();
        if (a.hunt != null || !HuntRules.eligible(level, q)) return;
        HuntRegistry reg = HuntRegistry.of(sp.server);
        if (reg.count(sp.getUUID()) >= MAX_PER_PLAYER) return;
        List<EntityType<?>> mobs = HuntRules.huntableMobs(level, q);
        HuntLocator.Result r = HuntLocator.find(level, sp.blockPosition(), mobs, sp.getRandom());
        if (r == null) {
            if (announce) sp.sendSystemMessage(Component.translatable(KEY + "no_place").withStyle(ChatFormatting.GRAY));
            return;
        }
        placeAt(sp, a, q, r.center(), r.mobs(), HuntRules.elementOf(r.biome()), announce);
    }

    /** Cria a área num ponto dado (também usado pelo comando de teste "aqui"). */
    public static void placeAt(ServerPlayer sp, ActiveQuest a, GuildQuest q, BlockPos center, List<EntityType<?>> mobs, Element element,
                               boolean announce) {
        ServerLevel level = sp.serverLevel();
        HuntRegistry reg = HuntRegistry.of(sp.server);
        // Id único mesmo com várias áreas criadas no mesmo tick (comando "scout", missão + prova).
        String base = "hunt_" + sp.getUUID().toString().substring(0, 8) + "_" + Long.toString(level.getGameTime(), 36);
        String id = base;
        for (int n = 1; reg.has(id) || Places.get(level, id) != null; n++) id = base + "_" + n;
        long expires = sp.server.overworld().getGameTime() + LIFETIME;
        HuntState h = new HuntState(id, level.dimension(), center, HuntRules.ids(mobs), element.id, HuntState.TRAVEL, 0, expires, false,
                sp.getUUID());
        a.hunt = h;
        registerPlace(level, h);
        reg.put(id, new HuntRegistry.Entry(sp.getUUID(), level.dimension(), center, expires));
        liveGrounds = reg.size();
        GuildService.data(sp).markDirty();
        if (!announce) return;
        int dist = Mth.floor(Math.sqrt(sp.blockPosition().distSqr(new BlockPos(center.getX(), sp.getBlockY(), center.getZ()))));
        sp.sendSystemMessage(Component.translatable(KEY + "marked", placeName(h).copy().withColor(element.color), dist,
                direction(sp.blockPosition(), center)).withStyle(ChatFormatting.YELLOW));
        sp.sendSystemMessage(Component.translatable(KEY + "marked_hint").withStyle(ChatFormatting.GRAY));
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** "Área de Caça: Goblin" (ou "Goblin e mais 2"). */
    public static Component placeName(HuntState h) {
        return Component.translatable(KEY + "place", creatureName(HuntRules.types(h.mobs)));
    }

    private static Component creatureName(List<EntityType<?>> types) {
        if (types.isEmpty()) return Component.translatable(KEY + "creatures");
        Component first = types.get(0).getDescription();
        return types.size() == 1 ? first : Component.translatable("guild.isekai_mahou.objective.and_more", first, types.size() - 1);
    }

    public static int colorOf(HuntState h) {
        return color(h);
    }

    private static int color(HuntState h) {
        Element e = Element.byId(h.element);
        return e == null ? 0x6BD66B : e.color;
    }

    private static void registerPlace(ServerLevel level, HuntState h) {
        BlockPos c = h.center;
        BoundingBox box = new BoundingBox(c.getX() - RADIUS, c.getY() - 16, c.getZ() - RADIUS, c.getX() + RADIUS, c.getY() + 40,
                c.getZ() + RADIUS);
        Places.register(level, new Places.Place(h.placeId, placeName(h), TYPE, "guild", level.dimension(), box, c, false, color(h)));
    }

    /** Direção (N, NE...) de {@code from} até {@code to}. */
    public static Component direction(BlockPos from, BlockPos to) {
        double ang = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        int idx = Math.floorMod(Math.round((float) (ang / 45.0)), 8);
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return Component.translatable(KEY + "dir." + dirs[idx]);
    }

    // =====================================================================================
    //  Tick (1x por segundo por missão ativa, chamado pelo GuildService)
    // =====================================================================================
    public static void tick(ServerPlayer sp, GuildData d, ActiveQuest a, GuildQuest q, boolean questComplete) {
        HuntState h = a.hunt;
        if (h == null) return;
        MinecraftServer server = sp.server;
        HuntRegistry reg = HuntRegistry.of(server);
        if (!reg.has(h.placeId) || !h.active()) {
            // Apagada por fora (varredura, comando): esquece.
            a.hunt = null;
            d.markDirty();
            return;
        }
        if (server.overworld().getGameTime() >= h.expires) {
            finish(sp, d, a, Reason.EXPIRED);
            return;
        }
        ServerLevel hl = server.getLevel(h.dim);
        if (hl == null) {
            finish(sp, d, a, Reason.GONE);
            return;
        }
        int obj = questComplete ? -1 : currentObjective(sp, q, a, h);
        if (obj < 0) {
            clear(sp, d, a, q, hl);
            return;
        }
        boolean here = sp.level() == hl && !sp.isSpectator();
        double dist = here ? horizontal(sp.position(), h.center) : Double.MAX_VALUE;
        if (!h.surveyed && dist < 160 && hl.isLoaded(h.center)) survey(hl, h, reg, d);
        if (h.surveyed && dist < BANNER_RANGE && hl.isLoaded(h.center)) ensureBanner(hl, h);

        if (h.phase == HuntState.TRAVEL) {
            if (h.surveyed && dist <= RADIUS && sp.isAlive()) startFight(sp, d, hl, h);
            return;
        }
        // FIGHT
        if (dist > LEAVE || !sp.isAlive()) {
            retreat(sp, d, hl, h);
            return;
        }
        // Saindo da área: as criaturas que já estão aí continuam atrás dele, mas novas ondas só vêm quando ele voltar
        // (senão os círculos de invocação nasceriam no centro, longe da vista). Avisa 1x por saída.
        if (dist > RADIUS + 16) {
            if (!h.warnedLeave) {
                h.warnedLeave = true;
                sp.displayClientMessage(Component.translatable(KEY + "leaving", LEAVE).withStyle(ChatFormatting.GOLD), true);
                sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
            }
            return;
        }
        if (dist <= RADIUS + 8) h.warnedLeave = false;
        fightTick(sp, d, a, q, hl, h, obj);
    }

    /** Primeiro objetivo de abate ainda não cumprido que tem criaturas desta área (-1 = nenhum: a caçada aqui acabou). */
    private static int currentObjective(ServerPlayer sp, GuildQuest q, ActiveQuest a, HuntState h) {
        int[] prog = GuildService.progress(sp, q, a);
        List<EntityType<?>> here = HuntRules.types(h.mobs);
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            if (o.kind() != QuestObjective.Kind.KILL || prog[i] >= o.target()) continue;
            for (EntityType<?> t : o.existingEntities()) if (here.contains(t)) return i;
        }
        return -1;
    }

    private static double horizontal(Vec3 p, BlockPos c) {
        double dx = p.x - (c.getX() + 0.5), dz = p.z - (c.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Mede o chão de verdade no centro (o gerador só estima) e afasta da água. */
    private static void survey(ServerLevel hl, HuntState h, HuntRegistry reg, GuildData d) {
        BlockPos best = null;
        for (int ring = 0; ring <= 16 && best == null; ring += 4) {
            for (int k = 0; k < (ring == 0 ? 1 : 8) && best == null; k++) {
                double ang = k * Math.PI / 4;
                int x = h.center.getX() + Mth.floor(Math.cos(ang) * ring), z = h.center.getZ() + Mth.floor(Math.sin(ang) * ring);
                BlockPos top = new BlockPos(x, hl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!hl.isLoaded(top)) continue;
                var ground = hl.getBlockState(top.below());
                if (ground.is(BlockTags.LOGS) || ground.is(BlockTags.LEAVES)) continue;
                if (hl.getFluidState(top.below()).isEmpty() && hl.getFluidState(top).isEmpty()) best = top;
            }
        }
        if (best == null) {
            int x = h.center.getX(), z = h.center.getZ();
            best = new BlockPos(x, hl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        h.center = best;
        h.surveyed = true;
        registerPlace(hl, h);
        reg.updateCenter(h.placeId, best);
        d.markDirty();
    }

    private static void ensureBanner(ServerLevel hl, HuntState h) {
        AABB box = new AABB(h.center).inflate(3, 6, 3);
        List<HuntBanner> found = hl.getEntitiesOfClass(HuntBanner.class, box, b -> h.placeId.equals(b.placeId()));
        int want = h.phase == HuntState.FIGHT ? HuntBanner.BATTLE : HuntBanner.CALM;
        if (found.isEmpty()) {
            HuntBanner.plant(hl, h, placeName(h), color(h));
            return;
        }
        for (HuntBanner b : found) b.setBannerState(want);
    }

    private static void startFight(ServerPlayer sp, GuildData d, ServerLevel hl, HuntState h) {
        h.phase = HuntState.FIGHT;
        h.cooldown = 3;
        h.waveAge = 0;
        h.warnedLeave = false;
        d.markDirty();
        // O letreiro do lugar ("Área de Caça: Goblin") já aparece ao entrar: aqui só a barra de ação e a trompa.
        sp.displayClientMessage(Component.translatable(KEY + "start").withColor(color(h)), true);
        sp.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.5F, 1.25F);
        hl.playSound(null, h.center, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2F, 0.7F);
        ensureBanner(hl, h);
    }

    private static void retreat(ServerPlayer sp, GuildData d, ServerLevel hl, HuntState h) {
        h.phase = HuntState.TRAVEL;
        h.pending = 0;
        h.cooldown = 0;
        d.markDirty();
        int n = dispel(hl, h);
        for (HuntBanner b : hl.getEntitiesOfClass(HuntBanner.class, new AABB(h.center).inflate(3, 6, 3), b -> h.placeId.equals(b.placeId()))) {
            b.setBannerState(HuntBanner.CALM);
        }
        if (n > 0 || h.wave > 0) {
            sp.sendSystemMessage(Component.translatable(KEY + "retreat", placeName(h)).withStyle(ChatFormatting.GRAY));
        }
    }

    // =====================================================================================
    //  Ondas
    // =====================================================================================
    /** Ondas previstas por rank: F/E 2 · D/C 3 · B/A/S 4 (depois disso vêm "reforços" até o objetivo ser cumprido). */
    public static int wavesFor(GuildRank rank) {
        return rank.level() <= 2 ? 2 : rank.level() <= 4 ? 3 : 4;
    }

    private static void fightTick(ServerPlayer sp, GuildData d, ActiveQuest a, GuildQuest q, ServerLevel hl, HuntState h, int obj) {
        List<Mob> alive = marked(hl, h);
        h.waveAge++;
        // Quem se escondeu fica brilhando (dá para achar através das paredes).
        if (h.waveAge == 50 && !alive.isEmpty()) {
            for (Mob m : alive) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false));
            sp.displayClientMessage(Component.translatable(KEY + "hiding", alive.size()).withStyle(ChatFormatting.GOLD), true);
        }
        // Os círculos de invocação duram no máximo ~4 s: se algum callback se perdeu (nível recarregado), não trava as ondas.
        if (h.pending > 0 && h.waveAge > 15) h.pending = 0;
        boolean stuck = h.waveAge > 90 && alive.size() <= 1;
        if ((!alive.isEmpty() && !stuck) || h.pending > 0) return;
        if (h.wave >= MAX_WAVES) {
            sp.sendSystemMessage(Component.translatable(KEY + "exhausted", placeName(h)).withStyle(ChatFormatting.GRAY));
            finish(sp, d, a, Reason.EXHAUSTED);
            return;
        }
        if (h.cooldown > 0) {
            if (h.wave > 0 && h.cooldown <= 3) {
                sp.displayClientMessage(Component.translatable(KEY + "next_wave", h.cooldown).withStyle(ChatFormatting.YELLOW), true);
            }
            h.cooldown--;
            return;
        }
        spawnWave(sp, a, q, hl, h, obj, alive.size());
    }

    private static void spawnWave(ServerPlayer sp, ActiveQuest a, GuildQuest q, ServerLevel hl, HuntState h, int obj, int aliveNow) {
        QuestObjective o = q.objectives().get(obj);
        List<EntityType<?>> here = HuntRules.types(h.mobs);
        List<EntityType<?>> types = new ArrayList<>();
        boolean peaceful = hl.getDifficulty() == Difficulty.PEACEFUL;
        boolean blocked = false;
        for (EntityType<?> t : o.existingEntities()) {
            if (!here.contains(t)) continue;
            // No Pacífico os monstros somem no mesmo tick: as ondas só gastariam o limite da área.
            if (peaceful && t.getCategory() == MobCategory.MONSTER) {
                blocked = true;
                continue;
            }
            types.add(t);
        }
        if (types.isEmpty()) {
            h.cooldown = 5;
            if (blocked && h.waveAge % 15 == 0) {
                sp.displayClientMessage(Component.translatable(KEY + "peaceful").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        int[] prog = GuildService.progress(sp, q, a);
        int remaining = Math.max(1, o.target() - prog[obj]);
        List<ServerPlayer> hunters = hunters(hl, h);
        int players = Math.max(1, hunters.size());
        GuildRank rank = q.rank();
        int waves = wavesFor(rank);
        int base = Mth.ceil(o.target() / (float) waves);
        int size = Math.min(base, remaining + 1);
        size = Math.round(size * (1F + 0.5F * (players - 1)));
        size = Mth.clamp(size, 1, Math.min(10, MAX_ALIVE - aliveNow));
        if (size <= 0) return;
        RandomSource rnd = hl.random;
        List<BlockPos> spots = new ArrayList<>();
        List<EntityType<?>> picks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            EntityType<?> type = types.get(rnd.nextInt(types.size()));
            BlockPos spot = findSpot(hl, h, sp, type, rnd);
            if (spot == null) continue;
            spots.add(spot);
            picks.add(type);
        }
        if (spots.isEmpty()) {
            // Sem chão livre perto (caverna, água, árvores): tenta de novo daqui a 2 s sem gastar a onda.
            h.cooldown = 2;
            if (h.waveAge % 10 == 0) sp.displayClientMessage(Component.translatable(KEY + "no_room").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        size = spots.size();
        h.wave++;
        h.waveAge = 0;
        h.cooldown = WAVE_COOLDOWN;
        boolean elite = rank.level() >= 3 && h.wave >= 2;
        Component what = Component.literal(size + "× ").append(types.get(0).getDescription());
        Component banner = h.wave <= waves ? Component.translatable(KEY + "wave", h.wave, waves, what)
                : Component.translatable(KEY + "reinforcements", what);
        for (ServerPlayer p : hunters) {
            p.displayClientMessage(banner.copy().withColor(0xFF7A5C), true);
        }
        hl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.85F);
        int rgb = color(h);
        ServerPlayer target = sp;
        for (int i = 0; i < size; i++) {
            EntityType<?> type = picks.get(i);
            boolean alpha = elite && i == size - 1;
            BlockPos spot = spots.get(i);
            int warm = 30 + i * 5;
            Vec3 at = Vec3.atBottomCenterOf(spot);
            h.pending++;
            final int playersF = players;
            Telegraph.delayed(hl, Telegraph.Shape.circle(at, alpha ? 2.2F : 1.6F).warmup(warm).color(alpha ? 0xFFC94F : rgb)
                    .vertical(1.0, 2.0), hits -> {
                h.pending = Math.max(0, h.pending - 1);
                if (h.phase != HuntState.FIGHT || !HuntRegistry.of(hl.getServer()).has(h.placeId)) return;
                spawnMob(hl, h, type, at, alpha, rank, playersF, target);
            });
        }
    }

    /** Jogadores (vivos, não espectadores) dentro da área. */
    private static List<ServerPlayer> hunters(ServerLevel hl, HuntState h) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : hl.players()) {
            if (p.isAlive() && !p.isSpectator() && horizontal(p.position(), h.center) <= RADIUS + 16) out.add(p);
        }
        return out;
    }

    /** Um chão firme e livre para a criatura, a 7–15 blocos do caçador e dentro da área. */
    @Nullable
    private static BlockPos findSpot(ServerLevel hl, HuntState h, ServerPlayer sp, EntityType<?> type, RandomSource rnd) {
        // Âncora: o chão sob o caçador (voando/pulando não conta); fora da área, o centro.
        BlockPos at = horizontal(sp.position(), h.center) <= RADIUS + 8 ? sp.blockPosition() : h.center;
        BlockPos anchor = new BlockPos(at.getX(), Math.min(at.getY(), hl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(),
                at.getZ())), at.getZ());
        for (int tries = 0; tries < 16; tries++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 7 + rnd.nextDouble() * 8;
            int x = anchor.getX() + Mth.floor(Math.cos(ang) * dist), z = anchor.getZ() + Mth.floor(Math.sin(ang) * dist);
            if (horizontal(new Vec3(x + 0.5, 0, z + 0.5), h.center) > RADIUS + 8) continue;
            int y = hl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!hl.isLoaded(pos) || Math.abs(y - anchor.getY()) > 10) continue;
            BlockPos below = pos.below();
            if (!hl.getBlockState(below).isFaceSturdy(hl, below, Direction.UP)) continue;
            if (!hl.getFluidState(pos).isEmpty() || !hl.getFluidState(below).isEmpty()) continue;
            if (!hl.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))) continue;
            return pos;
        }
        return null;
    }

    private static void spawnMob(ServerLevel hl, HuntState h, EntityType<?> type, Vec3 at, boolean alpha, GuildRank rank, int players,
                                 ServerPlayer target) {
        Entity e = type.create(hl);
        if (!(e instanceof Mob m)) {
            if (e != null) e.discard();
            return;
        }
        m.moveTo(at.x, at.y, at.z, hl.random.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(m, hl, hl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null);
        if (m.isSpawnCancelled()) {
            m.discard();
            return;
        }
        var tag = m.getPersistentData();
        mark(m, h, rank);
        // Cavaleiros criados pelo próprio mob (aranha com esqueleto) também são da área (somem com ela).
        for (Entity p : m.getIndirectPassengers()) if (p instanceof Mob pm) mark(pm, h, rank);
        // ...e a montaria que ele mesmo criou (zumbi bebê na galinha), que já entrou no mundo.
        if (m.getVehicle() instanceof Mob mount) {
            mark(mount, h, rank);
            h.spawned.add(mount.getUUID());
        }
        // Força por rank e por grupo (vida), dano por rank; o Alfa é bem mais duro.
        float hp = 1F + 0.10F * (rank.level() - 1) + 0.35F * (players - 1);
        float dmg = 1F + 0.07F * (rank.level() - 1);
        if (alpha) {
            hp *= 2.6F;
            dmg *= 1.35F;
            tag.putBoolean(TAG_ELITE, true);
            modifier(m, Attributes.SCALE, MOD_SCALE, 0.3);
            modifier(m, Attributes.MOVEMENT_SPEED, MOD_SPEED, 0.1);
            modifier(m, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, 0.5);
            m.setCustomName(Component.translatable(KEY + "alpha", type.getDescription()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            m.setCustomNameVisible(true);
        }
        modifier(m, Attributes.MAX_HEALTH, MOD_HEALTH, hp - 1F);
        modifier(m, Attributes.ATTACK_DAMAGE, MOD_DAMAGE, dmg - 1F);
        m.setHealth(m.getMaxHealth());
        // Mortos-vivos da área usam um capuz de couro verde da Guilda: não queimam ao sol no meio da caçada.
        if (m.getType().is(EntityTypeTags.UNDEAD) && m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            ItemStack hood = new ItemStack(Items.LEATHER_HELMET);
            hood.set(DataComponents.DYED_COLOR, new DyedItemColor(HUNT_LEATHER, false));
            m.setItemSlot(EquipmentSlot.HEAD, hood);
            m.setDropChance(EquipmentSlot.HEAD, 0F);
        }
        if (!hl.tryAddFreshEntityWithPassengers(m)) return;
        h.spawned.add(m.getUUID());
        for (Entity p : m.getIndirectPassengers()) if (p instanceof Mob) h.spawned.add(p.getUUID());
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, alpha ? 20 * 12 : 20 * 3, 0, false, false));
        if (target != null && target.isAlive() && !target.isCreative() && target.level() == hl && m.getType().getCategory() != MobCategory.CREATURE) {
            m.setTarget(target);
        }
        hl.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.02);
        hl.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.0, at.z, alpha ? 30 : 12, 0.5, 0.8, 0.5, 0.05);
        if (alpha) hl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1.2, at.z, 20, 0.5, 0.8, 0.5, 0.2);
        hl.playSound(null, at.x, at.y, at.z, alpha ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE,
                alpha ? 1.2F : 0.7F, alpha ? 0.7F : 1.1F);
    }

    private static void mark(Mob m, HuntState h, GuildRank rank) {
        var tag = m.getPersistentData();
        tag.putString(TAG, h.placeId);
        tag.putString(TAG_ELEMENT, h.element);
        tag.putInt(TAG_RANK, rank.level());
    }

    private static void modifier(Mob m, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id,
                                 double amount) {
        if (Math.abs(amount) < 1.0E-4) return;
        AttributeInstance inst = m.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // =====================================================================================
    //  Marcas
    // =====================================================================================
    @Nullable
    public static String markOf(Entity e) {
        if (!(e instanceof Mob)) return null;
        var t = e.getPersistentData();
        return t.contains(TAG) ? t.getString(TAG) : null;
    }

    private static AABB areaBox(HuntState h) {
        BlockPos c = h.center;
        return new AABB(c.getX() - RADIUS - 24, c.getY() - 32, c.getZ() - RADIUS - 24, c.getX() + RADIUS + 25, c.getY() + 48,
                c.getZ() + RADIUS + 25);
    }

    /** Caixa larga (até onde o dono pode ir sem a área recuar): para varrer marcados que saíram correndo atrás dele. */
    private static AABB wideBox(BlockPos c) {
        int r = LEAVE + 32;
        return new AABB(c.getX() - r, c.getY() - 48, c.getZ() - r, c.getX() + r + 1, c.getY() + 64, c.getZ() + r + 1);
    }

    /**
     * Mobs vivos desta área: pelos UUIDs invocados (O(1), valem mesmo fora da caixa da área) e, quando a lista está vazia ou a
     * cada 10 s, os marcados achados na caixa da área (depois de relogar, a lista começa vazia).
     */
    private static List<Mob> marked(ServerLevel hl, HuntState h) {
        List<Mob> out = new ArrayList<>();
        for (Iterator<UUID> it = h.spawned.iterator(); it.hasNext(); ) {
            Entity e = hl.getEntity(it.next());
            if (e instanceof Mob m && m.isAlive() && h.placeId.equals(markOf(m))) out.add(m);
            else it.remove();
        }
        if (h.spawned.isEmpty() || h.waveAge % 10 == 0) {
            for (Mob m : hl.getEntitiesOfClass(Mob.class, areaBox(h), m -> m.isAlive() && h.placeId.equals(markOf(m)))) {
                if (h.spawned.add(m.getUUID())) out.add(m);
            }
        }
        return out;
    }

    /** Faz sumir (fumaça) os mobs carregados da área. */
    private static int dispel(ServerLevel hl, HuntState h) {
        Set<Mob> list = new LinkedHashSet<>(marked(hl, h));
        list.addAll(hl.getEntitiesOfClass(Mob.class, wideBox(h.center), m -> m.isAlive() && h.placeId.equals(markOf(m))));
        h.spawned.clear();
        for (Mob m : list) {
            hl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            m.discard();
        }
        return list.size();
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    /** A caçada desta área terminou (objetivo cumprido): saque, título e a área some. */
    private static void clear(ServerPlayer sp, GuildData d, ActiveQuest a, GuildQuest q, ServerLevel hl) {
        HuntState h = a.hunt;
        if (h == null) return;
        boolean fought = h.phase == HuntState.FIGHT || h.wave > 0;
        if (fought) {
            int lvl = q.rank().level();
            Item ess = essence(h.element);
            Item arcane = essence("arcane");
            List<ItemStack> loot = new ArrayList<>();
            if (ess != null) loot.add(new ItemStack(ess, 2 + lvl / 2));
            if (arcane != null) loot.add(new ItemStack(arcane, lvl >= 4 ? 2 : 1));
            for (ItemStack s : loot) GuildService.give(sp, s);
            MagicApi.giveXp(sp, 15 * lvl, "hunt");
            MagicApi.addCounter(sp, "guild.hunts_cleared", 1);
            sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.1F);
            Vec3 c = Vec3.atBottomCenterOf(h.center);
            if (sp.level() == hl) {
                hl.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 4.5, c.z, 40, 0.8, 0.6, 0.8, 0.15);
                hl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.0, c.z, 30, 3.0, 0.8, 3.0, 0.0);
                hl.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            if (!loot.isEmpty()) {
                Rewards.toast(sp, Component.translatable(KEY + "cleared_toast"), placeName(h), loot.get(0), color(h));
            }
            sp.sendSystemMessage(Component.translatable(KEY + "cleared", placeName(h)).withStyle(ChatFormatting.GREEN));
        } else {
            sp.sendSystemMessage(Component.translatable(KEY + "done_elsewhere", placeName(h)).withStyle(ChatFormatting.GRAY));
        }
        finish(sp, d, a, Reason.CLEARED);
    }

    /** Apaga a área da missão (se houver) com o motivo dado. */
    public static void finish(@Nullable ServerPlayer sp, GuildData d, ActiveQuest a, Reason reason) {
        HuntState h = a.hunt;
        if (h == null) return;
        a.hunt = null;
        d.markDirty();
        if (sp != null) {
            remove(sp.server, h.placeId);
            MagicApi.get(sp).setFlag("visit.place." + h.placeId, false);
            if (reason == Reason.EXPIRED) {
                sp.sendSystemMessage(Component.translatable(KEY + "expired", placeName(h)).withStyle(ChatFormatting.GRAY));
            } else if (reason == Reason.ABANDONED || reason == Reason.TURNED_IN) {
                if (h.phase == HuntState.FIGHT || h.wave > 0) {
                    sp.displayClientMessage(Component.translatable(KEY + "removed", placeName(h)).withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
    }

    /** Remove tudo de uma área: lugar, registro e mobs carregados (o estandarte se recolhe sozinho). */
    public static void remove(MinecraftServer server, String placeId) {
        HuntRegistry reg = HuntRegistry.of(server);
        HuntRegistry.Entry e = reg.get(placeId);
        reg.remove(placeId);
        liveGrounds = reg.size();
        Places.Place p = Places.get(server.overworld(), placeId);
        if (p != null) {
            ServerLevel pl = server.getLevel(p.dim());
            if (pl != null) Places.remove(pl, placeId);
        }
        if (e == null) return;
        ServerLevel hl = server.getLevel(e.dim());
        if (hl == null) return;
        // Os lugares são guardados por nível: uma área fora do Overworld sai do nível dela (o letreiro some na hora).
        if (Places.get(hl, placeId) != null) Places.remove(hl, placeId);
        AABB box = wideBox(e.center());
        for (Mob m : hl.getEntitiesOfClass(Mob.class, box, m -> placeId.equals(markOf(m)))) {
            hl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            m.discard();
        }
        for (HuntBanner b : hl.getEntitiesOfClass(HuntBanner.class, box, b -> placeId.equals(b.placeId()))) b.fold();
    }

    public static boolean isLive(MinecraftServer server, String placeId) {
        return !placeId.isEmpty() && HuntRegistry.of(server).has(placeId);
    }

    // =====================================================================================
    //  Clique no estandarte
    // =====================================================================================
    public static void describe(ServerPlayer sp, String placeId) {
        MinecraftServer server = sp.server;
        HuntRegistry.Entry e = HuntRegistry.of(server).get(placeId);
        if (e == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(e.owner());
        HuntState h = owner == null ? null : stateOf(owner, placeId);
        Component name = h != null ? placeName(h) : Component.translatable(KEY + "creatures");
        MutableComponent msg = Component.literal("⚑ ").append(name);
        if (h != null) {
            msg.append(" — ").append(h.phase == HuntState.FIGHT
                    ? Component.translatable(KEY + "status_fight", Math.max(1, h.wave))
                    : Component.translatable(KEY + "status_calm"));
        }
        if (owner != null && owner != sp) msg.append(" ").append(Component.translatable(KEY + "status_owner", owner.getDisplayName()));
        long left = Math.max(0, e.expires() - server.overworld().getGameTime());
        msg.append(" ").append(Component.translatable(KEY + "status_left", left / 1200));
        sp.displayClientMessage(msg.withStyle(ChatFormatting.GOLD), true);
    }

    @Nullable
    private static HuntState stateOf(ServerPlayer owner, String placeId) {
        GuildData d = GuildService.data(owner);
        for (ActiveQuest a : d.active()) if (a.hunt != null && a.hunt.placeId.equals(placeId)) return a.hunt;
        ActiveQuest t = d.trial();
        return t != null && t.hunt != null && t.hunt.placeId.equals(placeId) ? t.hunt : null;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** Essências do elemento da área (e arcanas) caem dos mobs da área abatidos por jogadores. */
    public static void onDrops(LivingDropsEvent e) {
        LivingEntity v = e.getEntity();
        String mark = markOf(v);
        if (mark == null || !(v.level() instanceof ServerLevel sl)) return;
        // Só abates do caçador e do grupo dele (jogador, pet, invocação, companheiro): briga entre mobs não rende essência.
        Entity killer = e.getSource().getEntity();
        if (!e.isRecentlyHit() && (killer == null || creditor(sl.getServer(), killer) == null)) return;
        var tag = v.getPersistentData();
        boolean alpha = tag.getBoolean(TAG_ELITE);
        int rank = Math.max(1, tag.getInt(TAG_RANK));
        RandomSource r = sl.random;
        Item ess = essence(tag.getString(TAG_ELEMENT));
        Item arcane = essence("arcane");
        int nEss = alpha ? 2 + r.nextInt(2) : (r.nextFloat() < 0.5F + 0.05F * rank ? 1 : 0);
        int nArc = alpha ? 1 : (r.nextFloat() < 0.15F + 0.03F * rank ? 1 : 0);
        if (ess != null && nEss > 0) e.getDrops().add(new ItemEntity(sl, v.getX(), v.getY() + 0.5, v.getZ(), new ItemStack(ess, nEss)));
        if (arcane != null && nArc > 0) e.getDrops().add(new ItemEntity(sl, v.getX(), v.getY() + 0.5, v.getZ(), new ItemStack(arcane, nArc)));
        if (nEss + nArc > 0) sl.sendParticles(ParticleTypes.ENCHANT, v.getX(), v.getY() + 0.8, v.getZ(), 12, 0.3, 0.4, 0.3, 0.6);
    }

    /**
     * Abate de um mob da área: se quem matou não foi o dono (outro jogador, companheiro contratado, invocação, pet), o abate
     * conta para a missão do dono — caçar em grupo funciona. Conta {@code guild.hunt_kills}.
     */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        String mark = markOf(v);
        if (mark == null || !(v.level() instanceof ServerLevel sl)) return;
        HuntRegistry.Entry entry = HuntRegistry.of(sl.getServer()).get(mark);
        if (entry == null) return;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(entry.owner());
        if (owner == null || owner.level() != sl || horizontal(owner.position(), entry.center()) > LEAVE) return;
        Entity direct = e.getSource().getEntity();
        // Sem autor direto (queda, lava, fogo, cacto depois de um golpe): vale quem bateu por último (até 5 s antes).
        Entity killer = direct != null ? direct : v.getLastHurtByMob();
        if (killer == null) return;
        // Outro mob (briga entre criaturas, creeper da área) ou máquina: não conta para ninguém.
        if (killer != owner && creditor(sl.getServer(), killer) == null) return;
        MagicApi.addCounter(owner, "guild.hunt_kills", 1);
        // O núcleo já conta o abate DIRETO do próprio dono (kill.<mob>): não contar duas vezes.
        if (direct == owner) return;
        // Companheiro do próprio dono perto dele: o CompanionService já deu o abate (não contar duas vezes).
        if (direct instanceof AdventurerEntity adv && owner.getUUID().equals(adv.getOwnerUUID())
                && owner.distanceToSqr(adv) < COMPANION_ASSIST * COMPANION_ASSIST) return;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(v.getType());
        MagicApi.addCounter(owner, QuestObjective.killKey(type), 1);
    }

    /** Mesmo raio do CompanionService: lá o abate do companheiro perto do dono já conta para o dono. */
    private static final double COMPANION_ASSIST = 48.0;

    /**
     * O jogador "do grupo" por trás de um abate: um jogador de verdade, ou o dono de um bicho domado, invocação, projétil ou
     * companheiro contratado. Null para mobs selvagens, máquinas (FakePlayer) e o ambiente.
     */
    @Nullable
    private static ServerPlayer creditor(MinecraftServer server, Entity killer) {
        if (killer instanceof Player p) return p instanceof ServerPlayer sp && !(p instanceof FakePlayer) ? sp : null;
        UUID owner = null;
        if (killer instanceof OwnableEntity o) owner = o.getOwnerUUID();
        if (owner == null && killer instanceof TraceableEntity t && t.getOwner() != null) owner = t.getOwner().getUUID();
        if (owner == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        return p instanceof FakePlayer ? null : p;
    }

    /** Mobs e estandartes de áreas que já não existem não voltam ao mundo (carregar chunk antigo). */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Entity ent = e.getEntity();
        String mark = ent instanceof HuntBanner b ? b.placeId() : markOf(ent);
        if (mark == null) return;
        if (!HuntRegistry.of(sl.getServer()).has(mark)) e.setCanceled(true);
    }

    /** Na área só nascem as criaturas da missão: nascimentos naturais de monstros lá dentro são barrados. */
    public static void onPositionCheck(MobSpawnEvent.PositionCheck e) {
        if (liveGrounds == 0) return;
        MobSpawnType type = e.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        if (e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (inGround(e.getLevel().getLevel(), e.getEntity().blockPosition())) e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        if (liveGrounds == 0) return;
        MobSpawnType type = e.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.PATROL) return;
        if (e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (inGround(e.getLevel().getLevel(), e.getEntity().blockPosition())) e.setSpawnCancelled(true);
    }

    private static boolean inGround(ServerLevel sl, BlockPos pos) {
        Places.Place p = Places.at(sl, pos);
        return p != null && TYPE.equals(p.type());
    }

    // =====================================================================================
    //  Varredura (1x por minuto): áreas vencidas, órfãs e lugares sem registro
    // =====================================================================================
    public static void onServerTick(ServerTickEvent.Post e) {
        if (--sweepTimer > 0) return;
        sweepTimer = 1200;
        sweep(e.getServer());
    }

    public static int sweep(MinecraftServer server) {
        HuntRegistry reg = HuntRegistry.of(server);
        long now = server.overworld().getGameTime();
        int removed = 0;
        for (Map.Entry<String, HuntRegistry.Entry> en : reg.snapshot()) {
            String id = en.getKey();
            HuntRegistry.Entry entry = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.owner());
            boolean orphan = owner != null && stateOf(owner, id) == null;
            if (now >= entry.expires() || orphan || server.getLevel(entry.dim()) == null) {
                remove(server, id);
                removed++;
            }
        }
        // Lugares de área sem registro (servidor caiu entre um salvamento e outro).
        for (ServerLevel l : server.getAllLevels()) {
            for (Places.Place p : Places.all(l)) {
                if (TYPE.equals(p.type()) && !reg.has(p.id())) {
                    Places.remove(l, p.id());
                    removed++;
                }
            }
        }
        liveGrounds = reg.size();
        return removed;
    }

    /** Servidor parou: zera o estado estático. */
    public static void clearStatic() {
        liveGrounds = -1;
        sweepTimer = 100;
        HuntRules.clear();
    }

    // =====================================================================================
    @Nullable
    private static Item essence(String element) {
        if (element == null || element.isEmpty()) return null;
        Item it = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("essence_" + element)).orElse(Items.AIR);
        return it == Items.AIR ? null : it;
    }

    private HuntingGrounds() {}
}
