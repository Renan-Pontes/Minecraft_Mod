package com.renanpontes.isekaimahou.feature.espada.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.espada.EspadaLegends;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;
import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Grimmjow Jaegerjaquez</b>, Sexta Espada — o brigão rápido. Selado: socos em sequência, Sonído com soco, Cero azul
 * e <b>Gran Rey Cero</b> (corta o pulso: feixe azul misturado com sangue, enorme e marcado). Em 50%: "Kishire, Pantera"
 * — forma de pantera, muito mais rápido, rugido que empurra todo mundo, <b>Garra de la Pantera</b> (leque de dardos dos
 * cotovelos), <b>Desgarrón</b> (cinco garras azuis gigantes ARREMESSADAS lado a lado, que atravessam os alvos) e bote.
 */
public class Grimmjow extends EspadaBoss {
    public static final int FORM_SEALED = 0, FORM_PANTERA = 1;
    private static final ResourceLocation PANTERA_SPEED = IsekaiMahou.id("grimmjow_pantera_speed");
    private static final int A_NONE = 0, A_BUSY = 1;

    private int punchCd = 10, sonidoCd = 50, ceroCd = 100, granReyCd = 260, garraCd = 60, desgarronCd = 90, roarCd = 200, pounceCd = 80;
    private int attack = A_NONE;
    private int attackTimer;
    private float attackYaw;
    private int combo;

    public Grimmjow(EntityType<? extends Grimmjow> type, Level level) {
        super(type, level);
        this.xpReward = 400;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(440.0, 8.0, 0.34, 11.0).add(Attributes.ATTACK_KNOCKBACK, 0.8);
    }

    @Override
    public String bossId() {
        return "grimmjow";
    }

    @Override
    public int bossColor() {
        return EspadaFx.GRIMM_BLUE;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.BLUE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("punch", "punch2", "sonido", "cero", "gran_rey_cero", "garra", "desgarron", "roar", "pounce");
    }

    @Override
    public String musicTheme() {
        return "grimmjow";
    }

    @Override
    public String textureName() {
        return form() == FORM_PANTERA ? "grimmjow_pantera" : "grimmjow";
    }

    private static final List<String> BONES = List.of("jacket", "jaw_mask", "hair_long", "ears", "tail1", "fin_l", "fin_r", "claws_l",
            "claws_r");

    @Override
    public List<String> toggledBones() {
        return BONES;
    }

    @Override
    public boolean isBoneVisible(String bone) {
        boolean pantera = form() == FORM_PANTERA;
        return switch (bone) {
            case "jacket", "jaw_mask" -> !pantera;
            default -> pantera;
        };
    }

    @Override
    protected int deathTicks() {
        return 70;
    }

    @Override
    protected int rewardXp() {
        return 520;
    }

    @Override
    protected String[] combatQuotes() {
        return form() == FORM_PANTERA ? new String[] {"q_rel1", "q_rel2", "q_rel3"} : new String[] {"q1", "q2", "q3", "q4"};
    }

    @Override
    protected boolean isBusy() {
        return attack != A_NONE;
    }

    // ------------------------------------------------------------------ Fases

