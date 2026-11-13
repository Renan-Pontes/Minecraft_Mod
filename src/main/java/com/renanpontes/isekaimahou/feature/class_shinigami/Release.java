package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Liberação da zanpakutō: <b>Shikai</b> (usar a espada com o rank III — custa 12% do Vigor, dura 60 s + 15 s por rank
 * acima do III; usar de novo sela) e <b>Bankai</b> (a forma G {@code zanpakuto_bankai}, controlada por {@link BankaiState}).
 * Enquanto liberada, os golpes com a espada causam mais dano e aplicam a assinatura do arquétipo ({@link Signatures}).
 * O estado vive só em memória (some ao sair/morrer) e é espelhado no {@code custom_data} das zanpakutō na mão (modelo).
 * <p>
 * Release 1.0: cada liberação tem um espaço de receita de Transformação ({@link ReleaseForge}) — a receita escolhida
 * pelo jogador entra por cima quando a liberação começa e sai quando a espada é selada. Sem receita, nada muda.
 */
public final class Release {
    public enum Mode { SEALED, SHIKAI, BANKAI }

    /** Custo da Shikai (fração do Vigor máximo) e recarga depois de selar. */
    public static final float SHIKAI_COST = 0.12F;
    public static final int SHIKAI_COOLDOWN = 300;
    /** Multiplicador de dano corpo a corpo com a espada liberada. */
    public static final float SHIKAI_MELEE = 1.25F;
    public static final float BANKAI_MELEE = 1.6F;

    private static final class State {
        Mode mode = Mode.SEALED;
        long until;
        long cooldownUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static Mode mode(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null ? Mode.SEALED : s.mode;
    }

    public static boolean released(ServerPlayer sp) {
        return mode(sp) != Mode.SEALED;
    }

    /** Duração da Shikai pelo rank (III: 60 s · IV: 75 s · V: 90 s). */
    public static int shikaiTicks(MagicData d) {
        return 1200 + 300 * Math.max(0, Shinigami.rank(d) - 3);
    }

