package com.renanpontes.isekaimahou.feature.origins_a.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.origins_a.shape.Shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.WolfVariants;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Metamorfose no cliente: guarda a forma de cada jogador (vinda do servidor), desenha o jogador como um
 * animal do vanilla ("manequim" que copia rotação, passos, dano...), esconde a mão em 1ª pessoa e cuida do
 * movimento especial do jogador local (planar/bater asas como falcão, escalar paredes como aranha).
 */
public final class ShapeClient {
    private record State(Shape shape, Element element) {}

    private static final class Mannequin {
        final LivingEntity entity;
        final Level level;
        float flapping = 1.0F;
        boolean wasSwinging;

        Mannequin(LivingEntity entity, Level level) {
            this.entity = entity;
            this.level = level;
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, Mannequin> MANNEQUINS = new HashMap<>();

    // Falcão (jogador local)
    private static final int MAX_FLAPS = 4;
    private static int flaps = MAX_FLAPS;
    private static int flapCooldown;
    private static boolean jumpHeld;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ShapeClient::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(ShapeClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ShapeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            STATES.clear();
            MANNEQUINS.clear();
        });
    }

    /** Chamado pelo pacote FormSync. */
    public static void apply(int entityId, int shape, int element) {
        Shape s = Shape.byNetId(shape);
        MANNEQUINS.remove(entityId);
        if (s == null) {
            STATES.remove(entityId);
            return;
        }
        Element[] all = Element.values();
        STATES.put(entityId, new State(s, element >= 0 && element < all.length ? all[element] : Element.ARCANE));
    }

    // =====================================================================================
    //  Manequins
    // =====================================================================================
    private static Mannequin mannequin(Player p, State st) {
        Mannequin m = MANNEQUINS.get(p.getId());
        if (m != null && m.level == p.level()) return m;
        LivingEntity e = create(p.level(), st);
        if (e == null) return null;
        m = new Mannequin(e, p.level());
        copyPose(m, p, true);
        MANNEQUINS.put(p.getId(), m);
        return m;
    }

    private static LivingEntity create(Level level, State st) {
        EntityType<? extends LivingEntity> type = switch (st.shape()) {
            case WOLF -> EntityType.WOLF;
            case BEAR -> EntityType.POLAR_BEAR;
            case FALCON -> EntityType.PARROT;
            case DOLPHIN -> EntityType.DOLPHIN;
            case SPIDER -> EntityType.SPIDER;
            case BEAST -> beastType(st.element());
        };
        LivingEntity e = type.create(level);
        if (e == null) return null;
        e.setSilent(true);
        if (e instanceof Wolf w) {
            w.setTame(true, false);
            level.registryAccess().registry(Registries.WOLF_VARIANT)
                    .flatMap(r -> r.getHolder(wolfVariant(st.element())))
                    .ifPresent(w::setVariant);
        }
        if (e instanceof Parrot parrot) parrot.setVariant(Parrot.Variant.GRAY);
        if (e instanceof Horse h) h.setVariant(Variant.WHITE);
        if (e instanceof Breeze b) b.idle.start(0);
        return e;
    }

    /** Fera Elemental: uma criatura do vanilla por elemento (fusões usam o primeiro elemento-pai). */
    private static EntityType<? extends LivingEntity> beastType(Element el) {
        Element base = el.isFusion() ? el.parentA : el;
        return switch (base) {
            case FIRE -> EntityType.BLAZE;
            case WATER -> EntityType.GUARDIAN;
            case EARTH -> EntityType.RAVAGER;
            case WIND -> EntityType.BREEZE;
            case LIGHT -> EntityType.HORSE;
            case SHADOW -> EntityType.WARDEN;
            default -> EntityType.ENDERMAN;
        };
    }

    private static ResourceKey<WolfVariant> wolfVariant(Element el) {
        Element base = el.isFusion() ? el.parentA : el;
        return switch (base) {
            case FIRE -> WolfVariants.RUSTY;
            case WATER -> WolfVariants.ASHEN;
            case EARTH -> WolfVariants.WOODS;
            case WIND -> WolfVariants.STRIPED;
            case LIGHT -> WolfVariants.SNOWY;
            case SHADOW -> WolfVariants.BLACK;
            default -> WolfVariants.PALE;
        };
    }