    @Override
    protected void onPhaseChange(ServerLevel sl, int phase) {
        if (phase != 1) return;
        attack = A_NONE;
        startTransform(sl, FORM_PANTERA, 80);
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.grimmjow.release"),
                Component.translatable("boss.isekai_mahou.grimmjow.release.sub"), EspadaFx.GRIMM_BLUE, 80, null);
        quote("release");
    }

    @Override
    protected void transformTick(ServerLevel sl, int t, int total, int form) {
        if (t == 10) EspadaFx.charge(sl, this, position().add(0, 1.2, 0), 40, 1.2F, EspadaFx.GRIMM_BLUE, EspadaFx.STYLE_BLUE);
        if (t == 50) {
            setForm(FORM_PANTERA);
            EspadaFx.release(sl, this, EspadaFx.GRIMM_BLUE, 3);
            EspadaSounds.play(this, EspadaSounds.RELEASE, 2.4F, 1.0F);
            roarBlast(sl, 10F);
        }
    }

    @Override
    protected void onTransformDone(ServerLevel level, int form) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(PANTERA_SPEED)) {
            speed.addPermanentModifier(new AttributeModifier(PANTERA_SPEED, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        desgarronCd = 40;
        garraCd = 20;
        hintOnce(level, "pantera");
    }

    @Override
    protected void onReset(ServerLevel level) {
        attack = A_NONE;
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(PANTERA_SPEED);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        quote("intro");
        EspadaSounds.play(this, EspadaSounds.ROAR, 1.2F, 1.3F);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        if (punchCd > 0) punchCd--;
        if (sonidoCd > 0) sonidoCd--;
        if (ceroCd > 0) ceroCd--;
        if (granReyCd > 0) granReyCd--;
        if (garraCd > 0) garraCd--;
        if (desgarronCd > 0) desgarronCd--;
        if (roarCd > 0) roarCd--;
        if (pounceCd > 0) pounceCd--;
        if (attack != A_NONE) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--attackTimer <= 0) attack = A_NONE;
            return;
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        boolean pantera = form() == FORM_PANTERA;
        if (granReyCd <= 0 && los && d > 5 && d < 30) {
            startGranRey(sl, target);
        } else if (pantera && roarCd <= 0 && d < 7) {
            startRoar(sl);
        } else if (pantera && desgarronCd <= 0 && los && d < 22) {
            startDesgarron(sl, target);
        } else if (d < 3.4 && punchCd <= 0) {
            startPunch(sl, target);
        } else if (pantera && garraCd <= 0 && los && d > 5 && d < 20) {
            startGarra(sl, target);
        } else if (pantera && pounceCd <= 0 && onGround() && d > 6 && d < 16) {
            startPounce(sl, target);
        } else if (sonidoCd <= 0 && d > 5 && d < 18) {
            sonidoPunch(sl, target);
        } else if (!pantera && ceroCd <= 0 && los && d > 6 && d < 24) {
            startCero(sl, target);
        } else {
            chase(target, pantera ? 1.25 : 1.1);
        }
    }

    private void busy(int ticks, float yaw) {
        attack = A_BUSY;
        attackTimer = ticks;
        attackYaw = yaw;
        holdYaw(yaw);
    }

    // ---- Socos e Sonído

    private void startPunch(ServerLevel sl, LivingEntity target) {
        combo = (combo + 1) % 3;
        punchCd = combo == 0 ? 22 : 9;
        busy(9, yawTo(target.position()));
        action(combo == 2 ? "punch2" : "punch");
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (combo == 2 ? 1.3F : 0.9F);
        Vec3 origin = position();
        float yaw = attackYaw;
        boolean pantera = form() == FORM_PANTERA;
        boolean finisher = combo == 2;
        Telegraph.delayed(sl, Telegraph.Shape.cone(origin, yaw, 90F, 3.8F).warmup(7).color(EspadaFx.GRIMM_BLUE).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            knock(e, origin, finisher ? 1.1 : 0.4, finisher ? 0.35 : 0.1);
                            if (pantera) Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
                        }
                    }
                    EspadaFx.slash(sl, this, origin.add(0, 1.1, 0), Vec3.directionFromRotation(0, yaw), 2.6F, EspadaFx.GRIMM_BLUE);
                    playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.1F, 0.9F);
                });
    }

    private void sonidoPunch(ServerLevel sl, LivingEntity target) {
        sonidoCd = form() == FORM_PANTERA ? 45 : 70;
        Vec3 from = position();
        if (!BossKit.blinkNear(this, target, 2.0)) return;
        action("sonido");
        setDeltaMovement(Vec3.ZERO);
        faceTowards(target);
        EspadaFx.sonido(sl, this, from, position(), EspadaFx.GRIMM_BLUE);
        EspadaSounds.play(this, EspadaSounds.SONIDO, 1.2F, 1.2F);
        punchCd = 0;
        combo = 1;
        startPunch(sl, target);
    }

    // ---- Cero e Gran Rey Cero

    private void startCero(ServerLevel sl, LivingEntity target) {
        ceroCd = 150;
        beam(sl, target, false);
    }

    private void startGranRey(ServerLevel sl, LivingEntity target) {
        granReyCd = form() == FORM_PANTERA ? 300 : 380;
        beam(sl, target, true);
        announce("gran_rey");
        hintOnce(sl, "gran_rey");
    }

    private void beam(ServerLevel sl, LivingEntity target, boolean big) {
        int charge = big ? 40 : 20;
        float width = big ? 4.2F : 1.8F;
        double range = big ? 36 : 24;
        faceTowards(target);
        busy(charge + 10, getYRot());
        action(big ? "gran_rey_cero" : "cero");
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        Vec3 from = position().add(0, 1.4, 0).add(dir.scale(0.9));
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 to = lineEnd(sl, from, aim, range);
        EspadaFx.charge(sl, this, from, charge, big ? 1.4F : 0.7F, EspadaFx.GRIMM_BLUE, big ? EspadaFx.STYLE_BLOOD : EspadaFx.STYLE_BLUE);
        EspadaSounds.play(this, EspadaSounds.CERO_CHARGE, big ? 2.0F : 1.2F, big ? 0.8F : 1.2F);
        if (big) playSound(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
        float dmg = scaled(big ? 24F : 11F);
        Telegraph.delayed(sl, Telegraph.Shape.line(groundAt(sl, from), groundAt(sl, to), width).warmup(charge)
                .color(big ? 0x2F6BFF : EspadaFx.GRIMM_BLUE).owner(this).castName(attackName(big ? "gran_rey_cero" : "cero"))
                .element(Element.WIND).vertical(2.0, 30.0), ignored -> {
                    if (!isAlive() || isTransforming()) return;
                    EspadaFx.beam(sl, this, from, to, width, big ? 16 : 9, EspadaFx.GRIMM_BLUE, big ? EspadaFx.STYLE_BLOOD : EspadaFx.STYLE_BLUE);
                    EspadaSounds.play(this, EspadaSounds.CERO_FIRE, big ? 2.8F : 1.6F, big ? 0.7F : 1.1F);
                    BossKit.shake(sl, from, big ? 36 : 18, big ? 2.0F : 0.8F, big ? 18 : 8);
                    for (LivingEntity e : foesAlong(sl, from, to, width * 0.5)) {
                        if (e.hurt(damageSources().mobAttack(this), dmg)) {
                            knock(e, from, big ? 1.3 : 0.7, 0.3);
                            if (big) Statuses.apply(e, Statuses.Kind.BLEEDING, 3, 120, this, 1F);
                        }
                    }
                    EspadaFx.explosion(sl, to, big ? 4F : 2F, EspadaFx.GRIMM_BLUE, big ? EspadaFx.STYLE_BLOOD : EspadaFx.STYLE_BLUE);
                });
    }

    // ---- Pantera: Desgarrón, Garra, rugido, bote

    /**
     * Desgarrón: ele arranha o ar e CINCO garras azuis gigantes (lâminas em forma de marca de garra) saem voando lado a
     * lado. Aviso: as cinco faixas no chão. Cada lâmina atravessa quem pegar (corta cada alvo uma vez), faz sangrar e
     * só para em parede. Dá para escapar passando ENTRE as faixas ou saindo de lado.
     */
    private void startDesgarron(ServerLevel sl, LivingEntity target) {
        desgarronCd = 200;
        busy(34, yawTo(target.position()));
        action("desgarron");
        announce("desgarron");
        hintOnce(sl, "desgarron");
        EspadaSounds.play(this, EspadaSounds.ROAR, 1.0F, 1.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 base = position();
        float dmg = scaled(9F);
        for (int i = 0; i < DESGARRON_BLADES; i++) {
            Vec3 from = base.add(side.scale((i - 2) * DESGARRON_SPACING)).add(dir.scale(0.8));
            Vec3 to = lineEnd(sl, from.add(0, 0.6, 0), dir, DESGARRON_RANGE).subtract(0, 0.6, 0);
            Telegraph.Shape s = Telegraph.Shape.line(from, to, 1.1F).warmup(DESGARRON_WINDUP).color(EspadaFx.GRIMM_BLUE).owner(this).noRunes();
            if (i == 0) s.castName(attackName("desgarron")).element(Element.WIND);
            Telegraph.show(sl, s);
        }
        float yaw = attackYaw;
        BossKit.schedule(sl, DESGARRON_WINDUP, () -> launchDesgarron(sl, yaw, dmg));
    }

    private static final int DESGARRON_BLADES = 5, DESGARRON_WINDUP = 18;
    /** w10: as garras VOAM longe (30 blocos, mais rápidas) — marcas de garra gigantes atravessando a arena. */
    private static final float DESGARRON_SPACING = 1.5F, DESGARRON_SPEED = 1.6F;
    private static final double DESGARRON_RANGE = 30.0;

    private void launchDesgarron(ServerLevel sl, float yaw, float dmg) {
        if (!isAlive() || isTransforming() || isDeadOrDying()) return;
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 base = position().add(dir.scale(1.0));
        EspadaFx.clawLaunch(sl, this, base.add(0, 1.4, 0), dir, DESGARRON_SPACING, DESGARRON_BLADES, EspadaFx.GRIMM_BLUE);
        for (int i = 0; i < DESGARRON_BLADES; i++) {
            Vec3 from = base.add(side.scale((i - 2) * DESGARRON_SPACING)).add(0, 1.4, 0);
            EspadaBolt.shoot(sl, this, EspadaBolt.CLAW, from, dir, DESGARRON_SPEED, dmg, DESGARRON_RANGE);
        }
        EspadaSounds.play(this, EspadaSounds.CLAW, 2.0F, 0.7F);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        BossKit.shake(sl, position(), 24, 1.2F, 10);
    }

    private void startGarra(ServerLevel sl, LivingEntity target) {
        garraCd = 120;
        busy(22, yawTo(target.position()));
        action("garra");
        playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.4F, 0.7F);
        Vec3 base = position().add(0, 1.3, 0);
        float dmg = scaled(6F);
        double dy = (target.getY() + target.getBbHeight() * 0.5) - base.y;
        float spread = 70F;
        int n = 6;
        float[] yaws = new float[n];
        for (int i = 0; i < n; i++) yaws[i] = attackYaw - spread / 2 + spread * i / (n - 1);
        for (int i = 0; i < n; i++) {
            Vec3 d = Vec3.directionFromRotation(0, yaws[i]);
            Vec3 g = groundAt(sl, position());
            Vec3 to = lineEnd(sl, g.add(0, 0.6, 0).add(d), d, 20).subtract(0, 0.6, 0);
            Telegraph.Shape s = Telegraph.Shape.line(g.add(d), to, 0.9F).warmup(16).color(EspadaFx.GRIMM_BLUE).owner(this).noRunes();
            if (i == 0) {
                s.castName(attackName("garra")).element(Element.WIND);
                Telegraph.delayed(sl, s, hits -> {
                    if (!isAlive()) return;
                    for (float a : yaws) {
                        Vec3 dd = Vec3.directionFromRotation(0, a);
                        Vec3 v = new Vec3(dd.x, Math.max(-0.4, Math.min(0.3, dy / 12.0)), dd.z);
                        EspadaBolt.shoot(sl, this, EspadaBolt.DART, base.add(dd.scale(0.6)), v, 2.0F, dmg, 21);
                    }
                    playSound(SoundEvents.ARROW_SHOOT, 1.8F, 0.6F);
                });
            } else {
                Telegraph.show(sl, s);
            }
        }
    }

    private void startRoar(ServerLevel sl) {
        roarCd = 260;
        busy(20, getYRot());
        action("roar");
        BossKit.schedule(sl, 8, () -> {
            if (isAlive() && !isTransforming()) roarBlast(sl, 8F);
        });
    }

    private void roarBlast(ServerLevel sl, float radius) {
        EspadaFx.roar(sl, this, radius, EspadaFx.GRIMM_BLUE);
        EspadaSounds.play(this, EspadaSounds.ROAR, 2.6F, 0.9F);
        BossKit.shake(sl, position(), radius * 3, 1.6F, 16);
        for (LivingEntity e : foesNear(sl, position().add(0, 1, 0), radius)) {
            knock(e, position(), 1.8, 0.55);
            e.hurt(damageSources().mobAttack(this), scaled(4F));
        }
    }

    private void startPounce(ServerLevel sl, LivingEntity target) {
        pounceCd = 140;
        action("pounce");
        int t = BossKit.leapAt(this, target, 1.4);
        leapTicks = t;
        float dmg = scaled(12F);
        Telegraph.delayed(sl, Telegraph.Shape.circle(target.position(), 3.2F).warmup(Math.max(10, t)).color(EspadaFx.GRIMM_BLUE).owner(this),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            knock(e, position(), 0.8, 0.5);
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 2, 100, this, 1F);
                        }
                    }
                    EspadaFx.explosion(sl, position(), 2.6F, EspadaFx.GRIMM_BLUE, EspadaFx.STYLE_BLUE);
                    EspadaSounds.play(this, EspadaSounds.CLAW, 1.4F, 0.7F);
                    BossKit.shake(sl, position(), 18, 1.0F, 10);
                });
        EspadaSounds.play(this, EspadaSounds.ROAR, 1.0F, 1.5F);
    }

    // ------------------------------------------------------------------ Teste

    @Override
    public List<String> debugAttacks() {
        return List.of("desgarron", "garra", "gran_rey", "cero", "roar", "pounce", "pantera");
    }

    @Override
    public boolean debugCast(ServerLevel sl, String name, @Nullable LivingEntity target) {
        if (isTransforming() || introTicks() > 0) return false;
        attack = A_NONE;
        if (name.equals("pantera")) {
            if (form() == FORM_PANTERA) return false;
            setPhase(Math.max(phase(), 1));
            onPhaseChange(sl, 1);
            return true;
        }
        if (name.equals("roar")) {
            startRoar(sl);
            return true;
        }
        if (target == null) return false;
        switch (name) {
            case "desgarron" -> startDesgarron(sl, target);
            case "garra" -> startGarra(sl, target);
            case "gran_rey" -> startGranRey(sl, target);
            case "cero" -> startCero(sl, target);
            case "pounce" -> startPounce(sl, target);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Morte

    @Override
    protected void onDefeated(ServerLevel sl) {
        attack = A_NONE;
        super.onDefeated(sl);
        quote("death");
    }

    @Override
    protected void deathTick(ServerLevel sl, int t) {
        if (t % 6 == 0) EspadaFx.spark(sl, position().add(0, 1 + random.nextDouble(), 0), EspadaFx.GRIMM_BLUE);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(EspadaRegistries.PANTERA_CLAW.get(), 1, 1);
        dropItem(EspadaRegistries.REISHI_RESTORATION.get(), 1, 1);
        ItemStack legend = EspadaLegends.scroll(EspadaLegends.GRIMMJOW);
        if (!legend.isEmpty()) spawnAtLocation(legend);
        // w10: quem lutou aprende a técnica (a forma da Magia Lendária)
        EspadaLegends.teach(this, EspadaLegends.GRIMMJOW);
        var wind = CoreRegistries.ESSENCES.get(Element.WIND);
        if (wind != null) dropItem(wind.get(), 4, 6);
        dropById("mana_crystal", 2, 4);
    }
}
