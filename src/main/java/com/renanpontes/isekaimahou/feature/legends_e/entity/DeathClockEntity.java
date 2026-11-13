package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * O relógio de O Fim de Toda Vida: fica no centro do chão da área e é desenhado no céu (altura {@link #height()}).
 * O ponteiro avança uma marca por segundo; em 12 s ({@link #DURATION}) vem a execução. Depois o relógio racha e some.
 */
public class DeathClockEntity extends LeSpellEntity {
    public static final int MARK_TICKS = 20;
    public static final int MARKS = 12;
    public static final int DURATION = MARK_TICKS * MARKS;
    public static final int END_LIFE = 30;
    private static final int MAX_PER_CASTER = 3;

    private static final EntityDataAccessor<Float> DATA_AREA = SynchedEntityData.defineId(DeathClockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(DeathClockEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;

    public DeathClockEntity(EntityType<? extends DeathClockEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder b) {
        b.define(DATA_AREA, 14F);
        b.define(DATA_HEIGHT, 18F);
    }

    public float area() { return entityData.get(DATA_AREA); }

    /** Altura do relógio acima do chão. */
    public float height() { return entityData.get(DATA_HEIGHT); }

    public static void spawn(SpellContext ctx, Vec3 center, SpellStats stats) {
        ServerLevel level = ctx.level();
        float area = Mth.clamp(stats.radius(14F), 8F, 40F);
        if (!LeUtil.claim("death_clock", level, ctx.casterId(), center, area, DURATION)) return;
        UUID id = ctx.casterId();
        if (id != null && level.getEntities(LeRegistry.DEATH_CLOCK.get(), e -> id.equals(e.owner)).size() >= MAX_PER_CASTER) return;
        DeathClockEntity e = new DeathClockEntity(LeRegistry.DEATH_CLOCK.get(), level);
        e.bind(ctx, stats.copy());
        e.owner = id;
        e.entityData.set(DATA_AREA, area);
        e.entityData.set(DATA_HEIGHT, 12F + area * 0.45F);
        e.setPos(center.x, center.y, center.z);
        level.addFreshEntity(e);
        // A "sombra" do relógio no chão: quem estiver dentro dela quando a volta fechar, morre
        Telegraph.show(level, Telegraph.Shape.circle(center, area).warmup(DURATION).color(0x8A1A2A));
        LeFx.sound(level, center, SoundEvents.BELL_RESONATE, 3.0F, 0.5F);
        LeFx.sound(level, center.add(0, e.height(), 0), SoundEvents.WITHER_AMBIENT, 2.0F, 0.4F);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        Vec3 c = position();
        float area = area();
        if (age < DURATION) {
            if (age > 0 && age % MARK_TICKS == 0) tick(level, ctx, c, area, age / MARK_TICKS);
            if (age % MARK_TICKS == MARK_TICKS / 2) LeFx.sound(level, c.add(0, height(), 0), SoundEvents.NOTE_BLOCK_HAT, 2.5F, 0.6F);
            if (age % 6 == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * area;
                LeFx.send(level, Vfx.soul(), c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.03);
            }
        }
        if (age == DURATION) execute(level, ctx, c, area);
        if (age >= DURATION + END_LIFE) discard();
    }

    /** Cada marca: sino (mais grave a cada segundo) e aviso para os jogadores inimigos na sombra. */
    private void tick(ServerLevel level, SpellContext ctx, Vec3 c, float area, int mark) {
        LeFx.sound(level, c.add(0, height(), 0), SoundEvents.BELL_BLOCK, 3.0F, Math.max(0.5F, 1.3F - mark * 0.07F));
        int left = MARKS - mark;
        for (ServerPlayer p : level.players()) {
            if (!inside(p, c, area) || !LeUtil.isFoe(ctx, p)) continue;
            p.displayClientMessage(Component.translatable("message.isekai_mahou.legends_e.death_clock_warn", left)
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    private boolean inside(LivingEntity e, Vec3 c, float area) {
        double dx = e.getX() - c.x, dz = e.getZ() - c.z;
        return dx * dx + dz * dz <= area * area && e.getY() > c.y - 10 && e.getY() < c.y + height() + 4;
    }

    private void execute(ServerLevel level, SpellContext ctx, Vec3 c, float area) {
        SpellStats s = stats != null ? stats : new SpellStats();
        LivingEntity caster = ctx.caster();
        for (LivingEntity e : LeUtil.foesAround(ctx, c, area, 10, height() + 4, 128)) {
            if (!inside(e, c, area)) continue;
            Vec3 at = LeUtil.center(e);
            if (LeUtil.resistsExecution(e)) {
                LeUtil.percent(ctx, e, 0.35F, s, this);
            } else {
                LeUtil.execute(ctx, e, s, this);
            }
            LeFx.send(level, Vfx.sculkSoul(), at, 10, 0.3, 0.06);
            LeFx.send(level, LeFx.fade(0x8A1A2A, 0x000000, 1.5F), at, 24, 0.4, 0.04);
        }
        if (caster != null && !s.silent) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.legends_e.death_clock"), 0xC0182E, Announce.STYLE_ULTIMATE);
        }
        Vec3 top = c.add(0, height(), 0);
        LeFx.flash(level, c, area + 20, 0x3A0008, 22, 0.7F);
        BossKit.shake(level, c, area + 24, 1.8F, 30);
        LeFx.sound(level, top, SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
        LeFx.sound(level, top, SoundEvents.BELL_RESONATE, 4.0F, 0.5F);
        LeFx.sound(level, c, SoundEvents.WITHER_DEATH, 2.5F, 0.6F);
        LeFx.ring(level, c.add(0, 0.3, 0), new Vec3(0, 1, 0), area * 1.15F, 0xC0182E, 20);
        LeFx.ring(level, top, new Vec3(0, 1, 0), area * 0.9F, 0xFF3A4A, 16);
        LeFx.send(level, Vfx.soul(), c.x, c.y + 0.5, c.z, 80, area * 0.5, 0.5, area * 0.5, 0.08);
    }

    /** Ângulo do ponteiro em marcas (0..12), com o "tique" de relógio: anda rápido no começo de cada segundo e para. */
    public float handMarks(float ageF) {
        float t = Mth.clamp(ageF, 0F, DURATION + 6F);
        int mark = (int) (t / MARK_TICKS);
        if (mark == 0) return 0F;
        // Cada sino: o ponteiro salta para a próxima marca (rápido), balança um pouco e para
        float frac = (t - mark * MARK_TICKS) / MARK_TICKS;
        float k = Mth.clamp(frac / 0.18F, 0F, 1F);
        float ease = 1F - (1F - k) * (1F - k);
        float wobble = k >= 1F ? (float) Math.sin(Math.min(1F, (frac - 0.18F) / 0.15F) * Math.PI) * 0.05F : 0F;
        return Math.min(MARKS, mark - 1 + ease + wobble);
    }

    /** 0..1: o relógio aparece (primeiro segundo) e some depois da execução. */
    public float presence(float ageF) {
        float in = Mth.clamp(ageF / 20F, 0F, 1F);
        float out = ageF <= DURATION ? 1F : Mth.clamp(1F - (ageF - DURATION) / END_LIFE, 0F, 1F);
        return Math.min(in, out);
    }
}
