package com.renanpontes.isekaimahou.feature.class_gambler.luck;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfits;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Bônus de classe do Apostador (passivos) e as regras dos efeitos Sortudo/Azarado:
 * <ul>
 *   <li><b>A Sorte pende para ele</b>: rolagens melhores (+1 de Sorte, +1 por rank) e +Sorte vanilla nos saques;</li>
 *   <li><b>Esquiva da Sorte</b>: 5% (+1% por rank, +3% com o traje completo) de anular um golpe de criatura;</li>
 *   <li><b>Fortuna</b>: inimigos abatidos por ele às vezes deixam cair uma Ficha de Cassino (3% +1% por rank);</li>
 *   <li><b>Tensão</b> e <b>Jackpot</b>: ver {@link LuckService} e {@link JackpotService}.</li>
 * </ul>
 * Sortudo: +5% de esquiva por nível. Azarado: recebe +15% (+5% por nível) de dano e erra 20% (+5%) dos golpes.
 */
public final class GamblerPassives {
    private static final ResourceLocation CLASS_LUCK = IsekaiMahou.id("gambler_class_luck");
    private static final float DODGE_CAP = 0.25F;

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getAmount() <= 0) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // Azarado atacando: erra o golpe
        if (src.getEntity() instanceof LivingEntity attacker && src.getDirectEntity() == attacker) {
            MobEffectInstance jinx = attacker.getEffect(GbRegistries.JINXED);
            if (jinx != null && target.getRandom().nextFloat() < 0.20F + 0.05F * jinx.getAmplifier()) {
                event.setCanceled(true);
                miss((ServerLevel) target.level(), target, "message.isekai_mahou.gambler.jinx_miss");
                return;
            }
        }
        // Esquiva da Sorte (só golpes vindos de alguém: queda, fogo e afogamento não se esquivam)
        if (src.getEntity() != null) {
            float dodge = dodgeChance(target);
            if (dodge > 0 && target.getRandom().nextFloat() < dodge) {
                event.setCanceled(true);
                miss((ServerLevel) target.level(), target, "message.isekai_mahou.gambler.dodge");
                return;
            }
        }
        MobEffectInstance jinxed = target.getEffect(GbRegistries.JINXED);
        if (jinxed != null) event.setAmount(event.getAmount() * (1.15F + 0.05F * jinxed.getAmplifier()));
    }

    /** Chance de esquiva da sorte de uma entidade (0..0,25). */
    public static float dodgeChance(LivingEntity e) {
        float c = 0F;
        if (e instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            if (d.path() == MagicPath.GAMBLER) {
                c += 0.05F + 0.01F * HouseRanks.rank(d);
                if (GamblerOutfits.fullSet(sp)) c += 0.03F;
            }
        }
        MobEffectInstance lucky = e.getEffect(GbRegistries.LUCKY);
        if (lucky != null) c += 0.05F * (1 + Math.min(2, lucky.getAmplifier()));
        return Math.min(DODGE_CAP, c);
    }

    private static void miss(ServerLevel level, LivingEntity who, String key) {
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, who.getX(), who.getY() + who.getBbHeight() * 0.6, who.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(GbFx.dust(GbFx.GOLD, 0.9F), who.getX(), who.getY() + who.getBbHeight() + 0.3, who.getZ(), 5, 0.2, 0.1, 0.2, 0);
        GbFx.sound(level, who.position(), SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 2.0F);
        if (who instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
    }

    /** Fichas que caem dos inimigos abatidos pelo Apostador. */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(dead instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GAMBLER) return;
        float chance = 0.03F + 0.01F * HouseRanks.rank(d);
        if (sp.getRandom().nextFloat() >= chance) return;
        ServerLevel level = sp.serverLevel();
        ItemEntity chip = new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), new ItemStack(GbRegistries.CASINO_CHIP.get()));
        chip.setDefaultPickUpDelay();
        level.addFreshEntity(chip);
        GbFx.coins(level, dead.position().add(0, 0.8, 0), 4);
    }

    /** Abate por magia logo depois de uma rolagem boa = "abate de sorte" (requisito da Casa). */
    public static void onSpellDamage(SpellDamageEvent.Post event) {
        if (!event.killed() || !(event.ctx().caster() instanceof ServerPlayer sp)) return;
        if (LuckService.isGambler(sp) && LuckService.recentGoodRoll(sp)) MagicApi.addCounter(sp, LuckService.LUCKY_KILL, 1);
    }

    /** A cada 5 s: Sorte vanilla da classe (+1, +0,5 por rank) — saques melhores. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 100 != 0) return;
        AttributeInstance luck = sp.getAttribute(Attributes.LUCK);
        if (luck == null) return;
        MagicData d = MagicApi.get(sp);
        double want = d.path() == MagicPath.GAMBLER ? 1.0 + 0.5 * HouseRanks.rank(d) : 0.0;
        AttributeModifier cur = luck.getModifier(CLASS_LUCK);
        if (cur != null && cur.amount() == want) return;
        luck.removeModifier(CLASS_LUCK);
        if (want > 0) luck.addTransientModifier(new AttributeModifier(CLASS_LUCK, want, AttributeModifier.Operation.ADD_VALUE));
    }

    private GamblerPassives() {}
}
