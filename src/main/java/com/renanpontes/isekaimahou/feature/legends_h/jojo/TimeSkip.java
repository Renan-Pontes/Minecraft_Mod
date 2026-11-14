package com.renanpontes.isekaimahou.feature.legends_h.jojo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As regras do KING CRIMSON e do EPITÁFIO (servidor, em memória):
 * <ul>
 *   <li><b>Fora do tempo</b> (King Crimson em si mesmo, v1.1): o tempo é APAGADO em volta. O mundo continua andando,
 *       mas você fica invisível, ninguém mira você, golpes/magias/projéteis atravessam — e você também não acerta
 *       ninguém. No instante em que você sai do tempo, cada criatura/jogador perto (16 blocos, Expandir +, até 24; chefes
 *       nunca) ganha um DESTINO: um ponto seguro aleatório a 2–6 blocos de onde estava, marcado com um X vermelho no chão
 *       que SÓ VOCÊ vê. Os inimigos ficam com o tempo apagado em silêncio (andam, mas não miram e os ataques não
 *       acontecem). Quando o tempo volta, todos aparecem de uma vez no X deles, com a mão trocada (jogadores: outro
 *       slot da barra e, se tiverem outro loadout de magias, outro loadout; criaturas: troca mão principal/secundária
 *       e perdem o alvo). Você reaparece com um clarão; o PRIMEIRO golpe em até 3 s sai ×1,5 e por 3 s você vê o
 *       Epitáfio (para onde os inimigos vão).</li>
 *   <li><b>Tempo apagado</b> (King Crimson num alvo): ele continua andando "no automático", mas não mira e o que ele
 *       faz não acontece (os ataques dele não causam dano); deixa rastros de imagens. Jogadores: véu vermelho, sem
 *       travar o controle.</li>
 *   <li><b>Epitáfio</b> (aumento): quem você atinge fica "previsto" — você vê a linha do próximo movimento dele e o
 *       primeiro ataque dele em você é ESQUIVADO (um passo para o lado).</li>
 * </ul>
 * Nenhum bloco é mexido. Teleportes respeitam o EntityTeleportEvent (ex.: Domínio "Sem Fuga") e só acontecem na mesma
 * dimensão e na mesma arena. Dano de vazio/{@code /kill} sempre passa.
 */
public final class TimeSkip {
    public static final int CRIMSON = 0xB01040;
    public static final int GHOST = 0xD0306A;
    /** Cor do X do destino (só o dono do King Crimson vê). */
    public static final int MARK = 0xFF1030;
    public static final float FIRST_STRIKE = 1.5F;
    /** Duração da cutscene de quem conjura (o tempo fora do tempo só começa a contar depois dela). */
    public static final int CUTSCENE = 30;
    /** No máximo quantos destinos por conjuração. */
    public static final int MAX_DEST = 24;

    /** Uma vítima do salto: o X dela e onde ela estava (dimensão/arena) quando o tempo foi apagado. */
    private static final class Dest {
        final LivingEntity e;
        @Nullable
        final Vec3 to;
        final ResourceKey<Level> dim;
        final Optional<String> arena;
        final boolean silenced;

        Dest(LivingEntity e, @Nullable Vec3 to, boolean silenced) {
            this.e = e;
            this.to = to;
            this.dim = e.level().dimension();
            this.arena = ArenaBounds.at(e.level(), e.position()).map(ArenaBounds.Arena::id);
            this.silenced = silenced;
        }
    }

    private static final class Phase {
        final LivingEntity e;
        final Vec3 start;
        long end;
        final List<Dest> dests = new ArrayList<>();

        Phase(LivingEntity e, long end) {
            this.e = e;
            this.start = e.position();
            this.end = end;
        }
    }

    private static final class Erased {
        final LivingEntity e;
        long end;
        /** Apagado pelo King Crimson em si mesmo: sem véu, sem aviso, sem rastro (a vítima não sabe). */
        final boolean silent;

