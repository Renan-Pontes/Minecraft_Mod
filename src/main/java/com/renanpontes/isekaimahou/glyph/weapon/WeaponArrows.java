package com.renanpontes.isekaimahou.glyph.weapon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Flechas da forma {@code arrow}: flechas VANILLA marcadas com a magia (dados persistentes da entidade, chave
 * {@link #KEY}). Ao acertar ({@link ProjectileImpactEvent}) aplicam os efeitos; enquanto voam soltam o pó do
 * elemento. Perfurar/Ricochete/Teleguiado são feitos aqui (a flecha vanilla não sabe deles).
 * Sobrevive a salvar/carregar o mundo: a marca vai junto com a flecha.
 */
public final class WeaponArrows {
    /** Chave dos dados persistentes da flecha marcada. */
    public static final String KEY = "isekai_mahou_weapon_arrow";
    /** Flecha real (do arco): dano base ×0.75 do vanilla, porque a magia soma o dela. */
    private static final float REAL_DAMAGE_MULT = 0.75F;
    /** Flecha conjurada (sem arco): dano base baixo; o poder vem da magia. */
    private static final double CONJURED_DAMAGE = 0.8;
    private static final float CONJURED_SPEED = 2.4F;
    /** Voando por mais que isto sem acertar nada: a magia termina (Ao Terminar) e a flecha vira comum. */
    private static final int LIFETIME = 200;
    private static final double HOMING_RANGE = 18.0;

    private static final class Tracked {
        final AbstractArrow arrow;
        @Nullable SpellContext ctx;
        int ricochet;
        int pierce;
        final boolean homing;
        final float radius;
        final boolean sensitive;
        int age;
        final Set<UUID> hit = new HashSet<>();
        final Set<UUID> passed = new HashSet<>();

        Tracked(AbstractArrow arrow, @Nullable SpellContext ctx, CompoundTag tag) {
            this.arrow = arrow;
            this.ctx = ctx;
            this.ricochet = tag.getInt("Ricochet");
            this.pierce = tag.getInt("Pierce");
            this.homing = tag.getBoolean("Homing");
            this.radius = tag.getFloat("Radius");
            this.sensitive = tag.getBoolean("Sensitive");
            this.age = tag.getInt("Age");
        }

        @Nullable
        SpellContext context(ServerLevel level) {
            if (ctx == null) {
                CompoundTag tag = data(arrow);
                if (tag != null && tag.contains("Spell", Tag.TAG_COMPOUND)) ctx = SpellContext.load(level, tag.getCompound("Spell"));
            }
            return ctx;
        }

        /** Grava os contadores (e o contexto atual) de volta na flecha. */
        void save() {
            CompoundTag tag = data(arrow);
            if (tag == null) return;
            tag.putInt("Ricochet", ricochet);
            tag.putInt("Pierce", pierce);
            tag.putInt("Age", age);
            if (ctx != null) tag.put("Spell", ctx.save());
        }
    }

    private static final Map<UUID, Tracked> ACTIVE = new HashMap<>();

    // =====================================================================================
    //  Disparo
    // =====================================================================================

    /** A forma: dispara 1 + Dividir flechas (reais com arco + flecha; conjuradas sem). */
    public static void fire(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();

        // Arma de disparo na mão (só no estágio 0: estágios seguintes nascem longe da mão)
        ItemStack weapon = ItemStack.EMPTY;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (!ctx.hasOrigin()) {
            for (InteractionHand h : InteractionHand.values()) {
                ItemStack st = caster.getItemInHand(h);
                if (!st.isEmpty() && st.getItem() instanceof ProjectileWeaponItem) {
                    weapon = st;
                    hand = h;
                    break;
                }
            }
        }
        Player player = caster instanceof Player p ? p : null;
        boolean creative = player != null && player.getAbilities().instabuild;
        ItemStack ammo = ItemStack.EMPTY;
        if (!weapon.isEmpty()) ammo = player != null ? player.getProjectile(weapon) : new ItemStack(Items.ARROW);
        boolean real = !weapon.isEmpty() && !ammo.isEmpty() && ammo.getItem() instanceof ArrowItem;
        boolean crossbow = real && weapon.getItem() instanceof CrossbowItem;

        // Gasta a flecha (Infinidade/criativo não gastam) e a durabilidade da arma
        boolean firstPickup = false;
        ItemStack shotAmmo = ammo.isEmpty() ? ItemStack.EMPTY : ammo.copyWithCount(1);
        if (real && player != null && !creative) {
            int use = EnchantmentHelper.processAmmoUse(level, weapon, ammo, 1);
            if (use > 0) {
                ammo.shrink(use);
                if (ammo.isEmpty()) player.getInventory().removeItem(ammo);
                firstPickup = true;
            }
            weapon.hurtAndBreak(1, caster, LivingEntity.getSlotForHand(hand));
        }

        int n = s.count();
        float speed = (real ? (crossbow ? 3.15F : 3.0F) : CONJURED_SPEED) * s.speedMult();
        Vec3 look = ctx.look();
        for (int i = 0; i < n; i++) {
            float yawOff = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 6.0F;
            float pitchOff = 0;
            if (s.spread) {
                yawOff = (ctx.random().nextFloat() - 0.5F) * 30.0F;
                pitchOff = (ctx.random().nextFloat() - 0.5F) * 14.0F;
            }
            AbstractArrow arrow;
            if (real) {
                arrow = ((ArrowItem) shotAmmo.getItem()).createArrow(level, shotAmmo, caster, weapon.isEmpty() ? null : weapon);
                arrow.setBaseDamage(arrow.getBaseDamage() * REAL_DAMAGE_MULT);
                arrow.setCritArrow(true);
                if (crossbow) arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
                arrow.pickup = i == 0 && firstPickup ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.CREATIVE_ONLY;
            } else {
                arrow = new Arrow(level, caster, new ItemStack(Items.ARROW), null);
                arrow.setBaseDamage(CONJURED_DAMAGE);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            }
            if (ctx.hasOrigin()) {
                Vec3 o = ctx.eyePos();
                arrow.setPos(o.x, o.y - arrow.getBbHeight() / 2, o.z);
            }
            Vec3 d = CoreGlyphs.rotate(look, yawOff, pitchOff);
            arrow.shoot(d.x, d.y, d.z, speed, s.spread ? 0.0F : 0.2F);
            Tracked tracked = mark(arrow, ctx, s);
            if (ctx.previousTarget() != null) {
                // Estágio: a flecha nasce DENTRO de quem acabou de ser atingido — atravessa ele sem acertar de novo
                tracked.hit.add(ctx.previousTarget());
                tracked.passed.add(ctx.previousTarget());
            }
            if (!level.addFreshEntity(arrow)) ACTIVE.remove(arrow.getUUID());
        }

        Vec3 muzzle = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.8));
        ElementFx.burst(level, muzzle, ctx.element(), 8, 0.15);
        if (real) {
            WeaponFx.sound(level, caster.position(), crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, 1.0F,
                    1.0F / (level.random.nextFloat() * 0.4F + 1.2F) + 0.5F);
        } else {
            WeaponFx.sound(level, muzzle, SoundEvents.ARROW_SHOOT, 0.8F, 1.6F);
            WeaponFx.sound(level, muzzle, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.5F);
        }
    }

    /** Marca a flecha com a magia (antes do addFreshEntity). */
    private static Tracked mark(AbstractArrow arrow, SpellContext ctx, SpellStats s) {
        CompoundTag tag = new CompoundTag();
        tag.put("Spell", ctx.save());
        tag.putInt("Ricochet", Math.max(0, s.ricochet));
        tag.putInt("Pierce", Math.max(0, s.pierce));
        tag.putBoolean("Homing", s.homing);
        tag.putFloat("Radius", s.aoe > 0 ? s.radius(1.5F) : 0F);
        tag.putBoolean("Sensitive", s.sensitive);
        tag.putInt("Age", 0);
        arrow.getPersistentData().put(KEY, tag);
        // Mesmo contexto para as flechas irmãs (Dividir): os disparos de ligação contam juntos, como no Projétil
        Tracked t = new Tracked(arrow, ctx, tag);
        ACTIVE.put(arrow.getUUID(), t);
        return t;
    }

    @Nullable
    private static CompoundTag data(Entity e) {
        CompoundTag pd = e.getPersistentData();
        return pd.contains(KEY, Tag.TAG_COMPOUND) ? pd.getCompound(KEY) : null;
    }

    private static void unmark(Tracked t) {
        t.arrow.getPersistentData().remove(KEY);
        ACTIVE.remove(t.arrow.getUUID());
    }

    /** A flecha leva uma magia da forma Flecha? */
    public static boolean isSpellArrow(Entity e) {
        return e instanceof AbstractArrow && data(e) != null;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Flecha marcada carregada do disco (ou que voltou a um chunk): volta a ser acompanhada. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (ACTIVE.containsKey(arrow.getUUID())) return;
        CompoundTag tag = data(arrow);
        if (tag != null) ACTIVE.put(arrow.getUUID(), new Tracked(arrow, null, tag));
    }

    /**
     * Saiu do mundo (descarregou, foi recolhida, sumiu): para de acompanhar. O estado já está gravado na flecha
     * (ver {@link Tracked#save}, chamado a cada mudança e a cada segundo de voo).
     */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        Tracked t = ACTIVE.get(arrow.getUUID());
        if (t != null && t.arrow == arrow) ACTIVE.remove(arrow.getUUID());
    }

    public static void onImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        if (!(proj instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        Tracked t = ACTIVE.get(arrow.getUUID());
        if (t == null) {
            CompoundTag tag = data(arrow);
            if (tag == null) return;
            t = new Tracked(arrow, null, tag);
            ACTIVE.put(arrow.getUUID(), t);
        }
        SpellContext ctx = t.context(level);
        if (ctx == null) {
            unmark(t);
            return;
        }
        HitResult hit = event.getRayTraceResult();
        Vec3 motion = arrow.getDeltaMovement();
        if (hit instanceof EntityHitResult ehr) {
            onEntityHit(event, level, t, ctx, ehr, motion);
        } else if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            onBlockHit(event, level, t, ctx, bhr, motion);
        }
    }

    private static void onEntityHit(ProjectileImpactEvent event, ServerLevel level, Tracked t, SpellContext ctx,
                                    EntityHitResult ehr, Vec3 motion) {
        AbstractArrow arrow = t.arrow;
        Entity target = ehr.getEntity();
        UUID id = target.getUUID();
        if (t.hit.contains(id)) {
            // Já atingido: atravessa se foi perfurado (senão o vanilla decide, sem aplicar a magia de novo)
            if (t.passed.contains(id)) event.setCanceled(true);
            return;
        }
        t.hit.add(id);
        Vec3 at = ehr.getLocation();
        ctx.impact(at, motion, target);
        boolean pierce = t.pierce > 0;
        if (pierce) {
            // Perfurar: o dano da flecha é aplicado aqui e ela segue voando
            event.setCanceled(true);
            t.pierce--;
            t.passed.add(id);
            pierceDamage(level, arrow, target);
            t.save();
        } else {
            // A flecha some (ou quica) no vanilla: a marca acaba aqui
            unmark(t);
        }
        float radius = t.radius;
        boolean sensitive = t.sensitive;
        SpellScheduler.schedule(1, () -> {
            if (radius > 0) {
                ctx.resolveArea(at, radius, arrow, sensitive);
                ElementFx.burst(level, at, ctx.element(), (int) (12 + radius * 8), radius * 0.4);
            } else if (target.isAlive() && target.level() == level) {
                target.invulnerableTime = 0;
                ctx.resolveOnEntity(target, arrow);
                ElementFx.burst(level, at, ctx.element(), 10, 0.25);
            }
        });
    }

    private static void onBlockHit(ProjectileImpactEvent event, ServerLevel level, Tracked t, SpellContext ctx,
                                   BlockHitResult bhr, Vec3 motion) {
        AbstractArrow arrow = t.arrow;
        if (t.ricochet > 0) {
            event.setCanceled(true);
            t.ricochet--;
            Vec3 n = Vec3.atLowerCornerOf(bhr.getDirection().getNormal());
            Vec3 v = motion.subtract(n.scale(2 * motion.dot(n))).scale(0.85);
            arrow.setDeltaMovement(v);
            arrow.hasImpulse = true;
            arrow.hurtMarked = true;
            ElementFx.burst(level, bhr.getLocation(), ctx.element(), 6, 0.2);
            WeaponFx.sound(level, bhr.getLocation(), SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 1.6F);
            t.save();
            return;
        }
        Vec3 at = bhr.getLocation();
        ctx.impact(at, motion, null);
        if (t.radius > 0) {
            ctx.resolveArea(at, t.radius, arrow, true);
        } else {
            ctx.resolveOnBlock(bhr, arrow);
        }
        ElementFx.burst(level, at, ctx.element(), (int) (10 + t.radius * 8), Math.max(0.25, t.radius * 0.4));
        unmark(t);
    }

    /** Dano da flecha num alvo perfurado (igual ao vanilla: velocidade × dano base, encantamentos, crítico). */
    private static void pierceDamage(ServerLevel level, AbstractArrow arrow, Entity target) {
        Entity owner = arrow.getOwner();
        DamageSource src = arrow.damageSources().arrow(arrow, owner != null ? owner : arrow);
        double base = arrow.getBaseDamage();
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon != null) base = EnchantmentHelper.modifyDamage(level, weapon, target, src, (float) base);
        int dmg = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * base, 0.0, Integer.MAX_VALUE));
        if (arrow.isCritArrow()) dmg = (int) Math.min((long) dmg + level.random.nextInt(dmg / 2 + 2), Integer.MAX_VALUE);
        if (owner instanceof LivingEntity lo) lo.setLastHurtMob(target);
        if (target.hurt(src, dmg)) {
            WeaponFx.sound(level, target.position(), SoundEvents.ARROW_HIT, 1.0F, 1.2F);
        }
    }

    /** Todo tick: rastro do elemento, Teleguiado e fim do tempo de voo. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Tracked>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Tracked t = it.next().getValue();
            AbstractArrow arrow = t.arrow;
            if (arrow.isRemoved() || !(arrow.level() instanceof ServerLevel level) || level.getEntity(arrow.getUUID()) != arrow) {
                it.remove();
                continue;
            }
            SpellContext ctx = t.context(level);
            if (ctx == null) {
                arrow.getPersistentData().remove(KEY);
                it.remove();
                continue;
            }
            t.age++;
            if (t.age > LIFETIME) {
                ctx.end(arrow.position(), arrow.getDeltaMovement());
                arrow.getPersistentData().remove(KEY);
                it.remove();
                continue;
            }
            if (t.age % 20 == 0) t.save();
            trail(level, arrow, ctx);
            if (t.homing && t.age > 3) steer(arrow, ctx, t);
        }
    }

    private static void trail(ServerLevel level, AbstractArrow arrow, SpellContext ctx) {
        Element el = ctx.element();
        Vec3 p = arrow.position();
        Vec3 back = arrow.getDeltaMovement().scale(-0.5);
        int rgb = ctx.color() & 0xFFFFFF;
        level.sendParticles(WeaponFx.dust(rgb, 0.9F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        level.sendParticles(WeaponFx.dust(rgb, 0.6F), p.x + back.x, p.y + back.y, p.z + back.z, 1, 0.03, 0.03, 0.03, 0);
        if (arrow.tickCount % 3 == 0) level.sendParticles(ElementFx.signature(el), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private static void steer(AbstractArrow arrow, SpellContext ctx, Tracked t) {
        LivingEntity best = null;
        double bestD = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity e : ctx.enemiesAround(arrow.position(), HOMING_RANGE)) {
            if (t.hit.contains(e.getUUID())) continue;
            double d = e.distanceToSqr(arrow);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return;
        Vec3 v = arrow.getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-3) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(arrow.position()).normalize();
        arrow.setDeltaMovement(v.normalize().lerp(to, 0.25).normalize().scale(speed));
        arrow.hasImpulse = true;
        arrow.hurtMarked = true;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private WeaponArrows() {}
}
