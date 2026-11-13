package com.renanpontes.isekaimahou.feature.familiars;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras do Familiar (servidor): o ritual de despertar, o corpo no mundo, o vínculo, os estágios e a mudança de forma.
 * <ul>
 *   <li><b>Despertar</b>: Incenso do Vínculo aceso perto de uma fogueira, com classe escolhida e nível
 *       {@link #AWAKEN_LEVEL}+. A forma nasce do jeito de jogar ({@link FamiliarShaping}).</li>
 *   <li><b>Estágios</b>: 2 no nível {@link #STAGE2_LEVEL} e 3 no {@link #STAGE3_LEVEL} — o nível do dono é o requisito
 *       principal ("upar pesa mais que item"); o vínculo (tempo junto) só impede pular etapas.</li>
 *   <li><b>Mudar com você</b>: a cada {@link #DRIFT_EVERY} ticks o jeito de jogar é reavaliado; se outra forma passa a
 *       combinar BEM mais por {@link #DRIFT_NEEDED} avaliações seguidas (e já faz um dia de jogo desde a última
 *       mudança), o familiar se transforma. Trocar de classe/raça pesa muito, então ele acompanha.</li>
 * </ul>
 * Tudo espalhado: por jogador, uma checagem leve por segundo e as avaliações de minuto em minuto.
 */
public final class FamiliarService {
    public static final int AWAKEN_LEVEL = 10;
    public static final int STAGE2_LEVEL = 30, STAGE2_BOND = 120;
    public static final int STAGE3_LEVEL = 75, STAGE3_BOND = 400;
    static final int DRIFT_EVERY = 2400, DRIFT_NEEDED = 5;
    /** Tempo mínimo de jogo entre duas mudanças de forma (um dia do Minecraft). */
    private static final long MIN_SHIFT_GAP = 24000L;
    /** Flag/contador no MagicData para outros módulos lerem por texto (títulos, diário...). */
    public static final String FLAG_AWAKENED = "familiars.awakened", COUNTER_STAGE = "familiars.stage";

    /** Corpo ativo de cada jogador (só na memória: o corpo não é salvo). */
    private static final Map<UUID, FamiliarEntity> BODIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();

    private FamiliarService() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) tick(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> FamiliarContent.data(e.getEntity()).dirty = true);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            removeBody(e.getEntity().getUUID(), false);
            LAST_HURT.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> removeBody(e.getEntity().getUUID(), false));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            FamiliarData d = FamiliarContent.data(sp);
            d.dirty = true;
            // O Corvo lembra onde você caiu: guia sozinho por 5 minutos depois de renascer
            if (!e.isEndConquered() && d.awakened()) d.setDeathGuideUntil(sp.serverLevel().getGameTime() + 6000);
        });
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.LevelUp.class, e -> {
            ServerPlayer sp = e.getPlayer();
            FamiliarData d = FamiliarContent.data(sp);
            if (!d.awakened()) return;
            d.addBond(5 * Math.max(1, Math.min(5, e.newLevel - e.oldLevel)));
            checkStage(sp, d);
        });
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp && e.getNewDamage() > 0) {
                LAST_HURT.put(sp.getUUID(), sp.serverLevel().getGameTime());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            BODIES.clear();
            LAST_HURT.clear();
        });
    }

    /** Tempo de jogo do último dano levado pelo jogador (0 = nunca nesta sessão). */
    static long lastHurt(ServerPlayer sp) {
        return LAST_HURT.getOrDefault(sp.getUUID(), 0L);
    }

    /** O corpo {@code body} é o familiar "oficial" do jogador agora? (corpos órfãos somem sozinhos) */
    static boolean isActiveBody(ServerPlayer owner, FamiliarEntity body) {
        return BODIES.get(owner.getUUID()) == body;
    }

    @Nullable
    static FamiliarEntity body(ServerPlayer sp) {
        FamiliarEntity b = BODIES.get(sp.getUUID());
        return b != null && !b.isRemoved() && b.level() == sp.level() ? b : null;
    }

    // =====================================================================================
    //  Tick (por jogador)
    // =====================================================================================
    private static void tick(ServerPlayer sp) {
        FamiliarData d = FamiliarContent.data(sp);
        if (d.dirty) {
            d.dirty = false;
            sync(sp, d);
        }
        // Deslocado pelo id: os jogadores não fazem o trabalho todos no mesmo tick
        int t = sp.tickCount + (sp.getId() & 15);
        if (t % 20 != 0) return;
        if (!d.awakened()) {
            if (t % 200 == 0) ritualHints(sp);
            return;
        }
        ensureBody(sp, d);
        if (t % 1200 == 0) {
            FamiliarEntity b = body(sp);
            if (b != null && b.distanceToSqr(sp) < 32 * 32) d.addBond(1);
            checkStage(sp, d);
        }
        if (t % DRIFT_EVERY == 0) drift(sp, d, false);
    }

    private static void ritualHints(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        if (md.path() == MagicPath.NONE || md.level() < AWAKEN_LEVEL) return;
        if (sp.getInventory().contains(new ItemStack(FamiliarContent.BOND_INCENSE.get()))) Hints.show(sp, "familiar_incense");
        else Hints.show(sp, "familiar_ritual");
    }

    // =====================================================================================
    //  Corpo no mundo
    // =====================================================================================
    private static void ensureBody(ServerPlayer sp, FamiliarData d) {
        boolean want = d.summoned() && sp.isAlive() && !sp.isSpectator();
        FamiliarEntity b = BODIES.get(sp.getUUID());
        if (!want) {
            if (b != null) removeBody(sp.getUUID(), false);
            return;
        }
        if (b != null && (b.isRemoved() || b.level() != sp.level())) {
            removeBody(sp.getUUID(), false);
            b = null;
        }
        if (b == null) spawnBody(sp, d);
    }

    private static void spawnBody(ServerPlayer sp, FamiliarData d) {
        ServerLevel level = sp.serverLevel();
        FamiliarEntity b = FamiliarContent.FAMILIAR.get().create(level);
        if (b == null) return;
        Vec3 back = Vec3.directionFromRotation(0, sp.getYRot()).scale(-1.0);
        Vec3 pos = sp.position().add(back).add(0, d.form().flying ? sp.getBbHeight() * 0.8 : 0.1, 0);
        b.moveTo(pos.x, pos.y, pos.z, sp.getYRot(), 0);
        b.bind(sp, d);
        if (!level.noCollision(b)) b.moveTo(sp.getX(), sp.getY() + (d.form().flying ? sp.getBbHeight() * 0.8 : 0.1), sp.getZ(), sp.getYRot(), 0);
        // Entra no mapa ANTES de entrar no mundo: o corpo confere se é o oficial já no primeiro tick
        BODIES.put(sp.getUUID(), b);
        if (!level.addFreshEntity(b)) {
            BODIES.remove(sp.getUUID());
            return;
        }
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.3, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
    }

    private static void removeBody(UUID owner, boolean poof) {
        FamiliarEntity b = BODIES.remove(owner);
        if (b == null || b.isRemoved()) return;
        if (poof && b.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
            sl.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.5F, 1.5F);
        }
        b.discard();
    }

    // =====================================================================================
    //  Ritual de despertar
    // =====================================================================================
    /** Há uma fogueira ACESA a até 4 blocos? (vale dos dois lados: o cliente usa para não começar a animação à toa) */
    public static boolean nearCampfire(Player p) {
        Level level = p.level();
        BlockPos c = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) return true;
        }
        return false;
    }

    /** O que falta para o ritual (null = pode acender). */
    @Nullable
    public static Component ritualProblem(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        if (md.path() == MagicPath.NONE) return Component.translatable("familiars.isekai_mahou.ritual.need_class");
        if (md.level() < AWAKEN_LEVEL) return Component.translatable("familiars.isekai_mahou.ritual.need_level", AWAKEN_LEVEL);
        if (!nearCampfire(sp)) return Component.translatable("familiars.isekai_mahou.ritual.need_fire");
        return null;
    }

    /**
     * O incenso terminou de queimar: desperta o familiar; se o jogador já tem um, renova o vínculo e a forma se
     * ajusta NA HORA a quem ele é agora (em vez de esperar a mudança lenta).
     */
    public static void completeRitual(ServerPlayer sp) {
        FamiliarData d = FamiliarContent.data(sp);
        if (!d.awakened()) {
            awaken(sp, null);
            return;
        }
        d.addBond(10);
        FamiliarForm before = d.form();
        drift(sp, d, true);
        if (d.form() == before) {
            sp.displayClientMessage(Component.translatable("familiars.isekai_mahou.ritual.same", displayName(d)).withColor(d.form().rgb), true);
        }
        checkStage(sp, d);
    }

    /** Desperta o familiar ({@code forced} = forma escolhida por comando; null = pelo jeito de jogar). */
    public static boolean awaken(ServerPlayer sp, @Nullable FamiliarForm forced) {
        FamiliarData d = FamiliarContent.data(sp);
        if (d.awakened()) return false;
        FamiliarForm form = forced != null ? forced : FamiliarShaping.evaluate(sp).best();
        d.awaken(form, sp.serverLevel().getGameTime());
        MagicData md = MagicApi.get(sp);
        md.setFlag(FLAG_AWAKENED, true);
        if (md.counter(COUNTER_STAGE) < 1) MagicApi.addCounter(sp, COUNTER_STAGE, 1 - md.counter(COUNTER_STAGE));
        ensureBody(sp, d);
        burst(sp, form, 40);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.9F);
        sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        sp.sendSystemMessage(Component.translatable("familiars.isekai_mahou.msg.awakened", form.displayName(1).withColor(form.rgb)));
        Hints.show(sp, "familiar_awakened");
        return true;
    }

    // =====================================================================================
    //  Estágios
    // =====================================================================================
    static int stageFor(int level, int bond) {
        if (level >= STAGE3_LEVEL && bond >= STAGE3_BOND) return 3;
        if (level >= STAGE2_LEVEL && bond >= STAGE2_BOND) return 2;
        return 1;
    }

    /** Sobe UM estágio se o dono já cumpre os requisitos (nunca desce). */
    static void checkStage(ServerPlayer sp, FamiliarData d) {
        if (!d.awakened() || d.stage() >= FamiliarForm.MAX_STAGE) return;
        if (stageFor(MagicApi.get(sp).level(), d.bond()) > d.stage()) setStage(sp, d, d.stage() + 1, true);
    }

    static void setStage(ServerPlayer sp, FamiliarData d, int stage, boolean announce) {
        int old = d.stage();
        closeStash(sp);
        d.setStage(stage);
        returnOverflow(sp, d);
        MagicData md = MagicApi.get(sp);
        if (md.counter(COUNTER_STAGE) < d.stage()) MagicApi.addCounter(sp, COUNTER_STAGE, d.stage() - md.counter(COUNTER_STAGE));
        FamiliarEntity b = body(sp);
        if (b != null) {
            b.apply(d);
            b.playAction("happy");
        }
        if (!announce || d.stage() <= old) return;
        burst(sp, d.form(), 50);
        sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        sp.sendSystemMessage(Component.translatable("familiars.isekai_mahou.msg.evolved",
                d.form().displayName(old).withColor(d.form().rgb), d.form().displayName(d.stage()).withColor(d.form().rgb)));
    }

    // =====================================================================================
    //  "Vão mudando com você"
    // =====================================================================================
    /** Reavalia o jeito de jogar. {@code force} = muda já se outra forma combina mais (incenso/comando). */
    static void drift(ServerPlayer sp, FamiliarData d, boolean force) {
        FamiliarShaping.Result r = FamiliarShaping.evaluate(sp);
        FamiliarForm best = r.best();
        if (best == d.form()) {
            d.setCandidate("", 0);
            return;
        }
        // Só conta se a outra forma combina BEM mais (senão o familiar ficaria trocando por qualquer coisa)
        if (!force && r.score(best) < r.score(d.form()) * 1.15F + 4F) {
            d.setCandidate("", 0);
            return;
        }
        int streak = best.id.equals(d.candidate()) ? d.candidateStreak() + 1 : 1;
        long now = sp.serverLevel().getGameTime();
        if (force || streak >= DRIFT_NEEDED && now - d.lastShift() >= MIN_SHIFT_GAP) setForm(sp, d, best, true);
        else d.setCandidate(best.id, Math.min(streak, DRIFT_NEEDED));
    }

    static void setForm(ServerPlayer sp, FamiliarData d, FamiliarForm form, boolean announce) {
        if (form == d.form()) return;
        closeStash(sp);
        d.setForm(form, sp.serverLevel().getGameTime());
        returnOverflow(sp, d);
        // Corpo novo: quem anda e quem voa se movem de jeitos diferentes
        removeBody(sp.getUUID(), true);
        ensureBody(sp, d);
        if (!announce) return;
        burst(sp, form, 40);
        sp.playNotifySound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.7F, 1.3F);
        sp.sendSystemMessage(Component.translatable("familiars.isekai_mahou.msg.shifted", form.displayName(d.stage()).withColor(form.rgb)));
        Hints.show(sp, "familiar_shifted");
    }

    // =====================================================================================
    //  Carinho, chamar/guardar, nome
    // =====================================================================================
    static void pet(ServerPlayer sp, FamiliarEntity body) {
        FamiliarData d = FamiliarContent.data(sp);
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + body.getBbHeight() + 0.2, body.getZ(), 2, 0.2, 0.1, 0.2, 0);
        body.playAction("happy");
        body.playAmbientSound();
        // Carinho rende vínculo uma vez por dia do mundo (não dá para "farmar" clicando)
        int day = MagicApi.worldDay(sp);
        if (d.petDay() != day) {
            d.setPetDay(day);
            d.addBond(2);
            checkStage(sp, d);
        }
        if (d.help()) FamiliarHelp.active(body, sp, d, false);
        Hints.show(sp, "familiar_stash");
    }

    static void toggleSummon(ServerPlayer sp) {
        FamiliarData d = FamiliarContent.data(sp);
        if (!d.awakened()) return;
        d.setSummoned(!d.summoned());
        if (d.summoned()) {
            ensureBody(sp, d);
        } else {
            closeStash(sp);
            removeBody(sp.getUUID(), true);
        }
        sp.displayClientMessage(Component.translatable(d.summoned() ? "familiars.isekai_mahou.msg.called"
                : "familiars.isekai_mahou.msg.dismissed", displayName(d)).withColor(d.form().rgb), true);
    }

    static void onAction(ServerPlayer sp, FamiliarNetwork.Action a) {
        FamiliarData d = FamiliarContent.data(sp);
        switch (a.op()) {
            case FamiliarNetwork.Action.SYNC -> sync(sp, d);
            case FamiliarNetwork.Action.TOGGLE_SUMMON -> toggleSummon(sp);
            case FamiliarNetwork.Action.TOGGLE_HELP -> {
                if (d.awakened()) d.setHelp(!d.help());
            }
            case FamiliarNetwork.Action.RENAME -> {
                if (!d.awakened()) return;
                d.setName(cleanName(a.text()));
                FamiliarEntity b = body(sp);
                if (b != null) b.apply(d);
            }
            case FamiliarNetwork.Action.STASH -> openStash(sp);
            case FamiliarNetwork.Action.FORGET_SPELL -> FamiliarSpell.teach(sp, null);
            default -> { }
        }
    }

    private static String cleanName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < FamiliarData.MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c >= ' ' && c != '§' && c != 127) sb.append(c);
        }
        return sb.toString().trim();
    }

    /** Nome a mostrar: o que o jogador deu ou o nome da forma no estágio atual. */
    static MutableComponent displayName(FamiliarData d) {
        return d.name().isEmpty() ? d.form().displayName(d.stage()) : Component.literal(d.name());
    }

    // =====================================================================================
    //  Bolsa ("carregar itens")
    // =====================================================================================
    /** Inventário da bolsa: uma janela sobre a lista guardada nos dados do jogador. */
    private static final class Stash extends SimpleContainer {
        private final FamiliarData data;

        Stash(FamiliarData data, int size) {
            super(size);
            this.data = data;
            // Direto na lista (setItem chamaria setChanged e gravaria a bolsa ainda pela metade por cima dos dados)
            for (int i = 0; i < size; i++) getItems().set(i, data.stash().get(i));
        }

        @Override
        public void setChanged() {
            super.setChanged();
            NonNullList<ItemStack> list = data.stash();
            for (int i = 0; i < getContainerSize(); i++) list.set(i, getItem(i));
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && FamiliarContent.data(player) == data && data.summoned();
        }
    }

    static void openStash(ServerPlayer sp) {
        FamiliarData d = FamiliarContent.data(sp);
        if (!d.awakened()) return;
        FamiliarEntity b = body(sp);
        if (b == null || b.distanceToSqr(sp) > 8 * 8) {
            sp.displayClientMessage(Component.translatable("familiars.isekai_mahou.msg.need_call").withColor(0xA89EC0), true);
            return;
        }
        int rows = d.form().stashRows(d.stage());
        MenuType<ChestMenu> type = rows >= 3 ? MenuType.GENERIC_9x3 : rows == 2 ? MenuType.GENERIC_9x2 : MenuType.GENERIC_9x1;
        Stash stash = new Stash(d, rows * 9);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(type, id, inv, stash, rows),
                Component.translatable("familiars.isekai_mahou.stash", displayName(d))));
    }

    /** Fecha a bolsa aberta (antes de mexer no tamanho dela: evita duplicar itens). */
    private static void closeStash(ServerPlayer sp) {
        if (sp.containerMenu instanceof ChestMenu cm && cm.getContainer() instanceof Stash) sp.closeContainer();
    }

    /** A bolsa encolheu (mudou de forma): o que não cabe mais volta para o jogador. */
    private static void returnOverflow(ServerPlayer sp, FamiliarData d) {
        NonNullList<ItemStack> list = d.stash();
        for (int i = d.form().stashRows(d.stage()) * 9; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s.isEmpty()) continue;
            list.set(i, ItemStack.EMPTY);
            sp.getInventory().placeItemBackInInventory(s);
        }
    }

    /** Devolve TUDO da bolsa ao jogador (comando de reset). */
    static void returnAll(ServerPlayer sp, FamiliarData d) {
        closeStash(sp);
        NonNullList<ItemStack> list = d.stash();
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s.isEmpty()) continue;
            list.set(i, ItemStack.EMPTY);
            sp.getInventory().placeItemBackInInventory(s);
        }
    }

    static void reset(ServerPlayer sp) {
        FamiliarData d = FamiliarContent.data(sp);
        returnAll(sp, d);
        removeBody(sp.getUUID(), true);
        d.reset();
        MagicApi.get(sp).setFlag(FLAG_AWAKENED, false);
    }

    // =====================================================================================
    //  Sincronização e efeitos
    // =====================================================================================
    static void sync(ServerPlayer sp, FamiliarData d) {
        if (!d.awakened()) {
            PacketDistributor.sendToPlayer(sp, FamiliarNetwork.State.EMPTY);
            return;
        }
        int nextLevel = d.stage() == 1 ? STAGE2_LEVEL : d.stage() == 2 ? STAGE3_LEVEL : 0;
        int nextBond = d.stage() == 1 ? STAGE2_BOND : d.stage() == 2 ? STAGE3_BOND : 0;
        List<FamiliarNetwork.Reason> reasons = new ArrayList<>();
        for (FamiliarShaping.Reason r : FamiliarShaping.reasons(sp, d.form())) reasons.add(new FamiliarNetwork.Reason(r.kind(), r.arg()));
        int spellWait = d.spell().isEmpty() ? 0 : (int) Math.max(0, (d.spellReady() - sp.serverLevel().getGameTime() + 19) / 20);
        PacketDistributor.sendToPlayer(sp, new FamiliarNetwork.State(true, d.form().id, d.stage(), d.bond(), d.summoned(), d.help(),
                d.name(), nextLevel, nextBond, d.candidate(), d.candidateStreak(), DRIFT_NEEDED, reasons, d.spell(), spellWait));
    }

    /** Espiral de brilho na cor da forma em volta do jogador (despertar, evoluir, mudar). */
    private static void burst(ServerPlayer sp, FamiliarForm form, int count) {
        ServerLevel level = sp.serverLevel();
        Vector3f rgb = new Vector3f((form.rgb >> 16 & 255) / 255F, (form.rgb >> 8 & 255) / 255F, (form.rgb & 255) / 255F);
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.2F);
        for (int i = 0; i < count; i++) {
            double a = i * 0.45, h = 0.1 + 2.0 * i / count, r = 1.1 - 0.5 * i / count;
            level.sendParticles(dust, sp.getX() + Math.cos(a) * r, sp.getY() + h, sp.getZ() + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2, sp.getZ(), 12, 0.4, 0.5, 0.4, 0.03);
    }
}
