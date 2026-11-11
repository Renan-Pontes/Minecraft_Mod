package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_gunslinger.duel.DuelService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.glyph.GunTriggers;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GunslingerArmorItem;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Combate com as armas de mana (servidor):
 * <ul>
 *   <li><b>Tiro básico</b> (clique direito): dano = base da arma × tier/rank × nível × poder mágico × DAMAGE_SCALE.</li>
 *   <li><b>Recarga</b> (tecla de trocar de mão, F, ou gatilho vazio): enche o tambor em alguns ticks e gasta uma
 *       FRAÇÃO do Foco máximo por cartucho. Dispara o gatilho de glifo "Ao Recarregar".</li>
 *   <li><b>Bônus de classe</b> do Pistoleiro: recarga 40% mais rápida e pela metade do Foco; magias com forma de arma
 *       devolvem 20% do Foco e recarregam 25% mais rápido; tiro na cabeça ×1,5 (×1,25 para as outras classes);
 *       +4% de dano de bala por rank do Caminho do Gatilho; traje completo: recarga mais 15% mais rápida.</li>
 * </ul>
 * Estado (recargas em andamento) por jogador, limpo ao sair e ao parar o servidor.
 */
public final class GunCombat {
    /** Recarga em andamento: arma (mão), fim e Foco pago (devolvido se a recarga for cancelada). */
    private record Reload(InteractionHand hand, ItemStack stack, long end, float paid, int ticks) {}

    private static final Map<UUID, Reload> RELOADS = new HashMap<>();
    /** Pistolas duplas: qual mão atira agora. */
    private static final Map<UUID, Boolean> DUAL_LEFT = new HashMap<>();

    public static final float CLASS_RELOAD_SPEED = 0.6F;
    public static final float CLASS_RELOAD_COST = 0.5F;
    public static final float SET_RELOAD_SPEED = 0.85F;
    public static final float SPELL_REFUND = 0.20F;
    public static final float SPELL_COOLDOWN = 0.75F;
    public static final float RANK_DAMAGE = 0.04F;

    // =====================================================================================
    //  Regras comuns
    // =====================================================================================

    /** Aliado do atirador (mesma regra do SpellContext: dono, bichos, grupo/facção, PvP desligado). */
    public static boolean isAlly(@Nullable Entity owner, Entity e) {
        if (owner == null || e == null) return false;
        if (e == owner) return true;
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return true;
        Boolean social = com.renanpontes.isekaimahou.core.api.Party.allyOverride(owner, e);
        if (social != null) return social;
        if (owner.isAlliedTo(e)) return true;
        return owner instanceof Player && e instanceof Player && owner.level().getServer() != null
                && !owner.level().getServer().isPvpAllowed();
    }

    public static boolean isGunslinger(@Nullable Entity e) {
        return e instanceof Player p && MagicApi.get(p).path() == MagicPath.GUNSLINGER;
    }

    /** Multiplicador do tiro na cabeça (o Pistoleiro mira melhor). */
    public static float headshotMultiplier(@Nullable Entity owner) {
        return isGunslinger(owner) ? 1.5F : 1.25F;
    }

    /** Multiplicador do tier da arma (revólver I–IV; as outras crescem com o rank — ver {@link #damageMultiplier}). */
    public static float tierMultiplier(ManaGunItem gun) {
        return 1.0F + 0.18F * gun.tier();
    }

    /** Multiplicador total do tiro básico do jogador. */
    public static float damageMultiplier(Player p, ManaGunItem gun) {
        MagicData d = MagicApi.get(p);
        boolean own = d.path() == MagicPath.GUNSLINGER;
        int rank = TriggerPath.rankOf(d);
        float m = tierMultiplier(gun);
        if (gun.kind() != GunKind.REVOLVER) m *= 1.0F + 0.12F * rank;
        if (own) m *= 1.0F + RANK_DAMAGE * rank;
        else m *= 0.8F; // outras classes atiram, mas sem o treino do Pistoleiro
        m *= 1.0F + 0.012F * d.level();
        AttributeInstance sp = p.getAttribute(CoreRegistries.SPELL_POWER);
        if (sp != null) m *= (float) Math.max(0.1, sp.getValue());
        return m * Progression.DAMAGE_SCALE;
    }

