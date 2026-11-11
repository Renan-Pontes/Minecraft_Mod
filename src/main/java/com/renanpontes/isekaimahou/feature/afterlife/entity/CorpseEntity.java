package com.renanpontes.isekaimahou.feature.afterlife.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.properties.Property;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeConfig;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeContent;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;
import com.renanpontes.isekaimahou.feature.afterlife.CorpseIndex;

import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * O Corpo: fica deitado onde o jogador caiu, com o inventário inteiro (+ armadura, mão secundária e acessórios do
 * Curios). É salvo com o chunk e nunca some sozinho — só quando esvazia (desbota) ou quando o dono volta a ele.
 * <p>
 * Regras contra duplicação: todo item entra aqui UMA vez (o espaço de origem é esvaziado no mesmo passo) e sai UMA vez
 * (é tirado do contêiner ANTES de ir para o jogador). Tudo roda na thread do servidor.
 * <p>
 * É um {@link LivingEntity} só para o cliente desenhar a armadura e o item da mão com as camadas do vanilla (inclusive
 * armaduras GeckoLib); no servidor ele não tem equipamento (as cópias de exibição só existem no cliente), não leva dano,
 * não é empurrado e nenhum mob o vê como alvo.
 */
public class CorpseEntity extends LivingEntity {
    public static final int SIZE = 54;
    public static final int FADE_TICKS = 30;
    /** Origem desconhecida (veio do chão, de uma morte de verdade sem correspondência). */
    public static final int NO_ORIGIN = -1;
    /** Origem = um espaço do Curios (a chave fica em {@link #curioKeys}). */
    public static final int CURIO_ORIGIN = -2;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(CorpseEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_SIG = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_CHARRED = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHALLENGE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FADE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<ItemStack>[] DATA_SHOW = new EntityDataAccessor[6];

    static {
        for (int i = 0; i < DATA_SHOW.length; i++) DATA_SHOW[i] = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    }

    private final SimpleContainer items = new SimpleContainer(SIZE);
    private final int[] origin = new int[SIZE];
    private final String[] curioKeys = new String[SIZE];
    private int selected;
    private long created;
    private boolean displayDirty = true;
    private int viewers;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        java.util.Arrays.fill(origin, NO_ORIGIN);
        this.setNoGravity(true);
        this.noCulling = true;
        items.addListener(c -> displayDirty = true);
    }

