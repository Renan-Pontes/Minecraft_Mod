package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.HollowFate;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A HOLLOWIFICAÇÃO: o Shinigami que perde para o Vazio Interior (morre na prova do Mundo Interior) — ou o aceita por
 * vontade própria — é tomado por ele. A troca de classe acontece NA HORA (salva mesmo se o jogo cair no meio da cena);
 * depois roda a cena de ~5 s:
 * <ol>
 *   <li>0 s — o corpo trava, ossos brancos sobem pelo rosto (anim {@code hollow_mask_tear}), o coração bate;</li>
 *   <li>1 s — a MÁSCARA se fecha sobre o rosto (estalo de osso);</li>
 *   <li>2 s — o BURACO se abre no peito (Reiatsu negro jorra);</li>
 *   <li>2,75 s — a tela fica branca e depois negra, só a máscara e as rachaduras rubras;</li>
 *   <li>3,75 s — o RUGIDO: onda de choque, tremor, tudo em volta é empurrado;</li>
 *   <li>5 s — volta ao mundo (sai do Mundo Interior) já como Hollow.</li>
 * </ol>
 * Fica registrado no {@link MagicData} por que virou Hollow (contador {@link Hollow#ORIGIN} e flags), o rank de Shinigami
 * que tinha e o dia. Nível, Círculo, XP e glifos ficam; a zanpakutō (ligada à alma) se desfaz.
 */
public final class Hollowfication {
    public static final int SCENE_TICKS = 100;

    private static final class Scene {
        final int origin;
        int age;

        Scene(int origin) {
            this.origin = origin;
        }
    }

    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    /** Quem está diante da escolha "lutar ou aceitar" (e a luta a começar se escolher lutar). */
    private static final Map<UUID, Runnable> PENDING = new HashMap<>();

    /** O gancho que o Mundo Interior do Shinigami chama. */
    static final HollowFate.Hook HOOK = new HollowFate.Hook() {
        @Override
        public boolean offer(ServerPlayer sp, Runnable fight) {
            if (MagicApi.get(sp).path() != MagicPath.SHINIGAMI) return false;
            PENDING.put(sp.getUUID(), fight);
            PacketDistributor.sendToPlayer(sp, new HollowNetwork.Open(HollowNetwork.SCREEN_HOLLOW_CHOICE));
            return true;
        }

        @Override
        public boolean defeated(ServerPlayer sp) {
            if (MagicApi.get(sp).path() != MagicPath.SHINIGAMI || SCENES.containsKey(sp.getUUID())) return false;
            begin(sp, Hollow.ORIGIN_DEFEATED);
            return true;
        }
    };

    public static boolean inProgress(ServerPlayer sp) {
        return SCENES.containsKey(sp.getUUID());
    }

    /** No começo da cena a máscara ainda não se fechou (o visual só aparece na etapa 1). */
    static boolean maskHidden(ServerPlayer sp) {
        Scene s = SCENES.get(sp.getUUID());
        return s != null && s.age < 20;
    }

    /** Resposta da tela "lutar ou aceitar". */
    static void onChoice(ServerPlayer sp, boolean accept) {
        Runnable fight = PENDING.remove(sp.getUUID());
        if (fight == null) return;
        if (!accept) {
            fight.run();
            return;
        }
        // aceitar só vale para um Shinigami dentro do próprio Mundo Interior
        if (MagicApi.get(sp).path() != MagicPath.SHINIGAMI || !InnerWorld.inOwn(sp)) return;
        begin(sp, Hollow.ORIGIN_ACCEPTED);
    }

    // =====================================================================================
    //  A troca de classe
    // =====================================================================================
    /** Começa a hollowificação (troca a classe já e agenda a cena). */
    public static void begin(ServerPlayer sp, int origin) {
        convert(sp, origin);
        SCENES.put(sp.getUUID(), new Scene(origin));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SCENE_TICKS, 6, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SCENE_TICKS + 40, 4, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
    }

    /** Vira Hollow: classe, registro da origem, a lâmina que se desfaz, glifos e magias iniciais. */
    static void convert(ServerPlayer sp, int origin) {
        MagicData d = MagicApi.get(sp);
        MagicPath was = d.path();
        if (was == MagicPath.SHINIGAMI) HollowForms.purgeForHollowfication(sp);
        d.setPath(MagicPath.HOLLOW);
        // por que virou Hollow (contadores salvos; o rank de Shinigami fica guardado como história)
        MagicApi.addCounter(sp, Hollow.ORIGIN, origin - d.counter(Hollow.ORIGIN));
        int shinRank = d.counter("ritual_rank.shinigami");
        MagicApi.addCounter(sp, Hollow.SHINIGAMI_RANK, shinRank - d.counter(Hollow.SHINIGAMI_RANK));
        MagicApi.addCounter(sp, Hollow.ORIGIN_DAY, MagicApi.worldDay(sp) - d.counter(Hollow.ORIGIN_DAY));
        d.setFlag(Hollow.FLAG_DEFEATED, origin == Hollow.ORIGIN_DEFEATED);
        d.setFlag(Hollow.FLAG_ACCEPTED, origin == Hollow.ORIGIN_ACCEPTED);
        // não começa faminto; a alma do próprio Shinigami vira as primeiras Almas
        MagicApi.addCounter(sp, Hollow.LAST_FEED, Hollow.nowSeconds(sp) - d.counter(Hollow.LAST_FEED));
        if (Hollow.souls(d) < 20) MagicApi.addCounter(sp, Hollow.SOULS, 20 - d.counter(Hollow.SOULS));
        int lost = purgeBlade(sp);
        for (String g : HollowRanks.BASE_GLYPHS) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        // quem já tinha evoluído (comando, voltou a ser Hollow) recupera os glifos dos estágios
        for (HollowRanks.Rank r : HollowRanks.ranks()) {
            if (r.number() > Hollow.rank(d)) break;
            for (String g : r.glyphs()) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        }
        // v1.4: as técnicas são inatas (tecla "Instinto Hollow" e Roda Hollow): nada de loadout pronto (dicas no fim da cena)
        d.fillMana();
        d.markDirty();
        if (lost > 0) sp.sendSystemMessage(Hollow.msg("blade_lost").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /**
     * A zanpakutō é ligada à alma do Shinigami: quando o Hollow toma a alma, ela se desfaz (inventário, mão secundária e
     * o item no cursor). Devolve quantas sumiram. Também usado no tick do Hollow (se uma voltar pelo vínculo de alma).
     */
    static int purgeBlade(ServerPlayer sp) {
        int n = 0;
        Container inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isBlade(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                n++;
            }
        }
        if (isBlade(sp.containerMenu.getCarried())) {
            sp.containerMenu.setCarried(ItemStack.EMPTY);
            n++;
        }
        if (n > 0) {
            ServerLevel sl = sp.serverLevel();
            sl.sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            HollowFx.sound(sl, sp.position(), SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
        }
        return n;
    }

    /** A zanpakutō (e qualquer forma dela) — por id, sem depender da classe do item. */
    static boolean isBlade(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        return IsekaiMahou.MODID.equals(id.getNamespace()) && (id.getPath().equals("zanpakuto") || id.getPath().startsWith("zanpakuto_"));
    }

    // =====================================================================================
    //  A cena
    // =====================================================================================
    static void tick(MinecraftServer server) {
        if (SCENES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Scene>> it = SCENES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Scene> me = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(me.getKey());
            Scene s = me.getValue();
            if (sp == null || !sp.isAlive()) {
                it.remove();
                continue;
            }
            step(sp, s);
            if (++s.age > SCENE_TICKS) {
                it.remove();
                finish(sp, s);
            }
        }
    }

    private static void step(ServerPlayer sp, Scene s) {
        ServerLevel sl = sp.serverLevel();
        Vec3 face = sp.getEyePosition();
        Vec3 chest = sp.position().add(0, sp.getBbHeight() * 0.62, 0);
        switch (s.age) {
            case 0 -> {
                cinematic(sp, 0);
                MahouAnims.play(sp, "hollow_mask_tear");
                HollowNetwork.fx(sp, HollowNetwork.MASK_FORM, face, Vec3.ZERO, 20F, Hollow.BONE_RGB);
                HollowFx.sound(sl, face, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 0.6F);
                HollowFx.sound(sl, face, SoundEvents.SKELETON_AMBIENT, 1.0F, 0.4F);
                sp.sendSystemMessage(Hollow.msg(s.origin == Hollow.ORIGIN_ACCEPTED ? "scene_accept" : "scene_defeat")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            }
            case 10, 30, 50 -> HollowFx.sound(sl, face, SoundEvents.WARDEN_HEARTBEAT, 1.6F, 0.5F + s.age * 0.004F);
            case 20 -> {
                cinematic(sp, 1);
                HollowEvents.syncLook(sp);
                HollowFx.sound(sl, face, HollowSounds.MASK.get(), 1.2F, 1.0F);
                HollowFx.sound(sl, face, SoundEvents.BONE_BLOCK_PLACE, 1.4F, 0.5F);
                HollowFx.sound(sl, face, SoundEvents.SKELETON_HURT, 1.0F, 0.4F);
                sl.sendParticles(HollowFx.BONE, face.x, face.y, face.z, 24, 0.25, 0.25, 0.25, 0.04);
            }
            case 40 -> {
                cinematic(sp, 2);
                HollowNetwork.fx(sp, HollowNetwork.SHOCKWAVE, sp.position(), Vec3.ZERO, 4F, Hollow.DARK_RGB);
                sl.sendParticles(HollowFx.BLACK, chest.x, chest.y, chest.z, 40, 0.15, 0.15, 0.15, 0.25);
                sl.sendParticles(HollowFx.REIATSU, chest.x, chest.y, chest.z, 20, 0.15, 0.15, 0.15, 0.2);
                HollowFx.sound(sl, chest, SoundEvents.WITHER_HURT, 1.0F, 0.5F);
                HollowFx.sound(sl, chest, SoundEvents.BONE_BLOCK_BREAK, 1.2F, 0.4F);
            }
            case 55 -> {
                cinematic(sp, 3);
                HollowFx.sound(sl, face, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8F, 0.6F);
            }
            case 75 -> {
                cinematic(sp, 4);
                MahouAnims.play(sp, "transform_roar");
                HollowNetwork.fx(sp, HollowNetwork.RESURRECCION, sp.position(), Vec3.ZERO, 9F, Hollow.REIATSU_RGB);
                HollowNetwork.fxNear(sl, HollowNetwork.SHAKE, sp.getId(), sp.position(), Vec3.ZERO, 1.2F, Hollow.REIATSU_RGB, 48);
                HollowFx.sound(sl, face, HollowSounds.ROAR.get(), 2.0F, 0.9F);
                HollowFx.sound(sl, face, SoundEvents.WARDEN_ROAR, 1.4F, 0.8F);
                HollowFx.sound(sl, face, SoundEvents.ENDER_DRAGON_GROWL, 0.9F, 0.7F);
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, sp.getX(), sp.getY() + 1, sp.getZ(), 1, 0, 0, 0, 0);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(8), le -> le != sp && le.isAlive())) {
                    Vec3 push = e.position().subtract(sp.position());
                    push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : push.normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.6, 0.6, push.z * 1.6));
                    e.hurtMarked = true;
                }
            }
            default -> {
                // Reiatsu negro-rubro vazando do corpo durante a cena (pouco: o cliente desenha o resto)
                if (s.age > 40 && s.age % 4 == 0) {
                    sl.sendParticles(HollowFx.BLACK, sp.getX(), sp.getY() + 1.0, sp.getZ(), 3, 0.35, 0.7, 0.35, 0.03);
                    sl.sendParticles(HollowFx.REIATSU, sp.getX(), sp.getY() + 1.0, sp.getZ(), 2, 0.35, 0.7, 0.35, 0.03);
                }
            }
        }
    }

    private static void finish(ServerPlayer sp, Scene s) {
        cinematic(sp, 5);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (InnerWorld.is(sp.level())) InnerWorld.leave(sp);
        HollowEvents.syncLook(sp);
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
        sp.connection.send(new ClientboundSetTitleTextPacket(Hollow.msg("became_title").withColor(Hollow.BONE_RGB)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Hollow.msg(s.origin == Hollow.ORIGIN_ACCEPTED ? "became_sub_accept"
                : "became_sub_defeat").withColor(Hollow.REIATSU_RGB)));
        Announce.send(sp, Hollow.msg("became_announce", sp.getDisplayName()), Hollow.REIATSU_RGB, Announce.STYLE_ULTIMATE);
        for (int i = 1; i <= 5; i++) sp.sendSystemMessage(Component.translatable("message.isekai_mahou.hollow.became_tip." + i).withStyle(ChatFormatting.GRAY));
    }

    private static void cinematic(ServerPlayer sp, int stage) {
        PacketDistributor.sendToPlayer(sp, new HollowNetwork.Cinematic(stage, SCENE_TICKS));
    }

    static void forget(UUID id) {
        SCENES.remove(id);
        PENDING.remove(id);
    }

    static void clear() {
        SCENES.clear();
        PENDING.clear();
    }

    private Hollowfication() {}
}