    /** Acertou uma bala: contadores do Caminho do Gatilho. */
    public static void onBulletHit(ServerPlayer sp, Entity target, boolean head) {
        if (sp instanceof FakePlayer) return;
        MagicApi.addCounter(sp, TriggerPath.HITS, 1);
        if (head) {
            MagicApi.addCounter(sp, TriggerPath.HEADSHOTS, 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.headshot")
                    .withColor(0xFFD86B), true);
        }
    }

    // =====================================================================================
    //  Tiro básico
    // =====================================================================================

    /** Puxou o gatilho (clique ou automático). */
    public static void trigger(ServerPlayer p, ItemStack stack, InteractionHand hand) {
        ManaGunItem gun = ManaGunItem.of(stack);
        if (gun == null || p.isSpectator()) return;
        if (RELOADS.containsKey(p.getUUID())) return; // recarregando
        ServerLevel level = p.serverLevel();
        int rounds = ManaGunItem.rounds(stack);
        if (rounds <= 0) {
            GsFx.empty(level, p.getEyePosition());
            startReload(p, hand);
            return;
        }
        if (DuelService.onPlayerShot(p)) return; // queimou a largada do duelo
        GunKind kind = gun.kind();
        Vec3 look = p.getLookAngle();
        int side = 1;
        if (kind == GunKind.DUAL_PISTOLS) {
            boolean left = DUAL_LEFT.merge(p.getUUID(), true, (a, b) -> !a);
            side = left ? -1 : 1;
        }
        if (hand == InteractionHand.OFF_HAND) side = -side;
        Vec3 muzzle = GsFx.muzzle(p, look, side);
        float dmg = kind.damage * damageMultiplier(p, gun);
        boolean scoped = kind == GunKind.RIFLE && p.isShiftKeyDown();
        float spread = scoped ? 0F : kind.spread * (p.isSprinting() ? 2.0F : 1.0F);
        int rgb = gunColor(gun);
        byte style = kind == GunKind.SHOTGUN ? ManaBulletEntity.STYLE_PELLET : kind == GunKind.RIFLE ? ManaBulletEntity.STYLE_HEAVY
                : ManaBulletEntity.STYLE_NORMAL;
        for (int i = 0; i < kind.pellets; i++) {
            ManaBulletEntity b = ManaBulletEntity.plain(level, p, muzzle, dmg, rgb, style).fire(look, kind.speed, spread);
            if (kind.pierce > 0) b.pierce(kind.pierce);
            if (kind == GunKind.SHOTGUN) b.knockback(0.35F).lifetime(12);
            level.addFreshEntity(b);
        }
        if (!p.getAbilities().instabuild) ManaGunItem.setRounds(stack, rounds - 1);
        GsFx.shot(level, muzzle, look, rgb, kind);
        if (!kind.automatic) p.getCooldowns().addCooldown(gun, kind.interval);
        // no tiro, "ticks" leva o tipo da arma (o cliente escolhe o coice)
        GsNetwork.anim(p, side < 0 ? GsNetwork.SHOOT_LEFT : GsNetwork.SHOOT, kind.ordinal(), rgb);
        // escopeta: o coice empurra para trás
        if (kind == GunKind.SHOTGUN) {
            p.push(-look.x * 0.25, 0.02, -look.z * 0.25);
            p.hurtMarked = true;
        }
    }

    /** Cor do tiro: dourado no revólver, e cada arma com a sua. */
    public static int gunColor(ManaGunItem gun) {
        return switch (gun.kind()) {
            case REVOLVER -> gun.tier() >= 4 ? 0xFFB22A : gun.tier() >= 2 ? 0xFFD86B : GsFx.GOLD;
            case PISTOL -> 0x8FD8FF;
            case SHOTGUN -> 0xFF7A3A;
            case DUAL_PISTOLS -> 0xC08CFF;
            case RIFLE -> 0x9AF0B0;
        };
    }

