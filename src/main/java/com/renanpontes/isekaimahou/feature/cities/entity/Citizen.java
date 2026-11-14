package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.CityCrimes;
import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.CityRoutine;
import com.renanpontes.isekaimahou.feature.cities.CitySiege;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cidadão: anda pela cidade, conversa, conta rumores, foge de monstros e de noite fica em casa.
 * Release 1.0 (rotina simples): um em cada três passa o começo da noite na taverna (perto do estalajadeiro) antes de
 * ir para casa.
 * v1.1 ("mundo vivo"): cerca de um em cada seis cidadãos que nascem é uma <b>criança</b> — menor (atributo de escala),
 * corre pelas ruas brincando de dia, tem falas próprias e não vai à taverna. Moradores salvos antes disso continuam
 * adultos; quem vem de um encontro na estrada nunca é criança.
 * 10 variantes (pele, cabelo, roupa); a tabela {@link #LOOKS} bate com a ordem do tools/gen/cities.py.
 */
public class Citizen extends CityNpc {
    /** Por variante: feminina?, barba, bolsa, boné. */
    private static final boolean[][] LOOKS = {
            {false, false, false, true},
            {true, false, false, false},
            {false, true, false, false},
            {true, false, true, false},
            {false, false, true, false},
            {true, false, false, true},
            {false, true, false, true},
            {true, false, false, false},
            {false, false, false, false},
            {true, false, true, false},
    };

    private static final int TAVERN_LINES = 4;
    /** Onde fica a taverna (casa do estalajadeiro mais perto); procurada uma vez por dia. Só em memória. */
    @Nullable private BlockPos tavern;
    private long tavernDay = -1;

    /** Criança (v1.1)? Decidido uma vez, no primeiro tick de um morador NOVO; salvo no NBT. */
    private boolean child;
    private boolean childDecided;
    private static final int KID_LINES = 6;
    private static final ResourceLocation CHILD_SCALE = IsekaiMahou.id("city_child_scale");
    private static final ResourceLocation CHILD_SPEED = IsekaiMahou.id("city_child_speed");

    public Citizen(EntityType<? extends Citizen> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new PanicGoal(this, 1.35));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Mob.class, Citizen::isMonster, 10.0F, 0.8, 1.2, e -> true));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, this::isFeared, 8.0F, 0.75, 1.1, e -> true));
        goalSelector.addGoal(5, new KidPlayGoal());
    }

    public boolean isChild() {
        return child;
    }

    /** Vira criança: menor e um pouco mais rápida (modificadores permanentes, salvos com o morador). */
    private void becomeChild() {
        child = true;
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.addOrReplacePermanentModifier(new AttributeModifier(CHILD_SCALE, -0.38,
                AttributeModifier.Operation.ADD_VALUE));
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrReplacePermanentModifier(new AttributeModifier(CHILD_SPEED, 0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /**
     * Brincadeira de criança: de dia, de vez em quando dispara correndo até um ponto perto (dentro do raio de casa).
     * Só pega o caminho uma vez por corrida — custo de um passeio comum.
     */
    private final class KidPlayGoal extends Goal {
        KidPlayGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!child || getTarget() != null || random.nextInt(50) != 0) return false;
            return CityRoutine.phase(level()) == CityRoutine.Phase.DAY && !CitySiege.active(cityId());
        }

        @Override
        public void start() {
            Vec3 to = DefaultRandomPos.getPos(Citizen.this, 9, 3);
            if (to != null) getNavigation().moveTo(to.x, to.y, to.z, 1.15);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }

    static boolean isMonster(LivingEntity e) {
        // Invocações/companheiros de jogadores (com dono) não assustam ninguém.
        return e instanceof Enemy && !(e instanceof CityNpc)
                && !(e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerUUID() != null);
    }

    /** Civis evitam quem está Procurado NO REINO DELES e quem está em guerra com a cidade deles (fogem). */
    boolean isFeared(LivingEntity e) {
        return e instanceof Player p && !p.isCreative() && (CityCrimes.isWanted(p, kingdomFaction()) || isWarEnemy(p));
    }

    @Override
    public String kindId() {
        return "citizen";
    }

    @Override
    protected int variantCount() {
        return LOOKS.length;
    }

    @Override
    public boolean female() {
        return LOOKS[Math.floorMod(variant(), LOOKS.length)][0];
    }

    @Override
    public Set<String> visibleBones() {
        boolean[] l = LOOKS[Math.floorMod(variant(), LOOKS.length)];
        Set<String> s = new HashSet<>();
        if (l[0]) {
            s.add("hair_long");
            s.add("skirt");
        }
        if (l[1]) s.add("beard");
        if (l[2]) s.add("satchel");
        if (l[3]) s.add("cap");
        return s;
    }

    @Override
    protected int wanderRadius() {
        return 22;
    }

    @Override
    protected int currentRadius() {
        if (atTavern()) return 5;
        // De noite (e no cerco) os cidadãos voltam para casa.
        return level().isNight() ? 3 : wanderRadius();
    }

    @Override
    protected BlockPos currentCenter() {
        return atTavern() && tavern != null ? tavern : super.currentCenter();
    }

    /** Um em cada três cidadãos gosta da taverna (fixo por morador). Criança não vai. */
    private boolean tavernGoer() {
        return !child && Math.floorMod(getUUID().hashCode(), 3) == 0;
    }

    /** Começo da noite, a taverna é conhecida e a cidade não está sob cerco. */
    private boolean atTavern() {
        return tavern != null && CityRoutine.phase(level()) == CityRoutine.Phase.EVENING && !CitySiege.active(cityId());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!childDecided && !level().isClientSide) {
            childDecided = true;
            // morador novo (de cidade/coração/cidade de jogador): 1 em 6 é criança; encontro na estrada, nunca
            if (!getPersistentData().getBoolean("isekai_mahou.encounter") && random.nextInt(6) == 0) becomeChild();
        }
        // Uma busca por dia, no começo da noite, só para quem vai à taverna (espalhada pelos ticks).
        if (level().isClientSide || tickCount % 100 != Math.floorMod(getId(), 100) || !tavernGoer()) return;
        if (CityRoutine.phase(level()) != CityRoutine.Phase.EVENING) return;
        long day = level().getDayTime() / 24000L;
        if (day == tavernDay) return;
        tavernDay = day;
        tavern = findTavern();
    }

    @Nullable
    private BlockPos findTavern() {
        BlockPos from = home() != null ? home() : blockPosition();
        CityMerchant best = null;
        for (CityMerchant m : level().getEntitiesOfClass(CityMerchant.class, new AABB(from).inflate(56, 16, 56),
                m -> m.isAlive() && m.profession() == Profession.INNKEEPER)) {
            if (best == null || m.distanceToSqr(this) < best.distanceToSqr(this)) best = m;
        }
        if (best == null) return null;
        return best.home() != null ? best.home() : best.blockPosition();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Child", child);
        tag.putBoolean("ChildSet", childDecided);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        child = tag.getBoolean("Child");
        // Salvo antes da v1.1 (tem as chaves do morador, mas não "ChildSet"): continua adulto — ninguém "encolhe" num
        // mundo antigo. Morador saído do molde (.nbt da cidade, sem essas chaves) ainda sorteia no primeiro tick.
        childDecided = tag.contains("ChildSet") ? tag.getBoolean("ChildSet") : tag.contains("CityBorn");
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        if (child && !isWarEnemy(near) && !isFeared(near)) {
            return Component.translatable("dialogue.isekai_mahou.cities.kid." + random.nextInt(KID_LINES));
        }
        if (isWarEnemy(near)) return CityLines.pick("citizen.enemy", random, kingdomName());
        if (isFeared(near)) return CityLines.pick("citizen.wanted", random);
        if (atTavern()) return CityLines.line("citizen.tavern." + random.nextInt(TAVERN_LINES));
        if (!level().isNight() && random.nextInt(100) < 30) return CityLines.kingdomFlavor(kingdom(), random);
        return CityLines.pick(level().isNight() ? "citizen.night" : "citizen.ambient", random, kingdomName());
    }
}
