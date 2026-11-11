package com.renanpontes.isekaimahou.feature.character.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RacePassives;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Parte de cliente do módulo "character": tela "Escolha a sua Raça", aba "Raça" do menu B, letreiro de nível de raça,
 * tecla racial (padrão V: Transformação / golpe da forma; Shift + V: visão no escuro), a camada de aparência racial e o
 * movimento racial do jogador local (o Aracnídeo escala paredes; o Slime quica ao cair).
 */
public class CharacterClient implements ClientModule {
    public static final KeyMapping RACIAL = new KeyMapping("key.isekai_mahou.racial_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CoreKeys.CATEGORY);

    private static double lastVy;
    private static boolean wasOnGround = true;

    @Override
    public void initClient(IEventBus modBus) {
        CharacterNetwork.onData = ClientCharacter::onData;
        CharacterNetwork.onLook = ClientCharacter::onLook;
        CharacterNetwork.onFx = ClientCharacter::onFx;
        CharacterNetwork.onOpen = o -> Minecraft.getInstance().setScreen(new CreationScreen(o.mode(), o.altar()));
        CharacterNetwork.onLevelUp = u -> UnlockOverlay.show(Race.byIndex(u.race()), u.level());

        MenuTabs.register("character", 5, Component.translatable("screen.isekai_mahou.character.tab"), CharacterTab::new);

        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(RACIAL));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(IsekaiMahou.id("character_unlock"), UnlockOverlay::render));
        modBus.addListener(CharacterClient::addLayers);
        NeoForge.EVENT_BUS.addListener(CharacterClient::onTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientCharacter.clear();
            UnlockOverlay.clear();
        });
    }

    @SuppressWarnings("unchecked")
    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r) {
                var pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                pr.addLayer(new RaceFeatureLayer(pr));
            }
        }
    }

    private static void onTick(ClientTickEvent.Post e) {
        UnlockOverlay.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() % 100 == 0) ClientCharacter.prune();
        while (RACIAL.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            if (!ClientCharacter.data().created()) {
                mc.player.displayClientMessage(Component.translatable("message.isekai_mahou.character.not_created"), true);
                continue;
            }
            PacketDistributor.sendToServer(new CharacterNetwork.UseAbility(mc.player.isShiftKeyDown()
                    ? CharacterNetwork.KEY_VISION : CharacterNetwork.KEY_MAIN));
        }
        if (mc.player != null && mc.level != null && !mc.isPaused()) {
            localMovement(mc.player);
            if (mc.level.getGameTime() % 4 == 0) ambient(mc);
        }
    }

    // =====================================================================================
    //  Movimento racial do jogador local (o movimento do jogador é do cliente)
    // =====================================================================================
    private static void localMovement(LocalPlayer p) {
        CharacterData d = ClientCharacter.data();
        boolean free = !p.getAbilities().flying && !p.isSpectator() && !p.isPassenger() && !p.isInWater() && !p.isInLava()
                && !p.isFallFlying();
        Vec3 v = p.getDeltaMovement();
        // Aracnídeo: escala paredes (frente/pulo = sobe; Shift = gruda; nada = escorrega devagar)
        if (free && d.is(Race.ARACHNID) && p.horizontalCollision && RacePassives.touchingWall(p)) {
            int lv = ClientCharacter.raceLevel();
            boolean form = ClientCharacter.form(p.getId()) != null;
            double climb = form ? 0.35 : lv >= 4 ? 0.25 : lv >= 3 ? 0.2 : lv >= 2 ? 0.15 : 0.1;
            boolean up = p.input.forwardImpulse > 0.1F || p.input.jumping;
            if (up) p.setDeltaMovement(v.x, Math.max(v.y, climb), v.z);
            else if (p.isShiftKeyDown()) p.setDeltaMovement(v.x, Math.max(v.y, 0), v.z);
            else p.setDeltaMovement(v.x, Math.max(v.y, -0.15), v.z);
            p.resetFallDistance();
        }
        // Slime (nível II+): quica ao cair de alto (Shift = não quica)
        if (free && d.is(Race.SLIME) && ClientCharacter.raceLevel() >= 2 && p.onGround() && !wasOnGround && lastVy < -0.55
                && !p.isShiftKeyDown()) {
            double bounce = Math.min(0.9, -lastVy * 0.6);
            p.setDeltaMovement(v.x, bounce, v.z);
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.8F, 1.0F, false);
            for (int i = 0; i < 8; i++) {
                p.level().addParticle(ParticleTypes.ITEM_SLIME, p.getX() + (p.getRandom().nextDouble() - 0.5),
                        p.getY() + 0.1, p.getZ() + (p.getRandom().nextDouble() - 0.5), 0, 0.1, 0);
            }
        }
        lastVy = p.getDeltaMovement().y;
        wasOnGround = p.onGround();
    }

    /** Partículas suaves de raça nos jogadores visíveis (fantasma: névoa; anjo: brilho; slime: gotas). */
    private static void ambient(Minecraft mc) {
        RandomSource rnd = mc.level.random;
        for (Player p : mc.level.players()) {
            if (p.isInvisible() || p.isSpectator()) continue;
            ClientCharacter.Look look = ClientCharacter.look(p.getId());
            if (look == null || look.race() == null) continue;
            double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.6, z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.6;
            switch (look.race()) {
                case PHANTOM -> {
                    if (rnd.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.SOUL, x, p.getY() + 0.15, z, 0, 0.01, 0);
                    else mc.level.addParticle(ParticleTypes.WHITE_ASH, x, p.getY() + rnd.nextDouble() * p.getBbHeight(), z, 0, 0.01, 0);
                }
                case ANGEL -> {
                    if (rnd.nextInt(5) == 0) mc.level.addParticle(ParticleTypes.END_ROD, x, p.getY() + p.getBbHeight() + 0.25, z,
                            0, 0.005, 0);
                }
                case SLIME -> {
                    if (rnd.nextInt(6) == 0) mc.level.addParticle(ParticleTypes.ITEM_SLIME, x, p.getY() + rnd.nextDouble() * p.getBbHeight(), z,
                            0, 0, 0);
                }
                default -> { }
            }
        }
    }
}
