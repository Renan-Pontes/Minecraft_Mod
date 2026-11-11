package com.renanpontes.isekaimahou.feature.guild.entity;

import com.renanpontes.isekaimahou.feature.guild.GuildService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimationController;

/** Recepcionista da Guilda: registra aventureiros, mostra o quadro de missões, recebe as entregas e cuida da loja. */
public class GuildReceptionistEntity extends GuildNpcEntity {
    private static final int AMBIENT_LINES = 6;
    private int stampCooldown = 200;

    public GuildReceptionistEntity(EntityType<? extends GuildReceptionistEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected boolean isStaff() {
        return true;
    }

    @Override
    protected String animationPrefix() {
        return "animation.guild_receptionist";
    }

    @Override
    protected void addActions(AnimationController<GuildNpcEntity> action) {
        super.addActions(action);
        action.triggerableAnim("stamp", once("stamp"));
        action.triggerableAnim("bow", once("bow"));
    }

    @Override
    protected void onTalk(ServerPlayer player) {
        triggerAnim("action", "bow");
        GuildService.openReception(player, this);
    }

    @Override
    protected String ambientLine() {
        return key("receptionist.ambient." + random.nextInt(AMBIENT_LINES));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // De vez em quando carimba papéis no balcão (só parada, sem conversar).
        if (!level().isClientSide && --stampCooldown <= 0) {
            stampCooldown = 240 + random.nextInt(360);
            if (!isSpeaking() && getNavigation().isDone()) triggerAnim("action", "stamp");
        }
    }
}
