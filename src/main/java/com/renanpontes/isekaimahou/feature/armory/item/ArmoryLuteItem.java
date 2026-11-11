package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.armory.ArmoryAllies;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;

/**
 * Alaúde do Bardo: instrumento e foco mágico (Poder Mágico, Foco máximo e regen como um cajado).
 * Segurar o botão direito = TOCAR: uma melodia de notas (o timbre melhora com o tier), notas coloridas no ar e +1 de
 * Foco por segundo. Tocar também conta como "mirando" para o Foco do núcleo (agachado e parado) e é o que a forma
 * "Canção" do classes_ranged procura (jogador usando um item da tag {@code #isekai_mahou:instruments}).
 * Tier IV — Balada Ressonante: a cada 2 s tocando, você e os aliados a até 8 blocos ganham Regeneração I e Velocidade I.
 */
public class ArmoryLuteItem extends Item implements ArmoryWeapon {
    /** Uma nota a cada 5 ticks (4 por segundo). */
    public static final int NOTE_INTERVAL = 5;
    public static final float FOCUS_PER_SECOND = 1.0F;
    public static final int AURA_INTERVAL = 40;
    public static final double AURA_RADIUS = 8.0;
    public static final int AURA_TICKS = 70;
    /** Melodia em pentatônica (0..24 como no bloco musical; 12 = Fá#). */
    private static final int[] MELODY = {6, 10, 13, 15, 18, 15, 13, 10, 8, 11, 13, 18, 20, 18, 15, 13,
            6, 8, 10, 13, 15, 13, 10, 8, 11, 13, 15, 18, 15, 13, 11, 10};

    private final WeaponKind kind;
    private final int tier;

    public ArmoryLuteItem(WeaponKind kind, int tier, Properties props) {
        super(props.stacksTo(1).attributes(ArmoryWeapon.attributes(kind, tier)));
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public WeaponKind kind() {
        return kind;
    }

    @Override
    public int tier() {
        return tier;
    }

    /** true se a entidade está tocando um alaúde agora. */
    public static boolean isPlaying(LivingEntity e) {
        return e != null && e.isUsingItem() && e.getUseItem().getItem() instanceof ArmoryLuteItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // A pose de "tocar" (dois braços segurando o alaúde) vem do cliente (IClientItemExtensions)
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int t = getUseDuration(stack, entity) - remaining;
        if (t % NOTE_INTERVAL == 0) playNote(server, entity, t / NOTE_INTERVAL);
        if (t > 0 && t % 20 == 0 && entity instanceof ServerPlayer sp) ClassResources.gain(sp, FOCUS_PER_SECOND);
        if (tier >= WeaponKind.MAX_TIER && t > 0 && t % AURA_INTERVAL == 0) resonantBallad(server, entity);
    }

    /** Uma nota da melodia: som (timbre por tier) + nota colorida subindo da caixa do alaúde. */
    private void playNote(ServerLevel level, LivingEntity e, int step) {
        int note = MELODY[Math.floorMod(step, MELODY.length)];
        float pitch = NoteBlock.getPitchFromNote(note);
        Holder<SoundEvent> lead = tier >= 3 ? SoundEvents.NOTE_BLOCK_HARP : SoundEvents.NOTE_BLOCK_GUITAR;
        level.playSound(null, e.getX(), e.getY() + 1, e.getZ(), lead.value(), SoundSource.PLAYERS, 0.75F, pitch);
        boolean beat = step % 4 == 0;
        if (beat && tier >= 2) {
            // Baixo no tempo forte (o acorde do alaúde de adepto em diante)
            level.playSound(null, e.getX(), e.getY() + 1, e.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS,
                    0.5F, NoteBlock.getPitchFromNote(Math.max(0, note - 12)));
        }
        if (beat && tier >= 3) {
            level.playSound(null, e.getX(), e.getY() + 1, e.getZ(),
                    (tier >= 4 ? SoundEvents.NOTE_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_BELL).value(), SoundSource.PLAYERS,
                    0.35F, pitch);
        }
        double yaw = Math.toRadians(e.yBodyRot);
        double x = e.getX() - Math.sin(yaw) * 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
        double z = e.getZ() + Math.cos(yaw) * 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
        // NOTE com contagem 0: o primeiro "deslocamento" é a cor da nota (0..1), como no bloco musical
        level.sendParticles(ParticleTypes.NOTE, x, e.getY() + 1.3 + level.random.nextDouble() * 0.4, z, 0,
                note / 24.0, 0, 0, 1);
    }

    /** Balada Ressonante (tier IV): regeneração e velocidade para quem está perto. */
    private void resonantBallad(ServerLevel level, LivingEntity bard) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, bard.getBoundingBox().inflate(AURA_RADIUS),
                e -> e.isAlive() && (e == bard || ArmoryAllies.isFriend(bard, e)) && e.distanceToSqr(bard) <= AURA_RADIUS * AURA_RADIUS);
        for (LivingEntity e : near) {
            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, AURA_TICKS, 0, true, true), bard);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, AURA_TICKS, 0, true, true), bard);
            level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 0,
                    level.random.nextDouble(), 0, 0, 1);
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level.sendParticles(ParticleTypes.NOTE, bard.getX() + Math.cos(a) * 2.5, bard.getY() + 0.4, bard.getZ() + Math.sin(a) * 2.5,
                    0, i / 16.0, 0, 0, 1);
        }
        level.playSound(null, bard.getX(), bard.getY(), bard.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.5F);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ArmoryWeapon.appendTooltip(this, getDescriptionId() + ".desc", tooltip);
    }
}
