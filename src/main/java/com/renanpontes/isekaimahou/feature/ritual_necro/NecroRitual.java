package com.renanpontes.isekaimahou.feature.ritual_necro;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.ritual_necro.block.OssuaryAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_necro.block.OssuaryStructure;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.PhylacteryItem;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRite;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRites;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Regras do Rito Ossuário: checagem (altar, meia-noite, Círculo, almas), início e recompensas.
 * Rank do Necromante = contador {@link #RANK}; o rito em andamento fica em {@link #PENDING} no jogador,
 * então a recompensa nunca se perde (altar quebrado, jogador saiu, servidor caiu).
 */
public final class NecroRitual {
    /** Rank do Rito Ossuário (quantas vezes o jogador completou). */
    public static final String RANK = "ritual_rank.necromancer";
    /** Convenção entre módulos: +1 a cada subida de ritual de classe. */
    public static final String GLOBAL_RANK = "ritual_rank";
    /** Rank em andamento (0 = nenhum). */
    public static final String PENDING = "necro.pending_rank";
    /** Limite de servos mortos-vivos (lido pelo módulo dos servos). */
    public static final String SERVANT_CAP = "necro.servant_cap";
    /** Total de almas colhidas. */
    public static final String SOULS_HARVESTED = "necro.souls";
    /** Marca no NBT persistente das entidades dadas pelo rito (dono). */
    public static final String OWNER_TAG = "isekai_mahou.necro_owner";
    public static final ResourceLocation SERVANT_ID = IsekaiMahou.id("undead_servant");
    public static final int RGB = 0x5FE0E8;
    /** Prefixo das traduções das mensagens do rito (message.isekai_mahou.necro.&lt;chave&gt;). */
    private static final String MSG_PREFIX = "message.isekai_mahou.necro.";

    private static final long MIDNIGHT_START = 17000L;
    private static final long MIDNIGHT_END = 19000L;

    public static int rank(MagicData d) {
        return d.counter(RANK);
    }

    /** Meia-noite: dayTime entre 17000 e 19000 numa dimensão com ciclo de dia. */
    public static boolean isMidnight(Level level) {
        if (level.dimensionType().hasFixedTime()) return false;
        long t = Math.floorMod(level.getDayTime(), 24000L);
        return t >= MIDNIGHT_START && t <= MIDNIGHT_END;
    }

    /** Minutos reais até a próxima meia-noite (o dia do Minecraft dura 20 min). */
    private static int minutesToMidnight(Level level) {
        long t = Math.floorMod(level.getDayTime(), 24000L);
        long until = Math.floorMod(MIDNIGHT_START - t, 24000L);
        return Math.max(1, (int) Math.ceil(until / 1200.0));
    }

    // =====================================================================================
    //  Checagem / início
    // =====================================================================================

    /** Clique no altar com o Filactério: se tudo estiver certo, consome as almas e começa o rito. */
    public static void tryStart(ServerPlayer player, ItemStack phylactery, OssuaryAltarBlockEntity altar) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = altar.getBlockPos();
        if (altar.isActive()) {
            player.displayClientMessage(msg("altar_busy").withStyle(ChatFormatting.RED), true);
            return;
        }
        MagicData d = MagicApi.get(player);
        if (d.counter(PENDING) > 0) {
            // Rito interrompido sem chegar ao fim (ex.: o altar ficou fora dos chunks carregados): conclui agora
            finishPending(player, level, pos);
            return;
        }
        int next = rank(d) + 1;
        NecroRite rite = NecroRites.get(next);
        if (!checklist(player, altar, rite, SoulStore.of(phylactery), true)) {
            level.playSound(null, pos, SoundEvents.SKELETON_HURT, SoundSource.BLOCKS, 0.6F, 0.5F);
            return;
        }

        // Tudo certo: as almas são oferecidas e o rito começa
        phylactery.set(NecroContent.SOULS.get(), SoulStore.of(phylactery).remove(rite.souls()));
        MagicApi.addCounter(player, PENDING, next);
        altar.start(player, next);

        Component title = msg("rite_title", roman(next));
        Component subtitle = msg("rite_subtitle");
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(player) <= 24 * 24) {
                Cutscenes.play(viewer, "ascension", player, title, subtitle, RGB, OssuaryAltarBlockEntity.RITUAL_TICKS, false);
            }
        }
        player.sendSystemMessage(msg("rite_begin", roman(next)).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 1.0, 0);
        level.sendParticles(Vfx.soul(), c.x, c.y, c.z, 20, 0.4, 0.3, 0.4, 0.03);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 0.5F);
    }

    /** Clique com a mão vazia: mostra a lista de checagem com as almas do melhor Filactério do inventário. */
    public static void showStatus(ServerPlayer player, OssuaryAltarBlockEntity altar) {
        if (altar.isActive()) {
            player.displayClientMessage(msg("altar_busy").withStyle(ChatFormatting.DARK_AQUA), true);
            return;
        }
        MagicData d = MagicApi.get(player);
        NecroRite rite = NecroRites.get(rank(d) + 1);
        ItemStack phyl = PhylacteryItem.find(player, true);
        boolean ok = checklist(player, altar, rite, phyl.isEmpty() ? null : SoulStore.of(phyl), false);
        if (ok) player.sendSystemMessage(msg("ready").withStyle(ChatFormatting.GREEN));
    }

    /**
     * Escreve no chat a lista do rito (✔/✘ para altar, crânios, Círculo, meia-noite e cada alma) e
     * destaca os blocos que faltam. {@code souls} null = jogador sem Filactério. Retorna true se tudo ok.
     */
    private static boolean checklist(ServerPlayer player, OssuaryAltarBlockEntity altar, NecroRite rite, SoulStore souls, boolean attempt) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = altar.getBlockPos();
        MagicData d = MagicApi.get(player);
        int next = rank(d) + 1;
        boolean ok = true;

        if (rite == null) {
            player.sendSystemMessage(msg("header_done", roman(rank(d))).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
            player.sendSystemMessage(msg(NecroRites.isEmpty() ? "no_rites" : "max_rank").withStyle(ChatFormatting.GRAY));
            ok = false;
        } else {
            player.sendSystemMessage(msg("header", roman(next)).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
        }

        // Altar e multibloco
        OssuaryStructure.Report rep = OssuaryStructure.check(level, pos);
        if (rep.complete()) {
            player.sendSystemMessage(line(true, msg("structure_ok")));
        } else {
            ok = false;
            if (!rep.missingRing().isEmpty()) player.sendSystemMessage(line(false, msg("missing_ring", rep.missingRing().size())));
            if (!rep.missingCorners().isEmpty()) {
                player.sendSystemMessage(line(false, msg("missing_corners", rep.missingCorners().size())));
                player.sendSystemMessage(msg("corner_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            altar.highlight(player, rep.missingRing(), rep.missingCorners());
        }
        if (rite != null && rite.requiredSkulls() > 0) {
            boolean skullsOk = rep.skulls() >= rite.requiredSkulls();
            player.sendSystemMessage(line(skullsOk, msg("skulls", rep.skulls(), rite.requiredSkulls())));
            if (!skullsOk) {
                ok = false;
                if (rep.complete()) altar.highlight(player, List.of(), rep.cornersWithoutSkull());
            }
        }

        if (rite != null) {
            // Círculo mínimo
            if (rite.minCircle() > 1) {
                boolean circleOk = d.circle() >= rite.minCircle();
                player.sendSystemMessage(line(circleOk, msg("circle", d.circle(), rite.minCircle())));
                ok &= circleOk;
            }
            // Meia-noite
            if (isMidnight(level)) {
                player.sendSystemMessage(line(true, msg("midnight_now")));
            } else {
                ok = false;
                player.sendSystemMessage(line(false, level.dimensionType().hasFixedTime() ? msg("no_moon")
                        : msg("not_midnight", minutesToMidnight(level))));
            }
            // Almas
            if (souls == null) {
                ok = false;
                player.sendSystemMessage(line(false, msg("no_phylactery")));
            } else {
                boolean all = souls.has(rite.souls());
                player.sendSystemMessage(line(all, msg("souls_header")));
                for (Map.Entry<ResourceLocation, Integer> e : rite.souls().entrySet()) {
                    int have = souls.count(e.getKey());
                    boolean enough = have >= e.getValue();
                    player.sendSystemMessage(Component.literal("   ").append(msg("soul_line", soulName(e.getKey()),
                            Math.min(have, e.getValue()), e.getValue()).withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED)));
                }
                ok &= all;
            }
            if (attempt && !ok) player.displayClientMessage(msg("not_ready").withStyle(ChatFormatting.RED), true);
        }
        return ok;
    }

    private static Component line(boolean ok, MutableComponent text) {
        return Component.literal(ok ? " ✔ " : " ✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(text.withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.WHITE));
    }

    // =====================================================================================
    //  Conclusão
    // =====================================================================================

    /**
     * Entrega o rito em andamento (se houver). {@code altarLevel/altarPos} = onde o efeito acontece; null = no jogador.
     */
    public static void finishPending(ServerPlayer player, ServerLevel altarLevel, BlockPos altarPos) {
        MagicData d = MagicApi.get(player);
        int pending = d.counter(PENDING);
        if (pending <= 0) return;
        MagicApi.addCounter(player, PENDING, -pending);
        NecroRite rite = NecroRites.get(pending);
        complete(player, rite != null ? rite : NecroRite.fallback(pending), altarLevel, altarPos);
    }

    private static void complete(ServerPlayer player, NecroRite rite, ServerLevel altarLevel, BlockPos altarPos) {
        ServerLevel level = player.serverLevel();
        boolean atAltar = altarPos != null && altarLevel == level && player.blockPosition().closerThan(altarPos, 48);
        Vec3 focus = atAltar ? Vec3.atBottomCenterOf(altarPos).add(0, 1.0, 0) : player.position().add(0, 1.0, 0);

        MagicApi.addCounter(player, RANK, 1);
        MagicApi.addCounter(player, GLOBAL_RANK, 1);
        player.sendSystemMessage(msg("rite_complete", roman(rite.rank())).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));

        MagicApi.giveXp(player, rite.xp(), "ritual");

        // Despertar dos elementos de afinidade do Necromante (Sombra no 1º, Terra no 3º — vem do arquivo)
        if (rite.awaken() != null && !MagicApi.awakenElement(player, rite.awaken())) {
            MagicApi.giveElementXp(player, rite.awaken(), 60);
        }
        for (Map.Entry<Element, Integer> e : rite.elementXp().entrySet()) MagicApi.giveElementXp(player, e.getKey(), e.getValue());

        // Glifos de Necromancia até o tier do rank (cobre glifos que ficaram para trás)
        MagicData d = MagicApi.get(player);
        int taught = 0;
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() != MagicPath.NECROMANCER || g.tier() > rite.glyphTier() || d.knowsGlyph(g.id())) continue;
            if (MagicApi.learnGlyph(player, g.id())) {
                taught++;
                // A barra de ação só mostra o último: o chat guarda a lista do que o rito ensinou
                player.sendSystemMessage(msg("glyph_learned", g.displayName()).withStyle(ChatFormatting.AQUA));
            }
        }
        if (taught == 0) player.sendSystemMessage(msg("no_glyph").withStyle(ChatFormatting.GRAY));

        if (rite.servantCap() > 0) {
            // O contador é o BÔNUS de vagas (o limite base fica com o módulo dos servos)
            int bonus = MagicApi.addCounter(player, SERVANT_CAP, rite.servantCap());
            player.sendSystemMessage(msg("servant_cap", rite.servantCap(), bonus).withStyle(ChatFormatting.DARK_AQUA));
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, true));

        ServerLevel fxLevel = atAltar ? altarLevel : level;
        fxLevel.sendParticles(Vfx.soulFlame(), focus.x, focus.y, focus.z, 50, 0.4, 1.2, 0.4, 0.05);
        fxLevel.sendParticles(Vfx.soul(), focus.x, focus.y, focus.z, 25, 0.8, 0.8, 0.8, 0.04);
        fxLevel.playSound(null, focus.x, focus.y, focus.z, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F);
        fxLevel.playSound(null, focus.x, focus.y, focus.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);

        if (rite.servantGift() > 0) giftServants(player, rite.servantGift(), fxLevel, atAltar ? altarPos : player.blockPosition());
    }

    /**
     * Servos de presente: só se a entidade isekai_mahou:undead_servant existir (módulo dos servos).
     * Eles são permanentes e ocupam as vagas EXTRAS liberadas pelos ritos ({@link #SERVANT_CAP}); os presentes
     * ainda vivos contam, então as vagas base continuam livres para Erguer Morto e um rito só repõe os que caíram.
     */
    private static void giftServants(ServerPlayer player, int wanted, ServerLevel level, BlockPos center) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(SERVANT_ID);
        if (type.isEmpty()) return;
        int alive = giftedAlive(player, type.get());
        int count = Math.min(wanted, MagicApi.get(player).counter(SERVANT_CAP) - alive);
        if (count <= 0) {
            player.sendSystemMessage(msg("servant_gift_full", alive).withStyle(ChatFormatting.GRAY));
            return;
        }
        int spawned = 0;
        int[][] spots = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 2}, {0, -2}, {2, 0}, {-2, 0}};
        for (int i = 0; i < count; i++) {
            BlockPos at = freeSpot(level, center, spots[i % spots.length]);
            Entity e = type.get().spawn(level, ent -> bindOwner(ent, player), at, MobSpawnType.MOB_SUMMONED, false, false);
            if (e == null) continue;
            spawned++;
            level.sendParticles(Vfx.soulFlame(), e.getX(), e.getY() + 0.8, e.getZ(), 25, 0.3, 0.6, 0.3, 0.03);
            level.sendParticles(Vfx.sculkSoul(), e.getX(), e.getY() + 0.2, e.getZ(), 8, 0.4, 0.1, 0.4, 0.02);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.6F);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BONE_BLOCK_PLACE, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        if (spawned > 0) player.sendSystemMessage(msg("servant_gift", spawned).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Servos de presente deste jogador ainda de pé (em qualquer dimensão carregada). */
    private static int giftedAlive(ServerPlayer player, EntityType<?> type) {
        int n = 0;
        for (ServerLevel l : player.server.getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (e.getType() != type || !e.isAlive()) continue;
                CompoundTag tag = e.getPersistentData();
                if (tag.hasUUID(OWNER_TAG) && player.getUUID().equals(tag.getUUID(OWNER_TAG))) n++;
            }
        }
        return n;
    }

    private static BlockPos freeSpot(ServerLevel level, BlockPos center, int[] off) {
        BlockPos p = center.offset(off[0], 0, off[1]);
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                    && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) return q;
        }
        return center.above();
    }

    /**
     * Liga a criatura ao jogador: domável → tame; senão tenta setOwnerUUID(UUID)/setOwner(jogador) do módulo dos servos,
     * e sempre grava o dono no NBT persistente ({@link #OWNER_TAG}) como convenção.
     */
    private static void bindOwner(Entity entity, ServerPlayer player) {
        entity.getPersistentData().putUUID(OWNER_TAG, player.getUUID());
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(player);
            return;
        }
        for (Method m : entity.getClass().getMethods()) {
            if (m.getParameterCount() != 1) continue;
            Class<?> param = m.getParameterTypes()[0];
            try {
                if (m.getName().equals("setOwnerUUID") && param == UUID.class) {
                    m.invoke(entity, player.getUUID());
                    return;
                }
                if (m.getName().equals("setOwner") && param.isAssignableFrom(ServerPlayer.class) && LivingEntity.class.isAssignableFrom(param)) {
                    m.invoke(entity, player);
                    return;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                IsekaiMahou.LOGGER.debug("Servo do Rito Ossuario: nao deu para definir o dono por {}", m, ex);
            }
        }
    }

    // =====================================================================================
    //  Textos
    // =====================================================================================
    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(MSG_PREFIX + key, args);
    }

    /** Nome traduzido do mob de uma alma (id desconhecido vira um texto de "alma estranha"). */
    public static MutableComponent soulName(ResourceLocation type) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(type)
                .map(t -> t.getDescription().copy())
                .orElseGet(() -> Component.translatable("tooltip.isekai_mahou.phylactery.unknown_soul", type.toString()));
    }

    /** Rank em algarismos romanos (I..X). */
    public static String roman(int n) {
        String[] r = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private NecroRitual() {}
}
