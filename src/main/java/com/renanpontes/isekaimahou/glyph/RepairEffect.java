package com.renanpontes.isekaimahou.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * "Reparar" (efeito Arcano, tier 1, todas as classes): conserta o equipamento do alvo — arma nas duas mãos e armadura —
 * em 12% da durabilidade máxima de cada peça (× poder da magia). Com Si Mesmo conserta o seu; com Toque, o de um aliado.
 * Todo jogador com classe aprende ao entrar no mundo (inclusive personagens antigos).
 */
public class RepairEffect extends EffectGlyph {
    public static final String ID = "repair";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public RepairEffect() {
        super(ID, 10);
        element(Element.ARCANE);
        tier(1);
        minCircle(1);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !(le.level() instanceof ServerLevel level)) return;
        float power = Math.max(0.25F, stats.power());
        int repaired = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = le.getItemBySlot(slot);
            if (st.isEmpty() || !st.isDamageableItem() || st.getDamageValue() <= 0) continue;
            int amount = Math.max(1, Math.round(st.getMaxDamage() * 0.12F * power));
            st.setDamageValue(Math.max(0, st.getDamageValue() - amount));
            repaired++;
        }
        double x = le.getX(), y = le.getY() + le.getBbHeight() * 0.6, z = le.getZ();
        if (repaired > 0) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 14 + repaired * 4, 0.4, 0.5, 0.4, 0.15);
            level.sendParticles(ParticleTypes.WAX_OFF, x, y, z, 10, 0.35, 0.5, 0.35, 0.05);
            level.playSound(null, x, y, z, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.35F, 1.6F);
            level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** Ensina "Reparar" a quem já tem classe (personagens criados antes deste glifo existir). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            MagicData d = MagicApi.get(sp);
            if (d.path() != MagicPath.NONE && !d.knowsGlyph(com.renanpontes.isekaimahou.IsekaiMahou.id(ID))) {
                MagicApi.learnGlyphSilently(sp, com.renanpontes.isekaimahou.IsekaiMahou.id(ID));
            }
        });
    }
}
