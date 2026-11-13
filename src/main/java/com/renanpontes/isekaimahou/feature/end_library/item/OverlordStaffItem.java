package com.renanpontes.isekaimahou.feature.end_library.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgFx;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ObSummonEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cajado do Rei Supremo (inspirado em "Overlord"):
 * <ul>
 *   <li><b>Usar:</b> ergue 3 Guerreiros de Osso aliados por 60 s (40 de mana; recarga 45 s);</li>
 *   <li><b>Agachado + usar:</b> "Queda do Céu" — marca um círculo onde você mira e, 3 s depois, um pilar de luz cai do
 *       céu ferindo tudo que não é aliado (exige Círculo 6; 120 de mana; recarga própria de 5 min).</li>
 * </ul>
 * Os guerreiros são as invocações {@code isekai_mahou:bone_warrior} (módulo origins_b); sem ele, usa o Servo Morto-vivo.
 */
public class OverlordStaffItem extends Item {
    public static final int SUMMON_MANA = 40;
    public static final int SKYFALL_MANA = 120;
    public static final int SKYFALL_CIRCLE = 6;
    private static final int SUMMON_COOLDOWN = 900;
    private static final int SKYFALL_COOLDOWN = 6000;
    private static final String SKYFALL_KEY = "isekai_mahou.end_library.skyfall_until";
    private static final int HOLY = 0xFFF2B0;

    public OverlordStaffItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        boolean ok = player.isShiftKeyDown() ? skyfall(sl, player) : summon(sl, player);
        return ok ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    private boolean pay(Player player, MagicData data, int cost) {
        if (player.getAbilities().instabuild || data.consumeMana(cost)) return true;
        player.displayClientMessage(Component.translatable("message.isekai_mahou.end_library.no_mana", cost).withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ---------------- Exército dos mortos
    private boolean summon(ServerLevel sl, Player player) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("bone_warrior"))
                .or(() -> BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("undead_servant"))).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("item.isekai_mahou.void_overlord_staff.no_undead").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        MagicData data = MagicApi.get(player);
        if (!pay(player, data, SUMMON_MANA)) return false;
        float power = 1.0F + Math.max(0, data.circle() - 3) * 0.15F;
        int raised = 0;
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + player.getYRot() * Math.PI / 180;
            BlockPos at = BlockPos.containing(player.getX() + Math.cos(a) * 2.2, player.getY(), player.getZ() + Math.sin(a) * 2.2);
            Entity e = type.spawn(sl, at, MobSpawnType.MOB_SUMMONED);
            if (e instanceof ObSummonEntity s) {
                s.setup(player, 1200, power);
                raised++;
                sl.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.5, e.getZ(), 14, 0.3, 0.5, 0.3, 0.03);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
            } else if (e != null) {
                e.discard(); // invocação sem dono viraria inimiga: não deixa
            }
        }
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
        player.displayClientMessage(Component.translatable("item.isekai_mahou.void_overlord_staff.raised", raised).withStyle(ChatFormatting.DARK_PURPLE), true);
        player.getCooldowns().addCooldown(this, SUMMON_COOLDOWN);
        return raised > 0;
    }

    // ---------------- Queda do Céu
    private boolean skyfall(ServerLevel sl, Player player) {
        MagicData data = MagicApi.get(player);
        if (data.circle() < SKYFALL_CIRCLE && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("item.isekai_mahou.void_overlord_staff.need_circle", SKYFALL_CIRCLE)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        long now = sl.getGameTime();
        long until = player.getPersistentData().getLong(SKYFALL_KEY);
        if (now < until && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("item.isekai_mahou.void_overlord_staff.skyfall_cd", (until - now) / 20 + 1)
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!pay(player, data, SKYFALL_MANA)) return false;
        player.getPersistentData().putLong(SKYFALL_KEY, now + SKYFALL_COOLDOWN);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(48));
        HitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        float damage = 30F + data.circle() * 3F;
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        player.displayClientMessage(Component.translatable("item.isekai_mahou.void_overlord_staff.skyfall").withStyle(ChatFormatting.GOLD), true);
        // O céu "abre" acima do alvo durante o aviso.
        for (int i = 0; i < 6; i++) {
            BossKit.schedule(sl, i * 10, () -> BgFx.ring(sl, at.add(0, 18, 0), 3.0 + Math.random(), 24, BgFx.dust(HOLY, 2.0F), 0.0));
        }
        Telegraph.delayed(sl, Telegraph.Shape.circle(at, 7.0F).warmup(60).color(Telegraph.HOLY).owner(player)
                .castName(Component.translatable("item.isekai_mahou.void_overlord_staff.skyfall_name")), hits -> {
                    for (LivingEntity e : hits) {
                        if (e == player || e instanceof Player) continue;
                        if (e instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())) continue;
                        // v1.0: cidades são sagradas — dentro de zona segura só inimigos de verdade são atingidos
                        if (!(e instanceof Enemy) && Places.isSafe(sl, e.blockPosition())) continue;
                        if (e.hurt(sl.damageSources().indirectMagic(player, player), damage)) e.igniteForSeconds(5);
                    }
                    BgFx.line(sl, at.add(0, 30, 0), at, ParticleTypes.END_ROD, 3.0);
                    BgFx.line(sl, at.add(0.4, 30, 0.4), at.add(0.4, 0, 0.4), BgFx.dust(HOLY, 3.0F), 3.0);
                    BgFx.sphere(sl, at.add(0, 1, 0), 6.0, 70, BgFx.dust(HOLY, 2.2F));
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
                    sl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.8F);
                    BossKit.shake(sl, at, 24, 1.0F, 16);
                    BossKit.flashNear(sl, at, 24, HOLY, 0.4F, 10);
                });
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.void_overlord_staff.desc").withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable("item.isekai_mahou.void_overlord_staff.use", SUMMON_MANA).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.isekai_mahou.void_overlord_staff.use_sneak", SKYFALL_CIRCLE, SKYFALL_MANA)
                .withStyle(ChatFormatting.GRAY));
    }
}
