package com.renanpontes.isekaimahou.feature.origins_a.familiar;

import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/** Invocação dos familiares e os ganchos que fazem o Gato assustar creepers/phantoms. */
public final class Familiars {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Familiars::onJoin);
        NeoForge.EVENT_BUS.addListener(Familiars::onChangeTarget);
    }

    /** Invoca (ou troca) o familiar do dono. Só um de cada tipo por dono. */
    public static FamiliarEntity summon(ServerLevel level, LivingEntity owner, boolean cat, int life, float power) {
        EntityType<? extends FamiliarEntity> type = cat ? OaRegistries.FAMILIAR_CAT.get() : OaRegistries.FAMILIAR_CROW.get();
        for (FamiliarEntity old : level.getEntities(type, e -> owner.getUUID().equals(e.getOwnerUUID()) && e.isAlive())) {
            old.vanish(level);
        }
        FamiliarEntity f = type.create(level);
        if (f == null) return null;
        Vec3 back = owner.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize().scale(-1.2);
        Vec3 pos;
        if (cat) {
            BlockPos ground = OaUtil.ground(level, owner.position().add(back));
            pos = Vec3.atBottomCenterOf(ground);
        } else {
            pos = owner.position().add(back.scale(0.5)).add(0, owner.getBbHeight() + 0.6, 0);
        }
        f.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0);
        f.setYHeadRot(owner.getYRot());
        f.setup(owner, life, power);
        level.addFreshEntity(f);
        level.sendParticles(Vfx.largeSmoke(), pos.x, pos.y + 0.3, pos.z, 16, 0.25, 0.3, 0.25, 0.02);
        level.sendParticles(Vfx.witch(), pos.x, pos.y + 0.4, pos.z, 24, 0.35, 0.35, 0.35, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, pos.x, pos.y, pos.z, cat ? SoundEvents.CAT_AMBIENT : SoundEvents.PARROT_AMBIENT,
                SoundSource.NEUTRAL, 1.0F, cat ? 1.0F : 0.55F);
        if (owner instanceof Player p) OaUtil.actionbar(p, cat ? "familiar_cat" : "familiar_crow", Math.max(1, life / 1200));
        return f;
    }

    /** Creepers passam a fugir do gato familiar (como fogem de gatos do vanilla). */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Creeper c) {
            c.goalSelector.addGoal(3, new AvoidEntityGoal<>(c, FamiliarCatEntity.class, 6.0F, 1.0, 1.2));
        }
    }

    /** Creeper/phantom não escolhe como alvo alguém com um gato familiar por perto. */
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Creeper) && !(event.getEntity() instanceof Phantom)) return;
        LivingEntity t = event.getNewAboutToBeSetTarget();
        if (t == null || t.level().isClientSide) return;
        if (!t.level().getEntitiesOfClass(FamiliarCatEntity.class, t.getBoundingBox().inflate(FamiliarCatEntity.SCARE_RANGE),
                c -> c.isAlive()).isEmpty()) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private Familiars() {}
}
