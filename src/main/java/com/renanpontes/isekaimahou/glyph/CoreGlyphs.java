package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponGlyphs;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do NÚCLEO (os outros vêm dos módulos):
 * <ul>
 *   <li>Formas: projectile, touch, self; v0.9 formas de ARMA strike, arrow, stance ({@code glyph.weapon})</li>
 *   <li>v0.9: gatilho reativo parry e efeito guard ({@code glyph.weapon})</li>
 *   <li>Efeitos: arcane_damage, push</li>
 *   <li>TODOS os aumentos do plano (+ v0.7: relentless)</li>
 *   <li>Gatilhos de movimento: trigger_sneak, trigger_sprint, trigger_air, trigger_water</li>
 *   <li>v0.7 — ligações entre estágios: link_hit, link_end, link_kill, link_leap</li>
 * </ul>
 */
public final class CoreGlyphs {

    /** Glifos que todo jogador já conhece ao escolher a classe (qualquer categoria). */
    public static final List<String> STARTER = List.of(
            "projectile", "touch", "self", "arcane_damage", "push", "amplify", "dampen", "accelerate", "split", "expand", "extend",
            RepairEffect.ID);
    /** v0.9: técnicas extras das classes Guerreiras (Golpe, Postura, Aparar e a Guarda da "Postura Firme"). */
    public static final List<String> STARTER_MARTIAL = List.of(WeaponGlyphs.STRIKE, WeaponGlyphs.STANCE, WeaponGlyphs.PARRY,
            WeaponGlyphs.GUARD);
    /** v0.9: técnicas extras das classes À Distância (Flecha). */
    public static final List<String> STARTER_RANGED = List.of(WeaponGlyphs.ARROW);

    /** Glifos iniciais de uma categoria: {@link #STARTER} + as técnicas de arma dela. */
    public static List<String> starter(MagicPath.Category category) {
        List<String> extra = switch (category == null ? MagicPath.Category.MAGIC : category) {
            case PHYSICAL -> STARTER_MARTIAL;
            case DEXTERITY -> STARTER_RANGED;
            default -> List.of();
        };
        if (extra.isEmpty()) return STARTER;
        List<String> out = new ArrayList<>(STARTER);
        out.addAll(extra);
        return List.copyOf(out);
    }

    // ---------- Ids v0.7 ----------
    public static final ResourceLocation LINK_HIT = IsekaiMahou.id("link_hit");
    public static final ResourceLocation LINK_END = IsekaiMahou.id("link_end");
    public static final ResourceLocation LINK_KILL = IsekaiMahou.id("link_kill");
    public static final ResourceLocation LINK_LEAP = IsekaiMahou.id("link_leap");
    public static final ResourceLocation RELENTLESS = SpellStats.RELENTLESS;

