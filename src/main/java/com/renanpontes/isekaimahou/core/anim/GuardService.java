package com.renanpontes.isekaimahou.core.anim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponParry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.0 — <b>Bloqueio com aparo</b> (servidor). Todo mundo pode bloquear segurando a tecla com uma arma ou cajado na mão:
 * <ul>
 *   <li><b>Bloqueio</b>: golpes pela FRENTE perdem parte do dano ({@link AnimConfig#GUARD_REDUCTION}; magia segura menos).
 *       Custa fome a cada golpe segurado e um pouco enquanto segura; anda devagar; golpe forte demais QUEBRA a guarda.</li>
 *   <li><b>Aparo</b>: se o golpe chega até {@link AnimConfig#PARRY_WINDOW} ticks depois de APERTAR a tecla, ele é anulado,
 *       o atacante fica atordoado por um instante e sai um som/clarão curto. Um aparo por aperto.</li>
 * </ul>
 * Não empilha com a esquiva: esquivar solta a guarda, a guarda não começa durante a invulnerabilidade da esquiva e a
 * janela do aparo não abre logo depois de esquivar nem para quem fica martelando a tecla. Atacar ou conjurar também
 * soltam a guarda. O Modo Paz trava o bloqueio como trava a esquiva.
 * <p>
 * Outros módulos: {@link #isGuarding(Player)}, os eventos {@link GuardEvent.Parry} / {@link GuardEvent.Block} e os
 * contadores {@link #COUNTER_PARRY} / {@link #COUNTER_BLOCK}. Teste: {@code /mahou guard info|hit [dano] [ticks]}.
 */
public final class GuardService {
    /** {@link AnimPayloads.GuardFx}: o servidor recusou/encerrou a guarda (o cliente larga a pose até soltar a tecla). */
    public static final byte FX_END = 0;
    /** {@link AnimPayloads.GuardFx}: aparo (clarão curto). */
    public static final byte FX_PARRY = 2;

    /** Contadores do {@code MagicData} (feitos, títulos, diário): aparos certos e golpes bloqueados. */
    public static final String COUNTER_PARRY = "feat.parry", COUNTER_BLOCK = "feat.guard_block";

    /** Lentidão enquanto bloqueia (modificador temporário, some ao soltar). */
    public static final ResourceLocation SLOW_ID = IsekaiMahou.id("guard_slow");
    private static final double SLOW = -0.45D;
    /** Ticks mínimos entre dois apertos para a janela do aparo abrir (martelar a tecla não vira invulnerabilidade). */
    private static final int PARRY_REARM = 14;
    /** Depois de esquivar: ticks sem poder bloquear / sem janela de aparo. */
    private static final int AFTER_DODGE_GUARD = 9, AFTER_DODGE_PARRY = 16;
    /** Guarda quebrada: ticks sem poder bloquear. */
    private static final int BREAK_LOCK = 40;
    /** De quanto em quanto tempo a pose é reenviada a quem está vendo (quem chegou depois, pose que expirou). */
    private static final int POSE_REFRESH = 150;
    private static final int MIN_FOOD = 6;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private static final class State {
        /** Tick em que a guarda atual começou; -1 = não está bloqueando. */
        long start = -1;
        /** Este aperto ainda pode virar aparo. */
        boolean window;
        long lastPress = NEVER, lockUntil = NEVER, noParryUntil = NEVER, lastParry = NEVER, lastBlock = NEVER;
        long nextPose, nextHoldCost;
        int parries, blocks;
        /** Golpe de treino agendado (comando de teste). */
        long testAt = -1;
        float testDamage;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** Dá para bloquear com este item? (por item; lido também pelo cliente) */
    private static final Map<Item, Boolean> GUARD_ITEMS = new ConcurrentHashMap<>();
    private static final String[] WEAPON_KEYS = {"sword", "katana", "zanpakuto", "blade", "saber", "sabre", "rapier", "falchion",
            "scimitar", "cutlass", "odachi", "wakizashi", "dagger", "knife", "kunai", "tanto", "spear", "lance", "halberd", "glaive",
            "naginata", "pike", "javelin", "hammer", "mace", "greataxe", "battleaxe", "claymore", "maul", "club", "scythe", "cleaver",
            "flail", "gauntlet", "knuckle", "cestus"};
    private static final String[] STAFF_KEYS = {"staff", "wand", "focus", "scepter", "sceptre", "grimoire", "tome", "codex",
            "catalyst"};
    /** Armas de classe do mod que não parecem arma pelo nome comum (cruz Quincy, bola de aço, cartas, armas de mana). */
    private static final String[] CLASS_KEYS = {"cross", "ball", "card", "deck", "gun", "pistol", "rifle", "whip", "claw"};
    /** Verdadeiro só enquanto o golpe de treino do comando está sendo aplicado (ele não tem entidade, mas pode ser aparado). */
    private static boolean testHit;

    static void init(IEventBus bus) {
        // Mesma prioridade da esquiva, registrado DEPOIS dela: golpe esquivado nem chega aqui
        bus.addListener(EventPriority.HIGH, false, LivingIncomingDamageEvent.class, GuardService::onIncomingDamage);
        bus.addListener(AttackEntityEvent.class, GuardService::onAttack);
        bus.addListener(EventPriority.LOWEST, false, SpellCastEvent.Pre.class, GuardService::onCast);
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("guard")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("hit").executes(c -> testHit(c.getSource(), 6F, 40))
                        .then(Commands.argument("dano", FloatArgumentType.floatArg(0.5F, 200F))
                                .executes(c -> testHit(c.getSource(), FloatArgumentType.getFloat(c, "dano"), 40))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(5, 200))
                                        .executes(c -> testHit(c.getSource(), FloatArgumentType.getFloat(c, "dano"),
                                                IntegerArgumentType.getInteger(c, "ticks"))))))));
    }

    // =====================================================================================
    //  API
    // =====================================================================================

    /** O jogador está bloqueando agora? (servidor) */
    public static boolean isGuarding(@Nullable Player player) {
        if (player == null) return false;
        State st = STATES.get(player.getUUID());
        return st != null && st.start >= 0;
    }

    /**
     * Dá para bloquear com este item na mão principal? Armas (espada, machado, lança, adaga, martelo, foice, arco...),
     * cajados, varinhas, focos, grimórios e as armas de classe do mod; ferramentas de minerar/cavar, blocos e escudos
     * (que já bloqueiam do jeito do Minecraft) não. Mão vazia também não.
     */
    public static boolean canGuardWith(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return GUARD_ITEMS.computeIfAbsent(stack.getItem(), item -> detect(new ItemStack(item)));
    }

    private static boolean detect(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.getUseAnimation() == UseAnim.BLOCK || item instanceof BlockItem || item instanceof SpawnEggItem) return false;
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
            return true;
        }
        if (item instanceof ProjectileWeaponItem) return true; // arco/besta também aparam (puxar a corda solta a guarda)
        if (item instanceof DiggerItem) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        boolean ours = IsekaiMahou.MODID.equals(id.getNamespace());
        if (path.contains("fishing") || path.contains("pickaxe") || path.contains("shovel")) return false;
        // Por palavra do id ("great_sword", "greatsword", "fire_wand"), para "wandering_..." ou "arcane_..." não contarem
        for (String word : path.split("_")) {
            for (String k : WEAPON_KEYS) if (word.endsWith(k)) return true;
            for (String k : STAFF_KEYS) if (word.endsWith(k)) return true;
            if (ours) for (String k : CLASS_KEYS) if (word.endsWith(k)) return true;
        }
        // Arma de classe com nome diferente: qualquer item que dá dano de ataque na mão
        boolean[] weapon = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE) && mod.amount() >= 2.0) weapon[0] = true;
        });
        return weapon[0];
    }

    /** Solta a guarda (se houver) e impede bloquear de novo por {@code lockTicks}. */
    public static void interrupt(ServerPlayer player, int lockTicks) {
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        long now = player.serverLevel().getGameTime();
        st.lockUntil = Math.max(st.lockUntil, now + lockTicks);
        if (st.start >= 0) end(player, st, true);
    }

    /** A esquiva saiu: solta a guarda e fecha a janela de aparo por um instante (não dá para emendar as duas). */
    static void onDodge(ServerPlayer player) {
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        long now = player.serverLevel().getGameTime();
        st.lockUntil = Math.max(st.lockUntil, now + AFTER_DODGE_GUARD);
        st.noParryUntil = Math.max(st.noParryUntil, now + AFTER_DODGE_PARRY);
        if (st.start >= 0) end(player, st, true);
    }

    // =====================================================================================
    //  Tecla
    // =====================================================================================
    static void request(ServerPlayer p, boolean down) {
        State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        if (!down) {
            if (st.start >= 0) end(p, st, false);
            return;
        }
        if (st.start >= 0) return;
        long now = p.serverLevel().getGameTime();
        if (!canGuardNow(p) || now < st.lockUntil || DodgeService.isDodging(p)) {
            refuse(p);
            return;
        }
        st.window = AnimConfig.PARRY_WINDOW.get() > 0 && now - st.lastPress >= PARRY_REARM && now >= st.noParryUntil;
        st.lastPress = now;
        st.start = now;
        st.nextPose = now + POSE_REFRESH;
        st.nextHoldCost = now + 20;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (p.isSprinting()) p.setSprinting(false);
        MahouAnims.playOthers(p, MahouAnims.GUARD_WEAPON);
    }

    private static boolean canGuardNow(ServerPlayer p) {
        if (!AnimConfig.GUARD_ENABLED.get() || PeaceMode.isOn(p)) return false;
        if (p.isSpectator() || p.isSleeping() || p.isDeadOrDying() || p.isUsingItem() || p.isFallFlying() || p.isSwimming()) return false;
        if (Statuses.incapacitated(p) || p.hasEffect(CoreRegistries.STUNNED)) return false;
        if (!p.getAbilities().instabuild && p.getFoodData().getFoodLevel() <= MIN_FOOD) return false;
        return canGuardWith(p.getMainHandItem());
    }

    private static void refuse(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new AnimPayloads.GuardFx(FX_END));
    }

    private static void end(ServerPlayer p, State st, boolean tellPlayer) {
        st.start = -1;
        st.window = false;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_ID);
        MahouAnims.stopOthers(p, MahouAnims.GUARD_WEAPON);
        if (tellPlayer) PacketDistributor.sendToPlayer(p, new AnimPayloads.GuardFx(FX_END));
    }

    /** Fim do tick do jogador: confere se ainda pode bloquear, cobra o cansaço de segurar e reenvia a pose. */
    static void tick(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        long now = p.serverLevel().getGameTime();
        if (st.testAt >= 0) trainingHit(p, st, now);
        if (st.start < 0) return;
        if (!canGuardNow(p)) {
            end(p, st, true);
            return;
        }
        if (now >= st.nextHoldCost) {
            st.nextHoldCost = now + 20;
            if (!p.getAbilities().instabuild) p.causeFoodExhaustion(0.15F);
        }
        if (now >= st.nextPose) {
            st.nextPose = now + POSE_REFRESH;
            MahouAnims.playOthers(p, MahouAnims.GUARD_WEAPON);
        }
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State st = STATES.get(p.getUUID());
        if (st == null || st.start < 0) return;
        float amount = event.getAmount();
        DamageSource src = event.getSource();
        if (amount <= 0F || !blockable(p, src)) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        LivingEntity attacker = src.getEntity() instanceof LivingEntity le && le != p ? le : null;
        Vec3 from = src.getSourcePosition();
        Vec3 at = p.getEyePosition().add(from.subtract(p.getEyePosition()).normalize().scale(0.8)).add(0, -0.25, 0);
        boolean creative = p.getAbilities().instabuild;

        // ---- Aparo: golpe direto (corpo a corpo ou projétil) dentro da janela; os golpes do mesmo instante vão juntos
        boolean direct = (src.getDirectEntity() != null || testHit) && !src.is(DamageTypeTags.IS_EXPLOSION);
        boolean timed = st.window && now - st.start <= AnimConfig.PARRY_WINDOW.get();
        if (direct && (timed || now - st.lastParry <= 2)) {
            event.setCanceled(true);
            if (now - st.lastParry <= 2) return;
            st.window = false;
            st.lastParry = now;
            st.lastPress = NEVER; // aparo certo: o próximo aperto já pode aparar de novo
            st.nextPose = now + 9; // quem está vendo volta a ver a guarda depois do gesto do aparo
            st.parries++;
            MagicApi.addCounter(p, COUNTER_PARRY, 1);
            parryEffects(p, attacker, level, at);
            NeoForge.EVENT_BUS.post(new GuardEvent.Parry(p, attacker, src, amount));
            return;
        }

        // ---- Bloqueio comum
        double reduction = AnimConfig.GUARD_REDUCTION.get();
        if (src.is(DamageTypeTags.BYPASSES_SHIELD) || src.is(CoreRegistries.SPELL_DAMAGE) || src.is(DamageTypeTags.IS_EXPLOSION)) {
            reduction *= 0.6; // magia e explosão passam mais pela arma
        }
        float reduced = (float) (amount * (1.0 - reduction));
        event.setAmount(reduced);
        // Golpe que o Minecraft vai descartar (o jogador acabou de apanhar) ou dano que pinga a cada tick: reduz em
        // silêncio — sem som, sem cansaço, sem contar e sem quebrar a guarda a cada tick
        boolean ignoredByCooldown = p.invulnerableTime > 10 && !src.is(DamageTypeTags.BYPASSES_COOLDOWN);
        if (ignoredByCooldown || now - st.lastBlock < 4) return;
        st.lastBlock = now;
        st.blocks++;
        MagicApi.addCounter(p, COUNTER_BLOCK, 1);
        if (!creative) {
            p.causeFoodExhaustion((float) Math.min(3.0, AnimConfig.GUARD_EXHAUSTION.get() + amount * 0.1));
        }
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.15);
        boolean broke = amount >= AnimConfig.GUARD_BREAK_DAMAGE.get();
        if (broke) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.7F, 0.9F);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.55F,
                    0.8F + level.random.nextFloat() * 0.15F);
        }
        NeoForge.EVENT_BUS.post(new GuardEvent.Block(p, attacker, src, amount, reduced));
        if (broke) {
            st.lockUntil = now + BREAK_LOCK;
            end(p, st, true);
        }
    }

    /** O golpe tem direção e veio pela frente (mesma regra do escudo: meio círculo à frente do corpo)? */
    private static boolean blockable(ServerPlayer p, DamageSource src) {
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        if (src.getEntity() == p) return false;
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 look = Vec3.directionFromRotation(0F, p.getYHeadRot());
        double dx = from.x - p.getX(), dz = from.z - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        // Em cima do jogador (sem direção clara) conta como frente
        return len < 0.3 || (dx * look.x + dz * look.z) / len > 0.0;
    }

    private static void parryEffects(ServerPlayer p, @Nullable LivingEntity attacker, ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 1.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.2F, 1.9F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.3);
        MahouAnims.play(p, MahouAnims.PARRY);
        PacketDistributor.sendToPlayer(p, new AnimPayloads.GuardFx(FX_PARRY));
        if (attacker != null && attacker.isAlive() && attacker.distanceToSqr(p) <= 36.0) {
            int stun = AnimConfig.PARRY_STUN.get();
            if (stun > 0) Statuses.apply(attacker, Statuses.Kind.STUNNED, 1, stun, p, 1F);
            attacker.knockback(0.45, p.getX() - attacker.getX(), p.getZ() - attacker.getZ());
        }
        // Guerreiras enchem um pouco de Vigor; quem tem magia com o gatilho Aparar solta o contra-golpe
        if (MagicApi.get(p).path().resource() == MagicPath.Resource.VIGOR) ClassResources.gain(p, 6F);
        WeaponParry.react(p, attacker);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isGuarding(p)) interrupt(p, 6);
    }

    private static void onCast(SpellCastEvent.Pre event) {
        if (!(event.getCaster() instanceof ServerPlayer p) || !event.getContext().isPlayerCast()) return;
        State st = STATES.get(p.getUUID());
        if (st == null || st.start < 0) return;
        // O contra-golpe do gatilho Aparar sai logo depois do aparo: esse não solta a guarda
        if (p.serverLevel().getGameTime() - st.lastParry <= 3) return;
        interrupt(p, 8);
    }

    // =====================================================================================
    //  Teste (/mahou guard ...)
    // =====================================================================================
    private static int info(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.guard.info", st.start >= 0, st.blocks, st.parries,
                AnimConfig.PARRY_WINDOW.get(), Math.round(AnimConfig.GUARD_REDUCTION.get() * 100),
                canGuardWith(p.getMainHandItem())), false);
        return 1;
    }

    private static int testHit(CommandSourceStack source, float damage, int ticks) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        st.testAt = p.serverLevel().getGameTime() + ticks;
        st.testDamage = damage;
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.guard.hit", damage, ticks), false);
        return 1;
    }

    /** Golpe de treino: dois bipes de contagem e um golpe pela frente (pode ser bloqueado e aparado). */
    private static void trainingHit(ServerPlayer p, State st, long now) {
        long left = st.testAt - now;
        if (left == 20 || left == 10) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        if (left > 0) return;
        st.testAt = -1;
        Vec3 front = p.getEyePosition().add(Vec3.directionFromRotation(0F, p.getYHeadRot()).scale(2.0));
        DamageSource src = new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MOB_ATTACK), front);
        float before = p.getHealth();
        testHit = true;
        try {
            p.hurt(src, st.testDamage);
        } finally {
            testHit = false;
        }
        float lost = before - p.getHealth();
        p.displayClientMessage(Component.translatable("command.isekai_mahou.guard.hit_result", String.format("%.1f", lost)), false);
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void clear() {
        STATES.clear();
    }

    private GuardService() {}
}
