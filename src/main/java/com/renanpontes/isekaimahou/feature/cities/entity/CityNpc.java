package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.CityData;
import com.renanpontes.isekaimahou.feature.cities.CityDialogue;
import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.CityPlaces;
import com.renanpontes.isekaimahou.feature.cities.CityRealm;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos NPCs da cidade (GeckoLib, humanoides): guarda, cidadão, mercador e lorde.
 * <ul>
 *   <li>Aparência: uma variante de textura (tom de pele, cabelo, roupa) + peças do modelo por tipo/profissão.</li>
 *   <li>Casa: a posição onde nasceram; passeiam num raio e voltam (de noite os civis ficam em casa).</li>
 *   <li>Cidade: os que nasceram com a estrutura ficam ligados à cidade ({@link CityData}); se morrerem, o coração da
 *       cidade traz um substituto por dia de jogo.</li>
 *   <li>Fala: balão sobre a cabeça (texto sincronizado) e a tela de diálogo ao clicar.</li>
 * </ul>
 */
public abstract class CityNpc extends PathfinderMob implements GeoEntity, Reputation.FactionMember {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Component> DATA_SPEECH = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Integer> DATA_SPEECH_AT = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.INT);
    /** Reino da cidade do NPC ("aldoria", "sahra", "nordheim"; "" = ainda não sabe → Aldória/tag). Vai ao cliente (textura, Olho Analítico). */
    private static final EntityDataAccessor<String> DATA_KINGDOM = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.STRING);
    /** Ícone de alerta sobre a cabeça (v1.0): 0 nada, {@link #ALERT_SUSPICIOUS} "?" ou {@link #ALERT_CONFRONT} "!". */
    private static final EntityDataAccessor<Integer> DATA_ALERT = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.INT);
    public static final int ALERT_NONE = 0, ALERT_SUSPICIOUS = 1, ALERT_CONFRONT = 2;
    /** Duração do balão de fala (ticks). */
    public static final int SPEECH_TICKS = 110;
    public static final int MALE_NAMES = 28;
    public static final int FEMALE_NAMES = 28;
    /** Nomes próprios de Sahra e de Nordheim (por gênero). */
    public static final int KINGDOM_NAMES = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos home;
    private String cityId = "";
    /** Nasceu com a estrutura da cidade (template) — só esses contam para o repovoamento. */
    private boolean cityBorn;
    private boolean linked;
    private boolean variantSet;
    private int linkTries;
    private boolean kingdomResolved;
    private int kingdomTries;
    /** O nome veio do sorteio (não de etiqueta de jogador): pode trocar pelo nome do reino ao descobrir a cidade. */
    private boolean autoNamed;
    @Nullable private Player lookTarget;
    private int lookTicks;
    private int chatterCooldown = 200 + (int) (Math.random() * 400);
    /** Tempo do mundo em que o ícone de alerta some (servidor). */
    private long alertUntil;
    /** Cidade (id de Places) para a guerra de cidades, descoberta pela casa quando o NPC não foi ligado (cache). */
    @Nullable private String warCity;
    private long warCityCheckedAt = Long.MIN_VALUE;
    /** Quantas vezes o bolso deste morador já foi furtado (marca do Ladino) — −1 = ainda não olhou. */
    private int robbedSeen = -1;
    private static final String ROBBED_TAG = "isekai_mahou.rogue_robbed";

    protected CityNpc(EntityType<? extends CityNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
    }

    // =====================================================================================
    //  Tipo
    // =====================================================================================
    /** "guard", "citizen", "merchant" ou "lord" (texturas, falas, contagens). */
    public abstract String kindId();

    /** Quantas variantes de textura existem para este tipo. */
    protected abstract int variantCount();

    /** A variante é feminina (nome, cabelo longo, vestido)? */
    public abstract boolean female();

    /** Peças opcionais do modelo visíveis (ver tools/gen/cities.py). */
    public abstract Set<String> visibleBones();

    /** Nome da textura em textures/entity/city_npc/. */
    public String textureName() {
        return kindId() + "_" + variant();
    }

    /** Chave usada nas contagens da cidade (repovoamento). */
    public String censusKey() {
        return kindId();
    }

    /** Raio de passeio em volta da casa. */
    protected int wanderRadius() {
        return 16;
    }

    /** Intervalo médio entre passeios (ticks). */
    protected int strollInterval() {
        return 120;
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    protected void setVariant(int v) {
        entityData.set(DATA_VARIANT, Math.floorMod(v, Math.max(1, variantCount())));
        variantSet = true;
    }

    @Override
    @Nullable
    public String factionId() {
        // Reino ainda não descoberto: a tag isekai_mahou:faction/kingdom (Aldória, como na v0.8).
        return kingdomSet() ? kingdom().factionId : null;
    }

    /** Facção efetiva (para a interface e o comércio). */
    public String faction() {
        String f = factionId();
        return f != null && !f.isEmpty() ? f : kingdomFaction();
    }

    // =====================================================================================
    //  Reino (v0.9)
    // =====================================================================================
    /** Reino da cidade do NPC (Aldória enquanto não souber). Funciona no cliente. */
    public CityKingdom kingdom() {
        return CityKingdom.byId(entityData.get(DATA_KINGDOM));
    }

    /** O NPC já sabe de que reino é? (cliente: o valor sincronizado) */
    public boolean kingdomSet() {
        return !entityData.get(DATA_KINGDOM).isEmpty();
    }

    /** Facção do REINO da cidade (mesmo para o escriba/sacerdote/receptador): crimes, serviços, guardas. */
    public String kingdomFaction() {
        return kingdom().factionId;
    }

    /** Nome curto do reino ("Aldória", "Sahra", "Nordheim") — argumento das falas. */
    public Component kingdomName() {
        return CityRealm.shortName(kingdom());
    }

    /** Passa a pertencer ao reino (a cidade disse). Quem tinha nome sorteado ganha um nome da terra (Sahra/Nordheim). */
    public void joinKingdom(CityKingdom k) {
        boolean changed = !k.id.equals(entityData.get(DATA_KINGDOM));
        entityData.set(DATA_KINGDOM, k.id);
        kingdomResolved = true;
        if (changed && autoNamed && k != CityKingdom.ALDORIA) setCustomName(randomName(k, getRandom()));
    }

    /** Nome sorteado da lista do reino. */
    protected MutableComponent randomName(CityKingdom k, RandomSource r) {
        String g = female() ? "f." : "m.";
        if (k == CityKingdom.ALDORIA) {
            return CityLines.tr("npc.isekai_mahou.cities.name." + g + r.nextInt(female() ? FEMALE_NAMES : MALE_NAMES));
        }
        return CityLines.tr("npc.isekai_mahou.cities.name." + k.id + "." + g + r.nextInt(KINGDOM_NAMES));
    }

    /**
     * Descobre o reino pela cidade onde mora: pergunta ao coração da cidade (o centro do lugar). Se o coração não estiver
     * carregado, tenta de novo depois; sem jeito (3 min), usa a facção gravada no lugar.
     * <p>Sem cidade registrada ainda (o coração de uma capital grande pode estar longe, fora da distância de simulação,
     * enquanto o jogador fica do lado de fora da muralha): NÃO desiste — depois dos primeiros 3 min passa a perguntar a
     * cada 30 s (ver {@link #aiStep}). Um morador da muralha de Sahra não pode virar de Aldória para sempre.
     */
    private void resolveKingdom(ServerLevel sl) {
        if (kingdomTries < 90) kingdomTries++;
        Places.Place p = CityPlaces.cityAt(sl, home != null ? home : blockPosition());
        if (p == null) return;   // fora de cidade (ovo no mato) ou cidade ainda não registrada: fica de Aldória/tag por ora
        if (sl.isLoaded(p.center()) && sl.getBlockEntity(p.center()) instanceof CityHeartBlockEntity heart) {
            joinKingdom(heart.kingdom());
        } else if (kingdomTries >= 90) {
            CityKingdom k = CityRealm.byFaction(p.faction());
            joinKingdom(k != null ? k : CityKingdom.ALDORIA);
        }
    }

    // =====================================================================================
    //  IA
    // =====================================================================================
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.7));
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(this, 0.55);
        stroll.setInterval(strollInterval());
        goalSelector.addGoal(6, stroll);
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, CityNpc.class, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_SPEECH, Component.empty());
        builder.define(DATA_SPEECH_AT, Integer.MIN_VALUE / 2);
        builder.define(DATA_KINGDOM, "");
        builder.define(DATA_ALERT, ALERT_NONE);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.STRUCTURE) cityBorn = true;
        randomizeIfNeeded(level.getRandom());
        setPersistenceRequired();
        return out;
    }

    /** Sorteia aparência e nome (uma vez). Subclasses estendem (profissão etc.). */
    protected void randomizeIfNeeded(RandomSource r) {
        if (!variantSet) setVariant(r.nextInt(Math.max(1, variantCount())));
        if (!hasCustomName()) {
            setCustomName(randomName(kingdom(), r));
            autoNamed = true;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (!variantSet || !hasCustomName()) randomizeIfNeeded(random);
        if (home == null) home = blockPosition();
        int radius = currentRadius();
        BlockPos center = currentCenter();
        if (!hasRestriction() || !getRestrictCenter().equals(center) || getRestrictRadius() != radius) restrictTo(center, radius);
        if (cityBorn && !linked && tickCount % 40 == 7 && linkTries < 90) {
            linkTries++;
            tryLink(sl);
        }
        if (!kingdomResolved && tickCount % (kingdomTries >= 90 ? 600 : 40) == 19) resolveKingdom(sl);
        if (tickCount % 10 == 3) checkRobbed(sl);
        if (alertUntil != 0 && sl.getGameTime() >= alertUntil) {
            alertUntil = 0;
            entityData.set(DATA_ALERT, ALERT_NONE);
        }
        if (lookTicks > 0) {
            lookTicks--;
            if (lookTarget != null && lookTarget.isAlive() && distanceToSqr(lookTarget) < 100) {
                getLookControl().setLookAt(lookTarget, 30.0F, 30.0F);
                if (getTarget() == null) getNavigation().stop();
            } else {
                lookTicks = 0;
            }
        }
        if (--chatterCooldown <= 0) {
            chatterCooldown = 500 + random.nextInt(700);
            Player near = level().getNearestPlayer(this, 7.0);
            if (near != null && !near.isSpectator() && !isSpeaking() && getTarget() == null) {
                // Quem tem um pedido hoje às vezes chama o jogador (release 1.0, sidequests).
                Component line = com.renanpontes.isekaimahou.feature.cities.CityQuests.hook(this, near);
                if (line == null) line = ambientLine(near);
                if (line != null) say(line);
            }
        }
    }

    /**
     * Furto do Ladino (marca {@code isekai_mahou.rogue_robbed[.n]} posta pelo módulo class_rogue, lida só pelo texto):
     * quando o bolso fica mais leve, o morador olha em volta e, se viu o ladrão, chama a guarda (v1.0).
     */
    private void checkRobbed(ServerLevel sl) {
        int n = 0;
        for (String t : getTags()) {
            if (t.equals(ROBBED_TAG)) n = Math.max(n, 1);
            else if (t.startsWith(ROBBED_TAG + ".")) {
                try {
                    n = Math.max(n, Integer.parseInt(t.substring(ROBBED_TAG.length() + 1)));
                } catch (NumberFormatException e) {
                    n = Math.max(n, 1);
                }
            }
        }
        if (robbedSeen < 0 || n <= robbedSeen) {
            robbedSeen = n;
            return;
        }
        robbedSeen = n;
        ServerPlayer thief = null;
        double best = 12.0 * 12.0;
        for (ServerPlayer p : sl.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            double d = p.distanceToSqr(this);
            if (d < best) {
                best = d;
                thief = p;
            }
        }
        if (thief != null) com.renanpontes.isekaimahou.feature.cities.CityConfront.onRobbed(this, thief);
    }

    /** Raio de passeio agora (os civis encolhem para casa de noite). */
    protected int currentRadius() {
        return wanderRadius();
    }

    /** Centro do passeio agora: a casa (no começo da noite parte dos cidadãos troca pela taverna). Só no servidor. */
    protected BlockPos currentCenter() {
        return home != null ? home : blockPosition();
    }

    private void tryLink(ServerLevel sl) {
        Places.Place p = CityPlaces.cityAt(sl, home != null ? home : blockPosition());
        if (p == null) return;
        cityId = p.id();
        linked = true;
        CityData.get(sl).registerNpc(cityId, censusKey());
    }

    /** Liga um NPC trazido pelo coração da cidade (já contado). */
    public void bindToCity(String id, BlockPos homePos) {
        cityId = id;
        linked = true;
        cityBorn = true;
        setHome(homePos);
    }

    public String cityId() {
        return cityId;
    }

    // =====================================================================================
    //  Alerta e guerra de cidades (v1.0)
    // =====================================================================================
    /** Mostra o ícone de alerta ("?" desconfiado, "!" abordando) por {@code ticks} (servidor). */
    public void alert(int kind, int ticks) {
        if (level().isClientSide) return;
        entityData.set(DATA_ALERT, kind);
        alertUntil = kind == ALERT_NONE ? 0 : level().getGameTime() + Math.max(1, ticks);
    }

    /** Ícone de alerta atual (funciona no cliente). */
    public int alertKind() {
        return entityData.get(DATA_ALERT);
    }

    /**
     * Cidade do NPC para a guerra ({@code Allegiance}): a cidade ligada; senão a cidade onde fica a casa dele (cache
     * de 30 s). null = fora de cidade.
     */
    @Nullable
    public String warCity() {
        if (!cityId.isEmpty()) return cityId;
        if (!(level() instanceof ServerLevel sl)) return null;
        long now = sl.getGameTime();
        if (now - warCityCheckedAt > 600 || now < warCityCheckedAt) {
            warCityCheckedAt = now;
            Places.Place p = CityPlaces.cityAt(sl, home != null ? home : blockPosition());
            warCity = p != null ? p.id() : null;
        }
        return warCity;
    }

    /**
     * O jogador está em guerra com a cidade deste NPC (cidade declarada inimiga, docs/v10/API_social.md §4)? Guardas
     * atacam, civis fogem e ninguém negocia. Sempre false no cliente.
     */
    public boolean isWarEnemy(@Nullable Player p) {
        if (!(p instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return false;
        String city = warCity();
        return city != null && Allegiance.isEnemyOf(sp, city);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, currentRadius());
    }

    @Override
    public void die(DamageSource source) {
        if (!dead && linked && !cityId.isEmpty() && level() instanceof ServerLevel sl) {
            CityData.get(sl).recordDeath(cityId, censusKey(), home != null ? home : blockPosition(), sl.getDayTime() / 24000L);
        }
        super.die(source);
    }

    // =====================================================================================
    //  Fala e conversa
    // =====================================================================================
    /** Fala solta perto de jogadores (null = nada). */
    @Nullable
    protected abstract Component ambientLine(Player near);

    public void say(Component text) {
        entityData.set(DATA_SPEECH, text);
        entityData.set(DATA_SPEECH_AT, (int) level().getGameTime());
        chatterCooldown = Math.max(chatterCooldown, 300 + random.nextInt(300));
    }

    public float speechAge(float partialTick) {
        return (float) ((int) level().getGameTime() - entityData.get(DATA_SPEECH_AT)) + partialTick;
    }

    public boolean isSpeaking() {
        return speechAge(0) < SPEECH_TICKS && !entityData.get(DATA_SPEECH).getString().isEmpty();
    }

    @Nullable
    public Component speech() {
        return isSpeaking() ? entityData.get(DATA_SPEECH) : null;
    }

    /** Vira para o jogador por alguns segundos (conversa). */
    public void faceFor(Player player, int ticks) {
        lookTarget = player;
        lookTicks = ticks;
        if (getTarget() == null) getNavigation().stop();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.NAME_TAG)) {
            autoNamed = false;   // batizado pelo jogador: o nome fica
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            if (getTarget() == player) {
                // Em combate com o jogador: sem conversa (o guarda aceita fiança mesmo assim).
                if (!(this instanceof CityGuard)) return InteractionResult.CONSUME;
            }
            faceFor(player, 120);
            CityDialogue.open(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Animação disparável no controlador "action" (wave, attack, cheer, bow, scold, halt). */
    public void playAction(String name) {
        triggerAnim("action", name);
    }

    // =====================================================================================
    //  Regras gerais
    // =====================================================================================
    /** Moradores não são arrastados de casa com uma guia. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** NPCs da cidade não brigam entre si. */
    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        if (other instanceof CityNpc) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof CityNpc) && super.canAttack(target);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("CityHome", NbtUtils.writeBlockPos(home));
        tag.putString("CityId", cityId);
        tag.putBoolean("CityBorn", cityBorn);
        tag.putBoolean("CityLinked", linked);
        tag.putInt("Variant", variant());
        tag.putBoolean("VariantSet", variantSet);
        tag.putString("Kingdom", entityData.get(DATA_KINGDOM));
        tag.putBoolean("KingdomSet", kingdomResolved);
        tag.putBoolean("AutoNamed", autoNamed);
        tag.putInt("RobbedSeen", robbedSeen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "CityHome").ifPresent(this::setHome);
        cityId = tag.getString("CityId");
        cityBorn = tag.getBoolean("CityBorn");
        linked = tag.getBoolean("CityLinked");
        entityData.set(DATA_KINGDOM, tag.getString("Kingdom"));
        kingdomResolved = tag.getBoolean("KingdomSet");
        autoNamed = tag.getBoolean("AutoNamed");   // moradores da v0.8 não têm: o nome que já tinham fica
        robbedSeen = tag.contains("RobbedSeen") ? tag.getInt("RobbedSeen") : -1;
        if (tag.contains("Variant")) {
            entityData.set(DATA_VARIANT, Math.floorMod(tag.getInt("Variant"), Math.max(1, variantCount())));
            variantSet = tag.getBoolean("VariantSet") || tag.contains("Variant");
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    protected static final String ANIM = "animation.city_npc.";

    protected static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop(ANIM + name);
    }

    protected static RawAnimation once(String name) {
        return RawAnimation.begin().thenPlay(ANIM + name);
    }

    /** Animação parada: subclasses podem trocar (ex.: ferreiro martelando). */
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation TALK = loop("talk");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CityNpc>(this, "main", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.012 ? RUN : WALK);
            }
            if (isSpeaking()) return state.setAndContinue(TALK);
            return state.setAndContinue(idleAnimation());
        }));
        AnimationController<CityNpc> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        action.triggerableAnim("wave", once("wave"));
        action.triggerableAnim("attack", once("attack"));
        action.triggerableAnim("cheer", once("cheer"));
        action.triggerableAnim("bow", once("bow"));
        action.triggerableAnim("scold", once("scold"));
        action.triggerableAnim("halt", once("halt"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
