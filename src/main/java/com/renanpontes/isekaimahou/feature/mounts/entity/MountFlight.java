package com.renanpontes.isekaimahou.feature.mounts.entity;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Física e estado de voo de uma montaria (composição: serve para TamableAnimal e AbstractHorse).
 *
 * <p>Quem move uma montaria com cavaleiro é o CLIENTE do cavaleiro (o vanilla faz assim com cavalos):
 * {@link #travelRidden} roda lá, lendo as entradas "subir/descer" que o cliente do módulo grava em
 * {@link #setInput} a cada tick (pulo = sobe, agachar = desce). O servidor só recebe a posição.
 *
 * <p>Também calcula, em todos os lados, se a criatura está no ar, se está planando (descendo sem bater
 * asas) e a inclinação/rolagem usadas pelo renderer — a partir do deslocamento real entre ticks, que
 * funciona igual para quem controla, para os outros clientes e para o servidor.
 */
public final class MountFlight {

    /**
     * Parâmetros de voo.
     *
     * @param flySpeed       velocidade de cruzeiro montado (blocos/tick)
     * @param climb          velocidade de subida (pulo)
     * @param descend        velocidade de descida (agachar)
     * @param glideSink      afundamento ao planar sem entrada vertical
     * @param accel          inércia horizontal (0..1, maior = responde mais rápido)
     * @param accelY         inércia vertical
     * @param takeoff        impulso vertical da decolagem
     * @param pitchInfluence quanto o olhar para cima/baixo inclina o voo (0 = nada)
     * @param swim           "nada" no ar seguindo o olhar em 3D e flutua parada (Arraia do Céu)
     * @param maxFall        queda máxima sem controle (asas abertas)
     * @param turnRate       graus por tick que a montaria gira em direção ao olhar do cavaleiro
     * @param flapSound      som da batida de asas
     * @param flapPitch      tom do som
     * @param flapInterval   ticks entre batidas
     */
    public record Params(double flySpeed, double climb, double descend, double glideSink, double accel, double accelY,
                         double takeoff, double pitchInfluence, boolean swim, double maxFall, float turnRate,
                         SoundEvent flapSound, float flapPitch, int flapInterval) {}

    private final Params params;

    // Entradas do cavaleiro (só o cliente que controla grava)
    private boolean inputUp;
    private boolean inputDown;
    private long inputTime = -100L;
    /** Cliente que pilota: está em voo (decolou ou caiu de uma altura). Fora disso anda e desce ladeiras normalmente. */
    private boolean riddenFlying;

    // Estado observado (todos os lados)
    private int airTicks;
    private boolean hasLast;
    private double lastX, lastY, lastZ;
    private double velY, velH;
    private boolean gliding;
    private int flapTimer;

    // Visual (cliente)
    private float tilt, tiltO, bank, bankO;
    private float lastYaw;

    // Proteção contra o vazio (servidor)
    @Nullable
    private Vec3 lastGround;
    private int rescueTicks;
    private boolean rescuing;

    public MountFlight(Params params) {
        this.params = params;
    }

    public Params params() {
        return params;
    }

    // =====================================================================================
    //  Entradas
    // =====================================================================================

    public void setInput(boolean up, boolean down, long gameTime) {
        this.inputUp = up;
        this.inputDown = down;
        this.inputTime = gameTime;
    }

    /** Entradas antigas (cliente travou/perdeu o foco) não valem: evita a montaria subir sozinha. */
    private boolean fresh(Level level) {
        return level.getGameTime() - inputTime <= 3L;
    }

    public boolean wantsUp(Level level) {
        return inputUp && fresh(level);
    }

    public boolean wantsDown(Level level) {
        return inputDown && fresh(level);
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================

    /** No ar há alguns ticks (evita piscar a animação em degraus). */
    public boolean isAirborne() {
        return airTicks > 3;
    }

    /** Planando: no ar, descendo e andando para a frente — asas abertas, sem bater. */
    public boolean isGliding() {
        return gliding;
    }

    /** Velocidade horizontal observada (blocos/tick). */
    public double horizontalSpeed() {
        return velH;
    }

    public double verticalSpeed() {
        return velY;
    }

    public float tilt(float partialTick) {
        return Mth.lerp(partialTick, tiltO, tilt);
    }

    public float bank(float partialTick) {
        return Mth.lerp(partialTick, bankO, bank);
    }

    /** Atualiza o estado observado. Chamar no fim do tick() da entidade, nos dois lados. */
    public void tick(LivingEntity mount) {
        boolean grounded = mount.onGround() || mount.isInWater() || mount.isInLava() || mount.isPassenger();
        airTicks = grounded ? 0 : Math.min(airTicks + 1, 1000);

        double x = mount.getX(), y = mount.getY(), z = mount.getZ();
        if (hasLast) {
            double dy = y - lastY;
            double dh = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));
            if (dh > 8.0 || Math.abs(dy) > 8.0) { // teleporte: ignora
                dy = 0.0;
                dh = 0.0;
            }
            velY = velY * 0.55 + dy * 0.45;
            velH = velH * 0.55 + dh * 0.45;
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLast = true;

        // Histerese para a troca bater asas <-> planar não tremer
        if (!isAirborne() || params.swim) gliding = false;
        else if (gliding) gliding = velY < 0.0 && velH > 0.08;
        else gliding = velY < -0.04 && velH > 0.14;

        if (mount.level().isClientSide) {
            tiltO = tilt;
            bankO = bank;
            float yaw = mount.getYRot();
            float dYaw = Mth.wrapDegrees(yaw - lastYaw);
            lastYaw = yaw;
            float targetTilt = 0F;
            float targetBank = 0F;
            if (isAirborne()) {
                targetTilt = Mth.clamp((float) (-velY * 55.0), -22F, 24F);
                targetBank = Mth.clamp(-dYaw * 2.2F, -26F, 26F);
            }
            tilt += (targetTilt - tilt) * 0.18F;
            bank += (targetBank - bank) * 0.2F;
        }
    }

    /** Acabou de levantar voo (subindo, no primeiro tick em que conta como "no ar")? */
    public boolean justTookOff() {
        return airTicks == 4 && velY > 0.03 && !params.swim;
    }

    /** Servidor: lufada de poeira/vento no chão e uma batida de asas forte ao decolar. */
    public static void takeoffFx(LivingEntity mount, MountFlight flight) {
        if (!(mount.level() instanceof ServerLevel sl) || !flight.justTookOff()) return;
        double r = mount.getBbWidth() * 0.6;
        sl.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY() + 0.1, mount.getZ(),
                14, r, 0.05, r, 0.06);
        sl.sendParticles(ParticleTypes.POOF, mount.getX(), mount.getY() + 0.2, mount.getZ(),
                6, r, 0.1, r, 0.02);
        mount.playSound(flight.params.flapSound, 1.0F, flight.params.flapPitch * 0.85F);
    }

    /** Servidor: está na hora de tocar o som da batida de asas? */
    public boolean flapSoundDue() {
        if (!isAirborne() || gliding) {
            flapTimer = 0;
            return false;
        }
        return flapTimer++ % Math.max(4, params.flapInterval) == 0;
    }

    // =====================================================================================
    //  Física
    // =====================================================================================

    /**
     * Voo com cavaleiro (roda no lado que controla). Devolve false quando a montaria deve andar no chão
     * (o chamador então usa a física normal).
     */
    public boolean travelRidden(LivingEntity mount, Player rider, Vec3 input, double boost, boolean dash) {
        Level level = mount.level();
        boolean up = wantsUp(level);
        boolean down = wantsDown(level);
        if (!params.swim) {
            if (mount.onGround() || mount.isInWater() || mount.isInLava()) riddenFlying = false;
            if (up || dash) {
                riddenFlying = true;
            } else if (!riddenFlying && mount.getDeltaMovement().y < -0.15 && heightAboveGround(mount, 3.0) > 2.5) {
                riddenFlying = true; // caiu de um penhasco: abre as asas
            }
            if (!riddenFlying) return false;
        }

        double speed = params.flySpeed * boost;
        double fwd = dash ? 1.0 : input.z;
        double strafe = dash ? 0.0 : input.x;
        float yawRad = mount.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yawRad);
        double cos = Mth.cos(yawRad);
        float pitch = rider.getXRot();
        double tx, ty, tz;
        if (params.swim || dash) {
            // Segue o olhar em 3D (nado no ar / mergulho)
            double cp = Mth.cos(pitch * Mth.DEG_TO_RAD);
            double sp = Mth.sin(pitch * Mth.DEG_TO_RAD);
            double lx = -sin * cp, ly = -sp, lz = cos * cp;
            double sx = cos, sz = sin; // direita/esquerda da montaria (strafe)
            tx = (lx * fwd + sx * strafe) * speed;
            ty = ly * fwd * speed;
            tz = (lz * fwd + sz * strafe) * speed;
        } else {
            double pv = fwd > 0.0 ? -Mth.sin(pitch * Mth.DEG_TO_RAD) * params.pitchInfluence : 0.0;
            double hScale = 1.0 - Math.abs(pv) * 0.35;
            tx = (strafe * cos - fwd * sin) * speed * hScale;
            tz = (fwd * cos + strafe * sin) * speed * hScale;
            ty = pv * fwd * speed;
        }
        if (up) ty += params.climb;
        else if (down) ty -= params.descend;
        else if (!params.swim && Math.abs(ty) < 0.02 && fwd > 0.1) ty = -params.glideSink; // planando para a frente, perde altura devagar

        if (params.swim && !down) {
            // A arraia flutua: perto do chão ela sobe sozinha até pairar
            double h = heightAboveGround(mount, 1.2);
            if (h < 0.6) ty = Math.max(ty, 0.12);
        }

        Vec3 v = mount.getDeltaMovement();
        double nx = v.x + (tx - v.x) * params.accel;
        double ny = v.y + (ty - v.y) * params.accelY;
        double nz = v.z + (tz - v.z) * params.accel;
        if (mount.onGround() && up) ny = Math.max(ny, params.takeoff);
        Vec3 next = new Vec3(nx, ny, nz);
        mount.setDeltaMovement(next);
        mount.move(MoverType.SELF, next);
        if (mount.horizontalCollision) {
            Vec3 after = mount.getDeltaMovement();
            mount.setDeltaMovement(after.x * 0.5, after.y, after.z * 0.5);
        }
        mount.resetFallDistance();
        mount.calculateEntityAnimation(true);
        return true;
    }

    /**
     * Voo selvagem (sem cavaleiro, no servidor). O {@link MountFlyMoveControl} ajusta a velocidade
     * na direção do alvo; aqui só move, aplica o arrasto do ar e, sem alvo, plana para baixo.
     */
    public void travelWild(Mob mob) {
        Vec3 v = mob.getDeltaMovement();
        if (!mob.isNoGravity()) {
            v = v.add(0.0, -0.04, 0.0);
            if (v.y < -params.maxFall) v = new Vec3(v.x, -params.maxFall, v.z);
        }
        mob.move(MoverType.SELF, v);
        Vec3 after = mob.getDeltaMovement();
        mob.setDeltaMovement(after.x * 0.98, after.y * 0.96, after.z * 0.98);
        mob.resetFallDistance();
        mob.calculateEntityAnimation(true);
    }

    /**
     * Servidor, fim do aiStep (montarias que pousam). Sem objetivo pedindo voo a criatura plana para baixo;
     * nas dimensões de ilhas flutuantes (Santuário Celestial) isso seria cair no vazio. Se não há chão
     * NENHUM embaixo (nem água) até o fundo do mundo, ela volta voando ao último chão em que pisou (ou
     * paira, se nunca pisou em nenhum) e só então plana e pousa. Sobre vales fundos ela plana normalmente.
     *
     * @param flightRequested algum objetivo pediu voo neste tick (ele manda; o resgate não interfere)
     */
    public void guardAgainstVoid(Mob mob, FlyingMount mount, boolean flightRequested) {
        if (mob.onGround() && !mob.isInWater()) {
            lastGround = mob.position();
            rescueTicks = 0;
            return;
        }
        if (flightRequested || mob.isVehicle() || mob.isInWater() || mob.isInLava() || mob.isLeashed()) {
            rescueTicks = 0;
            return;
        }
        if (rescueTicks <= 0) {
            // Checa a cada 10 ticks (raio longo); emenda direto se o resgate anterior acabou ainda no vazio
            if (!rescuing && mob.tickCount % 10 != 0) return;
            double depth = mob.getY() - mob.level().getMinBuildHeight() + 1.0;
            rescuing = depth > 0.0 && heightAboveGround(mob, depth) > depth;
            if (!rescuing) return;
            rescueTicks = 40;
        }
        rescueTicks--;
        mount.setFlyingMode(true);
        Vec3 home = lastGround != null && lastGround.distanceToSqr(mob.position()) < 192.0 * 192.0 ? lastGround : null;
        if (home != null) mob.getMoveControl().setWantedPosition(home.x, home.y + 2.0, home.z, 1.0);
        else mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.5);
    }

    /** Depois da física normal (no chão): limita a velocidade de queda — as asas abrem e ela plana. */
    public void clampFall(LivingEntity mount) {
        Vec3 v = mount.getDeltaMovement();
        if (v.y < -params.maxFall && !mount.isInWater() && !mount.isInLava()) {
            mount.setDeltaMovement(v.x, -params.maxFall, v.z);
        }
    }

    /** Altura do pé da criatura até o chão (até {@code max} blocos; devolve {@code max + 1} se não achar). */
    public static double heightAboveGround(LivingEntity e, double max) {
        Vec3 from = e.position();
        Vec3 to = from.subtract(0.0, max, 0.0);
        BlockHitResult hit = e.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        if (hit.getType() == HitResult.Type.MISS) return max + 1.0;
        return from.y - hit.getLocation().y;
    }

    /** Som padrão de asas (dragão, com tom ajustado por criatura). */
    public static SoundEvent wingSound() {
        return SoundEvents.ENDER_DRAGON_FLAP;
    }
}
