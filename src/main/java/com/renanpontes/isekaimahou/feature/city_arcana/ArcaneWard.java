package com.renanpontes.isekaimahou.feature.city_arcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * BARREIRA ARCANA: um domo invisível do tamanho do território (aparece brilhando onde bloqueia algo). Força pelo nível
 * do prédio (danificado = metade):
 * <ul>
 *   <li>dano de MAGIA de quem a cidade não protege contra quem ela protege: −{@link #reduction} (18% → 50%; chefes, metade);</li>
 *   <li>projéteis mágicos de INIMIGOS (monstros, hordas, jogadores em guerra ou fora do toque de recolher) que entram no
 *       território se desfazem com chance {@link #fizzleChance} (52% → 100%); de chefes, nunca;</li>
 *   <li>magias conjuradas por inimigos DENTRO do território perdem {@link #castWeaken} do poder e podem se dissipar
 *       ({@link #castDispel}: 10% → 30%);</li>
 *   <li>pulso de defesa a cada 2 s: inimigos a até 8+4×nível blocos do catalisador levam choque (1,5–3,5 de dano mágico)
 *       e lentidão; chefes e jogadores só ficam lentos.</li>
 * </ul>
 * Dentro de arenas de chefe seladas a barreira fica quieta (a luta funciona como em qualquer lugar).
 */
public final class ArcaneWard {
    /** Projéteis mágicos acompanhados (decididos ao entrar num território com barreira). */
    private static final List<Projectile> TRACKED = new ArrayList<>();
    private static final int MAX_TRACKED = 512;
    private static final Map<UUID, Long> WARN = new HashMap<>();

    public static float reduction(int lv) {
        return 0.10F + 0.08F * lv;
    }

    public static float fizzleChance(int lv) {
        return Math.min(1F, 0.40F + 0.12F * lv);
    }

    public static float castDispel(int lv) {
        return 0.05F + 0.05F * lv;
    }

    public static float castWeaken(int lv) {
        return 0.06F * lv;
    }

    public static double pulseRadius(int lv) {
        return 8 + 4 * lv;
    }

    // =====================================================================================
    //  Dano de magia contra quem a cidade protege
    // =====================================================================================
    static void onSpellDamage(SpellDamageEvent.Pre e) {
        LivingEntity target = e.target();
        if (!(target.level() instanceof ServerLevel sl)) return;
        CityArcana.City c = CityArcana.at(target);
        if (c == null || c.ward <= 0) return;
        LivingEntity caster = e.ctx().caster();
        if (caster == null || caster == target || !CityArcana.friendly(c, target) || CityArcana.friendly(c, caster)) return;
        if (CityArcana.inBossArena(sl, target.position())) return;
        float red = reduction(c.ward);
        if (CityArcana.isBoss(caster)) red *= 0.5F;
        e.setAmount(e.getAmount() * (1F - red));
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = caster.position().subtract(target.position());
        Vec3 n = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();
        if (c.fxReady(CityArcanaNet.WARD_HIT, sl.getGameTime(), 4)) {
            CityArcanaNet.send(sl, CityArcanaNet.WARD_HIT, at.add(n.scale(0.9)), n, 0.9F, CityArcana.wardColor(c));
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.6F);
        }
    }

    // =====================================================================================
    //  Magias de inimigos conjuradas dentro do território
    // =====================================================================================
    static void onCastPre(SpellCastEvent.Pre e) {
        LivingEntity caster = e.getCaster();
        if (!(caster.level() instanceof ServerLevel sl)) return;
        CityArcana.City c = CityArcana.at(caster);
        if (c == null || c.ward <= 0 || !CityArcana.hostile(c, caster)) return;
        if (CityArcana.inBossArena(sl, caster.position())) return;
        SpellContext ctx = e.getContext();
        if (CityArcana.isBoss(caster)) {
            ctx.setPowerMult(ctx.powerMult() * (1F - castWeaken(c.ward) * 0.5F));
            return;
        }
        Vec3 at = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        if (sl.random.nextFloat() < castDispel(c.ward)) {
            e.setCanceled(true);
            CityArcanaNet.send(sl, CityArcanaNet.WARD_HIT, at, new Vec3(0, 1, 0), 1.2F, CityArcana.wardColor(c));
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, 1.3F);
            if (caster instanceof ServerPlayer sp && warn(sp)) {
                sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.ward.dispelled",
                        SettlementService.cityName(sp.server, c.s)).withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        ctx.setPowerMult(ctx.powerMult() * (1F - castWeaken(c.ward)));
        if (caster instanceof ServerPlayer sp && warn(sp)) {
            sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.ward.weakened",
                    SettlementService.cityName(sp.server, c.s), Math.round(castWeaken(c.ward) * 100)).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static boolean warn(ServerPlayer sp) {
        long now = sp.serverLevel().getGameTime();
        Long last = WARN.get(sp.getUUID());
        if (last != null && now - last < 60) return false;
        if (WARN.size() > 256) WARN.clear();
        WARN.put(sp.getUUID(), now);
        return true;
    }

    // =====================================================================================
    //  Projéteis mágicos que entram no território
    // =====================================================================================
    /** Projétil "mágico": de magia (glifo), bolas de fogo/caveiras/cargas de vento, balas de shulker, poções e os do mod. */
    static boolean magical(Projectile p) {
        if (p instanceof SpellProjectileEntity || p instanceof AbstractHurtingProjectile || p instanceof ShulkerBullet
                || p instanceof ThrownPotion) return true;
        if (p instanceof AbstractArrow) return false;
        return IsekaiMahou.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).getNamespace());
    }

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Projectile p)) return;
        if (TRACKED.size() >= MAX_TRACKED || !magical(p) || !CityArcana.anyWard(e.getLevel().dimension())) return;
        TRACKED.add(p);
    }

    /** A cada 2 ticks: decide os projéteis que entraram num território com barreira. */
    static void tickProjectiles(MinecraftServer server) {
        if (TRACKED.isEmpty()) return;
        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            Projectile p = TRACKED.get(i);
            if (p.isRemoved() || p.tickCount > 400 || !(p.level() instanceof ServerLevel sl)) {
                TRACKED.remove(i);
                continue;
            }
            CityArcana.City c = CityArcana.at(p);
            if (c == null || c.ward <= 0) continue;
            TRACKED.remove(i); // decidido uma vez só
            Entity owner = p.getOwner();
            if (!CityArcana.hostile(c, owner) || CityArcana.isBoss(owner)) continue;
            if (CityArcana.inBossArena(sl, p.position())) continue;
            if (sl.random.nextFloat() >= fizzleChance(c.ward)) continue;
            fizzle(sl, c, p);
        }
    }

    private static void fizzle(ServerLevel sl, CityArcana.City c, Projectile p) {
        Vec3 pos = p.position();
        // normal: para fora do território (da praça até o ponto), ou contra o voo se estiver bem no meio
        Vec3 out = new Vec3(pos.x - c.s.heart.getX() - 0.5, 0, pos.z - c.s.heart.getZ() - 0.5);
        Vec3 n;
        if (c.edgeDistance(pos.x, pos.z) < 8 && out.lengthSqr() > 1) n = out.normalize();
        else n = p.getDeltaMovement().lengthSqr() > 1.0E-4 ? p.getDeltaMovement().normalize().scale(-1) : new Vec3(0, 1, 0);
        p.discard();
        if (c.fxReady(CityArcanaNet.WARD_HIT, sl.getGameTime(), 3)) {
            CityArcanaNet.send(sl, CityArcanaNet.WARD_HIT, pos, n, 1.6F + 0.2F * c.ward, CityArcana.wardColor(c));
        }
        sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.2F, 0.9F + sl.random.nextFloat() * 0.3F);
        sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.6F);
        if (p.getOwner() instanceof ServerPlayer sp && warn(sp)) {
            sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.ward.fizzled",
                    SettlementService.cityName(sp.server, c.s)).withStyle(ChatFormatting.RED), true);
        }
    }

    // =====================================================================================
    //  Pulso de defesa (a cada 40 ticks)
    // =====================================================================================
    static void pulse(MinecraftServer server) {
        for (CityArcana.City c : CityArcana.cities()) {
            if (c.ward <= 0) continue;
            CityArcana.Site site = c.site(CityArcana.WARD);
            ServerLevel sl = server.getLevel(c.dim);
            if (site == null || sl == null || !sl.isLoaded(site.center())) continue;
            int lv = c.ward;
            double r = pulseRadius(lv);
            Vec3 crystal = Vec3.atBottomCenterOf(site.center()).add(0, 4 + 2 * lv + 2, 0);
            AABB box = new AABB(site.center()).inflate(r, 12, r);
            int hit = 0;
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && CityArcana.hostile(c, x))) {
                if (hit >= 6) break;
                if (CityArcana.inBossArena(sl, le.position())) continue;
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, lv >= 3 ? 1 : 0, true, true));
                boolean player = le instanceof ServerPlayer;
                if (!player && !CityArcana.isBoss(le)) le.hurt(sl.damageSources().magic(), 1.0F + 0.5F * lv);
                CityArcanaNet.send(sl, CityArcanaNet.ZAP, crystal, le.position().add(0, le.getBbHeight() * 0.5, 0), 1F,
                        CityArcana.wardColor(c));
                hit++;
            }
            if (hit > 0) sl.playSound(null, crystal.x, crystal.y, crystal.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        }
    }

    static int tracked() {
        return TRACKED.size();
    }

    static void forget(UUID id) {
        WARN.remove(id);
    }

    static void clear() {
        TRACKED.clear();
        WARN.clear();
    }

    private ArcaneWard() {}
}