    /** Usar a espada: libera a Shikai, ou sela se já estiver liberada. */
    public static void toggleShikai(ServerPlayer sp, InteractionHand hand) {
        MagicData d = MagicApi.get(sp);
        Archetype a = Shinigami.archetype(d);
        State s = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        if (s.mode == Mode.BANKAI) {
            sp.displayClientMessage(Shinigami.msg("already_bankai").withColor(Shinigami.RGB), true);
            return;
        }
        if (s.mode == Mode.SHIKAI) {
            seal(sp, true);
            return;
        }
        // depois do Mugetsu a lâmina não responde
        if (ShinigamiTechniques.blockIfPowerless(sp)) return;
        if (!Shinigami.hasShikai(d) || a == null) {
            sp.displayClientMessage(Shinigami.msg(Shinigami.rank(d) < 2 ? "shikai_locked_name" : "shikai_locked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = sp.serverLevel().getGameTime();
        if (now < s.cooldownUntil && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Shinigami.msg("shikai_cooldown", (s.cooldownUntil - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!Shinigami.spendVigor(sp, SHIKAI_COST)) {
            sp.displayClientMessage(MagicApi.noResourceMessage(sp), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }
        s.mode = Mode.SHIKAI;
        // onda 5: a receita moldada pode esticar/encurtar a Shikai (Estender/Encurtar no corpo)
        int shikaiTicks = Math.max(100, Math.round(shikaiTicks(d) * ReleaseForge.plannedDuration(sp, ReleaseForge.SHIKAI)));
        s.until = now + shikaiTicks;
        MagicApi.addCounter(sp, Shinigami.RELEASES, 1);
        // grito do comando para quem está perto + efeitos
        Announce.send(sp, Shinigami.msg("shikai_shout", sp.getDisplayName(), a.command()), a.rgb, Announce.STYLE_SPELL);
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        ShinigamiFx.pillar(sl, at, 3.2, 50, ShinigamiFx.dust(a.rgb, 1.3F));
        ShinigamiFx.signature(sl, a, at.add(0, 1.0, 0), 30, 0.8);
        ShinigamiFx.sound(sl, at, SoundEvents.TRIDENT_RETURN, 1.0F, 0.7F);
        ShinigamiFx.sound(sl, at, releaseSound(a), 0.9F, 1.0F);
        // pressão espiritual: anel no chão, coluna na cor da alma e o comando escrito no ar
        ShinigamiFxNet.burst(sp, ShinigamiFxNet.RELEASE, at, Vec3.ZERO, 6.0F, a.rgb, a.command());
        ItemStack held = sp.getItemInHand(hand);
        if (held.getItem() instanceof ZanpakutoItem) ZanpakutoItem.setRelease(held, 1, a.counterValue(), s.until);
        // a receita moldada pelo jogador (se houver) entra por cima da Shikai
        ReleaseForge.begin(sp, ReleaseForge.SHIKAI, shikaiTicks);
        ShinigamiRanks.hintIfReady(sp);
    }

    /** Começa a Bankai (chamado pela forma G). */
    public static void beginBankai(ServerPlayer sp, int ticks) {
        State s = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        // a Shikai dá lugar à Bankai: a receita da Shikai sai (as duas não se somam)
        if (s.mode == Mode.SHIKAI) ReleaseForge.end(sp, ReleaseForge.SHIKAI);
        s.mode = Mode.BANKAI;
        s.until = sp.serverLevel().getGameTime() + ticks;
        syncHands(sp);
        ReleaseForge.begin(sp, ReleaseForge.BANKAI, ticks);
    }

    /** Sela a espada (fim do tempo, usar de novo, fim da Bankai). */
    public static void seal(ServerPlayer sp, boolean voluntary) {
        State s = STATES.get(sp.getUUID());
        if (s == null || s.mode == Mode.SEALED) return;
        Mode was = s.mode;
        s.mode = Mode.SEALED;
        // onda 5: a receita moldada mexe na recarga da liberação (Acelerar/Retardar no corpo) — lida antes de encerrar
        String slot = was == Mode.BANKAI ? ReleaseForge.BANKAI : ReleaseForge.SHIKAI;
        float cdMult = ReleaseForge.cooldownMult(sp, slot);
        ReleaseForge.end(sp, slot);
        s.cooldownUntil = sp.serverLevel().getGameTime()
                + Math.round((was == Mode.BANKAI ? SHIKAI_COOLDOWN * 2 : SHIKAI_COOLDOWN) * cdMult);
        clearInventory(sp.getInventory());
        ServerLevel sl = sp.serverLevel();
        ShinigamiFx.sound(sl, sp.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 0.6F);
        sl.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        if (was == Mode.BANKAI) {
            // o corpo cobra o preço da Bankai
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 0));
        }
        sp.displayClientMessage(Shinigami.msg(was == Mode.BANKAI ? "bankai_end" : voluntary ? "sealed" : "shikai_end").withStyle(ChatFormatting.GRAY), true);
    }

    /** Espelha o estado na pilha (modelo e dica). Chamado pelo inventoryTick da zanpakutō. */
    public static void syncStack(ServerPlayer sp, ItemStack stack) {
        State s = STATES.get(sp.getUUID());
        Archetype a = Shinigami.archetype(MagicApi.get(sp));
        int arch = a == null ? 0 : a.counterValue();
        boolean inHand = sp.getMainHandItem() == stack || sp.getOffhandItem() == stack;
        if (s == null || s.mode == Mode.SEALED || !inHand) ZanpakutoItem.setRelease(stack, 0, arch, 0L);
        else ZanpakutoItem.setRelease(stack, s.mode == Mode.BANKAI ? 2 : 1, arch, s.until);
    }

    private static void syncHands(ServerPlayer sp) {
        for (ItemStack st : new ItemStack[]{sp.getMainHandItem(), sp.getOffhandItem()}) {
            if (st.getItem() instanceof ZanpakutoItem) syncStack(sp, st);
        }
    }

    private static void clearInventory(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof ZanpakutoItem && ZanpakutoItem.release(st) != 0) {
                ZanpakutoItem.setRelease(st, 0, ZanpakutoItem.archetype(st), 0L);
            }
        }
    }

    /** Tick do servidor: fim do tempo e brilho da lâmina liberada. */
    static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            State s = e.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null) {
                it.remove();
                continue;
            }
            if (s.mode == Mode.SEALED) {
                if (sp.serverLevel().getGameTime() > s.cooldownUntil + 200) it.remove();
                continue;
            }
            long now = sp.serverLevel().getGameTime();
            if (now >= s.until && s.mode == Mode.SHIKAI) {
                seal(sp, false);
                continue;
            }
            // Bankai que perdeu o estado (troca de dimensão no meio): sela em vez de ficar liberada para sempre
            if (s.mode == Mode.BANKAI && now >= s.until + 20 && !BankaiState.active(sp)) {
                seal(sp, false);
                continue;
            }
            if (sp.tickCount % 8 == 0) {
                Archetype a = Shinigami.archetype(MagicApi.get(sp));
                if (a != null && sp.getMainHandItem().getItem() instanceof ZanpakutoItem) {
                    Vec3 look = sp.getLookAngle();
                    Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                    Vec3 blade = sp.position().add(0, sp.getBbHeight() * 0.55, 0).add(right.scale(0.45)).add(look.scale(0.5));
                    ShinigamiFx.signature(sp.serverLevel(), a, blade, s.mode == Mode.BANKAI ? 3 : 2, 0.15);
                }
            }
            if (sp.tickCount % 20 == 0) syncHands(sp);
        }
    }

    /** Multiplicador de dano corpo a corpo para quem segura a zanpakutō liberada. */
    public static float meleeMultiplier(ServerPlayer sp) {
        return switch (mode(sp)) {
            case SHIKAI -> SHIKAI_MELEE;
            case BANKAI -> BANKAI_MELEE;
            default -> 1.0F;
        };
    }

    @Nullable
    private static net.minecraft.sounds.SoundEvent releaseSound(Archetype a) {
        return switch (a) {
            case WEIGHT -> SoundEvents.ANVIL_LAND;
            case PETALS -> SoundEvents.CHERRY_LEAVES_BREAK;
            case FROST -> SoundEvents.GLASS_BREAK;
            case FLAME -> SoundEvents.BLAZE_SHOOT;
            case BRUSH -> SoundEvents.BOOK_PAGE_TURN;
        };
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void clear() {
        STATES.clear();
    }

    private Release() {}
}