    // =====================================================================================
    //  Recarga
    // =====================================================================================

    /** Tecla de trocar de mão (F) com arma na mão principal: recarrega em vez de trocar. */
    public static void onSwapHands(LivingSwapItemsEvent.Hands e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!(p.getMainHandItem().getItem() instanceof ManaGunItem)) return;
        e.setCanceled(true);
        startReload(p, InteractionHand.MAIN_HAND);
    }

    public static boolean reloading(Player p) {
        return RELOADS.containsKey(p.getUUID());
    }

    public static void startReload(ServerPlayer p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        ManaGunItem gun = ManaGunItem.of(stack);
        if (gun == null || RELOADS.containsKey(p.getUUID())) return;
        GunKind kind = gun.kind();
        int missing = kind.rounds - ManaGunItem.rounds(stack);
        if (missing <= 0) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.full").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MagicData d = MagicApi.get(p);
        boolean own = d.path() == MagicPath.GUNSLINGER;
        float cost = missing * kind.reloadCostPerRound * d.maxMana() * (own ? CLASS_RELOAD_COST : 1.0F);
        boolean creative = p.getAbilities().instabuild;
        if (!creative && d.mana() < cost) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.no_focus",
                    MagicApi.resourceName(p)).withStyle(ChatFormatting.RED), true);
            GsFx.empty(p.serverLevel(), p.getEyePosition());
            return;
        }
        if (!creative) d.consumeMana(cost);
        float speed = (own ? CLASS_RELOAD_SPEED : 1.0F) * (GunslingerArmorItem.fullSet(p) ? SET_RELOAD_SPEED : 1.0F);
        int ticks = Math.max(6, Math.round(kind.reloadTicks * speed));
        long end = p.serverLevel().getGameTime() + ticks;
        RELOADS.put(p.getUUID(), new Reload(hand, stack, end, creative ? 0 : cost, ticks));
        GsFx.reloadStart(p.serverLevel(), p.position().add(0, 1.2, 0), kind);
        GsNetwork.anim(p, GsNetwork.RELOAD, ticks, gunColor(gun));
        MagicApi.addCounter(p, TriggerPath.RELOADS, 1);
        GunTriggers.onReload(p);
    }

    /** Todo tick do jogador: termina (ou cancela) a recarga. */
    public static void tick(ServerPlayer p) {
        Reload r = RELOADS.get(p.getUUID());
        if (r == null) return;
        ItemStack held = p.getItemInHand(r.hand());
        if (held != r.stack() || !p.isAlive()) {
            // trocou de arma no meio: cancela e devolve o Foco
            RELOADS.remove(p.getUUID());
            if (r.paid() > 0) MagicApi.get(p).addMana(r.paid());
            GsNetwork.anim(p, GsNetwork.RELOAD_CANCEL, 0, 0);
            return;
        }
        long now = p.serverLevel().getGameTime();
        // estalos no meio da recarga (cartuchos entrando)
        long left = r.end() - now;
        if (left > 0 && left % 6 == 0) {
            GsFx.sound(p.serverLevel(), p.position().add(0, 1.2, 0), net.minecraft.sounds.SoundEvents.CROSSBOW_QUICK_CHARGE_1.value(),
                    0.35F, 1.6F + p.getRandom().nextFloat() * 0.3F);
        }
        if (now < r.end()) return;
        RELOADS.remove(p.getUUID());
        ManaGunItem gun = ManaGunItem.of(held);
        if (gun == null) return;
        ManaGunItem.setRounds(held, gun.kind().rounds);
        GsFx.reloadEnd(p.serverLevel(), p.position().add(0, 1.2, 0), gun.kind());
    }

    // =====================================================================================
    //  Arma na mão para as formas de glifo
    // =====================================================================================

    /** Arma de mana na mão (principal, depois secundária), ou null. */
    @Nullable
    public static ItemStack heldGun(LivingEntity e) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = e.getItemInHand(h);
            if (s.getItem() instanceof ManaGunItem) return s;
        }
        return null;
    }

    /**
     * Uma forma de arma vai disparar: com arma de mana carregada na mão, gasta um cartucho e devolve o bônus de poder
     * (×1,10 + 0,05 por tier); sem arma (ou tambor vazio) a forma conjura uma arma fantasma e sai com poder normal.
     * Para o Pistoleiro soma +4% por rank do Caminho do Gatilho.
     */
    public static float formPower(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return 1.0F;
        float m = 1.0F;
        if (caster instanceof Player p && MagicApi.get(p).path() == MagicPath.GUNSLINGER) {
            m *= 1.0F + RANK_DAMAGE * TriggerPath.rankOf(MagicApi.get(p));
        }
        if (ctx.hasOrigin()) return m; // estágio 2+: nasce longe da mão
        ItemStack gun = heldGun(caster);
        if (gun != null && ManaGunItem.rounds(gun) > 0) {
            if (!(caster instanceof Player p && p.getAbilities().instabuild)) ManaGunItem.setRounds(gun, ManaGunItem.rounds(gun) - 1);
            ManaGunItem g = ManaGunItem.of(gun);
            m *= 1.10F + 0.05F * (g == null ? 0 : g.tier());
        }
        return m;
    }

    /** Tipo da arma na mão (timbre do tiro das formas), revólver se não houver. */
    public static GunKind heldKind(@Nullable LivingEntity e) {
        ItemStack s = e == null ? null : heldGun(e);
        ManaGunItem g = s == null ? null : ManaGunItem.of(s);
        return g == null ? GunKind.REVOLVER : g.kind();
    }

    // =====================================================================================
    //  Bônus de classe nas magias
    // =====================================================================================

    /**
     * Magia com forma de arma do Pistoleiro, conjurada pelo próprio Pistoleiro: devolve 20% do Foco e encurta a
     * recarga do slot em 25% (o núcleo já gravou a recarga antes do Post; só mexemos se for exatamente a desta magia).
     */
    public static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        SpellContext ctx = e.getContext();
        if (!ctx.isPlayerCast() || ctx.stage() != 0) return;
        ParsedSpell spell = ctx.spell();
        if (spell.form().origin() != MagicPath.GUNSLINGER) return;
        MagicData d = MagicApi.get(p);
        if (d.path() != MagicPath.GUNSLINGER) return;
        if (!p.getAbilities().instabuild && !spell.anyStats(s -> s.overload)) {
            d.addMana(spell.manaCost() * SPELL_REFUND);
        }
        if (d.lastCastSlot < 0) return;
        String key = MagicData.slotKey(d.activeLoadout(), d.lastCastSlot);
        long now = p.serverLevel().getGameTime();
        Long cd = d.cooldowns.get(key);
        if (cd == null || cd != now + spell.cooldown()) return;
        int ticks = Math.max(1, Math.round(spell.cooldown() * SPELL_COOLDOWN));
        d.cooldowns.put(key, now + ticks);
        PacketDistributor.sendToPlayer(p, new CorePayloads.Cooldown(key, ticks));
    }

    /** Raio de tiro instantâneo (Bala Perfurante, Doze Badaladas): ponto final e alvos ao longo da linha. */
    public static Vec3 rayEnd(SpellContext ctx, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        var block = ctx.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                ctx.caster()));
        return block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : block.getLocation();
    }

    /** Usado pelos glifos para girar uma direção (graus). */
    public static Vec3 rotate(Vec3 dir, float yaw, float pitch) {
        return CoreGlyphs.rotate(dir, yaw, pitch);
    }

    public static void forget(UUID player) {
        RELOADS.remove(player);
        DUAL_LEFT.remove(player);
    }

    public static void clear() {
        RELOADS.clear();
        DUAL_LEFT.clear();
    }

    private GunCombat() {}
}
