package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.balance.MobScaling;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * As "sacanagens piores que a morte" do Dragão Primordial, que ficam nos JOGADORES depois do golpe (servidor):
 * <ul>
 *   <li><b>Estátua</b> — Petrificado (pedra do núcleo, amplificador 2) + Atordoado + Silenciado pelo tempo todo; a pedra
 *       DRENA 2% da vida (nunca abaixo de 10%) e 5% do recurso por segundo, e cada estátua cura o dragão. Leite e
 *       purificações não adiantam (a pedra volta). Um ALIADO agachado ao lado por 2 s racha a pedra.</li>
 *   <li><b>Maldição do Esquecimento</b> — 35% das magias conjuradas somem da memória (perde metade do custo e o slot
 *       recarrega 3 s). Um aliado agachado ao lado por 2 s quebra a maldição.</li>
 *   <li><b>Loadout Apagado</b> — os 4 slots do loadout ativo entram em recarga (troque de loadout!).</li>
 *   <li><b>Essência Drenada</b> — −8% de Poder Mágico e de dano físico por acúmulo (máx. 5) por um tempo.</li>
 *   <li><b>Ruína</b> — −15% de vida máxima por acúmulo (máx. 3) até o fim da luta.</li>
 * </ul>
 * Nada aqui mata de uma vez. Modificadores são TRANSITÓRIOS (não vão para o save). Tudo some com a morte do dragão,
 * com a morte/saída do jogador e ao parar o servidor. Custo: só roda quando há alguém afligido.
 */
public final class DragonCurses {
    public static final int RESCUE_TICKS = 40;
    public static final double RESCUE_RANGE = 2.6;
    public static final int MAX_DRAIN = 5;
    public static final int MAX_RUIN = 3;
    /** Depois de sair da pedra, 3 s sem virar estátua de novo (nada de "trava eterna"). */
    public static final int STATUE_IMMUNITY = 60;
    private static final float CURSE_CHANCE = 0.35F;
    private static final ResourceLocation DRAIN_ID = IsekaiMahou.id("primordial_drain");
    private static final ResourceLocation RUIN_ID = IsekaiMahou.id("primordial_ruin");
    private static final String MSG = "message.isekai_mahou.primordial_dragon.curse.";

    /** Jogador afligido (por UUID). */
    static final class Afflicted {
        @Nullable UUID dragon;
        long statueUntil;
        long statueImmuneUntil;
        long curseUntil;
        long drainUntil;
        int drainStacks;
        long ruinUntil;
        int ruinStacks;
        long eraseUntil;
        int erasedLoadout = -1;

        boolean empty(long now) {
            return statueUntil <= now && curseUntil <= now && drainStacks <= 0 && ruinStacks <= 0 && eraseUntil <= now
                    && statueImmuneUntil <= now;
        }
    }

    private static final Map<UUID, Afflicted> STATE = new HashMap<>();
    /** Quem está resgatando: socorrista → [alvo, progresso]. */
    private static final Map<UUID, Rescue> RESCUE = new HashMap<>();

    private record Rescue(UUID target, int progress) {}

    private static Afflicted of(LivingEntity e) {
        return STATE.computeIfAbsent(e.getUUID(), k -> new Afflicted());
    }

    // =====================================================================================
    //  Aplicar
    // =====================================================================================

