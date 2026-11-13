package com.renanpontes.isekaimahou.feature.city_arcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * OBELISCO DE NULIFICAÇÃO: dentro do território, quem o governante escolher NÃO consegue conjurar (o
 * {@link SpellCastEvent.Pre} é cancelado antes de gastar mana; mensagem + som). Modos (decreto do Senhor, na Runa de
 * Comando — a magnetita na frente do obelisco — ou {@code /mahou city_arcana obelisk <modo>}):
 * <ul>
 *   <li>{@link #MODE_OFF}: desligado;</li>
 *   <li>{@link #MODE_OUTSIDERS} (padrão): quem a cidade não protege — forasteiros, inimigos, monstros e hordas;</li>
 *   <li>{@link #MODE_ALL}: todos menos o Senhor e o conselho (cidadãos também ficam sem magia).</li>
 * </ul>
 * Nunca anula: chefes, arenas de chefe seladas, NPCs da cidade e automações (Pedestais Rúnicos). Obelisco danificado
 * falha metade das vezes.
 */
public final class NullObelisk {
    public static final int MODE_OFF = 0, MODE_OUTSIDERS = 1, MODE_ALL = 2;

    private static final Map<UUID, Long> WARN = new HashMap<>();
    /** Cidade em cuja zona de nulificação o jogador está (para avisar ao entrar/sair). */
    private static final Map<UUID, String> ZONE = new HashMap<>();

    public static MutableComponent modeName(int mode) {
        return Component.translatable("city_arcana.isekai_mahou.obelisk.mode." + mode);
    }

    public static MutableComponent modeDesc(int mode) {
        return Component.translatable("city_arcana.isekai_mahou.obelisk.mode." + mode + ".desc");
    }

    /** O Obelisco desta cidade anula a magia desta criatura? (sem olhar arena/chefe/dano do prédio) */
    public static boolean targets(CityArcana.City c, LivingEntity e, int mode) {
        if (mode == MODE_OFF || c.obelisk <= 0) return false;
        if (e instanceof FakePlayer) return false;
        if (e instanceof SettlementNpc n && n.cityId().equals(c.s.id)) return false;
        if (e instanceof ServerPlayer sp) {
            boolean debug = CityArcana.DEBUG_OUTSIDER.contains(sp.getUUID());
            if (!debug && (sp.isSpectator() || sp.getAbilities().instabuild)) return false;
            if (debug) return true;
            if (mode == MODE_ALL) return !isCouncil(c.s, sp.getUUID());
            return !CityArcana.friendly(c, sp);
        }
        return mode == MODE_ALL ? !CityArcana.friendly(c, e) : CityArcana.hostile(c, e);
    }

    /** Senhor ou conselheiro. */
    public static boolean isCouncil(Settlement s, UUID id) {
        return id.equals(s.owner) || s.council.contains(id);
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    static void onCastPre(SpellCastEvent.Pre e) {
        LivingEntity caster = e.getCaster();
        if (!(caster.level() instanceof ServerLevel sl)) return;
        CityArcana.City c = CityArcana.at(caster);
        if (c == null || c.obelisk <= 0 || !targets(c, caster, c.mode)) return;
        if (CityArcana.isBoss(caster) || CityArcana.inBossArena(sl, caster.position())) return;
        if (c.obeliskDamaged && sl.random.nextBoolean()) return;
        e.setCanceled(true);
        Vec3 at = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        CityArcanaNet.send(sl, CityArcanaNet.NULLIFY, at, Vec3.ZERO, 1.0F, 0x9B5CFF);
        if (caster instanceof ServerPlayer sp) {
            long now = sl.getGameTime();
            Long last = WARN.get(sp.getUUID());
            if (last == null || now - last > 30) {
                WARN.put(sp.getUUID(), now);
                sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.nullified",
                        SettlementService.cityName(sp.server, c.s)).withStyle(ChatFormatting.DARK_PURPLE), true);
                sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
            }
        } else {
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.7F, 1.5F);
        }
    }

    /** A cada 20 ticks: avisa quem entra/sai de uma zona de nulificação que o pega. */
    static void tickPlayers(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CityArcana.City c = CityArcana.at(sp);
            boolean in = c != null && c.obelisk > 0 && targets(c, sp, c.mode) && !CityArcana.inBossArena(sp.level(), sp.position());
            String was = ZONE.get(sp.getUUID());
            if (in && !c.s.id.equals(was)) {
                ZONE.put(sp.getUUID(), c.s.id);
                sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.enter",
                        SettlementService.cityName(server, c.s)).withStyle(ChatFormatting.DARK_PURPLE), true);
                sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 0.8F);
            } else if (!in && was != null) {
                ZONE.remove(sp.getUUID());
                sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.leave").withStyle(ChatFormatting.GRAY), true);
                sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.4F);
            }
        }
    }

    // =====================================================================================
    //  Runa de Comando: clique na magnetita do obelisco
    // =====================================================================================
    static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel sl = sp.serverLevel();
        BlockPos pos = e.getPos();
        if (!sl.getBlockState(pos).is(Blocks.LODESTONE) || sp.getMainHandItem().is(Items.COMPASS)) return;
        CityArcana.City c = CityArcana.at(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (c == null || c.obelisk <= 0) return;
        CityArcana.Site site = c.site(CityArcana.OBELISK);
        if (site == null || Math.abs(pos.getX() - site.center().getX()) > 8 || Math.abs(pos.getZ() - site.center().getZ()) > 8) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        boolean governor = sp.getUUID().equals(c.s.owner) || sp.hasPermissions(2);
        if (!governor) {
            sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.status", modeName(c.mode))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.not_governor",
                    SettlementService.cityName(sp.server, c.s)).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        setMode(sl, c, (c.mode + 1) % 3, sp);
    }

    /** Muda o modo (salva, atualiza o cache, avisa e faz o obelisco pulsar). */
    public static void setMode(ServerLevel sl, CityArcana.City c, int mode, ServerPlayer by) {
        int m = Math.max(MODE_OFF, Math.min(MODE_ALL, mode));
        CityArcanaData.get(sl.getServer()).setMode(c.s.id, m);
        c.mode = m;
        by.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.obelisk.set", modeName(m))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        by.displayClientMessage(modeDesc(m).withStyle(ChatFormatting.GRAY), false);
        CityArcana.Site site = c.site(CityArcana.OBELISK);
        if (site != null) {
            int height = 8 + 3 * Math.max(1, site.level());
            Vec3 top = Vec3.atBottomCenterOf(site.center()).add(0, height, 0);
            ServerLevel lv = sl.getServer().getLevel(c.dim);
            if (lv != null) {
                CityArcanaNet.send(lv, CityArcanaNet.OBELISK_PULSE, top, Vec3.ZERO, height, m == MODE_OFF ? 0x666677 : 0x9B5CFF);
                lv.playSound(null, top.x, top.y, top.z, m == MODE_OFF ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.BLOCKS, 1.5F, 0.7F);
            }
        }
    }

    static void forget(UUID id) {
        WARN.remove(id);
        ZONE.remove(id);
    }

    static void clear() {
        WARN.clear();
        ZONE.clear();
    }

    private NullObelisk() {}
}
