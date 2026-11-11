package com.renanpontes.isekaimahou.feature.bosses_a;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ElementalGuardian;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Magias e dano dos Guardiões. Os chefes conjuram receitas de glifos por id (de outros módulos);
 * se algum glifo não existir, tentam a próxima receita e, por fim, "projectile arcane_damage" (do núcleo).
 * Ataques próprios causam dano mágico pelo mesmo caminho do núcleo (reações elementais, resistências).
 */
public final class BossSpells {
    /** Última opção: sempre existe (glifos do núcleo). */
    public static final String FALLBACK = "projectile arcane_damage";
    private static final SpellRecipe TOUCH = recipeOf("touch arcane_damage");
    /** Cache: texto da receita → receita válida (ou vazia se faltar glifo). */
    private static final Map<String, Optional<SpellRecipe>> CACHE = new ConcurrentHashMap<>();

    /**
     * Conjura a primeira receita válida (glifos separados por espaço), mirando no alvo.
     * @return true se conjurou
     */
    public static boolean cast(Mob caster, Element element, LivingEntity target, float power, String... recipes) {
        if (target != null) aim(caster, target);
        List<String> options = new ArrayList<>(List.of(recipes));
        options.add(FALLBACK);
        for (String text : options) {
            Optional<SpellRecipe> recipe = CACHE.computeIfAbsent(text, BossSpells::resolve);
            if (recipe.isPresent() && SpellCaster.castAsMob(caster, element, recipe.get(), power) != null) return true;
        }
        return false;
    }

    private static Optional<SpellRecipe> resolve(String text) {
        SpellRecipe r = recipeOf(text);
        for (ResourceLocation id : r.glyphs()) {
            if (GlyphRegistry.get(id) == null) return Optional.empty();
        }
        return SpellParser.parse(r, null, null).ok() ? Optional.of(r) : Optional.empty();
    }

