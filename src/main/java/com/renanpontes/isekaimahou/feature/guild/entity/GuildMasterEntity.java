package com.renanpontes.isekaimahou.feature.guild.entity;

import com.renanpontes.isekaimahou.feature.guild.GuildService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimationController;

/** Mestre da Guilda: aplica as PROVAS de promoção de rank (F → S). Veterano grandalhão de barba. */
public class GuildMasterEntity extends GuildNpcEntity {
    private static final int AMBIENT_LINES = 6;

    public GuildMasterEntity(EntityType<? extends GuildMasterEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected boolean isStaff() {
        return true;
    }

    @Override
    protected String animationPrefix() {
        return "animation.guild_master";
    }

    @Override
    protected void addActions(AnimationController<GuildNpcEntity> action) {
        super.addActions(action);
        action.triggerableAnim("slam", once("slam"));
        action.triggerableAnim("nod", once("nod"));
    }

    @Override
    protected void onTalk(ServerPlayer player) {
        triggerAnim("action", "nod");
        GuildService.openMaster(player, this);
    }

    @Override
    protected String ambientLine() {
        return key("master.ambient." + random.nextInt(AMBIENT_LINES));
    }
}
