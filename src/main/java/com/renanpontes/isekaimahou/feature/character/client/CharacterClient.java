package com.renanpontes.isekaimahou.feature.character.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RacePassives;
import com.renanpontes.isekaimahou.feature.character.SlimePredator;
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
 * movimento racial do jogador local (o Aracnídeo escala paredes; o Slime quica ao cair). v1.3.1: o voo planado das formas
 * aladas fica no {@link GlideClient}.
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
        CharacterNetwork.onOpen = o -> {
            // release 1.0: os altares têm a própria tela (Recomeço; Reencarnação = raça OU classe). Personagem antigo sem
            // raça escolhida vai direto para a tela de raça (a 1ª escolha é grátis)
            boolean altarHub = o.mode() == CharacterNetwork.MODE_ALTAR && ClientCharacter.data().created();
            Minecraft.getInstance().setScreen(altarHub || o.mode() == CharacterNetwork.MODE_RENEWAL
                    ? new AltarScreen(o.mode(), o.altar()) : new CreationScreen(o.mode(), o.altar()));
        };
        CharacterNetwork.onLevelUp = u -> UnlockOverlay.show(Race.byIndex(u.race()), u.level());

        MenuTabs.register("character", 5, Component.translatable("screen.isekai_mahou.character.tab"), CharacterTab::new);

        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(RACIAL));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(IsekaiMahou.id("character_unlock"), UnlockOverlay::render));
        modBus.addListener(CharacterClient::addLayers);
        modBus.addListener(RaceMoteParticle::register);
        FormFx.init(modBus);
        GlideClient.init();
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
        // v1.7: o Slime que copiou o Fio Pegajoso da aranha (Predador) também escala, devagar
        boolean spiderSlime = SlimePredator.has(d, SlimePredator.Mimic.SPIDER);
        if (free && (d.is(Race.ARACHNID) || spiderSlime) && p.horizontalCollision && RacePassives.touchingWall(p)) {
            int lv = ClientCharacter.raceLevel();
            boolean form = ClientCharacter.form(p.getId()) != null;
            double climb = spiderSlime ? (form ? 0.2 : 0.12)
                    : form ? 0.35 : lv >= 4 ? 0.25 : lv >= 3 ? 0.2 : lv >= 2 ? 0.15 : 0.1;
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

    /**
     * Partículas suaves de raça nos jogadores visíveis (fantasma: névoa; anjo: brilho; slime: gotas). v1.3: crescem com
     * o nível de raça (brasas do Demônio, fumaça das narinas do Dracônico, brilhos do Elfo e do Humano no III+), passam
     * pelo preset gráfico e pulam quem está transformado (a aura da forma é do {@link FormFx}).
     */
    private static void ambient(Minecraft mc) {
        RandomSource rnd = mc.level.random;
        double range = FxQuality.range();
        float rate = FxQuality.particles();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player p : mc.level.players()) {
            if (p.isInvisible() || p.isSpectator()) continue;
            ClientCharacter.Look look = ClientCharacter.look(p.getId());
            if (look == null || look.race() == null || ClientCharacter.form(p.getId()) != null) continue;
            if (p.position().distanceToSqr(cam) > range * range || rnd.nextFloat() > rate) continue;
            double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.6, z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.6;
            int lv = look.level();
            if (lv >= 3) levelAmbient(mc, p, look, lv, rnd);
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

    /** Sinais discretos de poder racial a partir do nível III (mais frequentes no IV e no V). */
    private static void levelAmbient(Minecraft mc, Player p, ClientCharacter.Look look, int lv, RandomSource rnd) {
        int chance = lv >= 5 ? 6 : lv >= 4 ? 10 : 16;
        if (rnd.nextInt(chance) != 0) return;
        double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.7, z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.7;
        double y = p.getY() + rnd.nextDouble() * p.getBbHeight();
        switch (look.race()) {
            case DEMON -> mc.level.addParticle(rnd.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
            case DRAGONBORN -> {
                Vec3 eye = p.getEyePosition();
                Vec3 look2 = p.getLookAngle();
                mc.level.addParticle(ParticleTypes.SMOKE, eye.x + look2.x * 0.35, eye.y - 0.15, eye.z + look2.z * 0.35, look2.x * 0.03, 0.015, look2.z * 0.03);
            }
            case ELF -> mc.level.addParticle(FormFx.glow(0xBFF0D8, 0.6F), x, y, z, 0, 0.01, 0);
            case HUMAN -> mc.level.addParticle(FormFx.glow(0xFFD86B, 0.5F), x, y, z, 0, 0.015, 0);
            case HALF_ORC -> mc.level.addParticle(FormFx.dust(0x8A1010, 0.8F), x, y, z, 0, 0, 0);
            case DWARF -> {
                if (lv >= 4) mc.level.addParticle(ParticleTypes.SMALL_FLAME, x, p.getY() + 0.1, z, 0, 0.01, 0);
            }
            case ARACHNID -> mc.level.addParticle(FormFx.glow(0x9A6AD8, 0.5F), x, y, z, 0, 0.005, 0);
            case BEASTFOLK -> {
                if (p.isSprinting()) mc.level.addParticle(ParticleTypes.CLOUD, x, p.getY() + 0.1, z, 0, 0.01, 0);
            }
            case PHANTOM -> mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.01, 0);
            case ANGEL -> mc.level.addParticle(ParticleTypes.WHITE_ASH, x, p.getY() + p.getBbHeight() + 0.2, z, 0, -0.01, 0);
            case SLIME -> mc.level.addParticle(FormFx.glow(Race.SLIME.rgb, 0.7F), x, y, z, 0, 0.02, 0);
        }
    }
}
