package com.renanpontes.isekaimahou.feature.legends_k;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Utilitários de servidor das técnicas de classe do legends_k. As regras de alvo são as do legends_f
 * ({@link LfUtil#isFoe}): monstros, chefes, jogadores NÃO aliados (PvP desligado = aliado) e quem te ataca — então Modo
 * Paz, grupos, duelos e guerras de cidade valem igual aqui.
 */
public final class LkUtil {
    /** Prefixo das mensagens do módulo. */
    public static final String MSG = "message.isekai_mahou.legends_k.";

    public static boolean isFoe(SpellContext ctx, Entity e) {
        return LfUtil.isFoe(ctx, e);
    }

    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        return LfUtil.foesAround(ctx, c, radius, max);
    }

    /** Poder efetivo (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return LfUtil.power(ctx, stats);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo)? */
    public static boolean self(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    /** Só na primeira chamada de {@code key} nesta conjuração neste tick (formas de área chamam o efeito por alvo). */
    public static boolean once(SpellContext ctx, String key) {
        return LfUtil.oncePerTick(ctx, "lk:" + key);
    }

    /** Criatura "comum" que um glifo de controle pode mexer: nada de chefes, NPCs, suportes de armadura, jogadores. */
    public static boolean ordinaryMob(LivingEntity e) {
        return !(e instanceof Player) && !(e instanceof Npc) && !(e instanceof ArmorStand) && !Statuses.isBossLike(e);
    }

    /** Criatura fraca: comum e com vida máxima até {@code base} (+25% por Amplificar). */
    public static boolean weak(LivingEntity e, float base, SpellStats stats) {
        return ordinaryMob(e) && e.getMaxHealth() <= base * (1F + 0.25F * Math.max(0, stats.amplify));
    }

    public static boolean boss(LivingEntity e) {
        return Statuses.isBossLike(e);
    }

    /** Nível do jogador (1 para mobs/conjuradores sem dados). */
    public static int level(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer sp ? Math.max(1, MagicApi.get(sp).level()) : 10;
    }

    /** Dano "do mundo" que nada intangível pode impedir (/kill, vazio). */
    public static boolean unstoppable(DamageSource src) {
        return src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Dano que vem de alguém (golpe, projétil, magia, explosão causada) — não queda, fome, afogamento. */
    public static boolean fromSomeone(DamageSource src) {
        return src.getEntity() != null || src.getDirectEntity() != null;
    }

    /** Zona segura (cidade/vila): cidades são sagradas. */
    public static boolean safe(ServerLevel l, Vec3 p) {
        return Places.isSafe(l, BlockPos.containing(p));
    }

    /**
     * Área indestrutível (torre em escalada, masmorra): movimentos especiais não entram nem saem dela (sem atalhos em
     * quebra-cabeças). Os dois pontos dentro da mesma área = tudo bem.
     */
    public static boolean wardCrossing(ServerLevel l, Vec3 a, Vec3 b, @Nullable Entity caster) {
        boolean wa = Destruction.wardedFor(l, BlockPos.containing(a), caster);
        boolean wb = Destruction.wardedFor(l, BlockPos.containing(b), caster);
        return wa != wb;
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Aviso curto na barra de ação (sem poluir a tela). */
    public static void say(@Nullable Entity e, String key, ChatFormatting color, Object... args) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(MSG + key, args).withStyle(color), true);
    }

    /** Empurrão que também chega ao cliente de um jogador. */
    public static void impulse(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    private LkUtil() {}
}
