package com.renanpontes.isekaimahou.client.spellfx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.glyph.CircleStyle;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Partícula PLANA: círculo mágico (gira, pode seguir uma entidade — aos pés, na frente da mão ou no céu) e onda de
 * choque (anel que cresce). Cada uma é desenhada duas vezes: uma camada translúcida (aparece de dia) e um "gêmeo"
 * aditivo (brilha). Círculos com {@link SpellFxParticles#FLAG_KEEPALIVE} não se duplicam: um novo pacote igual só
 * estende a vida do que já existe (a conjuração manda um a cada poucos ticks).
 * <p>
 * release 1.0: o círculo principal pode vir com forma e símbolo escolhidos na receita ({@link CircleStyle}, nos bits
 * altos das flags) — a forma troca a arte, o símbolo é um segundo quadro no centro que NÃO gira (fica de pé para
 * quem conjura). O círculo aos pés também avisa a {@link ChannelAura} enquanto a conjuração é segurada.
 */
public class FlatFxParticle extends Particle {
    private static final Map<Long, FlatFxParticle> LIVE = new HashMap<>();

    private final boolean shock;
    private final boolean additive;
    private final int rgb;
    private final float radius;
    private final int follow;
    private final int mode;
    private final int tier;
    private final int layer;
    private final boolean release;
    private final boolean reverse;
    private final Vec3 normal;
    private final TextureAtlasSprite sprite;
    /** Símbolo do centro (círculo por magia), ou null. */
    @Nullable
    private final TextureAtlasSprite symbol;
    private final float baseAlpha;
    private final float spinSpeed;
    private final float angle0;
    @Nullable
    private FlatFxParticle twin;
    private Vec3 center;
    private boolean lost;

    FlatFxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, FxOptions o,
                   TextureAtlasSprite sprite, @Nullable TextureAtlasSprite symbol, boolean shock, boolean additive) {
        super(level, x, y, z);
        this.symbol = symbol;
        this.shock = shock;
        this.additive = additive;
        this.rgb = o.rgb();
        this.radius = Mth.clamp(o.scale(), 0.1F, 48F);
        this.follow = o.data();
        this.mode = SpellFxParticles.modeOf(o.flags());
        this.tier = SpellFxParticles.tierOf(o.flags());
        this.layer = SpellFxParticles.layerOf(o.flags());
        this.release = (o.flags() & SpellFxParticles.FLAG_RELEASE) != 0;
        this.reverse = (o.flags() & SpellFxParticles.FLAG_REVERSE) != 0;
        Vec3 n = new Vec3(vx, vy, vz);
        this.normal = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : n.normalize();
        this.sprite = sprite;
        this.xd = this.yd = this.zd = 0;
        this.gravity = 0;
        this.hasPhysics = false;
        this.lifetime = Math.max(3, o.life() > 0 ? o.life() : shock ? 12 : 20);
        float a = shock ? (additive ? 0.9F : 0.45F) : (additive ? 0.75F : 0.65F);
        if (!shock && tier <= 0) a *= 0.7F;
        this.baseAlpha = a;
        float sp = 0.035F + 0.008F * tier;
        if (layer == 2) sp *= 0.6F;
        if (mode == SpellFxParticles.MODE_FRONT) sp *= 1.8F;
        this.spinSpeed = sp;
        this.angle0 = random.nextFloat() * Mth.TWO_PI;
        this.center = new Vec3(x, y, z);
        setSize(0.1F, 0.1F);
        updateCenter(1.0F);
    }

    /** Cria o círculo/onda + o gêmeo aditivo; com keepalive, estende o existente e devolve null. */
    @Nullable
    static Particle create(FxOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                           SpriteSet sprites, boolean shock) {
        boolean keep = !shock && o.data() >= 0 && (o.flags() & SpellFxParticles.FLAG_KEEPALIVE) != 0;
        long key = ((long) o.data() << 8) | ((long) SpellFxParticles.modeOf(o.flags()) << 2) | SpellFxParticles.layerOf(o.flags());
        if (keep) {
            FlatFxParticle ex = LIVE.get(key);
            if (ex != null && ex.isAlive() && ex.level == level) {
                ex.extend(o.life() > 0 ? o.life() : 10);
                return null;
            }
        }
        int layer = SpellFxParticles.layerOf(o.flags());
        TextureAtlasSprite sprite = shock ? sprites.get(level.random) : sprites.get(layer, 3);
        TextureAtlasSprite symbol = null;
        if (!shock && (layer == 0 || layer == 3)) {
            // Círculo por magia: forma e símbolo da receita (só na camada principal; selos e camadas extras não mudam)
            int shape = CircleStyle.shapeOf(o.flags());
            int sym = CircleStyle.symbolOf(o.flags());
            if (sym > 0) symbol = SpellFxClient.styleSprite(CircleStyle.SHAPES + sym - 1);
            // com símbolo e forma padrão usa a forma 1 (igual ao círculo padrão, com o centro livre)
            if (shape == 0 && symbol != null) shape = 1;
            if (shape > 0) {
                TextureAtlasSprite s = SpellFxClient.styleSprite(shape - 1);
                if (s != null) sprite = s;
            }
        }
        FlatFxParticle main = new FlatFxParticle(level, x, y, z, vx, vy, vz, o, sprite, symbol, shock, false);
        FlatFxParticle glow = new FlatFxParticle(level, x, y, z, vx, vy, vz, o, sprite, symbol, shock, true);
        // os dois giram juntos
        glow.syncAngle(main);
        main.twin = glow;
        Minecraft.getInstance().particleEngine.add(glow);
        if (keep) {
            if (LIVE.size() > 256) LIVE.values().removeIf(p -> !p.isAlive());
            LIVE.put(key, main);
        }
        return main;
    }

    private float angleOffset;

    private void syncAngle(FlatFxParticle other) {
        this.angleOffset = other.angle0 - this.angle0;
    }

    /** Mais {@code ticks} de vida a partir de agora (círculo de conjuração ainda ativo). */
    void extend(int ticks) {
        if (lost) return;
        lifetime = Math.max(lifetime, age + ticks);
        if (twin != null) twin.lifetime = Math.max(twin.lifetime, twin.age + ticks);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        updateCenter(1.0F);
        setPos(center.x, center.y, center.z);
        // Conjuração segurada por bastante tempo: aura discreta em volta de quem conjura (opção do cliente)
        if (!shock && !additive && !release && !lost && follow >= 0 && mode == SpellFxParticles.MODE_GROUND
                && (layer == 0 || layer == 3) && age >= ChannelAura.LONG_HOLD) {
            Entity caster = level.getEntity(follow);
            if (caster != null) ChannelAura.castTick(level, caster, rgb, tier, age);
        }
        // Círculo de conjuração ativo: runas e brilho subindo da borda
        if (!shock && !additive && !release && follow >= 0 && tier >= 1 && mode == SpellFxParticles.MODE_GROUND
                && random.nextFloat() < 0.18F + 0.08F * tier && SpellFxParticles.RUNE.isBound()) {
            double a = random.nextDouble() * Math.PI * 2;
            double rr = radius * (0.75 + random.nextDouble() * 0.25);
            level.addParticle(new FxOptions(SpellFxParticles.RUNE.get(), rgb, 0.7F + 0.1F * tier, 0, -1, 0),
                    center.x + Math.cos(a) * rr, center.y + 0.05, center.z + Math.sin(a) * rr, 0, 0.02 + 0.01 * tier, 0);
        }
    }

    @Override
    public void remove() {
        super.remove();
        LIVE.values().remove(this);
    }

    /** Atualiza o centro (segue a entidade, se houver). */
    private void updateCenter(float pt) {
        if (follow < 0) return;
        Entity e = level.getEntity(follow);
        if (e == null || e.isRemoved()) {
            if (!lost) {
                lost = true;
                lifetime = Math.min(lifetime, age + 6);
                if (twin != null) twin.lifetime = Math.min(twin.lifetime, twin.age + 6);
            }
            return;
        }
        center = anchor(e, pt);
    }

    private Vec3 anchor(Entity e, float pt) {
        Vec3 p = e.getPosition(pt);
        return switch (mode) {
            case SpellFxParticles.MODE_SKY -> p.add(0, e.getBbHeight() + 3.0 + tier * 0.4 + layer * 0.02, 0);
            case SpellFxParticles.MODE_FRONT -> {
                Vec3 look = e.getViewVector(pt);
                Vec3 eye = e.getEyePosition(pt);
                boolean fp = isFirstPersonSelf(e);
                yield eye.add(look.scale(fp ? 1.5 : 0.9)).subtract(0, fp ? 0.45 : 0.25, 0);
            }
            default -> p.add(0, 0.04 + layer * 0.006 + (additive ? 0.003 : 0), 0);
        };
    }

    private static boolean isFirstPersonSelf(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float pt) {
        float ageF = age + pt;
        float t = Mth.clamp(ageF / lifetime, 0F, 1F);
        Vec3 c = center;
        Vec3 n = normal;
        Entity e = follow >= 0 && !lost ? level.getEntity(follow) : null;
        if (e != null) {
            c = anchor(e, pt);
            if (mode == SpellFxParticles.MODE_FRONT) n = e.getViewVector(pt);
        }
        if (mode == SpellFxParticles.MODE_GROUND || mode == SpellFxParticles.MODE_SKY) n = new Vec3(0, 1, 0);

        float r;
        float a;
        if (shock) {
            float ease = 1F - (1F - t) * (1F - t) * (1F - t);
            r = radius * (0.15F + 0.85F * ease);
            a = baseAlpha * (1F - t) * (1F - t * 0.5F);
        } else if (release) {
            float ease = 1F - (1F - t) * (1F - t);
            r = radius * (0.75F + 0.5F * ease);
            a = baseAlpha * (t < 0.08F ? t / 0.08F : (float) Math.pow(1F - (t - 0.08F) / 0.92F, 1.3));
        } else {
            float open = Mth.clamp(ageF / 6F, 0F, 1F);
            open = 1F - (1F - open) * (1F - open);
            r = radius * (0.55F + 0.45F * open) * (1F + 0.03F * Mth.sin(ageF * 0.25F));
            float out = Mth.clamp((lifetime - ageF) / 8F, 0F, 1F);
            a = baseAlpha * open * out;
        }
        if (e != null && mode == SpellFxParticles.MODE_FRONT && isFirstPersonSelf(e)) a *= 0.4F;
        if (a <= 0.005F) return;

        Vec3 cam = camera.getPosition();
        float cx = (float) (c.x - cam.x), cy = (float) (c.y - cam.y), cz = (float) (c.z - cam.z);
        Vec3[] uv = FxDraw.basis(n);
        float ang = angle0 + angleOffset + ageF * spinSpeed * (reverse ? -1F : 1F);
        Vec3[] ax = FxDraw.spin(uv[0], uv[1], shock ? 0F : ang, r);
        int col = additive ? FxDraw.lighten(rgb, 0.2F) : rgb;
        FxDraw.quad(vc, cx, cy, cz, ax[0], ax[1], sprite, FxDraw.r(col), FxDraw.g(col), FxDraw.b(col), a, FxRenderTypes.FULL_BRIGHT);

        if (symbol != null) {
            // Símbolo no centro: não gira. No chão fica "de pé" para quem conjura (topo para onde ele olha);
            // na frente da mão fica de pé no plano do círculo.
            Vec3 right = uv[0], up = uv[1];
            if (mode == SpellFxParticles.MODE_GROUND || mode == SpellFxParticles.MODE_SKY) {
                Vec3 f = e != null ? e.getViewVector(pt).multiply(1, 0, 1) : Vec3.ZERO;
                f = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
                up = f;
                right = new Vec3(-f.z, 0, f.x);
            }
            float sr = r * 0.34F;
            int sc = FxDraw.lighten(col, 0.25F);
            float lift = mode == SpellFxParticles.MODE_GROUND ? 0.004F : 0F;
            FxDraw.quad(vc, cx, cy + lift, cz, right.scale(sr), up.scale(sr), symbol, FxDraw.r(sc), FxDraw.g(sc), FxDraw.b(sc),
                    Math.min(1F, a * 1.15F), FxRenderTypes.FULL_BRIGHT);
        }
    }

    @Override
    public AABB getRenderBoundingBox(float pt) {
        double r = radius * 1.3 + 1.0;
        return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return additive ? FxRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Esquece os círculos vivos (troca de mundo/saída). */
    static void clear() {
        LIVE.clear();
    }

    /** Remove entradas mortas (chamado de vez em quando). */
    static void prune() {
        for (Iterator<FlatFxParticle> it = LIVE.values().iterator(); it.hasNext();) {
            if (!it.next().isAlive()) it.remove();
        }
    }
}