    private static SpellRecipe recipeOf(String text) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String s : text.trim().split("\\s+")) if (!s.isEmpty()) ids.add(IsekaiMahou.id(s));
        return new SpellRecipe(ids, "", -1, "");
    }

    /** Vira o conjurador (corpo, cabeça e olhar) para o centro do alvo — a forma Projétil usa a rotação. */
    public static void aim(Mob caster, Entity target) {
        Vec3 from = new Vec3(caster.getX(), caster.getEyeY() - 0.15, caster.getZ());
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 d = to.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG));
        caster.setYRot(yaw);
        caster.setXRot(pitch);
        caster.yHeadRot = yaw;
        caster.yBodyRot = yaw;
    }

    /** Vira só o corpo (horizontal) para um ponto. */
    public static void face(Mob mob, Vec3 point) {
        double dx = point.x - mob.getX(), dz = point.z - mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        setYaw(mob, yawOf(new Vec3(dx, 0, dz)));
    }

    /** Gira corpo, cabeça e rotação para o ângulo dado (graus, padrão do Minecraft). */
    public static void setYaw(Mob mob, float yaw) {
        mob.setYRot(yaw);
        mob.yHeadRot = yaw;
        mob.yBodyRot = yaw;
    }

    /** Ângulo (graus, padrão do Minecraft: 0 = sul/+Z, 90 = oeste) de uma direção horizontal. */
    public static float yawOf(Vec3 dir) {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Direção horizontal unitária de um ângulo (graus, padrão do Minecraft). */
    public static Vec3 dirOf(float yawDeg) {
        return Vec3.directionFromRotation(0, yawDeg);
    }

    /** Direção horizontal (unitária) de {@code from} até {@code to}; se coincidirem, devolve {@code fallback}. */
    public static Vec3 flatDir(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1.0E-4 ? fallback : d.normalize();
    }

    /**
     * O ponto no chão logo abaixo/perto de {@code p} (para telegrafias e impactos). Se {@code p} estiver dentro do
     * terreno (morro, degrau) sobe até 4 blocos até achar ar; depois desce até o primeiro chão. Nunca "gruda" no
     * teto de uma sala baixa.
     */
    public static Vec3 ground(Level level, Vec3 p) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y + 0.5), Mth.floor(p.z));
        for (int i = 0; i < 4 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) pos.move(Direction.UP);
        double y = groundY(level, p.x, pos.getY(), p.z, 0, 12);
        return new Vec3(p.x, y, p.z);
    }

    /**
     * Até onde dá para avançar em linha reta pela altura do peito antes de bater numa parede (para investidas e
     * linhas que não devem atravessar paredes).
     */
    public static double clearDistance(Level level, Vec3 from, Vec3 dir, double max, double chest) {
        Vec3 a = from.add(0, chest, 0);
        HitResult r = level.clip(new ClipContext(a, a.add(dir.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return r.getType() == HitResult.Type.MISS ? max : Math.max(0, r.getLocation().distanceTo(a) - 0.5);
    }

    /**
     * Dano mágico de um elemento causado pelo chefe (ataques próprios): passa por {@link SpellContext#damage},
     * então vale Encharcado+Raio, fraquezas, Exposto etc.
     */
    public static float hit(LivingEntity caster, Entity target, Element element, float amount) {
        if (caster == null || !(caster.level() instanceof ServerLevel level)) return 0F;
        SpellParser.Result r = SpellParser.parse(TOUCH, null, null);
        if (!r.ok()) {
            return target.hurt(caster.damageSources().mobAttack(caster), amount) ? amount : 0F;
        }
        SpellContext ctx = new SpellContext(level, caster, element, TOUCH, r.spell());
        return ctx.damage(target, amount, new SpellStats(), null, element);
    }

    /**
     * Quem os ataques em área do chefe atingem: jogadores (fora do criativo), quem luta contra ele e bichos de jogadores.
     * Outros chefes da mesma Câmara de Provação (tag de provação) nunca são atingidos.
     */
    public static boolean isFoe(Entity boss, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == boss || e.isSpectator()) return false;
        if (e instanceof Player p) return !p.isCreative();
        if (e instanceof ElementalGuardian || BossKit.isTrialBoss(e)) return false;
        if (e instanceof Mob m && m.getTarget() == boss) return true;
        return e instanceof OwnableEntity own && own.getOwnerUUID() != null;
    }

    /** Inimigos do chefe numa esfera. */
    public static List<LivingEntity> foesAround(Entity boss, Vec3 center, double radius) {
        return boss.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> isFoe(boss, e) && e.distanceToSqr(center) <= radius * radius);
    }

    /** Empurra a entidade (e avisa o cliente se for jogador). */
    public static void knock(Entity e, Vec3 dir, double strength, double lift) {
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize().scale(strength);
        double kbRes = e instanceof LivingEntity le
                ? le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) : 0;
        double f = 1.0 - Mth.clamp(kbRes, 0.0, 1.0) * 0.6;
        e.push(h.x * f, lift * f, h.z * f);
        e.hurtMarked = true;
    }

    /**
     * Altura do chão numa coluna (topo do primeiro bloco com colisão, de {@code y + up} até {@code y - down}).
     * Retorna {@code y} se não achar.
     */
    public static double groundY(BlockGetter level, double x, double y, double z, int up, int down) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), top = Mth.floor(y) + up;
        for (int by = top; by >= Mth.floor(y) - down; by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return by + shape.max(Direction.Axis.Y);
            if (!state.getFluidState().isEmpty() && level.getFluidState(pos.above()).isEmpty()) {
                return by + state.getFluidState().getHeight(level, pos);
            }
        }
        return y;
    }

    /** Altura dos pés da entidade acima do chão/líquido logo abaixo (até {@code max}; devolve {@code max} se não achar). */
    public static double heightAboveGround(Level level, Entity e, int max) {
        if (e.onGround()) return 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(e.getX()), bz = Mth.floor(e.getZ()), by0 = Mth.floor(e.getY());
        for (int by = by0; by >= by0 - max; by--) {
            pos.set(bx, by, bz);
            BlockState s = level.getBlockState(pos);
            if (!s.getFluidState().isEmpty()) return Math.max(0, e.getY() - (by + s.getFluidState().getHeight(level, pos)));
            VoxelShape shape = s.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return Math.max(0, e.getY() - (by + shape.max(Direction.Axis.Y)));
        }
        return max;
    }

    /** Espaço livre acima de um ponto (até {@code max}), para não lançar coisas de dentro do teto. */
    public static double headroom(Level level, Vec3 from, double max) {
        HitResult r = level.clip(new ClipContext(from, from.add(0, max, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return r.getType() == HitResult.Type.MISS ? max : r.getLocation().y - from.y;
    }

    /** Bloco embaixo de um ponto (para partículas de terra), ou pedra. */
    public static BlockState groundState(BlockGetter level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p.x, p.y - 0.3, p.z);
        BlockState s = level.getBlockState(pos);
        return s.isAir() || !s.getFluidState().isEmpty()
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : s;
    }

    private BossSpells() {}
}