    /** Novo corpo (ainda não adicionado ao mundo). */
    public static CorpseEntity create(ServerLevel level, ServerPlayer owner, Vec3 pos, float yaw, boolean charred, boolean challenge) {
        CorpseEntity c = new CorpseEntity(AfterlifeContent.CORPSE.get(), level);
        c.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        c.setYBodyRot(yaw);
        c.setYHeadRot(yaw);
        c.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        c.entityData.set(DATA_NAME, owner.getGameProfile().getName());
        Property tex = owner.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
        if (tex != null) {
            c.entityData.set(DATA_SKIN, tex.value());
            c.entityData.set(DATA_SKIN_SIG, tex.signature() == null ? "" : tex.signature());
        }
        c.entityData.set(DATA_CHARRED, charred);
        c.entityData.set(DATA_CHALLENGE, challenge);
        c.created = level.getGameTime();
        c.selected = owner.getInventory().selected;
        c.refreshName();
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_OWNER, Optional.empty());
        b.define(DATA_NAME, "");
        b.define(DATA_SKIN, "");
        b.define(DATA_SKIN_SIG, "");
        b.define(DATA_CHARRED, false);
        b.define(DATA_CHALLENGE, false);
        b.define(DATA_FADE, -1);
        for (EntityDataAccessor<ItemStack> a : DATA_SHOW) b.define(a, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------------------------------------- leitura

    @Nullable public UUID owner() { return entityData.get(DATA_OWNER).orElse(null); }
    public String ownerName() { return entityData.get(DATA_NAME); }
    public String skinValue() { return entityData.get(DATA_SKIN); }
    public String skinSignature() { return entityData.get(DATA_SKIN_SIG); }
    public boolean charred() { return entityData.get(DATA_CHARRED); }
    public boolean inChallenge() { return entityData.get(DATA_CHALLENGE); }
    public long created() { return created; }
    public boolean isOwner(Player p) { return p.getUUID().equals(owner()); }

    /** Opacidade (1 normal; desce até 0 quando esvaziou). */
    public float alpha(float partial) {
        int f = entityData.get(DATA_FADE);
        if (f < 0) return 1F;
        return Math.max(0F, Math.min(1F, (f - partial) / FADE_TICKS));
    }

    public boolean isEmptyCorpse() {
        return items.isEmpty();
    }

    public SimpleContainer container() {
        return items;
    }

    private void refreshName() {
        String key = charred() ? "entity.isekai_mahou.afterlife_corpse.charred_of" : "entity.isekai_mahou.afterlife_corpse.of";
        setCustomName(Component.translatable(key, ownerName()));
        setCustomNameVisible(true);
    }

    public void setInChallenge(boolean on) {
        entityData.set(DATA_CHALLENGE, on);
    }

    // ---------------------------------------------------------------------------------------------- entrada de itens

    /**
     * Tira TODO o inventário do jogador (41 espaços + Curios) e guarda aqui com o lugar de origem. Fecha antes qualquer
     * tela (o item no cursor e a grade de criação voltam ao inventário) para nada ficar de fora.
     */
    public void absorbInventory(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        player.inventoryMenu.removed(player);
        Inventory inv = player.getInventory();
        selected = inv.selected;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int slot = freeSlot();
            if (slot < 0) break; // cheio: o resto fica com o jogador (cai no chão/corpo se ele morrer de verdade)
            inv.setItem(i, ItemStack.EMPTY);
            put(slot, s, i, null);
        }
        for (CurioBridge.Taken t : CurioBridge.takeAll(player)) {
            int slot = freeSlot();
            if (slot < 0) {
                giveOrDrop(player, t.stack());
                continue;
            }
            put(slot, t.stack(), CURIO_ORIGIN, t.key());
        }
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /** Guarda uma pilha vinda do chão (morte de verdade). false = corpo cheio (a pilha fica como item no chão). */
    public boolean addOne(ItemStack s, int from) {
        if (s.isEmpty()) return true;
        int slot = freeSlot();
        if (slot < 0) return false;
        put(slot, s, from, null);
        return true;
    }

    private void put(int slot, ItemStack s, int from, @Nullable String curio) {
        items.setItem(slot, s);
        origin[slot] = from;
        curioKeys[slot] = curio;
    }

    private int freeSlot() {
        for (int i = 0; i < SIZE; i++) if (items.getItem(i).isEmpty()) return i;
        return -1;
    }

    /** Some com os itens amaldiçoados com Desaparecimento (como numa morte vanilla). */
    public void destroyVanishing() {
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.getItem(i);
            if (!s.isEmpty() && EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) items.setItem(i, ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------------------------------------- saída de itens

    /**
     * Devolve tudo ao jogador: cada item volta ao espaço de origem se ele estiver vazio (armadura vestida, barra de
     * atalhos no lugar), senão vai para o inventário, e o que não couber cai aos pés dele. Cada pilha é tirada do
     * contêiner antes de ser entregue. O corpo vazio some na hora.
     */
    public void restoreTo(ServerPlayer player) {
        Inventory inv = player.getInventory();
        // 1ª passada: lugares exatos (para nada "roubar" o espaço de outro item)
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            int o = origin[i];
            String key = curioKeys[i];
            origin[i] = NO_ORIGIN;
            curioKeys[i] = null;
            if (o >= 0 && o < inv.getContainerSize() && inv.getItem(o).isEmpty()) {
                inv.setItem(o, s);
            } else if (o == CURIO_ORIGIN && key != null && CurioBridge.put(player, key, s)) {
                // devolvido ao acessório
            } else if (o == NO_ORIGIN && equipIfFree(player, s)) {
                // armadura sem origem conhecida: vestida
            } else {
                rest.add(s);
            }
        }
        for (ItemStack s : rest) giveOrDrop(player, s);
        if (selected >= 0 && selected < 9) inv.selected = selected;
        items.setChanged();
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 1F);
        if (items.isEmpty()) vanish();
    }

    private static boolean equipIfFree(ServerPlayer player, ItemStack s) {
        EquipmentSlot slot = player.getEquipmentSlotForItem(s);
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return false;
        if (!player.getItemBySlot(slot).isEmpty()) return false;
        player.setItemSlot(slot, s);
        return true;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack s) {
        if (s.isEmpty()) return;
        player.getInventory().add(s);
        if (!s.isEmpty()) {
            ItemEntity drop = player.drop(s, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
    }

    /** Joga tudo no chão (o corpo foi destruído por comando): nada se perde. */
    public void spill() {
        if (!(level() instanceof ServerLevel)) return;
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            ItemEntity e = new ItemEntity(level(), getX(), getY() + 0.3, getZ(), s);
            e.setDefaultPickUpDelay();
            level().addFreshEntity(e);
        }
    }

    /** Some já (sem desbotar): devolvido ao dono. */
    private void vanish() {
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 12, 0.5, 0.15, 0.5, 0.02);
        discard();
    }

    // ---------------------------------------------------------------------------------------------- interação

    /** Dono e grupo sempre; os outros só depois da proteção (config). Criativo sempre. */
    public boolean canLoot(Player p) {
        if (p.isSpectator()) return false;
        if (isOwner(p) || p.isCreative()) return true;
        UUID o = owner();
        if (o != null && Party.members(p).contains(o)) return true;
        return protectionLeft() <= 0;
    }

    /** Ticks que faltam para qualquer um poder abrir. */
    public long protectionLeft() {
        return Math.max(0L, created + AfterlifeConfig.lootProtectionTicks() - level().getGameTime());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !isAlive() || entityData.get(DATA_FADE) >= 0) return InteractionResult.PASS;
        if (AfterlifeManager.isSpirit(sp)) return InteractionResult.FAIL;
        if (inChallenge()) {
            AfterlifeManager.helpRevive(sp, this);
            return InteractionResult.CONSUME;
        }
        if (isOwner(sp) && sp.isShiftKeyDown()) {
            restoreTo(sp);
            return InteractionResult.CONSUME;
        }
        if (!canLoot(sp)) {
            long min = Math.max(1L, (protectionLeft() + 1199L) / 1200L);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.afterlife.protected", ownerName(), min), true);
            return InteractionResult.CONSUME;
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CorpseMenu(id, inv, this), getDisplayName()));
        return InteractionResult.CONSUME;
    }

    void onMenuOpened() {
        viewers++;
    }

    void onMenuClosed() {
        viewers = Math.max(0, viewers - 1);
    }

    // ---------------------------------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        if (displayDirty) {
            displayDirty = false;
            refreshDisplay();
        }
        int fade = entityData.get(DATA_FADE);
        if (fade >= 0) {
            if (fade == 0) discard();
            else entityData.set(DATA_FADE, fade - 1);
            return;
        }
        if (tickCount % 20 == 0) {
            UUID o = owner();
            // o desafio acabou sem nos avisar (servidor caiu no meio): o corpo vira um corpo comum
            if (inChallenge() && (o == null || !AfterlifeManager.isChallengeCorpse(o, getUUID()))) setInChallenge(false);
            if (getServer() != null && o != null && !items.isEmpty()) {
                // o índice se refaz sozinho (entrada perdida) e acompanha um /tp de admin
                if (CorpseIndex.contains(getServer(), getUUID())) CorpseIndex.move(getServer(), getUUID(), blockPosition());
                else CorpseIndex.put(getServer(), new CorpseIndex.Entry(getUUID(), o, level().dimension(), blockPosition(), created, charred()));
            }
        }
        if (!inChallenge() && viewers == 0 && items.isEmpty()) {
            entityData.set(DATA_FADE, FADE_TICKS);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 8, 0.5, 0.1, 0.5, 0.01);
        }
    }

    private void clientParticles() {
        if (charred() && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.25,
                    getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.03, 0);
        }
        if (inChallenge() && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SCULK_SOUL, getX() + (random.nextDouble() - 0.5), getY() + 0.3,
                    getZ() + (random.nextDouble() - 0.5), 0, 0.04, 0);
        }
    }

    /** Cópias de exibição (armadura e mãos) para o cliente desenhar o corpo vestido. */
    private void refreshDisplay() {
        ItemStack[] show = new ItemStack[6];
        java.util.Arrays.fill(show, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty()) continue;
            int o = origin[i];
            int idx = switch (o) {
                case 39 -> 0;               // cabeça
                case 38 -> 1;               // peito
                case 37 -> 2;               // pernas
                case 36 -> 3;               // pés
                case 40 -> 5;               // mão secundária
                default -> o == selected ? 4 : -1;
            };
            if (idx >= 0) show[idx] = s.copyWithCount(1);
        }
        for (int k = 0; k < 6; k++) {
            if (!ItemStack.matches(entityData.get(DATA_SHOW[k]), show[k])) entityData.set(DATA_SHOW[k], show[k]);
        }
    }

    private static int showIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            case MAINHAND -> 4;
            case OFFHAND -> 5;
            default -> -1;
        };
    }

    // ---------------------------------------------------------------------------------------------- LivingEntity

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        if (!level().isClientSide) return NonNullList.withSize(4, ItemStack.EMPTY);
        return List.of(getItemBySlot(EquipmentSlot.FEET), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.CHEST),
                getItemBySlot(EquipmentSlot.HEAD));
    }

    /** Só o cliente tem equipamento (cópias para desenhar); no servidor fica vazio para nada de encantamento/atributo agir. */
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (!level().isClientSide) return ItemStack.EMPTY;
        int i = showIndex(slot);
        return i < 0 ? ItemStack.EMPTY : entityData.get(DATA_SHOW[i]);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // equipamento é só exibição (vem do contêiner)
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /** /kill: os itens caem no chão em vez de sumir com o corpo. */
    @Override
    public void kill() {
        spill();
        discard();
    }

    @Override
    protected void onBelowWorld() {
        setPos(getX(), level().getMinBuildHeight() + 1, getZ());
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (reason.shouldDestroy() && getServer() != null) CorpseIndex.remove(getServer(), getUUID());
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public void push(Entity e) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeSeenAsEnemy() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean isAffectedByPotions() { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean shouldShowName() { return true; }

    // ---------------------------------------------------------------------------------------------- salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID o = owner();
        if (o != null) tag.putUUID("Owner", o);
        tag.putString("OwnerName", ownerName());
        tag.putString("Skin", skinValue());
        tag.putString("SkinSig", skinSignature());
        tag.putBoolean("Charred", charred());
        tag.putBoolean("Challenge", inChallenge());
        tag.putLong("Created", created);
        tag.putInt("Selected", selected);
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("Slot", i);
            c.putInt("From", origin[i]);
            if (curioKeys[i] != null) c.putString("Curio", curioKeys[i]);
            c.put("Item", s.save(registryAccess()));
            list.add(c);
        }
        tag.put("Items", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(DATA_NAME, tag.getString("OwnerName"));
        entityData.set(DATA_SKIN, tag.getString("Skin"));
        entityData.set(DATA_SKIN_SIG, tag.getString("SkinSig"));
        entityData.set(DATA_CHARRED, tag.getBoolean("Charred"));
        entityData.set(DATA_CHALLENGE, tag.getBoolean("Challenge"));
        created = tag.getLong("Created");
        selected = tag.getInt("Selected");
        items.clearContent();
        java.util.Arrays.fill(origin, NO_ORIGIN);
        java.util.Arrays.fill(curioKeys, null);
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            CompoundTag c = list.getCompound(k);
            int i = c.getInt("Slot");
            if (i < 0 || i >= SIZE) continue;
            ItemStack s = ItemStack.parseOptional(registryAccess(), c.getCompound("Item"));
            if (s.isEmpty()) continue;
            items.setItem(i, s);
            origin[i] = c.getInt("From");
            curioKeys[i] = c.contains("Curio") ? c.getString("Curio") : null;
        }
        setNoGravity(true);
        displayDirty = true;
        refreshName();
    }
}
