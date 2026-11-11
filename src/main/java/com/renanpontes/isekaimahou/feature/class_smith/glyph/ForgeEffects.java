package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgeService;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithHammerItem;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Os 7 efeitos de FORJA do Ferreiro. Todos têm dois usos:
 * <ul>
 *   <li><b>Em você mesmo</b> (forma Si Mesmo, ou qualquer forma que atinja o conjurador): mexem na Lâmina Forjada da
 *   mão principal (ou da outra mão, segurando o Martelo). Uma arma vanilla (espada, machado, maça, enxada) vira Lâmina
 *   Forjada na primeira forja. Amplificar = 2 degraus; Martelo grau III+ na mão = +1 degrau; Inverter = desfaz.</li>
 *   <li><b>Num inimigo</b>: um golpe com a mesma ideia (estocada longa, lâmina larga, fio perfurante, serra que sangra,
 *   reflexo que cega, runa que explode, arma arremessada).</li>
 * </ul>
 */
public final class ForgeEffects {

    /** Base comum: decide entre forjar (alvo = conjurador jogador) e golpear. */
    public abstract static class ForgeEffect extends EffectGlyph {
        protected ForgeEffect(String id, int cost) {
            super(id, cost);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) {
                if (target instanceof ServerPlayer sp) forgeSelf(ctx, sp, stats);
                return;
            }
            combat(ctx, target, stats);
        }

        private void forgeSelf(SpellContext ctx, ServerPlayer sp, SpellStats stats) {
            ForgeService.Target t = ForgeService.target(sp, true);
            if (t == null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.no_blade_glyph")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            ForgeService.Result r = forge(ctx, sp, t, stats, steps(sp, stats));
            if (!r.ok()) sp.displayClientMessage(r.message(), true);
        }

        /** Degraus por conjuração (negativo com Inverter). */
        protected static int steps(ServerPlayer sp, SpellStats stats) {
            int n = 1 + (stats.amplify > 0 ? 1 : 0);
            if (heldHammerTier(sp) >= 3) n++;
            return stats.inverted ? -n : n;
        }

        private static int heldHammerTier(ServerPlayer sp) {
            int best = 0;
            for (ItemStack s : new ItemStack[] {sp.getMainHandItem(), sp.getOffhandItem()}) {
                if (s.getItem() instanceof SmithHammerItem h) best = Math.max(best, h.tier());
            }
            return best;
        }

        protected abstract ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps);

        protected abstract void combat(SpellContext ctx, Entity target, SpellStats stats);