    public static void register() {
        GlyphRegistry.register(new RepairEffect());
        RepairEffect.init();
        // ---------------- Formas ----------------
        GlyphRegistry.register(new ProjectileForm());
        GlyphRegistry.register(new TouchForm());
        GlyphRegistry.register(new SelfForm());
        // v0.9: formas/gatilho/efeito de ARMA (Golpe, Flecha, Postura, Aparar, Guarda)
        WeaponGlyphs.register();

        // ---------------- Efeitos ----------------
        GlyphRegistry.register(new ArcaneDamageEffect());
        GlyphRegistry.register(new PushEffect());

        // ---------------- Aumentos: potência ----------------
        aug("amplify", 10, s -> s.amplify++);
        aug("dampen", -5, s -> s.amplify--);
        aug("concentrate", 8, s -> s.concentrate = true).tier(2);
        aug("critical", 12, s -> s.crit = true).tier(2);
        aug("vampiric", 15, s -> s.vampiric = true).minCircle(2).tier(3);
        aug("overload", 0, s -> s.overload = true).minCircle(3).tier(4);
        aug("efficient", 0, s -> s.efficient = true).tier(2);
        // ---------------- Aumentos: área e tempo ----------------
        aug("expand", 10, s -> s.aoe++);
        aug("extend", 8, s -> s.duration++);
        aug("shorten", -3, s -> s.duration--);
        aug("persistent", 15, s -> s.persistent = true).minCircle(2).tier(3);
        aug("delay", 2, s -> s.delay++).tier(2);
        aug("echo", 20, s -> s.repeat++).minCircle(2).tier(3);
        // ---------------- Aumentos: trajetória ----------------
        aug("accelerate", 4, s -> s.speed++);
        aug("decelerate", 2, s -> s.speed--);
        aug("split", 12, s -> s.split++);
        aug("scatter", 4, s -> s.spread = true).tier(2);
        aug("pierce", 10, s -> s.pierce++).tier(2);
        aug("ricochet", 8, s -> s.ricochet++).tier(3);
        aug("chain", 15, s -> s.chain++).tier(3);
        // Insaciável: o Encadear continua pulando enquanto houver inimigo e mana (ver SpellContext)
        aug("relentless", 12, s -> s.custom.merge(SpellStats.RELENTLESS, 1, Integer::sum)).tier(3);
        aug("homing", 18, s -> s.homing = true).minCircle(2).tier(4);
        aug("phantom", 12, s -> s.ghost = true).minCircle(2).tier(4);
        aug("arc", 2, s -> s.arc = true).tier(2);
        aug("sensitive", 3, s -> s.sensitive = true).tier(2);
        // ---------------- Aumentos: alvo ----------------
        aug("enemies_only", 2, s -> s.enemiesOnly = true);
        aug("allies_only", 2, s -> s.alliesOnly = true);
        aug("invert", 5, s -> s.inverted = true).tier(2);
        // ---------------- Aumentos: conjuração ----------------
        aug("charge", 10, s -> s.charge++).castTicks(15).tier(2);
        aug("channel", 10, s -> s.channel = true).tier(3);
        aug("silent", 5, s -> s.silent = true).tier(2);
        aug("fortune", 15, s -> s.fortune = true).tier(3);
        aug("silk_touch", 10, s -> s.silkTouch = true).tier(3);

        // ---------------- Ligações entre estágios (v0.7, magias programáveis) ----------------
        link(LINK_HIT, 4, LinkGlyph.LinkKind.HIT, 2);
        link(LINK_END, 4, LinkGlyph.LinkKind.END, 2);
        link(LINK_KILL, 6, LinkGlyph.LinkKind.KILL, 3);
        link(LINK_LEAP, 10, LinkGlyph.LinkKind.LEAP, 3);

        // ---------------- Gatilhos de movimento (não entram na receita) ----------------
        for (MoveTrigger t : MoveTrigger.values()) {
            if (t.glyphId() != null) GlyphRegistry.register(new TriggerGlyph(t.glyphId().getPath(), 0, false));
        }
    }

    private static AugmentGlyph aug(String id, int cost, java.util.function.Consumer<SpellStats> mod) {
        return GlyphRegistry.register(new AugmentGlyph(id, cost, mod));
    }

    private static void link(ResourceLocation id, int cost, LinkGlyph.LinkKind kind, int tier) {
        LinkGlyph g = new LinkGlyph(id.getPath(), cost, kind);
        g.tier(tier);
        GlyphRegistry.register(g);
    }

