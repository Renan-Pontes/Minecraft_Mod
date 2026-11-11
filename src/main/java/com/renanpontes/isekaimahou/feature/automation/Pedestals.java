package com.renanpontes.isekaimahou.feature.automation;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Conjuradores dos Pedestais ({@link PedestalCaster}) e a ligação conjuração → pedestal.
 * <p>
 * Cada dono tem UM conjurador por mundo, com nome {@link #FAKE_NAME} e um UUID PRÓPRIO derivado do dono
 * ({@link #fakeId}). Não usamos o UUID do dono de propósito: o núcleo e os módulos guardam coisas por UUID do
 * conjurador (limite de magias persistentes, Aura, Órbitas, "você mesmo nunca é alvo", recarga de zonas pelo UUID)
 * — com o mesmo UUID um pedestal apagaria as zonas/auras do dono e, depois de recarregar o chunk, a magia
 * passaria a ser "do dono de verdade" (XP, sementes do inventário dele...).
 * <p>
 * O pedestal de uma conjuração é achado assim: o que está conjurando AGORA (efeitos imediatos) → o pedestal do
 * dono mais perto do ponto do efeito (projéteis que chegam depois) → o último pedestal desse dono que conjurou.
 */
public final class Pedestals {
    public static final String FAKE_NAME = "[Pedestal]";

    /** Conjuradores vivos por mundo e dono (soltos quando o mundo descarrega, como a FakePlayerFactory faz). */
    private static final Map<ServerLevel, Map<UUID, PedestalCaster>> CASTERS = new HashMap<>();
    @Nullable private static RunicPedestalBlockEntity current;
    private static final Map<UUID, WeakReference<RunicPedestalBlockEntity>> LAST = new HashMap<>();

    /** UUID do conjurador dos pedestais de um dono (estável entre reinícios, diferente do dono). */
    public static UUID fakeId(UUID owner) {
        return UUID.nameUUIDFromBytes(("isekai_mahou:runic_pedestal:" + owner).getBytes(StandardCharsets.UTF_8));
    }

    /** O FakePlayer que conjura pelos pedestais deste dono neste mundo. */
    public static PedestalCaster caster(ServerLevel level, UUID owner) {
        return CASTERS.computeIfAbsent(level, k -> new HashMap<>())
                .computeIfAbsent(owner, o -> new PedestalCaster(level, new GameProfile(fakeId(o), FAKE_NAME), o));
    }

    /** Chamado pelo pedestal em volta do castFrom. */
    public static void begin(RunicPedestalBlockEntity pedestal, UUID owner) {
        current = pedestal;
        LAST.put(owner, new WeakReference<>(pedestal));
    }

    public static void end() {
        current = null;
    }

    public static boolean isPedestalCaster(@Nullable Entity e) {
        return e instanceof PedestalCaster;
    }

    /** Dono do pedestal que conjurou (null = não é conjurador de pedestal). */
    @Nullable
    public static UUID ownerOf(@Nullable Entity e) {
        return e instanceof PedestalCaster pc ? pc.owner() : null;
    }

    /** O pedestal que fez esta conjuração (null = não veio de um pedestal). */
    @Nullable
    public static RunicPedestalBlockEntity of(SpellContext ctx, Vec3 near) {
        UUID owner = ownerOf(ctx.caster());
        if (owner == null) return null;
        if (current != null && !current.isRemoved() && current.getLevel() == ctx.level() && owner.equals(current.owner())) return current;
        List<RunicPedestalBlockEntity> around = ManaNet.scan(ctx.level(), BlockPos.containing(near), 16, RunicPedestalBlockEntity.class);
        for (RunicPedestalBlockEntity p : around) {
            if (owner.equals(p.owner())) return p;
        }
        WeakReference<RunicPedestalBlockEntity> ref = LAST.get(owner);
        RunicPedestalBlockEntity last = ref == null ? null : ref.get();
        return last != null && !last.isRemoved() && last.getLevel() == ctx.level() ? last : null;
    }

    // =====================================================================================
    //  Limpeza (não segurar mundos descarregados na memória)
    // =====================================================================================
    static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level) CASTERS.remove(level);
    }

    static void onServerStopped(ServerStoppedEvent e) {
        CASTERS.clear();
        LAST.clear();
        current = null;
    }

    // =====================================================================================
    //  Segurança extra: o conjurador já é aliado de jogadores e bichos com dono (o motor não os escolhe como
    //  alvo), mas dano/efeitos/raios que escapam por outros caminhos (projéteis de outros módulos, explosões
    //  em área, o raio de Chamar Tempestade que acerta quem está perto do alvo) também são barrados aqui.
    // =====================================================================================

    /** Jogadores e criaturas com dono (lobos, gatos, cavalos, invocações) ficam a salvo das fazendas. */
    private static boolean shielded(Entity e) {
        return e instanceof Player || (e instanceof OwnableEntity own && own.getOwnerUUID() != null);
    }

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (isPedestalCaster(e.getSource().getEntity()) && shielded(e.getEntity())) e.setCanceled(true);
    }

    static void onEffectApplicable(MobEffectEvent.Applicable e) {
        if (isPedestalCaster(e.getEffectSource()) && shielded(e.getEntity())
                && e.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    static void onLightning(EntityStruckByLightningEvent e) {
        if (isPedestalCaster(e.getLightning().getCause()) && shielded(e.getEntity())) e.setCanceled(true);
    }

    private Pedestals() {}
}
