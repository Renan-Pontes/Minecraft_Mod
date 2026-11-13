package com.renanpontes.isekaimahou.feature.transform_laws;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo: o efeito "Votos Selados" (só o ícone na tela; nível = nº de leis − 1). */
final class TlawContent {
    static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    /** Ícone/indicador enquanto há Leis de Transformação valendo. Sem atributos: as regras estão em {@link TlawRules}. */
    static final DeferredHolder<MobEffect, MobEffect> VOW = EFFECTS.register("transform_vow", VowEffect::new);

    /**
     * Release 1.0: marca da forma Transformação conjurada (ícone com o tempo que falta). Sem atributos: os efeitos da
     * receita são aplicados por {@link ForgeSessions}; a sessão acaba quando esta marca some.
     */
    static final DeferredHolder<MobEffect, MobEffect> FORGED = EFFECTS.register("forged_form", ForgedEffect::new);

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);
    /** Receitas de transformação escolhidas por espaço (forma racial, liberações). Sobrevive à morte. */
    static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgeData>> FORGE_DATA = ATTACHMENTS.register("transform_forge",
            () -> AttachmentType.serializable(ForgeData::new).copyOnDeath().build());

    static final class ForgedEffect extends MobEffect {
        ForgedEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xB98CFF);
        }
    }

    static final class VowEffect extends MobEffect {
        VowEffect() {
            super(MobEffectCategory.NEUTRAL, TlawFx.CHAIN_COLOR);
        }
    }

    static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private TlawContent() {}
}
