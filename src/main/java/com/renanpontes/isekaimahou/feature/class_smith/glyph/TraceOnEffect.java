package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithBladeEntity;
import com.renanpontes.isekaimahou.feature.class_smith.item.ProjectionItem;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Traçar: Ligar" (homenagem ao "Trace On" do arqueiro de lâminas; Arcano, origem do Ferreiro): PROJEÇÃO.
 * <ul>
 *   <li><b>Em você</b> (Si Mesmo): lê a arma de quem você está olhando (criatura, item no chão ou num quadro, até 24
 *   blocos) e projeta uma CÓPIA dela na sua mão — bate igual à original (modificadores e encantamentos) e some depois de
 *   30 s (Estender +). Sem nada na mira: projeta uma espada de ferro. Até 3 projeções ao mesmo tempo.</li>
 *   <li><b>Num inimigo</b>: copia a arma DELE e a lança de volta — 6 de dano (+50% se ele estava armado: a própria arma
 *   contra ele) — e a projeção também fica na sua mão (se houver espaço).</li>
 * </ul>
 */
public class TraceOnEffect extends EffectGlyph {
    public static final float DAMAGE = 6.0F;
    public static final int TICKS = 600;
    public static final double READ_RANGE = 24.0;
    public static final int COLOR = 0x9FD8FF;

    public TraceOnEffect() {
        super("smith_trace_on", 12);
        element(Element.ARCANE);
        origin(MagicPath.SMITH);
        tier(2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        if (target == caster) {
            ItemStack seen = readGaze(ctx, caster, sl);
            ItemStack copy = seen.isEmpty() ? new ItemStack(Items.IRON_SWORD) : seen;
            give(sl, caster, copy, stats);
            if (!stats.silent) Announce.send(caster, Component.translatable("announce.isekai_mahou.smith_trace_on"), COLOR, Announce.STYLE_SPELL);
            return;
        }
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ItemStack theirs = le.getMainHandItem();
        boolean armed = !theirs.isEmpty() && theirs.getItem() != Items.AIR;
        ItemStack copy = armed ? theirs.copyWithCount(1) : new ItemStack(Items.IRON_SWORD);
        float dmg = DAMAGE * (armed ? 1.5F : 1.0F);
        Vec3 tc = le.position().add(0, le.getBbHeight() * 0.5, 0);
        Vec3 src = ctx.eyePos();
        Vec3 back = tc.subtract(src);
        if (back.lengthSqr() < 1.0E-3) back = ctx.look();
        Vec3 from = src.add(0, 1.6, 0).subtract(back.normalize().scale(1.2));
        if (SmithBladeEntity.hasRoom(sl, ctx.casterId(), 1)) {
            sl.addFreshEntity(SmithBladeEntity.strike(sl, ctx, from, tc.subtract(from), copy, COLOR, 1.2F, 1.6F, 1,
                    (blade, victim) -> ctx.damage(victim, dmg, stats, blade)));
        } else {
            ctx.damage(le, dmg, stats, null);
            SmithFx.line(sl, from, tc, SmithFx.dust(COLOR, 0.8F), 0.4);
        }
        sl.sendParticles(SmithFx.dust(COLOR, 1.2F), from.x, from.y, from.z, 16, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(ParticleTypes.ENCHANT, tc.x, tc.y, tc.z, 20, 0.4, 0.5, 0.4, 0.6);
        SmithFx.line(sl, tc, from, SmithFx.dust(0xD8F0FF, 0.6F), 0.5);
        SmithFx.sound(sl, from, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.6F);
        if (armed && caster instanceof Player) give(sl, caster, copy, stats);
    }

    /** O que está na mira: arma de uma criatura, item no chão ou num quadro. Vazio se nada. */
    private static ItemStack readGaze(SpellContext ctx, LivingEntity caster, ServerLevel sl) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(READ_RANGE));
        HitResult block = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sl, caster, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e != caster && e.isAlive() && (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ItemFrame), 0.4F);
        if (hit == null) return ItemStack.EMPTY;
        Entity e = hit.getEntity();
        if (e instanceof LivingEntity le) return le.getMainHandItem().copyWithCount(1);
        if (e instanceof ItemEntity ie) return ie.getItem().copyWithCount(1);
        if (e instanceof ItemFrame f) return f.getItem().copyWithCount(1);
        return ItemStack.EMPTY;
    }

    /** Põe a projeção na mão vazia ou no inventário (no máximo {@link ProjectionItem#MAX_HELD}). */
    private static void give(ServerLevel sl, LivingEntity caster, ItemStack source, SpellStats stats) {
        if (!(caster instanceof Player p)) return;
        ItemStack proj = ProjectionItem.create(source, sl.getGameTime() + stats.duration(TICKS));
        trimOld(p);
        boolean placed;
        if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, proj);
            placed = true;
        } else {
            placed = p.getInventory().add(proj);
        }
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.7)).subtract(0, 0.4, 0);
        sl.sendParticles(SmithFx.dust(COLOR, 1.0F), hand.x, hand.y, hand.z, 20, 0.25, 0.25, 0.25, 0);
        sl.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0.03);
        SmithFx.sound(sl, hand, SoundEvents.ANVIL_USE, 0.5F, 1.8F);
        if (p instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(placed ? "message.isekai_mahou.class_smith.trace_on"
                    : "message.isekai_mahou.class_smith.trace_full", source.getHoverName()).withColor(COLOR), true);
        }
    }

    /** Mantém no máximo MAX_HELD − 1 projeções antes de criar outra (some a que vence primeiro). */
    private static void trimOld(Player p) {
        Inventory inv = p.getInventory();
        while (true) {
            int count = 0;
            int oldest = -1;
            long oldestUntil = Long.MAX_VALUE;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!(s.getItem() instanceof ProjectionItem)) continue;
                count++;
                Long u = s.get(com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries.PROJECTION_UNTIL.get());
                long uu = u == null ? 0 : u;
                if (uu < oldestUntil) {
                    oldestUntil = uu;
                    oldest = i;
                }
            }
            if (count < ProjectionItem.MAX_HELD || oldest < 0) return;
            inv.setItem(oldest, ItemStack.EMPTY);
        }
    }
}
