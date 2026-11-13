package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.Transformations;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ForestGodEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.KodamaEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de origem do DRUIDA: Deus da Floresta (homenagem ao Shishigami de Princesa Mononoke), Modo Sábio (homenagem
 * ao Senjutsu de Naruto) e Chamado dos Kodama (os espíritos da floresta de Mononoke).
 */
public final class DruidTechniques {
    public static List<EffectGlyph> all() {
        return List.of(new ForestGod(), new SageMode(), new Kodama());
    }

    // =====================================================================================
    //  Deus da Floresta
    // =====================================================================================
    /**
     * O Deus da Floresta caminha pela área por 12 s: a cada passo, flores brotam; a cada segundo, aliados a até 9 blocos
     * recuperam vida (e perdem venenos) e os inimigos têm 7 de vida arrancada. Com Inverter ele só tira: 11 por segundo.
     */
    public static class ForestGod extends EffectGlyph {
        public ForestGod() {
            super("druid_forest_god", 70);
            element(Element.EARTH);
            origin(MagicPath.DRUID);
            legendary();
            minCircle(7);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return ForestGodEntity.DAMAGE * 12; }

        @Override
        public float baseHeal() { return ForestGodEntity.HEAL * 12; }

        @Override
        public float hintRadius() { return 9F; }

        @Override
        public int hintDuration() { return 240; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 at = target == ctx.caster() ? ctx.caster().position().add(ctx.look().multiply(1, 0, 1).scale(3)) : target.position();
            summon(ctx, LfUtil.groundBelow(ctx.level(), at.add(0, 2, 0), 10), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            Vec3 p = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
            summon(ctx, LfUtil.groundBelow(ctx.level(), p.add(0, 1, 0), 10), stats);
        }

        private void summon(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!LfUtil.oncePerTick(ctx, "forest_god")) return;
            float radius = Mth.clamp(stats.radius(9F), 4F, 20F);
            int life = Mth.clamp(stats.duration(240), 100, 800);
            Vec3 look = ctx.look();
            float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
            ForestGodEntity.spawn(ctx, at, yaw, radius, life, stats, stats.inverted);
            if (ctx.caster() != null) {
                LfUtil.banner(ctx.caster(), stats.inverted ? "announce.isekai_mahou.legends_f.forest_god_death"
                        : "announce.isekai_mahou.legends_f.forest_god", stats.inverted ? 0x6B7A4A : 0xBFF0C8, true);
            }
        }
    }

    // =====================================================================================
    //  Modo Sábio
    // =====================================================================================
    /**
     * Fique PARADO 3 s absorvendo a energia natural (andar desfaz). Depois, 40 s de Modo Sábio: pigmento laranja nos
     * olhos, +0,35 de Poder Mágico, +4 de ataque, +4 de armadura, mais velocidade e mana; sente os inimigos a até 20
     * blocos e cada golpe corpo a corpo solta uma onda de energia natural (dano em volta do alvo).
     */
    public static class SageMode extends EffectGlyph {
        public SageMode() {
            super("druid_sage_mode", 20);
            element(Element.EARTH);
            origin(MagicPath.DRUID);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 800; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof ServerPlayer sp) || target != ctx.caster() || LfUtil.isFake(sp)) return;
            if (!LfUtil.oncePerTick(ctx, "sage")) return;
            Transformations.startMeditation(sp, Mth.clamp(stats.duration(800), 300, 2400));
        }
    }

    // =====================================================================================
    //  Chamado dos Kodama
    // =====================================================================================
    /**
     * Chama 3 Kodama (Dividir: +1 cada; máx. 8 por druida) por 90 s: seguem você balançando a cabeça, a cada 3 s curam o
     * aliado mais ferido por perto (o dobro perto de árvores) e prendem em raízes quem te ferir.
     */
    public static class Kodama extends EffectGlyph {
        public static final float HEAL = 2F;

        public Kodama() {
            super("druid_kodama", 12);
            element(Element.EARTH);
            origin(MagicPath.DRUID);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseHeal() { return HEAL; }

        @Override
        public int hintDuration() { return 1800; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (owner == null || LfUtil.isFake(owner) || !LfUtil.oncePerTick(ctx, "kodama")) return;
            ServerLevel level = ctx.level();
            int want = 3 + Math.max(0, stats.split);
            List<KodamaEntity> mine = level.getEntitiesOfClass(KodamaEntity.class, owner.getBoundingBox().inflate(64),
                    k -> owner.getUUID().equals(k.getOwnerUUID()));
            int room = 8 - mine.size();
            if (room <= 0) {
                LfUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_f.kodama_full").withStyle(ChatFormatting.GREEN));
                return;
            }
            int n = Math.min(want, room);
            float heal = HEAL * LfUtil.power(ctx, stats);
            int life = Mth.clamp(stats.duration(1800), 400, 6000);
            Vec3 base = target == owner ? owner.position() : target.position();
            for (int i = 0; i < n; i++) {
                KodamaEntity k = LfRegistry.KODAMA.get().create(level);
                if (k == null) return;
                double a = Math.PI * 2 * i / n + level.random.nextDouble();
                Vec3 p = LfUtil.groundBelow(level, base.add(Math.cos(a) * 1.6, 1.5, Math.sin(a) * 1.6), 5);
                k.moveTo(p.x, p.y, p.z, level.random.nextFloat() * 360F, 0);
                if (!level.noCollision(k, k.getBoundingBox())) k.moveTo(base.x, base.y, base.z, 0, 0);
                k.setup(owner, life, heal);
                level.addFreshEntity(k);
                level.sendParticles(Vfx.sporeAir(), p.x, p.y + 0.4, p.z, 6, 0.2, 0.3, 0.2, 0.01);
                com.renanpontes.isekaimahou.core.ElementFx.impactFx(level, p.add(0, 0.2, 0), Element.NATURE, 0x9AE070, 1, null, 0.6F);
            }
            LfUtil.sound(level, base, SoundEvents.BAMBOO_WOOD_HIT, 1.0F, 1.8F);
            LfUtil.sound(level, base, SoundEvents.AZALEA_LEAVES_PLACE, 1.0F, 1.2F);
            LfUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_f.kodama_on", n).withStyle(ChatFormatting.GREEN));
        }
    }

    private DruidTechniques() {}
}