    /** Gira uma direção por desvios de guinada/arfagem (graus), na mesma convenção de shootFromRotation. */
    public static Vec3 rotate(Vec3 dir, float yawOff, float pitchOff) {
        if (yawOff == 0 && pitchOff == 0) return dir;
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + yawOff;
        float pitch = (float) (-Mth.atan2(dir.y, horiz) * Mth.RAD_TO_DEG) + pitchOff;
        float y = yaw * Mth.DEG_TO_RAD;
        float p = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    // =====================================================================================
    //  Formas do núcleo
    // =====================================================================================

    /**
     * Projétil: lança 1+ projéteis (Dividir = mais projéteis em leque; Espalhar = direções aleatórias).
     * Estágio: sai da origem do contexto; o projétil avisa impact/end (ver SpellProjectileEntity).
     */
    public static class ProjectileForm extends FormGlyph {
        public ProjectileForm() { super("projectile", 5); }

        @Override
        public boolean stageable() { return true; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            SpellStats s = ctx.formStats();
            int n = s.count();
            float speed = 1.6F * s.speedMult();
            for (int i = 0; i < n; i++) {
                float yawOff = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 10.0F;
                float pitchOff = 0;
                if (s.spread) {
                    yawOff = (ctx.random().nextFloat() - 0.5F) * 40.0F;
                    pitchOff = (ctx.random().nextFloat() - 0.5F) * 20.0F;
                }
                SpellProjectileEntity p = SpellProjectileEntity.create(ctx, caster);
                if (ctx.hasOrigin()) {
                    Vec3 d = rotate(ctx.look(), yawOff, pitchOff);
                    p.shoot(d.x, d.y, d.z, speed, 0.5F);
                } else {
                    // Sem origem própria: igual à v0.6 (herda o movimento do conjurador)
                    p.shootFromRotation(caster, caster.getXRot() + pitchOff, caster.getYRot() + yawOff, 0.0F, speed, 0.5F);
                }
                ctx.level().addFreshEntity(p);
            }
        }
    }

    /** Toque: atinge o que estiver na sua frente até 4.5 blocos (entidade ou bloco). Estágio: a partir da origem. */
    public static class TouchForm extends FormGlyph {
        public TouchForm() { super("touch", 2); }

        @Override
        public boolean stageable() { return true; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            double reach = 4.5 + ctx.formStats().aoe;
            Vec3 eye = ctx.eyePos();
            Vec3 look = ctx.look();
            Vec3 end = eye.add(look.scale(reach));
            BlockHitResult block = ctx.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
            Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            EntityHitResult ent = ProjectileUtil.getEntityHitResult(ctx.level(), caster, eye, limit,
                    new AABB(eye, limit).inflate(1.0), e -> e.isPickable() && !e.isSpectator() && e != caster && !ctx.ignores(e));
            if (ent != null) {
                ctx.impact(ent.getLocation(), look, ent.getEntity());
                ctx.resolveOnEntity(ent.getEntity(), null);
                ElementFx.impact(ctx.level(), ent.getLocation(), ctx.element(), ctx.color(),
                        com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(ctx), look.scale(-1));
            } else if (block.getType() == HitResult.Type.BLOCK) {
                ctx.impact(block.getLocation(), look, null);
                ctx.resolveOnBlock(block, null);
                ElementFx.impact(ctx.level(), block.getLocation(), ctx.element(), ctx.color(),
                        com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(ctx), Vec3.atLowerCornerOf(block.getDirection().getNormal()));
            } else {
                ctx.end(limit, look);
            }
        }
    }

    /**
     * Si Mesmo: aplica os efeitos em você (e no bloco onde você pisa, para efeitos de bloco). Não vira estágio 2+,
     * mas como estágio 0 termina na hora: "self, cura, link_end, projectile, ..." cura e depois dispara dos seus olhos.
     */
    public static class SelfForm extends FormGlyph {
        public SelfForm() { super("self", 3); }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ctx.resolveOnSelf();
            ctx.resolveOnBlock(new BlockHitResult(caster.position(), net.minecraft.core.Direction.UP,
                    caster.blockPosition().below(), false), null);
            ElementFx.burst(ctx.level(), caster.position().add(0, 1, 0), ctx.element(), 14, 0.5);
            // Visual: anel subindo do chão em volta de quem recebe a magia
            int tier = com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(ctx);
            ElementFx.shockwave(ctx.level(), caster.position().add(0, 0.1, 0), ctx.color(), 1.2F + 0.25F * tier, 10, null);
            ctx.end(ctx.eyePos(), ctx.look());
        }
    }

    // =====================================================================================
    //  Efeitos do núcleo
    // =====================================================================================

    /** Dano Arcano: dano mágico puro (6 base). */
    public static class ArcaneDamageEffect extends EffectGlyph {
        public ArcaneDamageEffect() { super("arcane_damage", 10); }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, 6.0F, stats, null);
        }
    }

    /** Empurrar: afasta o alvo do conjurador, ou do ponto de onde o estágio nasceu (Inverter = puxa). */
    public static class PushEffect extends EffectGlyph {
        public PushEffect() { super("push", 8); }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 from;
            if (ctx.hasOrigin()) from = ctx.anchor() != null ? ctx.anchor() : ctx.eyePos();
            else from = ctx.caster() != null ? ctx.caster().position() : target.position().subtract(ctx.look());
            Vec3 dir = target.position().subtract(from);
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            dir = dir.normalize();
            if (stats.inverted) dir = dir.scale(-1);
            double strength = 1.2 * stats.power();
            target.push(dir.x * strength, 0.25 + 0.1 * strength, dir.z * strength);
            target.hurtMarked = true;
        }
    }

    private CoreGlyphs() {}
}
