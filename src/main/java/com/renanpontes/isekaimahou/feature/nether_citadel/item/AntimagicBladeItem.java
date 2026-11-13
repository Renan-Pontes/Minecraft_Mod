package com.renanpontes.isekaimahou.feature.nether_citadel.item;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcDemons;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lâmina Antimagia (Black Clover): espadão negro e pesado.
 * <ul>
 *   <li>Golpe: arranca até 3 efeitos BONS do alvo (escudos, força, regeneração...);</li>
 *   <li>Segurando: magias inimigas causam 30% menos dano em você ({@code NcEvents});</li>
 *   <li>Usar (botão direito): <b>Corte Antimagia</b> em leque à frente — destrói projéteis e construções mágicas inimigas
 *       (zonas, muros, runas, orbes...), fere e desfaz os buffs de quem estiver no corte. Recarga 5 s.</li>
 * </ul>
 */
public class AntimagicBladeItem extends SwordItem {
    public static final int COOLDOWN = 100;
    private static final double RANGE = 5.5;
    private static final double MIN_DOT = 0.57;

    public AntimagicBladeItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.swing(hand);
        if (level instanceof ServerLevel sl) {
            int cut = slash(sl, player);
            player.displayClientMessage(Component.translatable("item.isekai_mahou.demon_antimagic_blade.cut", cut)
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(hand));
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Corte em leque: devolve quantas magias/projéteis foram anulados. */
    private static int slash(ServerLevel sl, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int cut = 0;
        for (Entity e : sl.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 to = c.subtract(eye);
            if (to.length() > RANGE + e.getBbWidth() * 0.5 || (to.lengthSqr() > 1.0 && to.normalize().dot(look) < MIN_DOT)) continue;
            if (e instanceof Projectile proj) {
                if (proj.getOwner() == player) continue;
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.15, 0.15, 0.15, 0.02);
                e.discard();
                cut++;
            } else if (isSpellConstruct(e)) {
                if (ownedBy(e, player)) continue;
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 16, 0.4, 0.4, 0.4, 0.03);
                e.discard();
                cut++;
            } else if (NcDemons.isEnemyOf(player, e)) {
                LivingEntity le = (LivingEntity) e;
                le.hurt(player.damageSources().playerAttack(player), 6.0F);
                cut += dispel(le, 5);
            }
        }
        // Arco escuro do corte.
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        for (int i = -6; i <= 6; i++) {
            double a = i / 6.0;
            Vec3 p = eye.add(look.scale(2.8 - Math.abs(a) * 0.8)).add(right.scale(a * 2.6)).add(0, -0.3 - a * 0.25, 0);
            sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, i % 3 == 0 ? 1 : 0, 0, 0, 0, 0);
        }
        sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
        if (cut > 0) sl.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        return cut;
    }

    /** Entidades de magia do mod (spell_zone, spell_wall, spell_rune, spell_orb, domain_dome...). */
    private static boolean isSpellConstruct(Entity e) {
        ResourceLocation id = EntityType.getKey(e.getType());
        return id.getNamespace().equals(IsekaiMahou.MODID) && (id.getPath().startsWith("spell_") || id.getPath().equals("domain_dome"));
    }

    private static boolean ownedBy(Entity e, Player player) {
        if (e instanceof TraceableEntity t && t.getOwner() == player) return true;
        return e instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerUUID());
    }

    /** Remove até {@code max} efeitos benéficos; devolve quantos saíram. */
    public static int dispel(LivingEntity target, int max) {
        List<Holder<MobEffect>> good = new ArrayList<>();
        for (MobEffectInstance inst : target.getActiveEffects()) {
            if (inst.getEffect().value().isBeneficial()) good.add(inst.getEffect());
        }
        int n = 0;
        for (Holder<MobEffect> h : good) {
            if (n >= max) break;
            if (target.removeEffect(h)) n++;
        }
        if (n > 0 && target.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    10, 0.3, 0.4, 0.3, 0.02);
        }
        return n;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        NcLoreItem.lore(stack, lines);
        lines.add(Component.translatable("item.isekai_mahou.demon_antimagic_blade.passive").withStyle(ChatFormatting.DARK_AQUA));
    }
}