        /** Inimigos (não aliados, não o conjurador) numa esfera. */
        protected static java.util.List<LivingEntity> foesNear(SpellContext ctx, Vec3 c, double r, Entity except) {
            return ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e.isAlive()
                    && e != except && e != ctx.caster() && !ctx.isAlly(e) && e.distanceToSqr(c) <= r * r);
        }
    }

    // =====================================================================================
    //  Alongar
    // =====================================================================================
    /** Alongar: +comprimento (adaga → espada → espada longa → montante). Inimigo: estocada que atravessa a linha. */
    public static class Lengthen extends ForgeEffect {
        public static final float DAMAGE = 5.0F;

        public Lengthen() {
            super("forge_lengthen", 8);
            origin(MagicPath.SMITH);
            tier(1);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            return ForgeService.stepPart(sp, t, ForgedBlade.Part.LENGTH, steps);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 from = ctx.eyePos().subtract(0, 0.3, 0);
            Vec3 to = SmithFx.center(target);
            ctx.damage(target, DAMAGE, stats, null);
            // a lâmina estendida fere quem estiver no caminho (60%)
            Vec3 seg = to.subtract(from);
            double len = seg.length();
            if (len > 0.5) {
                Vec3 dir = seg.scale(1 / len);
                for (LivingEntity e : foesNear(ctx, from.add(seg.scale(0.5)), len / 2 + 1, target)) {
                    Vec3 rel = SmithFx.center(e).subtract(from);
                    double along = rel.dot(dir);
                    if (along < 0 || along > len) continue;
                    if (rel.subtract(dir.scale(along)).lengthSqr() <= 0.9 * 0.9) ctx.damage(e, DAMAGE * 0.6F, stats, null);
                }
            }
            SmithFx.line(sl, from, to, SmithFx.dust(SmithFx.STEEL, 0.9F), 0.25);
            SmithFx.line(sl, from, to, ParticleTypes.ELECTRIC_SPARK, 0.8);
            sl.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 10, 0.2, 0.2, 0.2, 0.3);
            SmithFx.sound(sl, to, SoundEvents.TRIDENT_HIT, 0.8F, 1.3F);
        }
    }

    // =====================================================================================
    //  Alargar
    // =====================================================================================
    /** Alargar: +largura (mais dano e repulsão, mais peso). Inimigo: lâmina larga que varre em volta do alvo. */
    public static class Widen extends ForgeEffect {
        public static final float DAMAGE = 4.0F;
        public static final float RADIUS = 2.5F;

        public Widen() {
            super("forge_widen", 9);
            origin(MagicPath.SMITH);
            tier(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            return ForgeService.stepPart(sp, t, ForgedBlade.Part.WIDTH, steps);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = SmithFx.center(target);
            ctx.damage(target, DAMAGE, stats, null);
            push(ctx, target, 0.7);
            float r = stats.radius(RADIUS);
            for (LivingEntity e : foesNear(ctx, c, r, target)) {
                ctx.damage(e, DAMAGE * 0.6F, stats, null);
                push(ctx, e, 0.5);
            }
            SmithFx.ring(sl, c.add(0, -0.4, 0), r, SmithFx.dust(SmithFx.STEEL, 1.1F), 28);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 3, r * 0.4, 0.1, r * 0.4, 0);
            SmithFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
        }

        private static void push(SpellContext ctx, Entity e, double force) {
            Vec3 d = e.position().subtract(ctx.eyePos());
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() < 1.0E-4) return;
            d = d.normalize().scale(force);
            e.push(d.x, 0.25, d.z);
            e.hurtMarked = true;
        }
    }

    // =====================================================================================
    //  Afiar Ponta
    // =====================================================================================
    /** Afiar Ponta: +ponta (perfura armadura e acerta em cheio). Inimigo: fio que ignora parte da armadura e EXPÕE. */
    public static class Sharpen extends ForgeEffect {
        public static final float DAMAGE = 4.5F;
        public static final int EXPOSE_TICKS = 80;

        public Sharpen() {
            super("forge_sharpen", 7);
            origin(MagicPath.SMITH);
            tier(1);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return EXPOSE_TICKS; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            return ForgeService.stepPart(sp, t, ForgedBlade.Part.TIP, steps);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float extra = 0F;
            if (target instanceof LivingEntity le) {
                // a ponta "acha a fresta": quanto mais armadura, mais o fio compensa
                extra = (float) Math.min(6.0, le.getArmorValue() * 0.25);
                ctx.status(le, CoreRegistries.EXPOSED, EXPOSE_TICKS, 0, stats);
            }
            ctx.damage(target, DAMAGE + extra, stats, null);
            Vec3 c = SmithFx.center(target);
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.25, 0.3, 0.25, 0.35);
            sl.sendParticles(SmithFx.dust(0xBFD8FF, 0.8F), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0);
            SmithFx.sound(sl, c, SoundEvents.ANVIL_HIT, 0.5F, 1.9F);
        }
    }

    // =====================================================================================
    //  Serrilhar
    // =====================================================================================
    /** Serrilhar: +serrilha (golpes fazem SANGRAR). Inimigo: serra que abre feridas (Sangrando, acumula). */
    public static class Serrate extends ForgeEffect {
        public static final float DAMAGE = 2.0F;
        public static final int BLEED_TICKS = 120;

        public Serrate() {
            super("forge_serrate", 9);
            origin(MagicPath.SMITH);
            tier(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return BLEED_TICKS; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            return ForgeService.stepPart(sp, t, ForgedBlade.Part.SERRATION, steps);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, DAMAGE, stats, null);
            if (target instanceof LivingEntity le && le.isAlive()) {
                int stacks = 2 + Math.max(0, stats.amplify);
                Statuses.apply(le, Statuses.Kind.BLEEDING, stacks, stats.duration(BLEED_TICKS), ctx.caster(), stats.power());
            }
            Vec3 c = SmithFx.center(target);
            ctx.level().sendParticles(SmithFx.dust(0xB0182A, 1.0F), c.x, c.y, c.z, 14, 0.3, 0.35, 0.3, 0);
            SmithFx.sound(ctx.level(), c, SoundEvents.SHEEP_SHEAR, 0.9F, 0.7F);
        }
    }

    // =====================================================================================
    //  Fosquear
    // =====================================================================================
    /** Fosquear: acabamento fosco (golpes pelas costas +25%); Inverter = polir (brilhante). Inimigo: reflexo que cega. */
    public static class Matte extends ForgeEffect {
        public static final float DAMAGE = 2.0F;
        public static final int BLIND_TICKS = 60;

        public Matte() {
            super("forge_matte", 6);
            origin(MagicPath.SMITH);
            tier(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return BLIND_TICKS; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            return ForgeService.setFinish(sp, t, steps < 0 ? ForgedBlade.Finish.BRIGHT : ForgedBlade.Finish.MATTE);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, DAMAGE, stats, null);
            if (target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stats.duration(BLIND_TICKS), 0), ctx.caster());
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(BLIND_TICKS + 20), 0), ctx.caster());
            }
            Vec3 c = SmithFx.center(target);
            ctx.level().sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
            ctx.level().sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.05);
            SmithFx.sound(ctx.level(), c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        }
    }

    // =====================================================================================
    //  Gravar Runa
    // =====================================================================================
    /**
     * Gravar Runa: grava na lâmina a runa do elemento da magia (magia Arcana: o da Essência na outra mão; gasta 1
     * essência). Golpes da lâmina causam dano extra do elemento. Amplificar também deixa o acabamento Rúnico; Inverter
     * apaga a runa. Inimigo: marca rúnica que explode em 1,5 s.
     */
    public static class Rune extends ForgeEffect {
        public static final float DAMAGE = 6.0F;
        public static final float RADIUS = 2.5F;
        public static final int FUSE = 30;

        public Rune() {
            super("forge_rune", 14);
            origin(MagicPath.SMITH);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public int hintDuration() { return FUSE; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            if (steps < 0) return ForgeService.setRune(sp, t, null);
            Element e = ctx.element() != Element.ARCANE ? ctx.element() : offhandEssence(sp);
            ForgeService.Result r = ForgeService.setRune(sp, t, e);
            if (r.ok() && stats.amplify > 0) {
                ForgeService.Target again = ForgeService.target(sp, false);
                if (again != null) ForgeService.setFinish(sp, again, ForgedBlade.Finish.RUNIC);
            }
            return r;
        }

        /** Elemento da Essência segurada na outra mão (Arcano se nenhuma). */
        private static Element offhandEssence(ServerPlayer sp) {
            Item off = sp.getOffhandItem().getItem();
            for (var e : CoreRegistries.ESSENCES.entrySet()) {
                if (e.getValue().get() == off) return e.getKey();
            }
            return Element.ARCANE;
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Element el = ctx.element();
            Vec3 c0 = SmithFx.center(target);
            sl.sendParticles(ElementFx.dust(el, 1.2F), c0.x, c0.y, c0.z, 16, 0.3, 0.4, 0.3, 0);
            sl.sendParticles(ParticleTypes.ENCHANT, c0.x, c0.y + 0.6, c0.z, 20, 0.4, 0.4, 0.4, 0.4);
            SmithFx.sound(sl, c0, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.4F);
            float r = stats.radius(RADIUS);
            SpellScheduler.schedule(FUSE, () -> {
                if (target.level() != sl) return;
                Vec3 c = target.isAlive() ? SmithFx.center(target) : c0;
                if (target.isAlive()) ctx.damage(target, DAMAGE, stats, null);
                for (LivingEntity e : foesNear(ctx, c, r, target)) ctx.damage(e, DAMAGE * 0.5F, stats, null);
                ElementFx.burst(sl, c, el, 30, r * 0.5);
                sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                SmithFx.ring(sl, c.add(0, -0.5, 0), r, ElementFx.dust(el, 1.0F), 24);
                SmithFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.5F);
            });
        }
    }

    // =====================================================================================
    //  Reforjar
    // =====================================================================================
    /**
     * Reforjar: refaz a lâmina em outra FAMÍLIA (lâmina → machado → lança → martelo → foice → lâmina), mantendo as
     * partes; Inverter volta uma. Inimigo: arremessa uma cópia espectral da arma da mão (dano pela arma).
     */
    public static class Reforge extends ForgeEffect {
        public static final float DAMAGE = 4.0F;

        public Reforge() {
            super("forge_reforge", 12);
            origin(MagicPath.SMITH);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        protected ForgeService.Result forge(SpellContext ctx, ServerPlayer sp, ForgeService.Target t, SpellStats stats, int steps) {
            BladeFamily f = ForgedBladeItem.get(t.stack()).family();
            int jumps = Math.abs(steps) >= 2 && stats.amplify > 0 ? 2 : 1;
            for (int i = 0; i < jumps; i++) f = steps < 0 ? f.previous() : f.next();
            return ForgeService.setFamily(sp, t, f);
        }

        @Override
        protected void combat(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            LivingEntity caster = ctx.caster();
            ItemStack held = caster == null ? ItemStack.EMPTY : caster.getMainHandItem();
            float base = DAMAGE;
            if (caster != null && caster.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                base = (float) Math.max(DAMAGE, Math.min(14.0, caster.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7));
            }
            final float dmg = base;
            Vec3 from = ctx.eyePos();
            Vec3 to = SmithFx.center(target);
            int travel = Math.max(2, Math.min(10, (int) (from.distanceTo(to) / 2.5)));
            ItemStack shown = held.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.IRON_SWORD) : held.copy();
            for (int i = 0; i <= travel; i++) {
                final int step = i;
                SpellScheduler.schedule(i, () -> {
                    Vec3 p = from.add(to.subtract(from).scale(step / (double) travel));
                    sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0.01);
                    sl.sendParticles(SmithFx.dust(SmithFx.STEEL, 0.8F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                });
            }
            SmithFx.sound(sl, from, SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.1F);
            SpellScheduler.schedule(travel, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                ctx.damage(target, dmg, stats, null);
                sl.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 12, 0.25, 0.3, 0.25, 0.3);
                SmithFx.sound(sl, to, SoundEvents.TRIDENT_HIT, 0.9F, 0.9F);
            });
        }
    }

    private ForgeEffects() {}
}