    /** Tamanho do desenho (o jogador já está com o tamanho da forma; isto ajusta o animal a ele). */
    private static float renderScale(State st, Player p) {
        float expected;
        float scale;
        switch (st.shape()) {
            case WOLF -> { expected = 0.55F; scale = 1.25F; }
            case BEAR -> { expected = 1.15F; scale = 1.25F; }
            case FALCON -> { expected = 0.45F; scale = 1.15F; }
            case DOLPHIN -> { expected = 0.7F; scale = 1.3F; }
            case SPIDER -> { expected = 0.6F; scale = 0.75F; }
            default -> {
                expected = 1.3F;
                Element base = st.element().isFusion() ? st.element().parentA : st.element();
                scale = switch (base) {
                    case FIRE -> 1.25F;
                    case WATER -> 2.0F;
                    case EARTH -> 1.0F;
                    case WIND -> 1.3F;
                    case LIGHT -> 1.35F;
                    default -> 0.85F;
                };
            }
        }
        // Respeita outros efeitos de tamanho (Encolher/Crescer de outros módulos)
        return scale * p.getScale() / expected;
    }

    /** Copia a pose do jogador para o manequim. {@code tick}: avança passos/asas (uma vez por tick). */
    private static void copyPose(Mannequin m, Player p, boolean tick) {
        LivingEntity e = m.entity;
        e.setPos(p.getX(), p.getY(), p.getZ());
        e.xo = p.xo;
        e.yo = p.yo;
        e.zo = p.zo;
        e.xOld = p.xOld;
        e.yOld = p.yOld;
        e.zOld = p.zOld;
        e.setYRot(p.getYRot());
        e.yRotO = p.yRotO;
        e.setXRot(p.getXRot());
        e.xRotO = p.xRotO;
        e.yBodyRot = p.yBodyRot;
        e.yBodyRotO = p.yBodyRotO;
        e.yHeadRot = p.yHeadRot;
        e.yHeadRotO = p.yHeadRotO;
        e.tickCount = p.tickCount;
        e.hurtTime = p.hurtTime;
        e.hurtDuration = p.hurtDuration;
        e.deathTime = p.deathTime;
        e.setOnGround(p.onGround());
        e.setInvisible(p.isInvisible());
        e.setShiftKeyDown(p.isShiftKeyDown());
        e.setDeltaMovement(new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo));
        if (!tick) return;
        e.walkAnimation.update(p.walkAnimation.speed(), 1.0F);
        if (e instanceof Wolf w) {
            w.setInSittingPose(p.isCrouching());
            w.setRemainingPersistentAngerTime(p.isSprinting() ? 40 : 0);
            w.setHealth(w.getMaxHealth() * Mth.clamp(p.getHealth() / Math.max(1.0F, p.getMaxHealth()), 0.05F, 1.0F));
        }
        if (e instanceof Parrot parrot) {
            // Mesma conta do Parrot.calculateFlapping (o manequim não roda o tick do vanilla)
            boolean air = !p.onGround();
            boolean gliding = air && p.getY() - p.yo < -0.02;
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            parrot.flapSpeed = Mth.clamp(parrot.flapSpeed + (air ? (gliding ? 0.6F : 4.0F) : -1.0F) * 0.3F, 0.0F, gliding ? 0.35F : 1.0F);
            if (air && m.flapping < 1.0F) m.flapping = 1.0F;
            m.flapping *= 0.9F;
            parrot.flap += m.flapping * 2.0F;
        }
        boolean swinging = p.swinging;
        if (swinging && !m.wasSwinging) {
            if (e instanceof Warden w) w.attackAnimationState.start(e.tickCount);
            if (e instanceof Breeze b) b.shoot.start(e.tickCount);
        }
        m.wasSwinging = swinging;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        State st = STATES.get(p.getId());
        if (st == null || p.isSpectator()) return;
        Mannequin m = mannequin(p, st);
        if (m == null) return;
        event.setCanceled(true);
        copyPose(m, p, false);
        // O nome do jogador some junto com o render cancelado: o manequim mostra no lugar (mesmas regras do vanilla)
        boolean showName = nameVisible(p);
        m.entity.setCustomName(showName ? p.getDisplayName() : null);
        m.entity.setCustomNameVisible(showName);
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float s = renderScale(st, p);
        ps.scale(s, s, s);
        EntityRenderer<? super LivingEntity> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m.entity);
        r.render(m.entity, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, ps, event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
    }

    /** Mesma regra do nome de jogador do vanilla: não para si mesmo/câmera, respeita invisibilidade e times. */
    private static boolean nameVisible(Player p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local == null || !Minecraft.renderNames() || p == mc.getCameraEntity() || p.isVehicle()) return false;
        boolean seen = !p.isInvisibleTo(local);
        Team team = p.getTeam();
        if (team == null || p == local) return seen;
        Team mine = local.getTeam();
        return switch (team.getNameTagVisibility()) {
            case ALWAYS -> seen;
            case NEVER -> false;
            case HIDE_FOR_OTHER_TEAMS -> mine == null ? seen : team.isAlliedTo(mine) && (team.canSeeFriendlyInvisibles() || seen);
            case HIDE_FOR_OWN_TEAM -> mine == null ? seen : !team.isAlliedTo(mine) && seen;
        };
    }

    /** Animal não tem mão de jogador em 1ª pessoa. */
    private static void onRenderHand(RenderHandEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p != null && STATES.containsKey(p.getId())) event.setCanceled(true);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        // Manequins: passos, asas, ataques; e partículas da Fera Elemental
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        while (it.hasNext()) {
            Map.Entry<Integer, State> en = it.next();
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof Player p)) {
                if (ent == null && mc.level.getGameTime() % 100 == 0) {
                    MANNEQUINS.remove(en.getKey());
                    it.remove();
                }
                continue;
            }
            Mannequin m = mannequin(p, en.getValue());
            if (m != null) copyPose(m, p, true);
            if (en.getValue().shape() == Shape.BEAST && !p.isInvisible() && p.tickCount % (reduced ? 8 : 3) == 0) {
                Element el = en.getValue().element();
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double rad = p.getBbWidth() * 0.7;
                mc.level.addParticle(ElementFx.signature(el), p.getX() + Math.cos(a) * rad,
                        p.getY() + p.getRandom().nextDouble() * p.getBbHeight(), p.getZ() + Math.sin(a) * rad, 0, 0.02, 0);
            }
        }
        LocalPlayer lp = mc.player;
        State own = STATES.get(lp.getId());
        Shape shape = own != null ? own.shape() : null;
        if (shape == Shape.FALCON) falcon(mc, lp);
        else flaps = MAX_FLAPS;
        if (shape == Shape.SPIDER) spider(lp);
    }

    /** Falcão: segurar no ar plana (cai devagar e acelera para frente); pular no ar bate as asas (4 por voo). */
    private static void falcon(Minecraft mc, LocalPlayer lp) {
        if (flapCooldown > 0) flapCooldown--;
        boolean jump = mc.options.keyJump.isDown();
        if (lp.onGround() || lp.isInWater() || lp.getAbilities().flying || lp.isFallFlying() || lp.isPassenger()) {
            flaps = MAX_FLAPS;
            jumpHeld = jump;
            return;
        }
        Vec3 v = lp.getDeltaMovement();
        Vec3 look = lp.getLookAngle();
        if (jump && !jumpHeld && flaps > 0 && flapCooldown <= 0) {
            flaps--;
            flapCooldown = 6;
            v = new Vec3(v.x + look.x * 0.2, Math.max(v.y, 0.0) + 0.5, v.z + look.z * 0.2);
            lp.playSound(SoundEvents.PHANTOM_FLAP, 0.7F, 1.5F);
            for (int i = 0; i < 6; i++) {
                lp.level().addParticle(ParticleTypes.CLOUD, lp.getX(), lp.getY(), lp.getZ(),
                        (lp.getRandom().nextDouble() - 0.5) * 0.2, -0.1, (lp.getRandom().nextDouble() - 0.5) * 0.2);
            }
        }
        jumpHeld = jump;
        if (!lp.isShiftKeyDown() && v.y < -0.08) {
            double vx = v.x, vz = v.z;
            if (lp.zza > 0) {
                Vec3 dir = new Vec3(look.x, 0, look.z);
                if (dir.lengthSqr() > 1.0E-4) {
                    dir = dir.normalize().scale(0.55);
                    vx = Mth.lerp(0.08, vx, dir.x);
                    vz = Mth.lerp(0.08, vz, dir.z);
                }
            }
            v = new Vec3(vx, -0.08, vz);
        }
        lp.setDeltaMovement(v);
    }

    /** Aranha: encostado numa parede e andando, sobe; agachado, fica grudado. */
    private static void spider(LocalPlayer lp) {
        if (!lp.horizontalCollision || (lp.zza == 0 && lp.xxa == 0)) return;
        Vec3 v = lp.getDeltaMovement();
        double y = lp.isShiftKeyDown() ? Math.max(v.y, 0.0) : Math.max(v.y, 0.16);
        lp.setDeltaMovement(v.x, y, v.z);
        lp.resetFallDistance();
    }

    private ShapeClient() {}
}
