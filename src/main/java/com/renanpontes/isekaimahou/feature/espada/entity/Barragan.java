package com.renanpontes.isekaimahou.feature.espada.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.aging.RespiraRot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Barragan Louisenbairn</b>, Segunda Espada — o aspecto da Velhice, o antigo REI de Hueco Mundo. Tudo nele ENVELHECE:
 * <ul>
 *   <li><b>Respira</b> (w10: 360°): o miasma roxo-negro explode para TODOS os lados (círculo marcado) e vira uma cúpula
 *       de nuvem que se espalha devagar em volta dele e
 *       crescendo (para em paredes). Quem é tocado fica <b>Envelhecido</b> e apodrece (definhar); flechas, projéteis e
 *       magias que cruzam a nuvem viram pó; o chão APARENTA apodrecer (só visual, nada muda de verdade no mundo);</li>
 *   <li><b>Mão do Respira</b>: agarra quem está perto — contato direto = muito envelhecimento;</li>
 *   <li><b>Gran Caída</b>: o machado esmaga em linha ou é arremessado;</li>
 *   <li><b>Fracción</b> (75%): chama servos para lutar pelo rei;</li>
 *   <li><b>Arrogante</b> (50%): "Pudrete, Arrogante!" — a carne apodrece e cai: ele VIRA O REI DE OSSOS (modelo do Rei
 *       Vazio, o rei de Hueco Mundo), maior, com aura que envelhece, Respira maior, Domo de Respira e os golpes do Rei:
 *       <b>Ruína do Trono</b> (quatro anéis de decadência) e <b>Poças da Decadência</b> (seguem cada jogador);</li>
 *   <li><b>Respira: Senescência</b> (25%): a decadência cobre a arena inteira — só as zonas verdes salvam; depois ele
 *       fica exausto (janela de dano).</li>
 * </ul>
 * No Palácio de Hueco Mundo ele é o "Rei Hollow" do trono (o módulo hueco_mundo o invoca pelo id; um Rei Vazio de um
 * mundo antigo vira Barragan já em Arrogante via {@link EspadaBoss#START_FORM_KEY}). Leite não cura o Envelhecido.
 */
public class Barragan extends EspadaBoss {
    public static final int FORM_SEALED = 0, FORM_ARROGANTE = 1;
    private static final net.minecraft.resources.ResourceLocation ARROGANTE_SPEED = IsekaiMahou.id("barragan_arrogante_speed");
    private static final int A_NONE = 0, A_AXE = 1, A_RESPIRA = 2, A_GRAB = 3, A_THROW = 4, A_DOME = 5, A_SUMMON = 6, A_RUIN = 7,
            A_POOLS = 8, A_DOMAIN = 9;
    /** Corpo do rei de ossos (Arrogante): o modelo do Rei Vazio é maior. */
    private static final EntityDimensions ARROGANTE_SIZE = EntityDimensions.scalable(1.3F, 3.1F).withEyeHeight(2.8F);
    /** Respira: vida de cada bolo de miasma (ticks) e quantos ticks o sopro leva para chegar ao alcance. */
    private static final int PUFF_LIFE = 60, BREATH_TICKS = 55;

    /** Um "bolo" de miasma do Respira (anda, cresce, para em parede e some). O cliente simula igual. */
    private static final class Puff {
        Vec3 pos;
        Vec3 vel;
        double radius;
        int life;

        Puff(Vec3 pos, Vec3 vel, double radius, int life) {
            this.pos = pos;
            this.vel = vel;
            this.radius = radius;
            this.life = life;
        }
    }

    private final List<Puff> puffs = new ArrayList<>();
    private final Map<UUID, Long> lastAged = new HashMap<>();
    private int axeCd = 30, respiraCd = 80, grabCd = 120, throwCd = 140, domeCd = 200, summonCd, ruinCd = 120, poolsCd = 80, domainCd = 40;
    private int attack = A_NONE;
    private int attackTimer;
    private float attackYaw;
    private int auraCd = 30;
    private int crumbleSoundCd;
    private int exhausted;

    public Barragan(EntityType<? extends Barragan> type, Level level) {
        super(type, level);
        this.xpReward = 450;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(560.0, 12.0, 0.24, 15.0).add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override
    public String bossId() {
        return "barragan";
    }

    @Override
    public int bossColor() {
        return EspadaFx.RESPIRA_LIGHT;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.75F, 0.5F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("axe_smash", "respira", "grab", "axe_throw", "dome", "summon", "ruin", "pools", "respira_domain");
    }

    @Override
    public String musicTheme() {
        // O mesmo tema nas duas formas (o jogador escolheu a música do rei).
        return "barragan";
    }

    public boolean isArrogante() {
        return form() == FORM_ARROGANTE;
    }

    @Override
    public String textureName() {
        if (!isArrogante()) return "barragan";
        return phase() >= 3 ? "hollow_king_p2" : "hollow_king";
    }

    /** Arrogante = o rei de ossos: modelo do Rei Vazio com as animações dele copiadas com os nomes do Barragan. */
    @Override
    public String modelName() {
        return isArrogante() ? "hollow_king" : "barragan";
    }

    @Override
    public String animationName() {
        return isArrogante() ? "barragan_arrogante" : "barragan";
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        // (entityData ainda não existe enquanto o construtor de Entity calcula o tamanho inicial)
        return entityData != null && isArrogante() ? ARROGANTE_SIZE : super.getDefaultDimensions(pose);
    }

    private static final List<String> BONES = List.of("beard", "coat_fur", "crown_small", "cloak", "crown_big");

    @Override
    public List<String> toggledBones() {
        return BONES;
    }

    @Override
    public boolean isBoneVisible(String bone) {
        boolean released = isArrogante();
        return switch (bone) {
            case "beard", "coat_fur", "crown_small" -> !released;
            case "cloak", "crown_big" -> released;
            default -> true;
        };
    }

    @Override
    protected int deathTicks() {
        return 90;
    }

    @Override
    protected int rewardXp() {
        return 600;
    }

    @Override
    protected double arenaRadius() {
        return 32.0;
    }

    @Override
    protected String[] combatQuotes() {
        return isArrogante() ? new String[] {"q_rel1", "q_rel2", "q_rel3"} : new String[] {"q1", "q2", "q3", "q4"};
    }

    @Override
    protected boolean isBusy() {
        return attack != A_NONE;
    }

    // ------------------------------------------------------------------ Dano: projéteis apodrecem

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAwake() && source.getDirectEntity() instanceof Projectile proj && !isTransforming()) {
            double reach = isArrogante() ? 99 : 4.5;
            if (proj.distanceTo(this) < reach) {
                // O tempo chega no projétil antes dele: só metade do golpe passa.
                amount *= 0.5F;
                EspadaFx.crumble((ServerLevel) level(), proj.position());
                hintOnce((ServerLevel) level(), "projectiles");
            }
        }
        // Exausto depois da Senescência: janela de dano.
        if (exhausted > 0) amount *= 1.3F;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Fases

    @Override
    protected void onPhaseChange(ServerLevel sl, int phase) {
        if (phase == 1) {
            announce("phase1");
            quote("fraccion");
            summonFraccion(sl);
        } else if (phase == 2) {
            if (isArrogante()) return;
            startArrogante(sl);
        } else if (phase == 3) {
            announce("phase3");
            Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.barragan.phase3"),
                    Component.translatable("boss.isekai_mahou.barragan.phase3.sub"), EspadaFx.RESPIRA_LIGHT, 60, null);
            domainCd = 20;
            hintOnce(sl, "domain");
        }
    }

    /** "Pudrete, Arrogante!": a carne apodrece e cai; resta o rei de ossos. */
    private void startArrogante(ServerLevel sl) {
        attack = A_NONE;
        puffs.clear();
        startTransform(sl, FORM_ARROGANTE, 100);
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.barragan.release"),
                Component.translatable("boss.isekai_mahou.barragan.release.sub"), EspadaFx.RESPIRA_LIGHT, 100, null);
        quote("release");
        EspadaSounds.play(this, EspadaSounds.RESPIRA, 2.4F, 0.5F);
        EspadaFx.darkness(sl, this, 30);
    }

    @Override
    protected void transformTick(ServerLevel sl, int t, int total, int form) {
        // A carne apodrece e cai em pó, o miasma sobe em coluna... e sobra o esqueleto coroado.
        if (t % 8 == 0 && t < 60) {
            EspadaFx.auraPulse(sl, this, 2F + t * 0.06F, EspadaFx.RESPIRA);
            EspadaFx.crumble(sl, position().add(0, 0.6 + random.nextDouble() * 1.8, 0));
            sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BONE_BLOCK.defaultBlockState()),
                    getX(), getY() + 2.2, getZ(), 8, 0.5, 0.6, 0.5, 0);
        }
        if (t == 20) EspadaFx.charge(sl, this, position().add(0, 1.6, 0), 40, 1.6F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
        if (t == 60) {
            setForm(FORM_ARROGANTE);
            EspadaFx.release(sl, this, EspadaFx.RESPIRA_LIGHT, 2);
            EspadaFx.explosion(sl, position().add(0, 0.5, 0), 6F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
            EspadaFx.respiraRing(sl, this, position().add(0, 1.0, 0), 1.5F, 0.22, 30, 16);
            EspadaSounds.play(this, EspadaSounds.RELEASE, 2.6F, 0.5F);
            playSound(SoundEvents.WITHER_SPAWN, 1.2F, 0.6F);
            BossKit.shake(sl, position(), 48, 2.4F, 34);
            for (LivingEntity e : foesNear(sl, position(), 9)) {
                knock(e, position(), 1.2, 0.45);
                Aging.apply(e, 1, this);
            }
        }
    }

    @Override
    protected void onTransformDone(ServerLevel level, int form) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ARROGANTE_SPEED)) {
            speed.addPermanentModifier(new AttributeModifier(ARROGANTE_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        domeCd = 60;
        ruinCd = 80;
        poolsCd = 40;
        hintOnce(level, "arrogante");
    }

    /** Já nasce em Arrogante (Rei Vazio antigo convertido): fase 2, forma do rei e velocidade, sem a cena. */
    @Override
    protected void applyStartForm(ServerLevel level, int form) {
        if (form != FORM_ARROGANTE) return;
        setPhase(Math.max(phase(), 2));
        setForm(FORM_ARROGANTE);
        onTransformDone(level, FORM_ARROGANTE);
    }

    @Override
    protected void onReset(ServerLevel level) {
        attack = A_NONE;
        exhausted = 0;
        puffs.clear();
        lastAged.clear();
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ARROGANTE_SPEED);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        quote("intro");
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        if (axeCd > 0) axeCd--;
        if (respiraCd > 0) respiraCd--;
        if (grabCd > 0) grabCd--;
        if (throwCd > 0) throwCd--;
        if (domeCd > 0) domeCd--;
        if (summonCd > 0) summonCd--;
        if (ruinCd > 0) ruinCd--;
        if (poolsCd > 0) poolsCd--;
        if (domainCd > 0) domainCd--;
        if (crumbleSoundCd > 0) crumbleSoundCd--;
        tickPuffs(sl);
        if (isArrogante()) tickAura(sl);
        if (tickCount % 200 == 0) lastAged.values().removeIf(t -> t < sl.getGameTime() - 200);
        if (exhausted > 0) {
            exhausted--;
            getNavigation().stop();
            if (exhausted % 10 == 0) sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight(), getZ(), 6, 0.3, 0.2, 0.3, 0.01);
            return;
        }

        if (attack != A_NONE) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--attackTimer <= 0) attack = A_NONE;
            return;
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        boolean rel = isArrogante();
        if (rel && phase() >= 3 && domainCd <= 0) {
            startDomain(sl);
        } else if (rel && ruinCd <= 0 && d < 16) {
            startRuin(sl);
        } else if (rel && poolsCd <= 0) {
            startPools(sl);
        } else if (phase() >= 1 && summonCd <= 0 && countMinions(sl, 40) < (rel ? 2 : 1)) {
            summonFraccion(sl);
        } else if (rel && domeCd <= 0 && d < 10) {
            startDome(sl);
        } else if (grabCd <= 0 && d < 4.5) {
            startGrab(sl, target);
        } else if (respiraCd <= 0 && d < (rel ? 17 : 14) && los) {
            startRespira(sl, target);
        } else if (axeCd <= 0 && d < 5.5) {
            startAxe(sl, target);
        } else if (throwCd <= 0 && d > 7 && d < 24 && los) {
            startThrow(sl, target);
        } else {
            chase(target, rel ? 1.1 : 0.95);
        }
    }

    private void busy(int kind, int ticks, float yaw) {
        attack = kind;
        attackTimer = ticks;
        attackYaw = yaw;
        holdYaw(yaw);
    }

    // ---- Gran Caída (machado)

    private void startAxe(ServerLevel sl, LivingEntity target) {
        axeCd = isArrogante() ? 34 : 44;
        busy(A_AXE, 22, yawTo(target.position()));
        action("axe_smash");
        playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.4F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position();
        Vec3 to = lineEnd(sl, from.add(0, 0.5, 0), dir, isArrogante() ? 8 : 6).subtract(0, 0.5, 0);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F;
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 2.6F).warmup(18).color(EspadaFx.RESPIRA_LIGHT).owner(this)
                .castName(attackName("gran_caida")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            knock(e, from, 0.9, 0.45);
                            Aging.apply(e, 1, this);
                        }
                    }
                    EspadaFx.explosion(sl, to, 2.2F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
                    playSound(SoundEvents.ANVIL_LAND, 1.4F, 0.5F);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.7F);
                    BossKit.shake(sl, to, 18, 1.2F, 10);
                });
    }

    private void startThrow(ServerLevel sl, LivingEntity target) {
        throwCd = isArrogante() ? 110 : 160;
        busy(A_THROW, 20, yawTo(target.position()));
        action("axe_throw");
        Vec3 from = position().add(0, getBbHeight() * 0.8, 0);
        Vec3 aim = target.position().add(0, 1, 0).subtract(from).normalize();
        Vec3 to = lineEnd(sl, from, aim, 26);
        float dmg = scaled(11F);
        Telegraph.delayed(sl, Telegraph.Shape.line(groundAt(sl, from), groundAt(sl, to), 2.0F).warmup(14).color(EspadaFx.RESPIRA_LIGHT)
                .owner(this).noRunes().castName(attackName("gran_caida_throw")).vertical(2.0, 20.0), ignored -> {
                    if (!isAlive()) return;
                    EspadaBolt.shoot(sl, this, EspadaBolt.AXE, from, aim, 1.6F, dmg, 28);
                    playSound(SoundEvents.TRIDENT_THROW.value(), 1.4F, 0.5F);
                });
    }

    // ---- Respira (sopro)

    private void startRespira(ServerLevel sl, LivingEntity target) {
        boolean rel = isArrogante();
        respiraCd = rel ? 150 : 200;
        busy(A_RESPIRA, 50, yawTo(target.position()));
        action("respira");
        announce("respira");
        hintOnce(sl, "respira");
        EspadaSounds.play(this, EspadaSounds.RESPIRA, 1.2F, 0.6F);
        // Inspira: o miasma é sugado para ele durante o aviso.
        EspadaFx.charge(sl, this, breathCenter(), 24, 1.6F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
        // w10: Respira em 360° — o círculo inteiro em volta dele (fuja para LONGE, não para o lado).
        float range = rel ? 16F : 12F;
        Telegraph.delayed(sl, Telegraph.Shape.circle(position(), range).warmup(24).color(EspadaFx.RESPIRA_LIGHT).owner(this)
                .castName(attackName("respira")).element(Element.SHADOW), ignored -> breathe(sl, range));
    }

    /** De onde sai o Respira em 360°: o peito/boca, sem avançar para a frente. */
    private Vec3 breathCenter() {
        return position().add(0, getBbHeight() * 0.72, 0);
    }

    /**
     * Solta a nuvem em 360°: {@code n} bolos em TODAS as direções ({@link EspadaFx#respiraDirs}), todos na MESMA
     * velocidade (alcance em {@link #BREATH_TICKS} ticks), que crescem e param em paredes. O cliente recebe os mesmos
     * parâmetros e desenha a mesma nuvem.
     */
    private void breathe(ServerLevel sl, float range) {
        if (!isAlive() || isTransforming()) return;
        Vec3 c = breathCenter();
        int n = isArrogante() ? 26 : 20;
        double speed = range / (double) BREATH_TICKS;
        EspadaFx.respiraSphere(sl, this, c, speed, PUFF_LIFE, n);
        EspadaFx.explosion(sl, c, 3.5F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
        EspadaSounds.play(this, EspadaSounds.RESPIRA, 2.6F, 0.7F);
        Vec3[] dirs = EspadaFx.respiraDirs(n);
        for (Vec3 d : dirs) puffs.add(new Puff(c.add(d.scale(0.8)), d.scale(speed), 1.2, PUFF_LIFE));
    }

    /** Domo de Respira (Arrogante): anel de miasma que se expande a partir dele. */
    private void startDome(ServerLevel sl) {
        domeCd = 320;
        busy(A_DOME, 40, getYRot());
        action("dome");
        announce("dome");
        EspadaSounds.play(this, EspadaSounds.RESPIRA, 2.0F, 0.4F);
        Vec3 c = position();
        Telegraph.delayed(sl, Telegraph.Shape.ring(c, 2.5F, 11F).warmup(30).color(EspadaFx.RESPIRA_LIGHT).owner(this)
                .castName(attackName("dome")).element(Element.SHADOW), ignored -> {
                    if (!isAlive()) return;
                    Vec3 o = c.add(0, 1.2, 0);
                    EspadaFx.respiraRing(sl, this, o, 2.5F, 0.16, 55, 14);
                    for (int i = 0; i < 14; i++) {
                        double a = Math.PI * 2 * i / 14;
                        Vec3 d = new Vec3(Math.cos(a), 0.02, Math.sin(a));
                        puffs.add(new Puff(o.add(d.scale(2.5)).add(0, -0.2, 0), d.scale(0.16), 1.4, 55));
                    }
                });
    }

    private void tickPuffs(ServerLevel sl) {
        if (puffs.isEmpty()) return;
        boolean check = tickCount % 3 == 0;
        boolean rel = isArrogante();
        Iterator<Puff> it = puffs.iterator();
        while (it.hasNext()) {
            Puff p = it.next();
            Vec3 next = p.pos.add(p.vel);
            // Para em paredes (a nuvem não atravessa blocos; o cliente faz o mesmo).
            BlockPos bp = BlockPos.containing(next);
            if (sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) p.pos = next;
            else p.vel = Vec3.ZERO;
            p.radius = Math.min(3.2, p.radius + 0.035);
            if (--p.life <= 0) {
                it.remove();
                continue;
            }
            if (!check) continue;
            AABB box = new AABB(p.pos, p.pos).inflate(p.radius);
            // Quem respira o miasma envelhece e apodrece (no máx. a cada 10 ticks por alvo).
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(e)
                    && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(p.pos) < (p.radius + 0.6) * (p.radius + 0.6))) {
                long now = sl.getGameTime();
                Long last = lastAged.get(e.getUUID());
                if (last != null && now - last < 10) continue;
                lastAged.put(e.getUUID(), now);
                Aging.apply(e, rel ? 2 : 1, this);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, rel ? 1 : 0), this);
                e.hurt(damageSources().indirectMagic(this, this), scaled(1.5F));
                if (e instanceof Player pl) crumbleHeldArrows(sl, pl);
            }
            // Projéteis que cruzam a nuvem viram pó.
            for (Projectile proj : sl.getEntitiesOfClass(Projectile.class, box, pr -> pr.getOwner() != this && !(pr instanceof EspadaBolt))) {
                EspadaFx.crumble(sl, proj.position());
                proj.discard();
                if (crumbleSoundCd <= 0) {
                    crumbleSoundCd = 6;
                    sl.playSound(null, proj.getX(), proj.getY(), proj.getZ(), EspadaSounds.CRUMBLE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                }
            }
            // O chão APARENTA apodrecer (só visual, para quem está perto; volta sozinho).
            if (random.nextInt(3) == 0) {
                BlockPos at = BlockPos.containing(p.pos.x + (random.nextDouble() - 0.5) * p.radius * 2, p.pos.y,
                        p.pos.z + (random.nextDouble() - 0.5) * p.radius * 2);
                for (int dy = 0; dy < 5; dy++) {
                    BlockPos b = at.below(dy);
                    if (!sl.getBlockState(b).isAir()) {
                        if (RespiraRot.rot(sl, b, this)) EspadaFx.crumble(sl, Vec3.atCenterOf(b).add(0, 0.5, 0));
                        break;
                    }
                }
            }
        }
    }

    /** Flechas na mão (principal/secundária) também apodrecem: uma por vez. */
    private void crumbleHeldArrows(ServerLevel sl, Player p) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(hand);
            if (!s.isEmpty() && s.is(ItemTags.ARROWS) && random.nextInt(3) == 0) {
                s.shrink(1);
                EspadaFx.crumble(sl, p.position().add(0, 1.2, 0));
                return;
            }
        }
    }

    /** Aura do Arrogante: quem fica colado envelhece. */
    private void tickAura(ServerLevel sl) {
        if (--auraCd > 0) return;
        auraCd = 30;
        EspadaFx.auraPulse(sl, this, 3.8F, EspadaFx.RESPIRA);
        for (LivingEntity e : foesNear(sl, position().add(0, 1, 0), 3.8)) Aging.apply(e, 1, this);
    }

    // ---- Mão do Respira

    private void startGrab(ServerLevel sl, LivingEntity target) {
        grabCd = isArrogante() ? 160 : 220;
        busy(A_GRAB, 24, yawTo(target.position()));
        action("grab");
        playSound(SoundEvents.WITHER_AMBIENT, 0.8F, 0.6F);
        float dmg = scaled(8F);
        Telegraph.delayed(sl, Telegraph.Shape.follow(target, 1.8F).warmup(18).lock(6).color(EspadaFx.RESPIRA_LIGHT).owner(this)
                .castName(attackName("grab")).element(Element.SHADOW), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e) || e.distanceTo(this) > 6.5) continue;
                        // Contato direto com a mão do Respira: envelhece MUITO.
                        e.hurt(damageSources().indirectMagic(this, this), dmg);
                        int st = Aging.apply(e, 4, this);
                        Aging.rotArmor(e, 6);
                        Statuses.apply(e, Statuses.Kind.CORRUPTED, 1, 100, this, 1F);
                        EspadaFx.grab(sl, this, position().add(0, getBbHeight() * 0.65, 0), e.position().add(0, 1, 0), EspadaFx.RESPIRA);
                        EspadaFx.crumble(sl, e.position().add(0, 1, 0));
                        sl.playSound(null, e.getX(), e.getY(), e.getZ(), EspadaSounds.CRUMBLE.get(), SoundSource.HOSTILE, 1.4F, 0.6F);
                        if (st >= 6) quote("aged");
                    }
                });
    }

    // ---- Golpes do Rei (Arrogante)

    /**
     * Ruína do Trono: ergue o machado (cinzas e miasma se juntam na lâmina), crava no chão e QUATRO anéis de decadência
     * se espalham; quem pisa onde a onda já passou escapa das seguintes.
     */
    private void startRuin(ServerLevel sl) {
        ruinCd = 340;
        busy(A_RUIN, 60, getYRot());
        action("ruin");
        announce("ruin");
        hintOnce(sl, "ruin");
        Vec3 c = position();
        EspadaFx.charge(sl, this, position().add(0, getBbHeight() + 0.6, 0), 30, 1.6F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
        for (int t = 0; t < 30; t += 9) {
            final int tick = t;
            BossKit.schedule(sl, t, () -> {
                if (isAlive()) playSound(SoundEvents.WITHER_AMBIENT, 1.6F, 0.4F + tick * 0.02F);
            });
        }
        float dmg = scaled(13F);
        for (int k = 0; k < 4; k++) {
            final float inner = k == 0 ? 0F : 2F + 5F * k - 1F, outer = 2F + 5F * (k + 1);
            final int warm = 30 + k * 7;
            Telegraph.Shape s = Telegraph.Shape.ring(c, inner, outer).warmup(warm).color(0x6E5A2E).owner(this);
            if (k == 0) s.castName(attackName("ruin")).element(Element.SHADOW);
            Telegraph.delayed(sl, s, hits -> {
                if (!isAlive()) return;
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                        e.hurtMarked = true;
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), this);
                        Aging.apply(e, 1, this);
                    }
                }
                double mid = (inner + outer) / 2;
                EspadaFx.respiraRing(sl, this, c.add(0, 0.6, 0), (float) mid, 0.0, 14, Math.max(8, (int) (mid * 2)));
                ringParticles(sl, c.add(0, 0.3, 0), mid, 40, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()));
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.5F + inner * 0.02F);
            });
        }
        BossKit.schedule(sl, 30, () -> {
            if (!isAlive()) return;
            EspadaFx.explosion(sl, c, 4F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
            EspadaFx.flash(sl, c.add(0, 1, 0), 6F, 0xD8C8A0);
            playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.6F);
            BossKit.shake(sl, c, 34, 2.4F, 24);
            BossKit.crackFloor(sl, c, 5.0, 6, 60);
        });
    }

    /** Poças da Decadência: um círculo segue cada jogador, trava e fica no chão apodrecendo quem pisa. */
    private void startPools(ServerLevel sl) {
        poolsCd = 220;
        busy(A_POOLS, 20, getYRot());
        action("pools");
        hintOnce(sl, "pools");
        float dmg = scaled(4F);
        List<ServerPlayer> targets = new ArrayList<>(BossKit.partyAround(this, 40));
        for (int i = 0; i < Math.min(4, targets.size()); i++) {
            ServerPlayer p = targets.get(i);
            if (!isFoe(p)) continue;
            int[] id = new int[1];
            Telegraph.Shape s = Telegraph.Shape.follow(p, 3.2F).warmup(40).hold(80).color(0x6E5A2E).owner(this);
            if (i == 0) s.castName(attackName("pools")).element(Element.SHADOW);
            id[0] = Telegraph.delayed(sl, s, hits -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at == null || !isAlive()) return;
                for (LivingEntity e : hits) {
                    if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg * 2)) Aging.apply(e, 1, this);
                }
                EspadaFx.respiraRing(sl, this, at.add(0, 0.4, 0), 1.6F, 0.0, 80, 6);
                for (int k = 10; k <= 80; k += 10) {
                    BossKit.schedule(sl, k, () -> {
                        if (!isAlive()) return;
                        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(at, 3.2F))) {
                            if (!isFoe(e)) continue;
                            e.hurt(damageSources().indirectMagic(this, this), dmg);
                            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1), this);
                        }
                    });
                }
            });
        }
    }

    /**
     * Respira: Senescência (25%): a decadência cobre a arena inteira em 3,5 s; só as três zonas verdes salvam. Quem
     * fica fora envelhece muito e apodrece. Depois ele fica EXAUSTO por 3 s (leva +30% de dano).
     */
    private void startDomain(ServerLevel sl) {
        domainCd = 460;
        busy(A_DOMAIN, 80, getYRot());
        action("respira_domain");
        announce("domain");
        quote("domain");
        Vec3 arena = BossKit.arenaCenter(this);
        Vec3 c = arena != null ? arena : position();
        double r = Math.max(14, Math.min(BossKit.arenaRadius(this) > 0 ? BossKit.arenaRadius(this) : 24, 30)) + 2;
        Telegraph.show(sl, Telegraph.Shape.circle(c, (float) r).warmup(70).color(0x6E5A2E).owner(this).noRunes()
                .castName(attackName("domain")).element(Element.SHADOW));
        List<Vec3> safes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vec3 s = findGroundSpot(sl, c, 5, Math.max(7, r - 4), null);
            if (s == null) continue;
            safes.add(s);
            Telegraph.safe(sl, s, 3.2F, 70);
        }
        EspadaSounds.play(this, EspadaSounds.RESPIRA, 3.0F, 0.4F);
        EspadaFx.darkness(sl, this, 60);
        float dmg = scaled(20F);
        for (int t = 10; t < 70; t += 15) {
            final int tick = t;
            BossKit.schedule(sl, t, () -> {
                if (!isAlive()) return;
                // A nuvem sobe do chão da arena inteira, cada vez mais perto.
                EspadaFx.respiraRing(sl, this, c.add(0, 0.6, 0), (float) (r * 0.9), -0.06, 20, 14 + tick / 5);
                EspadaFx.respiraRing(sl, this, c.add(0, 0.6, 0), (float) (r * 0.5), -0.04, 20, 8 + tick / 10);
            });
        }
        BossKit.schedule(sl, 70, () -> {
            if (!isAlive()) return;
            for (ServerPlayer p : BossKit.partyAround(this, r + 6)) {
                if (!isFoe(p)) continue;
                boolean safe = false;
                for (Vec3 s : safes) {
                    if (Telegraph.Shape.safe(s, 3.2F).contains(p)) safe = true;
                }
                if (safe) continue;
                p.hurt(damageSources().indirectMagic(this, this), dmg);
                Aging.apply(p, 3, this);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 2), this);
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1), this);
            }
            EspadaFx.respiraRing(sl, this, c.add(0, 0.8, 0), 2.0F, 0.35, 40, 24);
            EspadaFx.explosion(sl, c, 6F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
            BossKit.shake(sl, c, r + 8, 1.8F, 30);
            exhausted = 60;
            hintOnce(sl, "exhausted");
        });
    }

    private static void ringParticles(ServerLevel sl, Vec3 c, double r, int n, net.minecraft.core.particles.ParticleOptions o) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            sl.sendParticles(o, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.1);
        }
    }

    // ---- Fracción

    private void summonFraccion(ServerLevel sl) {
        summonCd = 700;
        busy(A_SUMMON, 30, getYRot());
        action("summon");
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.6F, 0.6F);
        int n = isArrogante() ? 3 : 2;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("arrancar")).orElse(EntityType.WITHER_SKELETON);
        for (int i = 0; i < n; i++) {
            Vec3 spot = findGroundSpot(sl, position(), 3.0, 7.0, getEyePosition());
            if (spot == null) continue;
            Entity e = type.create(sl);
            if (!(e instanceof Mob m)) continue;
            m.moveTo(spot.x, spot.y, spot.z, random.nextFloat() * 360F, 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(spot)), MobSpawnType.MOB_SUMMONED, null);
            m.addTag(MINION_TAG);
            m.setCustomName(Component.translatable("entity.isekai_mahou.barragan.fraccion"));
            if (getTarget() != null) m.setTarget(getTarget());
            sl.addFreshEntity(m);
            EspadaFx.auraPulse(sl, m, 1.5F, EspadaFx.RESPIRA_LIGHT);
        }
    }

    // ------------------------------------------------------------------ Teste

    @Override
    public List<String> debugAttacks() {
        return List.of("respira", "dome", "grab", "axe", "throw", "summon", "arrogante", "ruin", "pools", "domain");
    }

    @Override
    public boolean debugCast(ServerLevel sl, String name, @Nullable LivingEntity target) {
        if (isTransforming() || introTicks() > 0) return false;
        // Golpes do Rei só existem no corpo de ossos (as animações deles estão no modelo do Arrogante).
        if ((name.equals("ruin") || name.equals("pools") || name.equals("domain")) && !isArrogante()) return false;
        attack = A_NONE;
        exhausted = 0;
        switch (name) {
            case "arrogante" -> {
                if (isArrogante()) return false;
                setPhase(Math.max(phase(), 2));
                startArrogante(sl);
            }
            case "dome" -> startDome(sl);
            case "summon" -> summonFraccion(sl);
            case "ruin" -> startRuin(sl);
            case "pools" -> startPools(sl);
            case "domain" -> startDomain(sl);
            default -> {
                if (target == null) return false;
                switch (name) {
                    case "respira" -> startRespira(sl, target);
                    case "grab" -> startGrab(sl, target);
                    case "axe" -> startAxe(sl, target);
                    case "throw" -> startThrow(sl, target);
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Morte: vira pó (o próprio aspecto)

    @Override
    protected void onDefeated(ServerLevel sl) {
        attack = A_NONE;
        puffs.clear();
        super.onDefeated(sl);
        quote("death");
    }

    @Override
    protected void deathTick(ServerLevel sl, int t) {
        if (t % 8 == 0) EspadaFx.crumble(sl, position().add(0, 0.4 + random.nextDouble() * 2.0, 0));
        if (t == 80) {
            EspadaFx.explosion(sl, position().add(0, 1, 0), 2.5F, EspadaFx.AGED, EspadaFx.STYLE_RESPIRA);
            sl.playSound(null, getX(), getY(), getZ(), EspadaSounds.CRUMBLE.get(), SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(EspadaRegistries.ARROGANTE_SHARD.get(), 1, 1);
        dropItem(EspadaRegistries.REISHI_RESTORATION.get(), 3, 4);
        ItemStack legend = EspadaLegends.scroll(EspadaLegends.BARRAGAN);
        if (!legend.isEmpty()) spawnAtLocation(legend);
        // w10: quem lutou aprende a técnica (a forma da Magia Lendária)
        EspadaLegends.teach(this, EspadaLegends.BARRAGAN);
        var shadow = CoreRegistries.ESSENCES.get(Element.SHADOW);
        if (shadow != null) dropItem(shadow.get(), 3, 6);
        var earth = CoreRegistries.ESSENCES.get(Element.EARTH);
        if (earth != null) dropItem(earth.get(), 2, 4);
        dropById("mana_crystal", 2, 4);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // A forma salva pode ser Arrogante: o corpo do rei de ossos é maior.
        refreshDimensions();
    }
}
