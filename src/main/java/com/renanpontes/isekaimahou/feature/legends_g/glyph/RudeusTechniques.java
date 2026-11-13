package com.renanpontes.isekaimahou.feature.legends_g.glyph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.feature.legends_g.entity.StoneShellEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lendas de Rudeus Greyrat (homenagem a "Mushoku Tensei"): o Canhão de Pedra — a magia de terra conjurada em
 * SILÊNCIO, girando a bala até ela furar qualquer coisa — e o Atoleiro, que transforma o chão em lama.
 */
public final class RudeusTechniques {
    public static final int STONE_COLOR = 0xA89A86;
    public static final int MUD_COLOR = 0x6B4A2B;

    /** Glifos COMUNS: o elemento + tier os colocam no caminho de Terra e nos sorteios de pergaminhos. */
    public static List<EffectGlyph> all() {
        return List.of(new StoneCannon(), new Quagmire());
    }

    // =====================================================================================
    //  Canhão de Pedra
    // =====================================================================================
    /**
     * EFEITO (Terra, tier 3): uma bala de pedra se forma girando e é disparada reta a 4 blocos/tick (Acelerar +): dano
     * 16 de TERRA no primeiro inimigo + empurrão; Perfurar atravessa (−30% por alvo). Num alvo, a bala nasce a 3 blocos
     * dele (do lado de quem conjura) e o fura — funciona com qualquer forma (até 3 balas por conjuração). Si Mesmo: sai
     * da sua mão para onde você olha. No bloco abre um buraquinho (se a destruição estiver ligada e o lugar não for
     * protegido).
     */
    public static class StoneCannon extends EffectGlyph {
        public static final float DAMAGE = 16F;

        public StoneCannon() {
            super("lg_stone_cannon", 20);
            element(Element.EARTH);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (LgUtil.self(ctx, target) || ctx.isAlly(target)) {
                if (LgUtil.once(ctx, "stone_cannon")) StoneShellEntity.spawn(ctx, stats, null);
                return;
            }
            if (!LgUtil.once(ctx, "stone_cannon:" + target.getId()) || !freeSlot(ctx)) return;
            StoneShellEntity.spawnNear(ctx, stats, target);
        }

        /** Até 3 balas por conjuração neste tick (formas de área chamam o efeito uma vez por alvo). */
        private static boolean freeSlot(SpellContext ctx) {
            for (int i = 0; i < 3; i++) if (LgUtil.once(ctx, "stone_cannon_slot" + i)) return true;
            return false;
        }
    }

    // =====================================================================================
    //  Atoleiro
    // =====================================================================================
    /**
     * EFEITO (Terra, tier 2): o chão vira lama num raio de 3,5 (Expandir +) por 6 s (Estender +): inimigos dentro ficam MUITO lentos (Lentidão
     * IV), Molhados (combina com raio e gelo!) e sem conseguir pular; chefes só ficam um pouco lentos. Não mexe em
     * bloco nenhum (é lama de mana). Si Mesmo: o atoleiro aparece onde você olha (até 24 blocos).
     */
    public static class Quagmire extends EffectGlyph {
        public Quagmire() {
            super("lg_quagmire", 12);
            element(Element.EARTH);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float hintRadius() { return 3.5F; }

        @Override
        public int hintDuration() { return 120; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            if (LgUtil.self(ctx, target) || ctx.isAlly(target)) {
                Vec3 end = LgUtil.rayEnd(ctx.level(), c.getEyePosition(), c.getLookAngle(), 24, c);
                place(ctx, stats, groundBelow(ctx.level(), end));
            } else {
                place(ctx, stats, groundBelow(ctx.level(), target.position().add(0, 0.5, 0)));
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (LgUtil.selfBlock(ctx, hit)) return;
            place(ctx, stats, hit.getLocation());
        }

        private void place(SpellContext ctx, SpellStats stats, Vec3 at) {
            if (!LgUtil.once(ctx, "quagmire")) return;
            float r = Mth.clamp(stats.radius(3.5F), 2F, 10F);
            int ticks = Mth.clamp(stats.duration(120), 60, 400);
            ServerLevel level = ctx.level();
            Zones.add(new Zone(level, ctx, at, r, level.getGameTime() + ticks));
            ElementFx.area(level, at.add(0, 0.05, 0), Element.EARTH, MUD_COLOR, r, 2, ticks);
            ElementFx.runeCircle(level, at.add(0, 0.06, 0), MUD_COLOR, r * 0.8F, 30, 2);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), at.x, at.y + 0.2, at.z,
                    Math.min(40, (int) (r * 6)), r * 0.5, 0.1, r * 0.5, 0.1);
            LgUtil.sound(level, at, SoundEvents.MUD_BREAK, 1.4F, 0.6F);
            LgUtil.sound(level, at, SoundEvents.HONEY_BLOCK_SLIDE, 1.2F, 0.5F);
        }
    }