    /**
     * Transforma em ESTÁTUA por {@code ticks}. Jogadores ficam no gerenciador (dreno, resgate, a pedra não sai com
     * leite); outras criaturas só recebem os efeitos.
     */
    public static void statue(LivingEntity e, int ticks, @Nullable PrimordialDragon by) {
        if (!(e.level() instanceof ServerLevel sl) || !e.isAlive()) return;
        if (e instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) return;
        Afflicted prev = STATE.get(e.getUUID());
        if (prev != null && prev.statueImmuneUntil > sl.getGameTime() && prev.statueUntil <= sl.getGameTime()) {
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + 1, e.getZ(), 12, 0.4, 0.8, 0.4, 0.1);
            return;
        }
        stoneEffects(e, ticks);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), e.getX(), e.getY() + 1, e.getZ(),
                40, 0.4, 0.8, 0.4, 0.1);
        sl.sendParticles(ParticleTypes.WHITE_ASH, e.getX(), e.getY() + 1, e.getZ(), 20, 0.5, 1, 0.5, 0.02);
        sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.HOSTILE, 1.4F, 0.5F);
        sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6F, 1.6F);
        if (!(e instanceof ServerPlayer sp)) return;
        Afflicted a = of(sp);
        a.statueUntil = Math.max(a.statueUntil, sl.getGameTime() + ticks);
        if (by != null) a.dragon = by.getUUID();
        sp.displayClientMessage(Component.translatable(MSG + "statue", ticks / 20).withStyle(ChatFormatting.GRAY), true);
        sp.sendSystemMessage(Component.translatable(MSG + "statue_chat").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void stoneEffects(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(CoreRegistries.PETRIFIED, ticks, 2, false, true, true));
        e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, ticks, 0, false, false, true));
        e.addEffect(new MobEffectInstance(CoreRegistries.SILENCED, ticks, 0, false, false, true));
    }

    public static boolean isStatue(LivingEntity e) {
        Afflicted a = STATE.get(e.getUUID());
        return a != null && a.statueUntil > e.level().getGameTime();
    }

    /** Maldição do Esquecimento por {@code ticks}. */
    public static void curse(ServerPlayer p, int ticks, @Nullable PrimordialDragon by) {
        if (p.isCreative() || p.isSpectator()) return;
        Afflicted a = of(p);
        a.curseUntil = Math.max(a.curseUntil, p.level().getGameTime() + ticks);
        if (by != null) a.dragon = by.getUUID();
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 2, p.getZ(), 16, 0.4, 0.3, 0.4, 0.02);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8F, 0.7F);
        p.displayClientMessage(Component.translatable(MSG + "forget", ticks / 20).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    public static boolean isCursed(LivingEntity e) {
        Afflicted a = STATE.get(e.getUUID());
        return a != null && a.curseUntil > e.level().getGameTime();
    }

    /**
     * Apaga o loadout ATIVO por {@code ticks}: os 4 slots entram em recarga (o cliente mostra a recarga). Devolve o
     * número do loadout apagado (1..9) ou 0.
     */
    public static int eraseLoadout(ServerPlayer p, int ticks, @Nullable PrimordialDragon by) {
        if (p.isCreative() || p.isSpectator()) return 0;
        MagicData d = MagicApi.get(p);
        int l = d.activeLoadout();
        long now = p.serverLevel().getGameTime();
        for (int s = 0; s < Progression.SLOTS; s++) {
            String key = MagicData.slotKey(l, s);
            long until = Math.max(d.cooldowns.getOrDefault(key, 0L), now + ticks);
            d.cooldowns.put(key, until);
            PacketDistributor.sendToPlayer(p, new CorePayloads.Cooldown(key, (int) (until - now)));
        }
        Afflicted a = of(p);
        a.eraseUntil = Math.max(a.eraseUntil, now + ticks);
        a.erasedLoadout = l;
        if (by != null) a.dragon = by.getUUID();
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.6, p.getZ(), 60, 0.5, 0.5, 0.5, 1.2);
        sl.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.6, p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.5F, 0.5F);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.6F);
        p.sendSystemMessage(Component.translatable(MSG + "erased", l + 1, ticks / 20).withStyle(ChatFormatting.DARK_PURPLE));
        return l + 1;
    }

    /** Essência Drenada: +{@code add} acúmulos (máx. {@link #MAX_DRAIN}), renovando a duração. */
    public static void drainEssence(LivingEntity e, int add, int ticks, @Nullable PrimordialDragon by) {
        // Só jogadores: o gerenciador acompanha (e tira) os modificadores pela lista de jogadores.
        if (!(e instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return;
        Afflicted a = of(e);
        int before = a.drainStacks;
        a.drainStacks = Math.min(MAX_DRAIN, a.drainStacks + add);
        a.drainUntil = Math.max(a.drainUntil, e.level().getGameTime() + ticks);
        if (by != null) a.dragon = by.getUUID();
        applyDrain(e, a.drainStacks);
        if (a.drainStacks != before) {
            sp.displayClientMessage(Component.translatable(MSG + "drained", a.drainStacks * 8).withStyle(ChatFormatting.RED), true);
        }
    }

    /** Ruína: −15% de vida máxima por acúmulo (máx. {@link #MAX_RUIN}) até o fim da luta (ou {@code ticks}). */
    public static void ruin(LivingEntity e, int add, int ticks, @Nullable PrimordialDragon by) {
        if (!(e instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return;
        Afflicted a = of(e);
        a.ruinStacks = Math.min(MAX_RUIN, a.ruinStacks + add);
        a.ruinUntil = Math.max(a.ruinUntil, e.level().getGameTime() + ticks);
        if (by != null) a.dragon = by.getUUID();
        applyRuin(e, a.ruinStacks);
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + 1.2, e.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
            sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 0.8F, 0.6F);
        }
        sp.displayClientMessage(Component.translatable(MSG + "ruin", a.ruinStacks * 15).withStyle(ChatFormatting.DARK_RED), true);
    }

    /** Tira {@code frac} do recurso máximo (Mana/Vigor/Foco). Devolve quanto tirou. */
    public static float drainResource(ServerPlayer p, float frac) {
        if (p.isCreative() || p.isSpectator()) return 0F;
        MagicData d = MagicApi.get(p);
        float take = Math.min(d.mana(), d.maxMana() * frac);
        if (take > 0) d.setMana(d.mana() - take);
        return take;
    }

    /** Tira vida "de dreno" (magia do dragão), sem passar de {@code floorFrac} da vida máxima. Devolve o que tirou. */
    public static float drainHealth(LivingEntity e, float frac, float floorFrac, Entity source) {
        float room = e.getHealth() - e.getMaxHealth() * floorFrac;
        float amount = Math.min(room, e.getMaxHealth() * frac);
        if (amount <= 0.05F) return 0F;
        float before = e.getHealth();
        int inv = e.invulnerableTime;
        e.invulnerableTime = 0;
        boolean owned = source != null && source != e;
        // O MobScaling da v1.0 multiplica o dano não-mágico das criaturas do mod (×1,5): o dreno já é uma fração exata
        // da vida, então desconta a escala para nunca furar o piso de {@code floorFrac}.
        if (owned) amount /= Math.max(1F, MobScaling.damageScale(source));
        // Sem o dragão (descarregado) a fonte seria o próprio jogador — o PvP desligado barraria: vira magia "sem dono".
        e.hurt(owned ? e.damageSources().indirectMagic(source, source) : e.damageSources().magic(), amount);
        e.invulnerableTime = Math.max(e.invulnerableTime, inv);
        return Math.max(0F, before - e.getHealth());
    }

    private static void applyDrain(LivingEntity e, int stacks) {
        setModifier(e.getAttribute(CoreRegistries.SPELL_POWER), DRAIN_ID, stacks <= 0 ? 0 : -0.08 * stacks);
        setModifier(e.getAttribute(Attributes.ATTACK_DAMAGE), DRAIN_ID, stacks <= 0 ? 0 : -0.08 * stacks);
    }

    private static void applyRuin(LivingEntity e, int stacks) {
        setModifier(e.getAttribute(Attributes.MAX_HEALTH), RUIN_ID, stacks <= 0 ? 0 : -0.15 * stacks);
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
    }

    private static void setModifier(@Nullable AttributeInstance attr, ResourceLocation id, double value) {
        if (attr == null) return;
        if (value == 0) {
            attr.removeModifier(id);
        } else {
            attr.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Remove o efeito (se houver) sem passar pela purificação do núcleo. */
    private static void removeEffect(LivingEntity e, Holder<MobEffect> eff) {
        if (e.hasEffect(eff)) e.removeEffect(eff);
    }

    // =====================================================================================
    //  Feitiços do jogador: a Maldição do Esquecimento
    // =====================================================================================

    /**
     * Chamado no {@code SpellCastEvent.Pre} de um jogador amaldiçoado: 35% de chance de a magia SUMIR (cancelar). Perde
     * metade da mana que a magia custaria e o slot recarrega 3 s. @return true se cancelou
     */
    public static boolean forgetCast(ServerPlayer p, float manaCost) {
        if (!isCursed(p) || p.getRandom().nextFloat() >= CURSE_CHANCE) return false;
        MagicData d = MagicApi.get(p);
        if (manaCost > 0) d.setMana(Math.max(0, d.mana() - manaCost * 0.5F));
        if (d.lastCastSlot >= 0 && d.lastCastSlot < Progression.SLOTS) {
            String key = MagicData.slotKey(d.activeLoadout(), d.lastCastSlot);
            long now = p.serverLevel().getGameTime();
            d.cooldowns.put(key, Math.max(d.cooldowns.getOrDefault(key, 0L), now + 60));
            PacketDistributor.sendToPlayer(p, new CorePayloads.Cooldown(key, 60));
        }
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.7, p.getZ(), 14, 0.3, 0.2, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.9, p.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
        p.displayClientMessage(Component.translatable(MSG + "forgot").withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    // =====================================================================================
    //  Tick (servidor)
    // =====================================================================================

    public static void tick(MinecraftServer server) {
        if (STATE.isEmpty()) {
            if (!RESCUE.isEmpty()) RESCUE.clear();
            return;
        }
        int tick = server.getTickCount();
        Iterator<Map.Entry<UUID, Afflicted>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Afflicted> me = it.next();
            Afflicted a = me.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(me.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            long now = p.level().getGameTime();
            tickOne(p, a, now, tick);
            if (a.empty(now)) it.remove();
        }
        if (tick % 2 == 0) tickRescue(server);
    }

    private static void tickOne(ServerPlayer p, Afflicted a, long now, int tick) {
        ServerLevel sl = p.serverLevel();
        // ---- Estátua ----
        if (a.statueUntil > 0) {
            if (now >= a.statueUntil) {
                a.statueUntil = 0;
                a.statueImmuneUntil = now + STATUE_IMMUNITY;
                endStone(p, false);
            } else {
                int left = (int) (a.statueUntil - now);
                // A pedra não sai com leite nem purificação: volta na hora.
                if (!p.hasEffect(CoreRegistries.PETRIFIED) || p.getEffect(CoreRegistries.PETRIFIED).getAmplifier() < 2) stoneEffects(p, left);
                if (tick % 20 == 0) {
                    PrimordialDragon d = dragonOf(sl, a);
                    Entity src = d != null ? d : p;
                    float took = drainHealth(p, 0.02F, 0.10F, src);
                    float mana = drainResource(p, 0.05F);
                    if (d != null && (took > 0 || mana > 0)) d.healEffectiveFraction(0.002F);
                    sl.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1, p.getZ(), 10, 0.35, 0.8, 0.35, 0.01);
                    if (d != null) {
                        DragonFx.line(sl, DragonFx.dust(0x9A948A, 1.0F), p.position().add(0, 1.2, 0), d.mouthPos(), 2.2);
                    }
                }
                if (tick % 40 == 0) {
                    p.displayClientMessage(Component.translatable(MSG + "statue", Math.max(1, left / 20)).withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
        // ---- Maldição ----
        if (a.curseUntil > 0) {
            if (now >= a.curseUntil) {
                a.curseUntil = 0;
                p.displayClientMessage(Component.translatable(MSG + "forget_end").withStyle(ChatFormatting.AQUA), true);
            } else if (tick % 10 == 0) {
                double ang = tick * 0.3;
                sl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() + Math.cos(ang) * 0.5, p.getY() + 2.15, p.getZ() + Math.sin(ang) * 0.5,
                        1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 2.0, p.getZ(), 4, 0.3, 0.1, 0.3, 0.5);
            }
        }
        // ---- Loadout apagado ----
        if (a.eraseUntil > 0 && now >= a.eraseUntil) {
            a.eraseUntil = 0;
            if (a.erasedLoadout >= 0) {
                p.displayClientMessage(Component.translatable(MSG + "erased_end", a.erasedLoadout + 1).withStyle(ChatFormatting.AQUA), true);
            }
            a.erasedLoadout = -1;
        }
        // ---- Essência drenada ----
        if (a.drainStacks > 0) {
            if (now >= a.drainUntil) {
                a.drainStacks = 0;
                applyDrain(p, 0);
                p.displayClientMessage(Component.translatable(MSG + "drained_end").withStyle(ChatFormatting.AQUA), true);
            } else if (tick % 40 == 0) {
                applyDrain(p, a.drainStacks); // volta depois de renascer/trocar de dimensão
                sl.sendParticles(DragonFx.dust(0xC0182E, 1.0F), p.getX(), p.getY() + 1, p.getZ(), 3 + a.drainStacks, 0.3, 0.6, 0.3, 0);
            }
        }
        // ---- Ruína ----
        if (a.ruinStacks > 0) {
            if (now >= a.ruinUntil) {
                a.ruinStacks = 0;
                applyRuin(p, 0);
                p.displayClientMessage(Component.translatable(MSG + "ruin_end").withStyle(ChatFormatting.AQUA), true);
            } else if (tick % 40 == 0) {
                applyRuin(p, a.ruinStacks);
            }
        }
    }

    private static void endStone(ServerPlayer p, boolean rescued) {
        removeEffect(p, CoreRegistries.PETRIFIED);
        removeEffect(p, CoreRegistries.STUNNED);
        removeEffect(p, CoreRegistries.SILENCED);
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), p.getX(), p.getY() + 1, p.getZ(),
                50, 0.4, 0.9, 0.4, 0.15);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.4F, rescued ? 1.2F : 0.8F);
        p.displayClientMessage(Component.translatable(MSG + (rescued ? "statue_rescued" : "statue_end")).withStyle(ChatFormatting.AQUA), true);
    }

    @Nullable
    private static PrimordialDragon dragonOf(ServerLevel sl, Afflicted a) {
        if (a.dragon == null) return null;
        return sl.getEntity(a.dragon) instanceof PrimordialDragon d && d.isAlive() ? d : null;
    }

    /**
     * Resgate: um jogador AGACHADO a até {@link #RESCUE_RANGE} de um aliado que é estátua ou está amaldiçoado enche a
     * barra em 2 s e quebra a pedra / a maldição. Só socorristas livres (estátuas não socorrem).
     */
    private static void tickRescue(MinecraftServer server) {
        Map<UUID, Rescue> next = new HashMap<>();
        for (Map.Entry<UUID, Afflicted> me : STATE.entrySet()) {
            Afflicted a = me.getValue();
            ServerPlayer victim = server.getPlayerList().getPlayer(me.getKey());
            if (victim == null) continue;
            long now = victim.level().getGameTime();
            boolean stone = a.statueUntil > now;
            boolean cursed = a.curseUntil > now;
            if (!stone && !cursed) continue;
            List<ServerPlayer> near = victim.serverLevel().getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(RESCUE_RANGE),
                    r -> r != victim && r.isAlive() && !r.isSpectator() && r.isShiftKeyDown() && !isStatue(r));
            for (ServerPlayer r : near) {
                if (next.containsKey(r.getUUID())) continue;
                Rescue old = RESCUE.get(r.getUUID());
                int prog = (old != null && old.target().equals(victim.getUUID()) ? old.progress() : 0) + 2;
                if (prog >= RESCUE_TICKS) {
                    free(victim, a, now, stone);
                    r.displayClientMessage(Component.translatable(MSG + "rescue_done", victim.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
                    r.serverLevel().playSound(null, r.getX(), r.getY(), r.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
                    continue;
                }
                next.put(r.getUUID(), new Rescue(victim.getUUID(), prog));
                int pct = prog * 100 / RESCUE_TICKS;
                r.displayClientMessage(Component.translatable(MSG + (stone ? "rescue_stone" : "rescue_curse"), victim.getDisplayName(), pct)
                        .withStyle(ChatFormatting.YELLOW), true);
                if (prog % 8 == 0) {
                    victim.serverLevel().sendParticles(stone ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY() + 1,
                            victim.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
                    victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            stone ? SoundEvents.STONE_HIT : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.8F + pct / 200F);
                }
            }
        }
        RESCUE.clear();
        RESCUE.putAll(next);
    }

    private static void free(ServerPlayer victim, Afflicted a, long now, boolean stone) {
        if (stone) {
            a.statueUntil = 0;
            a.statueImmuneUntil = now + STATUE_IMMUNITY;
            endStone(victim, true);
        } else {
            a.curseUntil = 0;
            victim.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 1.5, victim.getZ(), 20, 0.4, 0.6,
                    0.4, 0.2);
            victim.displayClientMessage(Component.translatable(MSG + "forget_end").withStyle(ChatFormatting.AQUA), true);
        }
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================

    /** O dragão caiu (ou sumiu): tudo o que ELE pôs nos jogadores acaba agora. */
    public static void releaseFrom(MinecraftServer server, UUID dragon) {
        Iterator<Map.Entry<UUID, Afflicted>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Afflicted> me = it.next();
            Afflicted a = me.getValue();
            if (a.dragon == null || !a.dragon.equals(dragon)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(me.getKey());
            if (p != null) {
                if (a.statueUntil > p.level().getGameTime()) endStone(p, true);
                applyDrain(p, 0);
                applyRuin(p, 0);
            }
            it.remove();
        }
    }

    /** Morte, renascimento ou saída do jogador: some tudo. */
    public static void clear(ServerPlayer p) {
        Afflicted a = STATE.remove(p.getUUID());
        RESCUE.remove(p.getUUID());
        if (a == null) return;
        applyDrain(p, 0);
        applyRuin(p, 0);
    }

    public static void clearStatic() {
        STATE.clear();
        RESCUE.clear();
    }

    /** Para o comando de teste. */
    public static int afflictedCount() {
        return STATE.size();
    }

    private DragonCurses() {}
}