        Erased(LivingEntity e, long end, boolean silent) {
            this.e = e;
            this.end = end;
            this.silent = silent;
        }
    }

    private static final class Sight {
        final LivingEntity target;
        long end;
        boolean dodge;

        Sight(LivingEntity target, long end, boolean dodge) {
            this.target = target;
            this.end = end;
            this.dodge = dodge;
        }
    }

    private static final Map<UUID, Phase> PHASED = new HashMap<>();
    private static final Map<UUID, Erased> ERASED = new HashMap<>();
    private static final Map<UUID, Long> STRIKE = new HashMap<>();
    /** v1.1: multiplicador do 1º golpe por quem conjurou (evolução "Reino Apagado" = ×2; padrão {@link #FIRST_STRIKE}). */
    private static final Map<UUID, Float> STRIKE_MULT = new HashMap<>();
    private static final Map<UUID, Map<UUID, Sight>> SIGHT = new HashMap<>();

    // =====================================================================================
    //  API
    // =====================================================================================

    public static boolean phased(@Nullable Entity e) {
        return e != null && PHASED.containsKey(e.getUUID());
    }

    public static boolean erased(@Nullable Entity e) {
        return e != null && ERASED.containsKey(e.getUUID());
    }

    /**
     * King Crimson em si mesmo (v1.1): sai do tempo por {@code ticks} (contados DEPOIS da cutscene curta de quem
     * conjura) e sorteia o destino de todos num raio {@code radius}.
     * @return quantos destinos foram marcados (0 se você já estava fora do tempo — aí só estende)
     */
    public static int crimson(SpellContext ctx, LivingEntity caster, int ticks, double radius) {
        if (!(caster.level() instanceof ServerLevel level)) return 0;
        long now = level.getGameTime();
        Phase cur = PHASED.get(caster.getUUID());
        if (cur != null) {
            cur.end = Math.max(cur.end, now + ticks);
            for (Dest d : cur.dests) {
                Erased er = ERASED.get(d.e.getUUID());
                if (er != null && er.silent) er.end = Math.max(er.end, cur.end);
            }
            LhUtil.screenTracking(caster, LhNet.PHASE, (int) (cur.end - now));
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, (int) (cur.end - now) + 2, 0, false, false, false));
            return 0;
        }
        ServerPlayer owner = caster instanceof ServerPlayer sp ? sp : null;
        int total = ticks + (owner != null ? CUTSCENE : 0);
        Phase ph = phase(caster, total);
        if (ph == null) return 0;

        // Quem está em volta (todo mundo: inimigos, aliados, jogadores) — menos chefes e quem anda junto com você
        double r2 = radius * radius;
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(caster) <= r2
                        && !LhUtil.boss(e) && e.getVehicle() != caster && caster.getVehicle() != e);
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        RandomSource rnd = level.random;
        for (LivingEntity e : around) {
            if (ph.dests.size() >= MAX_DEST) break;
            boolean foe = LhUtil.isFoe(ctx, e);
            ph.dests.add(new Dest(e, findSpot(level, e, rnd), foe));
            if (foe) silence(e, now + total);
        }
        if (owner != null) {
            Cutscenes.play(owner, "king_crimson", owner, Component.translatable("announce.isekai_mahou.legends_h.king_crimson"),
                    Component.translatable("cutscene.isekai_mahou.legends_h.kc_sub"), CRIMSON, CUTSCENE, true);
            if (!ph.dests.isEmpty()) {
                LhUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_h.kc_marked", ph.dests.size())
                        .withStyle(ChatFormatting.DARK_RED));
            }
            drawMarks(owner, ph, true);
        }
        return ph.dests.size();
    }

    /** Sai do tempo por {@code ticks} (invisível, intocável). Usado pelo {@link #crimson}. */
    @Nullable
    private static Phase phase(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        Phase ph = new Phase(e, now + ticks);
        PHASED.put(e.getUUID(), ph);
        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 2, 0, false, false, false));
        e.addEffect(new MobEffectInstance(LhRegistry.TIME_SKIP, ticks, 0, false, false, true));
        LhUtil.screenTracking(e, LhNet.PHASE, ticks);
        // Ninguém mais mira em você
        for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
        Vec3 c = LhUtil.center(e);
        ElementFx.flash(level, c, 0xFF2050, 2F);
        level.sendParticles(LhUtil.dust(GHOST, 1.4F), c.x, c.y, c.z, 16, 0.4, 0.8, 0.4, 0);
        LhUtil.sound(level, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.2F, 0.6F);
        LhUtil.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.5F);
        return ph;
    }

    /** O tempo da vítima some em silêncio (sem véu nem aviso): ela anda, mas não mira e não acerta. */
    private static void silence(LivingEntity e, long end) {
        Erased er = ERASED.get(e.getUUID());
        if (er != null) er.end = Math.max(er.end, end);
        else ERASED.put(e.getUUID(), new Erased(e, end, true));
        if (e instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
        }
    }

    /** v1.1: o 1º golpe depois do tempo apagado de {@code e} vale {@code mult} (evolução do King Crimson). */
    public static void setFirstStrike(LivingEntity e, float mult) {
        if (mult == FIRST_STRIKE) STRIKE_MULT.remove(e.getUUID());
        else STRIKE_MULT.put(e.getUUID(), mult);
    }

    /** King Crimson num alvo: o tempo dele é apagado por {@code ticks}. */
    public static void erase(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Erased er = ERASED.get(e.getUUID());
        if (er != null) er.end = Math.max(er.end, now + ticks);
        else ERASED.put(e.getUUID(), new Erased(e, now + ticks, false));
        if (e instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
        }
        if (e instanceof ServerPlayer sp) {
            LhUtil.screen(sp, LhNet.ERASED, sp.getId(), ticks);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.erased_you", String.format("%.1f", ticks / 20F))
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        Vec3 c = LhUtil.center(e);
        level.sendParticles(LhUtil.dust(GHOST, 1.4F), c.x, c.y, c.z, 14, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4,
                e.getBbWidth() * 0.5, 0);
        LhUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.5F);
    }

    /** Epitáfio: {@code seer} prevê {@code target} por {@code ticks}; com {@code dodge}, esquiva o 1º ataque dele. */
    public static void foresee(LivingEntity seer, LivingEntity target, int ticks, boolean dodge) {
        if (!(seer instanceof ServerPlayer) || seer == target || !(seer.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Map<UUID, Sight> map = SIGHT.computeIfAbsent(seer.getUUID(), k -> new HashMap<>());
        if (map.size() >= 16 && !map.containsKey(target.getUUID())) return;
        Sight s = map.get(target.getUUID());
        if (s == null) map.put(target.getUUID(), new Sight(target, now + ticks, dodge));
        else {
            s.end = Math.max(s.end, now + ticks);
            s.dodge |= dodge;
        }
    }

    // =====================================================================================
    //  Destinos (o X vermelho)
    // =====================================================================================

    /**
     * Um ponto seguro aleatório a 2–6 blocos de {@code e}: chão firme, espaço livre para o corpo, sem líquido/fogo/
     * cacto/teia, dentro da borda do mundo, na mesma arena e (nas primeiras tentativas) sem parede no caminho.
     * @return null se não achou (a vítima fica onde está, mas ainda troca a mão)
     */
    @Nullable
    private static Vec3 findSpot(ServerLevel level, LivingEntity e, RandomSource rnd) {
        Optional<String> arena = ArenaBounds.at(level, e.position()).map(ArenaBounds.Arena::id);
        Vec3 eye = LhUtil.center(e);
        int baseY = Mth.floor(e.getY() + 0.01);
        int[] dys = {0, 1, -1, 2, -2, -3};
        for (int i = 0; i < 20; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 2 + rnd.nextDouble() * 4;
            double x = e.getX() + Math.cos(ang) * dist, z = e.getZ() + Math.sin(ang) * dist;
            for (int dy : dys) {
                BlockPos feet = BlockPos.containing(x, baseY + dy, z);
                Vec3 p = new Vec3(x, feet.getY(), z);
                if (!standable(level, e, feet, p)) continue;
                if (!ArenaBounds.at(level, p).map(ArenaBounds.Arena::id).equals(arena)) continue;
                // As primeiras 14 tentativas exigem caminho livre (sem atravessar parede); depois, só o lugar seguro
                if (i < 14) {
                    Vec3 to = p.add(0, e.getBbHeight() * 0.5, 0);
                    if (level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType()
                            != HitResult.Type.MISS) continue;
                }
                return p;
            }
        }
        return null;
    }

    /** Dá para ficar de pé em {@code p} (pés no bloco {@code feet})? */
    private static boolean standable(ServerLevel level, LivingEntity e, BlockPos feet, Vec3 p) {
        if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet) || level.isOutsideBuildHeight(feet)) return false;
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || danger(floor)) return false;
        AABB box = e.getDimensions(e.getPose()).makeBoundingBox(p).deflate(0.02);
        if (!level.noCollision(e, box) || level.containsAnyLiquid(box.inflate(0, 0.1, 0))) return false;
        for (BlockPos bp : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (danger(level.getBlockState(bp))) return false;
        }
        return true;
    }

    private static boolean danger(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE)
                || s.is(Blocks.LAVA) || s.is(Blocks.POINTED_DRIPSTONE);
    }

    /** A vítima ainda conta (viva, conectada, na mesma dimensão do conjurador)? */
    private static boolean valid(Dest d, ServerLevel level) {
        LivingEntity e = d.e;
        if (e.isRemoved() || !e.isAlive() || e.level() != level || e.level().dimension() != d.dim) return false;
        return !(e instanceof ServerPlayer sp) || !sp.hasDisconnected();
    }

    /** Desenha (só para {@code sp}) o X de cada destino e, com {@code lines}, uma linha fraca da vítima até ele. */
    private static void drawMarks(ServerPlayer sp, Phase ph, boolean lines) {
        ServerLevel level = sp.serverLevel();
        for (Dest d : ph.dests) {
            if (d.to == null || d.e.level() != level) continue;
            Vec3 c = d.to.add(0, 0.08, 0);
            LhUtil.privateLine(sp, c.add(-0.55, 0, -0.55), c.add(0.55, 0, 0.55), MARK, 1.1F, 5);
            LhUtil.privateLine(sp, c.add(-0.55, 0, 0.55), c.add(0.55, 0, -0.55), MARK, 1.1F, 5);
            if (lines) LhUtil.privateLine(sp, d.e.position().add(0, 0.1, 0), c, CRIMSON, 0.5F, 8);
        }
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = e.getSource().getEntity();
        // Fora do tempo: nenhum ATAQUE te acerta (lava, queda e afogamento continuam valendo — não é imortalidade)
        if (phased(victim) && src != null && src != victim) {
            e.setCanceled(true);
            return;
        }
        if (src instanceof LivingEntity a && a != victim) {
            if (phased(a) || erased(a)) {
                e.setCanceled(true);
                return;
            }
            // Epitáfio: o golpe já estava previsto
            Map<UUID, Sight> map = SIGHT.get(victim.getUUID());
            Sight s = map == null ? null : map.get(a.getUUID());
            if (s != null && s.dodge && s.end > victim.level().getGameTime()) {
                s.dodge = false;
                e.setCanceled(true);
                sidestep(victim, a);
                return;
            }
            // Primeiro golpe depois do tempo apagado
            Long until = STRIKE.get(a.getUUID());
            if (until != null) {
                STRIKE.remove(a.getUUID());
                if (until >= victim.level().getGameTime()) {
                    e.setAmount(e.getAmount() * STRIKE_MULT.getOrDefault(a.getUUID(), FIRST_STRIKE));
                    if (victim.level() instanceof ServerLevel sl) {
                        Vec3 c = LhUtil.center(victim);
                        ElementFx.flash(sl, c, 0xFF2050, 2F);
                        sl.sendParticles(LhUtil.dust(CRIMSON, 1.5F), c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0);
                        LhUtil.sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.7F);
                    }
                }
            }
        }
    }

    /** Projéteis atravessam quem está fora do tempo. */
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        if (e.getRayTraceResult() instanceof EntityHitResult hit && phased(hit.getEntity())) e.setCanceled(true);
    }

    public static void onChangeTarget(LivingChangeTargetEvent e) {
        if (phased(e.getNewAboutToBeSetTarget()) || erased(e.getEntity())) e.setNewAboutToBeSetTarget(null);
    }

    private static void sidestep(LivingEntity victim, LivingEntity attacker) {
        Vec3 away = victim.position().subtract(attacker.position());
        Vec3 side = new Vec3(-away.z, 0, away.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(victim.getRandom().nextBoolean() ? 0.75 : -0.75);
        victim.setDeltaMovement(victim.getDeltaMovement().add(side.x, 0.15, side.z));
        victim.hurtMarked = true;
        if (victim.level() instanceof ServerLevel sl) {
            Vec3 c = LhUtil.center(victim);
            sl.sendParticles(LhUtil.dust(GHOST, 1.2F), c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0);
            LhUtil.sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 1.3F);
        }
        LhUtil.actionbar(victim, Component.translatable("message.isekai_mahou.legends_h.epitaph_dodge").withStyle(ChatFormatting.RED));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    public static void tick(MinecraftServer server) {
        if (PHASED.isEmpty() && ERASED.isEmpty() && SIGHT.isEmpty() && STRIKE.isEmpty()) return;
        long tick = server.getTickCount();
        List<Phase> resumed = new ArrayList<>();
        for (Iterator<Phase> it = PHASED.values().iterator(); it.hasNext(); ) {
            Phase ph = it.next();
            LivingEntity e = ph.e;
            if (!(e.level() instanceof ServerLevel level) || e.isRemoved() || !e.isAlive()) {
                it.remove();
                LhUtil.screenTracking(e, LhNet.PHASE, 0);
                release(ph);
                continue;
            }
            if (level.getGameTime() >= ph.end) {
                it.remove();
                resumed.add(ph);
                continue;
            }
            for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) m.setTarget(null);
            if (tick % 4 == 0) {
                // Quem saiu, morreu, deslogou ou trocou de dimensão perde o destino
                ph.dests.removeIf(d -> {
                    if (valid(d, level)) return false;
                    unsilence(d);
                    return true;
                });
                // O X vermelho de cada um — só quem é o King Crimson vê
                if (e instanceof ServerPlayer sp) drawMarks(sp, ph, tick % 8 == 0);
            }
        }
        for (Phase ph : resumed) {
            if (ph.e.level() instanceof ServerLevel level) resume(level, ph);
        }
        for (Iterator<Erased> it = ERASED.values().iterator(); it.hasNext(); ) {
            Erased er = it.next();
            LivingEntity e = er.e;
            if (!(e.level() instanceof ServerLevel level) || e.isRemoved() || !e.isAlive() || level.getGameTime() >= er.end) {
                it.remove();
                continue;
            }
            if (e instanceof Mob m && m.getTarget() != null) m.setTarget(null);
            if (!er.silent && tick % 3 == 0) {
                // Rastro de imagens: o que ele "faz" enquanto o tempo dele não existe
                Vec3 c = LhUtil.center(e);
                level.sendParticles(LhUtil.dust(GHOST, 1.1F), c.x, c.y, c.z, 3, e.getBbWidth() * 0.35, e.getBbHeight() * 0.35,
                        e.getBbWidth() * 0.35, 0);
            }
        }
        if (tick % 5 == 0) tickSight(server);
        if (tick % 100 == 0) {
            long now = server.overworld().getGameTime();
            STRIKE.values().removeIf(t -> t + 200 < now);
        }
    }

    private static void tickSight(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Sight>>> it = SIGHT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Sight>> en = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || !sp.isAlive()) {
                it.remove();
                continue;
            }
            long now = sp.serverLevel().getGameTime();
            LivingEntity nearest = null;
            double nd = Double.MAX_VALUE;
            for (Iterator<Sight> si = en.getValue().values().iterator(); si.hasNext(); ) {
                Sight s = si.next();
                LivingEntity t = s.target;
                if (s.end < now || t.isRemoved() || !t.isAlive() || t.level() != sp.level() || t.distanceToSqr(sp) > 48 * 48) {
                    si.remove();
                    continue;
                }
                Vec3 c = LhUtil.center(t);
                // O "futuro": para onde ele vai no próximo segundo...
                LhUtil.privateLine(sp, c, c.add(t.getDeltaMovement().multiply(20, 4, 20)), GHOST, 0.7F, 10);
                // ...e, se vem em você, a linha vermelha do golpe
                if (t instanceof Mob m && m.getTarget() == sp) {
                    LhUtil.privateLine(sp, c, LhUtil.center(sp), 0xFF2030, 0.9F, 16);
                    double d = t.distanceToSqr(sp);
                    if (d < nd) {
                        nd = d;
                        nearest = t;
                    }
                }
            }
            if (en.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            if (nearest != null && server.getTickCount() % 10 == 0) {
                LhUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_h.epitaph_dir", direction(sp, nearest))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** "pela frente/esquerda/direita/costas/de cima" — relativo para onde o jogador olha. */
    private static Component direction(ServerPlayer sp, LivingEntity from) {
        Vec3 to = from.position().subtract(sp.position());
        if (to.y > 3 && to.y * to.y > to.x * to.x + to.z * to.z) return Component.translatable("message.isekai_mahou.legends_h.dir_above");
        double ang = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-to.x, to.z)) - sp.getYRot());
        String key = Math.abs(ang) <= 45 ? "front" : Math.abs(ang) >= 135 ? "back" : ang > 0 ? "right" : "left";
        return Component.translatable("message.isekai_mahou.legends_h.dir_" + key);
    }

    /** O tempo volta: todos pulam para o X deles, com a mão trocada; o conjurador reaparece onde está agora. */
    private static void resume(ServerLevel level, Phase ph) {
        LivingEntity e = ph.e;
        e.removeEffect(MobEffects.INVISIBILITY);
        e.removeEffect(LhRegistry.TIME_SKIP);
        LhUtil.screenTracking(e, LhNet.PHASE, 0);
        long now = level.getGameTime();
        STRIKE.put(e.getUUID(), now + 60);

        // O salto: todo mundo aparece de uma vez onde o King Crimson sabia que iam estar
        for (Dest d : ph.dests) {
            unsilence(d);
            if (!valid(d, level)) continue;
            LivingEntity v = d.e;
            if (d.to != null && v.distanceToSqr(d.to) < 64 * 64
                    && ArenaBounds.at(level, v.position()).map(ArenaBounds.Arena::id).equals(d.arena)
                    && standable(level, v, BlockPos.containing(d.to), d.to)) {
                Vec3 from = LhUtil.center(v);
                if (ArcaneUtil.teleport(v, d.to, v.getYRot(), v.getXRot())) {
                    v.setDeltaMovement(Vec3.ZERO);
                    v.hurtMarked = true;
                    level.sendParticles(LhUtil.dust(GHOST, 1.0F), from.x, from.y, from.z, 6, 0.25, 0.5, 0.25, 0);
                    Vec3 c = LhUtil.center(v);
                    level.sendParticles(LhUtil.dust(CRIMSON, 1.1F), c.x, c.y, c.z, 8, 0.25, 0.5, 0.25, 0);
                }
            }
            shuffleHand(v);
            if (v instanceof ServerPlayer sp) {
                LhUtil.screen(sp, LhNet.SKIP, e.getId(), 10);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.skipped_you").withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        ph.dests.clear();

        Vec3 c = LhUtil.center(e);
        // Imagem que fica para trás no ponto de partida e clarão onde ele "aparece"
        if (ph.start.distanceToSqr(e.position()) > 4) {
            Vec3 s = ph.start.add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(LhUtil.dust(GHOST, 1.3F), s.x, s.y, s.z, 18, 0.3, 0.7, 0.3, 0);
        }
        ElementFx.flash(level, c, 0xFF2050, 4F);
        ElementFx.shockwave(level, c, CRIMSON, 9F, 12, null);
        level.sendParticles(LhUtil.dust(CRIMSON, 1.8F), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0);
        LhUtil.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 0.6F);
        LhUtil.sound(level, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.6F);
        skipFor(level, e.position(), e, 6);
        // Epitáfio curto: quem está em volta fica previsto por 3 s (sem esquiva)
        for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(16), m -> m.isAlive())) {
            foresee(e, m, 60, false);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
        }
        LhUtil.actionbar(e, Component.translatable("message.isekai_mahou.legends_h.time_resumes").withStyle(ChatFormatting.DARK_RED));
    }

    /**
     * "Mudou o que tinha na mão": jogador vai para outro slot da barra (e, se tiver outro loadout de magias com algo,
     * para outro loadout); criatura troca mão principal/secundária e perde o alvo.
     */
    private static void shuffleHand(LivingEntity v) {
        RandomSource rnd = v.getRandom();
        if (v instanceof ServerPlayer sp) {
            int cur = sp.getInventory().selected;
            int n = Math.floorMod(cur + 1 + rnd.nextInt(8), 9);
            sp.getInventory().selected = n;
            sp.connection.send(new ClientboundSetCarriedItemPacket(n));
            MagicData d = MagicApi.get(sp);
            List<Integer> other = new ArrayList<>();
            for (int l = 0; l < d.loadoutCount(); l++) {
                if (l != d.activeLoadout() && !d.loadoutEmpty(l)) other.add(l);
            }
            if (!other.isEmpty()) MagicApi.selectLoadout(sp, other.get(rnd.nextInt(other.size())));
            return;
        }
        ItemStack main = v.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = v.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!main.isEmpty() || !off.isEmpty()) {
            v.setItemSlot(EquipmentSlot.MAINHAND, off.copy());
            v.setItemSlot(EquipmentSlot.OFFHAND, main.copy());
        }
        if (v instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    /** Tira o "tempo apagado silencioso" de uma vítima (o salto acabou ou ela saiu da lista). */
    private static void unsilence(Dest d) {
        Erased er = ERASED.get(d.e.getUUID());
        if (er != null && er.silent) ERASED.remove(d.e.getUUID());
    }

    /** O conjurador sumiu no meio do salto (morreu/deslogou): ninguém é movido, as vítimas voltam ao normal. */
    private static void release(Phase ph) {
        for (Dest d : ph.dests) unsilence(d);
        ph.dests.clear();
    }

    /** Corte rápido na tela de quem está perto (o mundo "pulou"). */
    private static void skipFor(ServerLevel level, Vec3 p, Entity except, int ticks) {
        for (ServerPlayer sp : level.getPlayers(pl -> pl != except && pl.distanceToSqr(p) < 24 * 24)) {
            LhUtil.screen(sp, LhNet.SKIP, except.getId(), ticks);
        }
    }

    public static void forget(UUID id) {
        Phase ph = PHASED.remove(id);
        if (ph != null) {
            ph.e.removeEffect(MobEffects.INVISIBILITY);
            release(ph);
        }
        // Uma vítima que deslogou sai da lista de destinos de quem a marcou
        for (Phase other : PHASED.values()) {
            other.dests.removeIf(d -> d.e.getUUID().equals(id));
        }
        ERASED.remove(id);
        STRIKE.remove(id);
        STRIKE_MULT.remove(id);
        SIGHT.remove(id);
    }

    public static void clear() {
        PHASED.clear();
        ERASED.clear();
        STRIKE.clear();
        STRIKE_MULT.clear();
        SIGHT.clear();
    }

    private TimeSkip() {}
}