    /** Desce até o primeiro bloco sólido (até 6 blocos) — o atoleiro fica no chão. */
    static Vec3 groundBelow(ServerLevel level, Vec3 p) {
        return com.renanpontes.isekaimahou.feature.legends_f.LfUtil.groundBelow(level, p, 6);
    }

    // =====================================================================================
    //  Zonas de lama (servidor, só memória)
    // =====================================================================================
    record Zone(ServerLevel level, SpellContext ctx, Vec3 c, float r, long end) {}

    /** Zonas de Atoleiro ativas. No máx. 2 por conjurador (a mais velha some). */
    public static final class Zones {
        private static final List<Zone> ACTIVE = new ArrayList<>();

        static void add(Zone z) {
            int mine = 0;
            for (Zone o : ACTIVE) if (o.ctx().casterId() != null && o.ctx().casterId().equals(z.ctx().casterId())) mine++;
            if (mine >= 2) {
                for (Iterator<Zone> it = ACTIVE.iterator(); it.hasNext(); ) {
                    Zone o = it.next();
                    if (o.ctx().casterId() != null && o.ctx().casterId().equals(z.ctx().casterId())) {
                        it.remove();
                        break;
                    }
                }
            }
            if (ACTIVE.size() >= 64) ACTIVE.remove(0);
            ACTIVE.add(z);
        }

        public static void tick(MinecraftServer server) {
            if (ACTIVE.isEmpty()) return;
            for (Iterator<Zone> it = ACTIVE.iterator(); it.hasNext(); ) {
                Zone z = it.next();
                long now = z.level().getGameTime();
                if (now > z.end() || server.getLevel(z.level().dimension()) != z.level()) {
                    it.remove();
                    continue;
                }
                if (now % 2 != 0) continue;
                boolean wet = now % 20 == 0;
                for (LivingEntity e : LgUtil.foesAround(z.ctx(), z.c(), z.r(), 24)) {
                    double dy = e.getY() - z.c().y;
                    if (dy < -1.0 || dy > 1.6) continue;
                    if (Statuses.isBossLike(e)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false), z.ctx().caster());
                        continue;
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false), z.ctx().caster());
                    if (wet) Statuses.apply(e, Statuses.Kind.WET, 1, 80, z.ctx().caster(), 1F);
                    Vec3 v = e.getDeltaMovement();
                    e.setDeltaMovement(v.x * 0.4, Math.min(v.y, 0.0), v.z * 0.4);
                    if (e instanceof Mob m) m.setJumping(false);
                    if (e instanceof Player) e.hurtMarked = true;
                }
                if (now % 6 == 0) {
                    z.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()),
                            z.c().x, z.c().y + 0.15, z.c().z, Math.min(10, (int) (z.r() * 2)), z.r() * 0.45, 0.05, z.r() * 0.45, 0.02);
                    z.level().sendParticles(ParticleTypes.BUBBLE_POP, z.c().x, z.c().y + 0.2, z.c().z,
                            Math.min(6, (int) z.r()), z.r() * 0.4, 0.05, z.r() * 0.4, 0.01);
                }
                if (now % 30 == 0) LgUtil.sound(z.level(), z.c(), SoundEvents.MUD_STEP, 0.8F, 0.6F);
            }
        }

        public static void clear() {
            ACTIVE.clear();
        }

        private Zones() {}
    }

    private RudeusTechniques() {}
}
