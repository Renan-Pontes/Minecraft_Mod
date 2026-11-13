package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.HashSet;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Os glifos espaciais (Arcano; 5 da v1.6 + Recuperar Pertences da v1.0). São "de pergaminho": marcados com origem {@link MagicPath#NONE} — assim ficam FORA
 * do ensino automático do caminho Arcano e dos sorteios de loot/guilda, e só se aprendem pelo pergaminho certo
 * (Biblioteca do Vazio, Torres, a receita do Inventário Dimensional e o Enderman raro). Usáveis em magia de qualquer
 * elemento.
 */
public final class SpaceGlyphs {
    public static final ResourceLocation DIMENSIONAL_INVENTORY = IsekaiMahou.id("dimensional_inventory");
    public static final ResourceLocation DIMENSION_GATE = IsekaiMahou.id("dimension_gate");
    public static final ResourceLocation SPACE_STEP = IsekaiMahou.id("space_step");
    public static final ResourceLocation RETURN_MARK = IsekaiMahou.id("return_mark");
    public static final ResourceLocation SPACE_GRAVITY = IsekaiMahou.id("space_gravity");

    /** v1.0: Recuperar Pertences (Círculo 7) — ver {@link RecoverBelongings}. */
    public static final ResourceLocation RECOVER_BELONGINGS = RecoverBelongings.ID;

    public static final ResourceLocation[] ALL = {DIMENSIONAL_INVENTORY, DIMENSION_GATE, SPACE_STEP, RETURN_MARK, SPACE_GRAVITY,
            RECOVER_BELONGINGS};

    /** Cor do "espaço" (violeta claro) usada nos efeitos quando a magia não tem cor própria marcante. */
    public static final int SPACE_COLOR = 0xB98CFF;

    static void register() {
        GlyphRegistry.register(new DimensionalInventoryEffect());
        GlyphRegistry.register(new DimensionGateEffect());
        GlyphRegistry.register(new SpaceStepEffect());
        GlyphRegistry.register(new ReturnMarkEffect());
        GlyphRegistry.register(new SpaceGravityEffect());
        GlyphRegistry.register(new RecoverBelongings.Effect());
    }

    private static void actionbar(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic." + key, args).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Devolve parte da mana quando o glifo não conseguiu agir (ex.: sem destino para o portal). */
    private static void refund(SpellContext ctx, EffectGlyph g) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) MagicApi.get(sp).addMana(g.cost() * 0.8F);
    }

    // =====================================================================================
    //  Inventário Dimensional — abre o seu Item Box
    // =====================================================================================
    public static class DimensionalInventoryEffect extends EffectGlyph {
        public DimensionalInventoryEffect() {
            super("dimensional_inventory", 10);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public int hintDuration() {
            return -1;
        }

        @Override
        public float hintRadius() {
            return -1;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null || !ArcaneUtil.firstThisTick(ctx, "space_item_box")) return;
            ServerLevel level = ctx.level();
            Vec3 c = sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(0.9)).add(0, 1.1, 0);
            ArcaneUtil.ring(level, c, 0.55, ArcaneUtil.dust(ctx, 0.9F), 14);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 16, 0.2, 0.2, 0.2, 0.02);
            ItemBoxMenu.open(sp);
        }
    }

    // =====================================================================================
    //  Abrir Portal — portal de ida e volta para uma dimensão já visitada
    // =====================================================================================
    public static class DimensionGateEffect extends EffectGlyph {
        public DimensionGateEffect() {
            super("dimension_gate", 70);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public int hintDuration() {
            return SpaceGates.GATE_LIFE;
        }

        @Override
        public float hintRadius() {
            return -1;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null || !ArcaneUtil.firstThisTick(ctx, "space_gate")) return;
            openAt(ctx, sp, front(sp));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null || !ArcaneUtil.firstThisTick(ctx, "space_gate")) return;
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            // Forma Si Mesmo: o "bloco" é o chão embaixo de você — o portal abre na sua frente
            if (at.distanceTo(sp.position()) < 1.8 || at.distanceTo(sp.position()) > 32) at = front(sp);
            openAt(ctx, sp, at);
        }

        private static Vec3 front(ServerPlayer sp) {
            Vec3 dir = sp.getLookAngle().multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
            return sp.position().add(dir.scale(2.6));
        }

        private void openAt(SpellContext ctx, ServerPlayer sp, Vec3 at) {
            if (!SpaceGates.request(sp, at, sp.getYRot(), ctx.color())) refund(ctx, this);
        }
    }

    // =====================================================================================
    //  Passo Espacial (Teleporte curto) — atravessa paredes finas na direção do olhar
    // =====================================================================================
    public static class SpaceStepEffect extends EffectGlyph {
        /** Quantos blocos sólidos diferentes o salto pode atravessar. */
        private static final int MAX_WALL = 3;

        public SpaceStepEffect() {
            super("space_step", 14);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(3);
            minCircle(3);
        }

        @Override
        public float hintRadius() {
            return -1;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel level = ctx.level();
            if (target == c) {
                if (!ArcaneUtil.firstThisTick(ctx, "space_step")) return;
                Vec3 dir = c.getLookAngle();
                if (stats.inverted) dir = dir.scale(-1);
                double max = Math.min(32.0, 12.0 * stats.power());
                Vec3 spot = phaseSpot(c, dir, max);
                if (spot == null) {
                    if (c instanceof Player p) actionbar(p, "step_blocked");
                    level.sendParticles(ParticleTypes.SMOKE, c.getX(), c.getY() + 1, c.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
                    return;
                }
                Vec3 from = c.position();
                if (ArcaneUtil.teleport(c, spot, c.getYRot(), c.getXRot())) {
                    c.setDeltaMovement(c.getDeltaMovement().multiply(0.4, 0, 0.4));
                    c.hurtMarked = true;
                    fx(ctx, c, from, spot);
                }
                return;
            }
            // Em outro alvo: desloca-o alguns blocos na direção em que você olha (chefes resistem)
            if (!(target instanceof LivingEntity le) || SpaceGravity.isBoss(target) || target instanceof Player p && p.isCreative()) {
                ArcaneUtil.resisted(level, target, c);
                return;
            }
            Vec3 dir = c.getLookAngle().multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) return;
            if (stats.inverted) dir = dir.scale(-1);
            Vec3 spot = ArcaneUtil.findSafeSpot(le, dir, Math.min(16.0, 6.0 * stats.power()));
            if (spot == null) return;
            Vec3 from = le.position();
            if (ArcaneUtil.teleport(le, spot)) fx(ctx, le, from, spot);
        }

        /**
         * Ponto mais distante (até {@code max}) na direção {@code dir} onde o corpo cabe, podendo atravessar até
         * {@link #MAX_WALL} blocos sólidos — mas nunca para dentro (ou para fora) de uma arena de chefe selada.
         */
        static Vec3 phaseSpot(LivingEntity c, Vec3 dir, double max) {
            if (dir.lengthSqr() < 1.0E-6) return null;
            dir = dir.normalize();
            ServerLevel level = (ServerLevel) c.level();
            Object arenaHere = BossArena.at(level, c.position());
            double[] heights = {0, 0.5, 1.0, -0.5, -1.0, 1.5, -1.5};
            for (double d = max; d >= 1.5; d -= 0.5) {
                Vec3 feet = c.position().add(dir.scale(d));
                for (double dy : heights) {
                    Vec3 p = feet.add(0, dy, 0);
                    if (!ArcaneUtil.fits(c, p)) continue;
                    if (solidBetween(level, c.getEyePosition(), p.add(0, c.getEyeHeight(), 0)) > MAX_WALL) continue;
                    if (!sameArena(arenaHere, BossArena.at(level, p))) continue;
                    if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(p))) continue;
                    return p;
                }
            }
            return null;
        }

        private static boolean sameArena(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }

        /** Blocos sólidos (com colisão) diferentes no segmento a→b. */
        private static int solidBetween(ServerLevel level, Vec3 a, Vec3 b) {
            Set<BlockPos> seen = new HashSet<>();
            int n = (int) Math.ceil(a.distanceTo(b) / 0.25);
            for (int i = 0; i <= n; i++) {
                BlockPos bp = BlockPos.containing(a.lerp(b, i / (double) Math.max(1, n)));
                if (seen.contains(bp)) continue;
                BlockState s = level.getBlockState(bp);
                if (!s.getCollisionShape(level, bp).isEmpty()) seen.add(bp);
                if (seen.size() > MAX_WALL) break;
            }
            return seen.size();
        }

        private static void fx(SpellContext ctx, LivingEntity e, Vec3 from, Vec3 to) {
            ServerLevel level = ctx.level();
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.0F);
            ArcaneUtil.teleportFx(level, from, e.getBbHeight(), dust);
            ArcaneUtil.teleportFx(level, to, e.getBbHeight(), dust);
            ArcaneUtil.ring(level, from.add(0, 0.1, 0), 0.9, dust, 18);
            ArcaneUtil.ring(level, to.add(0, 0.1, 0), 0.9, dust, 18);
            ArcaneUtil.sound(level, from, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.3F);
            ArcaneUtil.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.6F);
        }
    }

    // =====================================================================================
    //  Marca de Retorno — marca um ponto; depois volta a ele
    // =====================================================================================
    public static class ReturnMarkEffect extends EffectGlyph {
        public ReturnMarkEffect() {
            super("return_mark", 18);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(4);
            minCircle(4);
        }

        @Override
        public int hintDuration() {
            return -1;
        }

        @Override
        public float hintRadius() {
            return -1;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null) return;
            ServerLevel level = ctx.level();
            SpaceData.Mark mark = SpaceData.mark(sp);
            if (target == sp) {
                if (!ArcaneUtil.firstThisTick(ctx, "return_mark")) return;
                if (mark == null || sp.isShiftKeyDown() || stats.inverted) {
                    setMark(ctx, sp);
                    return;
                }
                if (mark.dim() != level.dimension()) {
                    actionbar(sp, "mark_other_dim", SpaceGates.dimName(mark.dim().location()));
                    refund(ctx, this);
                    return;
                }
                if (BossArena.at(level, sp.position()) != null || BossArena.at(level, mark.pos()) != null) {
                    actionbar(sp, "mark_sealed");
                    refund(ctx, this);
                    return;
                }
                Vec3 to = mark.pos();
                if (!ArcaneUtil.fits(sp, to)) {
                    Vec3 alt = SpaceGates.safeNear(level, BlockPos.containing(to), 2);
                    if (alt == null) {
                        actionbar(sp, "mark_blocked");
                        refund(ctx, this);
                        return;
                    }
                    to = alt;
                }
                Vec3 from = sp.position();
                if (ArcaneUtil.teleport(sp, to, mark.yaw(), sp.getXRot())) {
                    sp.setDeltaMovement(Vec3.ZERO);
                    sp.hurtMarked = true;
                    fx(ctx, from, to, sp.getBbHeight());
                    actionbar(sp, "mark_returned");
                }
                return;
            }
            // Em outro alvo: manda a criatura para a sua marca (não chefes, não jogadores)
            if (mark == null || mark.dim() != level.dimension() || !(target instanceof LivingEntity le) || target instanceof Player
                    || SpaceGravity.isBoss(target)) {
                if (target instanceof LivingEntity) ArcaneUtil.resisted(level, target, sp);
                return;
            }
            if (mark.pos().distanceTo(target.position()) > 128 || !ArcaneUtil.fits(le, mark.pos())) return;
            Vec3 from = le.position();
            if (ArcaneUtil.teleport(le, mark.pos())) fx(ctx, from, mark.pos(), le.getBbHeight());
        }

        private static void setMark(SpellContext ctx, ServerPlayer sp) {
            ServerLevel level = ctx.level();
            if (BossArena.at(level, sp.position()) != null) {
                actionbar(sp, "mark_sealed");
                return;
            }
            SpaceData.setMark(sp, new SpaceData.Mark(level.dimension(), sp.position(), sp.getYRot()));
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.1F);
            ArcaneUtil.ring(level, sp.position().add(0, 0.1, 0), 1.0, dust, 24);
            ArcaneUtil.ring(level, sp.position().add(0, 0.1, 0), 0.5, dust, 12);
            level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 0.2, sp.getZ(), 10, 0.2, 0.6, 0.2, 0.02);
            ArcaneUtil.sound(level, sp.position(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.7F, 1.4F);
            BlockPos b = sp.blockPosition();
            actionbar(sp, "mark_set", b.getX(), b.getY(), b.getZ());
        }

        private static void fx(SpellContext ctx, Vec3 from, Vec3 to, float h) {
            ServerLevel level = ctx.level();
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.1F);
            ArcaneUtil.teleportFx(level, from, h, dust);
            ArcaneUtil.teleportFx(level, to, h, dust);
            ArcaneUtil.ring(level, to.add(0, 0.1, 0), 1.1, dust, 24);
            ArcaneUtil.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.8F);
            ArcaneUtil.sound(level, to, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, 1.6F);
        }
    }

    // =====================================================================================
    //  Gravidade — suspende (ou esmaga) alvos; num bloco vira um poço que puxa inimigos
    // =====================================================================================
    public static class SpaceGravityEffect extends EffectGlyph {
        public SpaceGravityEffect() {
            super("space_gravity", 20);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public float baseDamage() {
            return 4F;
        }

        @Override
        public int hintDuration() {
            return 60;
        }

        @Override
        public float hintRadius() {
            return 4F;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            ServerLevel level = ctx.level();
            long now = level.getServer().getTickCount();
            if (target == c) {
                if (!ArcaneUtil.firstThisTick(ctx, "space_gravity_self")) return;
                if (stats.inverted && !c.onGround()) {
                    SpaceGravity.slam(ctx, c, stats, now, true);
                } else {
                    // Salto sem peso: sobe e desce devagar
                    c.setDeltaMovement(c.getDeltaMovement().multiply(1, 0, 1).add(0, 1.05 * Math.min(1.6, stats.power()), 0));
                    c.hurtMarked = true;
                    c.resetFallDistance();
                    c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, stats.duration(100), 0, false, false, true));
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.getX(), c.getY() + 0.1, c.getZ(), 30, 0.5, 0.1, 0.5, 0.06);
                    ArcaneUtil.sound(level, c.position(), SoundEvents.SHULKER_SHOOT, 0.8F, 0.6F);
                }
                return;
            }
            if (stats.inverted) SpaceGravity.slam(ctx, target, stats, now, false);
            else SpaceGravity.lift(level, target, stats.duration(60), now);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            // Forma Si Mesmo também toca o chão embaixo de você: aí não abre poço
            if (c != null && at.distanceTo(c.position()) < 1.6) return;
            if (!ArcaneUtil.firstThisTick(ctx, "space_gravity_well")) return;
            SpaceGravity.well(ctx, at, stats.radius(4F), stats.duration(80), ctx.level().getServer().getTickCount(), stats.inverted);
        }
    }

    private SpaceGlyphs() {}
}
