package com.renanpontes.isekaimahou.feature.travel.block;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.travel.TravelContent;
import com.renanpontes.isekaimahou.feature.travel.TravelStones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.FakePlayer;

import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Dados da Pedra de Teleporte. Nas estruturas o bloco vem SEM NBT (o {@code onPlace} não roda na geração), então todo o
 * registro acontece aqui, no tick do servidor:
 * <ul>
 *   <li>pedra dentro de um lugar viajável (cidade, vila...) → vira a chegada dele ({@link Travel#registerStone});</li>
 *   <li>pedra solta → registra o próprio lugar {@link Travel#WAYSTONE_TYPE} (área da estrutura onde está, ou uma área
 *       pequena), com o nome dado na bigorna, o da estrutura, ou o do lugar mais próximo;</li>
 *   <li>se uma cidade for registrada depois em volta de uma pedra solta, a pedra passa a ser a chegada da cidade.</li>
 * </ul>
 * Pedra sem dono (estrutura, comando, criativo) não quebra em sobrevivência; a do jogador só o dono quebra.
 */
public class TravelStoneBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String TAG_PLACE = "Place", TAG_OWN = "OwnPlace", TAG_PLAYER = "PlayerMade", TAG_OWNER = "Owner",
            TAG_OWNER_NAME = "OwnerName", TAG_CUSTOM = "CustomName", TAG_DISPLAY = "Display", TAG_COLOR = "Color",
            TAG_ANCHOR = "Anchor";
    /** Distância (ao centro do bloco, 1 acima) que conta como "tocar" a pedra. */
    private static final double TOUCH_RANGE = 2.8;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.travel_stone.idle");
    private static final RawAnimation AWAKE = RawAnimation.begin().thenLoop("animation.travel_stone.awake");
    private static final RawAnimation FLARE = RawAnimation.begin().thenPlay("animation.travel_stone.flare");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private String placeId = "";
    private boolean ownPlace;
    private boolean playerMade;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    @Nullable
    private Component customName;
    @Nullable
    private Component display;
    private int color = TravelStones.WAYSTONE_RGB;
    @Nullable
    private BlockPos anchor;

    // Só em memória
    private int age;
    private int waited;
    /** Cliente: um jogador está perto (animação "desperta" e mais partículas). */
    private boolean awake;
    private boolean viewerNear;

    public TravelStoneBlockEntity(BlockPos pos, BlockState state) {
        super(TravelContent.TRAVEL_STONE_BE.get(), pos, state);
    }

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    public String placeId() {
        return placeId;
    }

    public boolean playerMade() {
        return playerMade;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean ownPlace() {
        return ownPlace;
    }

    @Nullable
    public Component customName() {
        return customName;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Nome mostrado (sincronizado com o cliente para o letreiro). */
    @Nullable
    public Component display() {
        return display;
    }

    public int color() {
        return color;
    }

    /** Pedra protegida: sem dono (estrutura/comando/criativo). */
    public boolean natural() {
        return !playerMade;
    }

    /** O jogador pode quebrar esta pedra em sobrevivência? */
    public boolean canBreak(UUID player) {
        return playerMade && (owner == null || owner.equals(player));
    }

    public boolean awake() {
        return awake;
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, TravelStoneBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.age++;
        // Sem lugar ainda, ou lugar próprio recém-criado (outro módulo pode registrar o da estrutura logo depois):
        // confere a cada 5 s; depois, a cada 30 s
        boolean early = be.placeId.isEmpty() || (be.ownPlace && be.age < 2400);
        if (be.age == 20 || (early && be.age % 100 == 0) || be.age % 600 == 0) be.resolve(sl, false);
        if (be.age % 10 == 5 && !be.placeId.isEmpty()) be.touchCheck(sl);
    }

    /** Colocada por um jogador (o nome da bigorna já veio pelos componentes do item). */
    public void placedBy(ServerLevel sl, ServerPlayer sp) {
        if (sp instanceof FakePlayer) {
            // Máquina de outro mod: pedra de jogador sem dono (qualquer um quebra; ninguém fica preso a ela)
            playerMade = true;
            resolve(sl, true);
            return;
        }
        playerMade = !sp.getAbilities().instabuild;
        if (playerMade) {
            owner = sp.getUUID();
            ownerName = sp.getGameProfile().getName();
        }
        resolve(sl, true);
        if (!placeId.isEmpty()) {
            Places.Place own = ownPlace ? Places.get(sl, placeId) : null;
            if (own != null && own.contains(sp.blockPosition())) {
                // O jogador está dentro do lugar novo: a entrada do núcleo (letreiro + "Novo destino") já avisa em
                // instantes; aqui só sintoniza, sem um segundo aviso igual
                MagicApi.get(sp).setFlag("travel." + placeId, true);
            } else {
                Travel.discover(sp, placeId);
            }
            TravelStones.raisedFx(sl, this, sp);
        }
    }

    /** Clique: sintoniza (1ª vez) ou abre a tela de viagem. */
    public void interact(ServerLevel sl, ServerPlayer sp) {
        // Sem lugar ainda, ou o lugar sumiu (removido por comando/outro módulo): resolve agora
        if (placeId.isEmpty() || Places.get(sl, placeId) == null) resolve(sl, true);
        if (placeId.isEmpty()) {
            TravelStones.waking(sp);
            return;
        }
        if (!Travel.discovered(sp, placeId)) {
            TravelStones.attune(sl, this, sp);
            return;
        }
        // Lugar já visitado (ex.: entrou na cidade) mas pedra nunca tocada: sintoniza junto e já abre a tela
        if (!TravelStones.attuned(sp, placeId)) TravelStones.attune(sl, this, sp);
        TravelStones.open(sl, this, sp);
    }

    /** Renomeia com uma Etiqueta (só a pedra com lugar próprio; o nome de uma cidade não muda). */
    public boolean rename(ServerLevel sl, Component name) {
        if (!ownPlace) return false;
        customName = name;
        resolve(sl, true); // o lugar próprio é registrado de novo com o nome novo
        return true;
    }

    /** Jogador a um passo da pedra = tocar: sintoniza. */
    private void touchCheck(ServerLevel sl) {
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 1.0, cz = worldPosition.getZ() + 0.5;
        for (ServerPlayer sp : sl.players()) {
            // Durante a entrada no jogo (classe, letreiro, tutorial) a pedra só responde ao clique
            if (sp.isSpectator() || sp instanceof FakePlayer || !sp.isAlive() || Onboarding.quiet(sp)) continue;
            if (sp.distanceToSqr(cx, cy, cz) > TOUCH_RANGE * TOUCH_RANGE) continue;
            // Pela flag da pedra (não só a de visita): quem entrou na cidade ainda vê a pedra responder ao 1º toque
            if (!TravelStones.attuned(sp, placeId)) TravelStones.attune(sl, this, sp);
        }
    }

    /**
     * Acha (ou cria) o lugar desta pedra. {@code force}: não espera o coração da cidade (clique ou colocação).
     */
    public void resolve(ServerLevel sl, boolean force) {
        Places.Place current = placeId.isEmpty() ? null : Places.get(sl, placeId);
        if (current != null && !current.dim().equals(sl.dimension())) current = null;

        if (ownPlace) {
            // Uma cidade/vila apareceu em volta: a pedra vira a chegada dela
            Places.Place host = TravelStones.host(sl, worldPosition, placeId, false);
            if (host != null) {
                Places.remove(sl, placeId);
                if (anchor != null) Travel.unregisterStone(sl, anchor);
                ownPlace = false;
                link(sl, host);
                return;
            }
            if (current == null) {
                registerOwn(sl, TravelStones.siteBox(sl, worldPosition, playerMade));
            } else {
                // Compara o texto (o nome guardado no Places volta da gravação com outra estrutura de componente)
                Component name = TravelStones.nameFor(sl, this);
                if (!sameText(name, current.name())) registerOwn(sl, current.box());
            }
            ensureArrival(sl, placeId);
            return;
        }
        if (current != null) {
            link(sl, current);
            return;
        }
        Places.Place host = TravelStones.host(sl, worldPosition, null, true);
        if (host != null) {
            link(sl, host);
            return;
        }
        if (!force && !playerMade && waited < TravelStones.registerWait(sl, worldPosition)) {
            waited += 100;
            return;
        }
        ownPlace = true;
        placeId = TravelStones.ownId(sl, worldPosition);
        registerOwn(sl, TravelStones.siteBox(sl, worldPosition, playerMade));
        ensureArrival(sl, placeId);
    }

    /** Liga a pedra a um lugar de outro dono (cidade, vila, lugar de outra pedra). */
    private void link(ServerLevel sl, Places.Place host) {
        int rgb = TravelStones.typeColor(host);
        boolean changed = !host.id().equals(placeId) || display == null || !sameText(display, host.name()) || color != rgb;
        placeId = host.id();
        if (changed) display = host.name();
        color = rgb;
        ensureArrival(sl, host.id());
        if (changed) sync();
    }

    private static boolean sameText(Component a, Component b) {
        return a.getString().equals(b.getString());
    }

    /** Registra/atualiza o lugar próprio (tipo waystone). */
    private void registerOwn(ServerLevel sl, BoundingBox box) {
        Component name = TravelStones.nameFor(sl, this);
        if (anchor == null) anchor = TravelStones.findAnchor(sl, worldPosition);
        BlockPos center = anchor != null ? anchor : worldPosition.above();
        Places.register(sl, new Places.Place(placeId, name, Travel.WAYSTONE_TYPE, "", sl.dimension(), box, center, false,
                TravelStones.WAYSTONE_RGB));
        display = name;
        color = TravelStones.WAYSTONE_RGB;
        sync();
    }

    /** Ponto de chegada: um chão seguro ao lado da pedra, registrado para o lugar se ninguém mais estiver. */
    private void ensureArrival(ServerLevel sl, String id) {
        if (anchor == null || !TravelStones.standable(sl, anchor)) {
            BlockPos fresh = TravelStones.findAnchor(sl, worldPosition);
            if (fresh != null && !fresh.equals(anchor)) {
                if (anchor != null) Travel.unregisterStone(sl, anchor);
                anchor = fresh;
                setChanged();
            }
        }
        BlockPos arrival = anchor != null ? anchor : worldPosition;
        GlobalPos cur = Travel.stone(sl.getServer(), id);
        if (cur != null && cur.dimension().equals(sl.dimension()) && cur.pos().equals(arrival)) return;
        if (cur == null || ownPlace || !TravelStones.stoneStillThere(sl.getServer(), cur)) {
            Travel.registerStone(sl, id, arrival);
        }
    }

    /** Bloco removido: some o lugar próprio e a chegada registrada. */
    public void onRemoved(ServerLevel sl) {
        if (ownPlace && !placeId.isEmpty()) Places.remove(sl, placeId);
        if (anchor != null) Travel.unregisterStone(sl, anchor);
        Travel.unregisterStone(sl, worldPosition);
    }

    /** Brilho de partida/chegada (todos por perto veem). */
    public void flare(ServerLevel sl, boolean arrival) {
        triggerAnim("flare", "flare");
        TravelStones.flareFx(sl, worldPosition, arrival);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    // =====================================================================================
    //  Cliente: animação e partículas
    // =====================================================================================
    public static void clientTick(Level level, BlockPos pos, BlockState state, TravelStoneBlockEntity be) {
        be.age++;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        if (be.age % 10 == 1) {
            be.awake = level.getNearestPlayer(x, y + 1, z, 6.0, false) != null;
            be.viewerNear = be.awake || level.getNearestPlayer(x, y + 1, z, 28.0, false) != null;
        }
        if (!be.viewerNear) return;
        boolean reduced = !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        RandomSource r = level.random;
        // Runas sendo puxadas para o fuste (partícula de encantamento voa do deslocamento até o centro)
        if (be.age % (reduced ? 10 : (be.awake ? 2 : 4)) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 1.2 + r.nextDouble() * 1.2;
            level.addParticle(ParticleTypes.ENCHANT, x, y + 1.0 + r.nextDouble() * 0.6, z,
                    Math.cos(a) * d, r.nextDouble() * 0.8 - 0.2, Math.sin(a) * d);
        }
        // Faíscas no cristal flutuante
        if (be.age % (reduced ? 24 : 8) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.3, y + 2.2 + r.nextDouble() * 0.3,
                    z + (r.nextDouble() - 0.5) * 0.3, 0.0, 0.015, 0.0);
        }
        // Desperta: anel de pó rúnico girando em volta do plinto
        if (be.awake && !reduced && be.age % 3 == 0) {
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.44F, 0.89F, 1.0F), 0.9F);
            for (int i = 0; i < 2; i++) {
                double a = be.age * 0.21 + i * Math.PI;
                level.addParticle(dust, x + Math.cos(a) * 0.95, y + 0.25, z + Math.sin(a) * 0.95, 0, 0.01, 0);
            }
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 12, s -> s.setAndContinue(awake ? AWAKE : IDLE)));
        controllers.add(new AnimationController<>(this, "flare", 0, s -> PlayState.STOP).triggerableAnim("flare", FLARE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  NBT, componentes e sincronia
    // =====================================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!placeId.isEmpty()) tag.putString(TAG_PLACE, placeId);
        if (ownPlace) tag.putBoolean(TAG_OWN, true);
        if (playerMade) tag.putBoolean(TAG_PLAYER, true);
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        if (!ownerName.isEmpty()) tag.putString(TAG_OWNER_NAME, ownerName);
        if (customName != null) tag.putString(TAG_CUSTOM, Component.Serializer.toJson(customName, registries));
        if (display != null) tag.putString(TAG_DISPLAY, Component.Serializer.toJson(display, registries));
        tag.putInt(TAG_COLOR, color);
        if (anchor != null) tag.putLong(TAG_ANCHOR, anchor.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placeId = tag.getString(TAG_PLACE);
        ownPlace = tag.getBoolean(TAG_OWN);
        playerMade = tag.getBoolean(TAG_PLAYER);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.getString(TAG_OWNER_NAME);
        customName = tag.contains(TAG_CUSTOM) ? parseCustomNameSafe(tag.getString(TAG_CUSTOM), registries) : null;
        display = tag.contains(TAG_DISPLAY) ? parseCustomNameSafe(tag.getString(TAG_DISPLAY), registries) : null;
        color = tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : TravelStones.WAYSTONE_RGB;
        anchor = tag.contains(TAG_ANCHOR) ? BlockPos.of(tag.getLong(TAG_ANCHOR)) : null;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (customName != null) components.set(DataComponents.CUSTOM_NAME, customName);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(TAG_CUSTOM);
    }

    /** Ao cliente só vai o necessário para o letreiro e a previsão de quebra. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!placeId.isEmpty()) tag.putString(TAG_PLACE, placeId);
        if (playerMade) tag.putBoolean(TAG_PLAYER, true);
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        if (display != null) tag.putString(TAG_DISPLAY, Component.Serializer.toJson(display, registries));
        tag.putInt(TAG_COLOR, color);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
