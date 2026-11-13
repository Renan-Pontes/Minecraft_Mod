package com.renanpontes.isekaimahou.feature.legends_f.necro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * O Exército das Sombras (homenagem a Solo Leveling): a lista PERMANENTE de soldados de cada jogador fica guardada na
 * sombra dele (anexo {@code shadow_army}, sobrevive à morte). Os soldados no mundo são só "manifestações" temporárias
 * (entidades que não são salvas): saem da sombra com o Comando ou o "Levante-se!", e voltam para ela ao serem
 * derrotados (60 s regenerando), quando o dono manda, quando falta mana para mantê-los ou quando o dono sai.
 */
public final class ShadowArmy {
    /** Ticks que um soldado derrotado leva para se refazer na sombra. */
    public static final int REGEN_TICKS = 1200;
    /** Mana (antiga, ×MANA_SCALE) por soldado manifestado por segundo. */
    public static final float UPKEEP = 0.4F;
    /** Mana (antiga, ×MANA_SCALE) para chamar cada soldado para fora da sombra. */
    public static final float SUMMON_COST = 3.0F;

    private static final Map<UUID, Map<Integer, ShadowSoldierEntity>> LIVE = new HashMap<>();
    private static final Set<UUID> HOLD = new HashSet<>();
    private static final Map<UUID, Integer> FOCUS = new HashMap<>();
    private static final Map<UUID, long[]> LAST_ATTACKED = new HashMap<>();

    // =====================================================================================
    //  Lista permanente (anexo)
    // =====================================================================================

    static CompoundTag data(ServerPlayer sp) {
        return sp.getData(LfRegistry.ARMY);
    }

    static ListTag list(ServerPlayer sp) {
        CompoundTag d = data(sp);
        if (!d.contains("list", Tag.TAG_LIST)) d.put("list", new ListTag());
        return d.getList("list", Tag.TAG_COMPOUND);
    }

    /** Tamanho máximo do exército: 3 + Círculo + 2 × rank do ritual da classe (máx. 30). */
    public static int cap(ServerPlayer sp) {
        return Math.min(30, 3 + MagicApi.get(sp).circle() + 2 * LfUtil.rank(sp));
    }

    public static int size(ServerPlayer sp) {
        return list(sp).size();
    }

    public static List<CompoundTag> roster(ServerPlayer sp) {
        ListTag l = list(sp);
        List<CompoundTag> out = new ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    @Nullable
    public static CompoundTag entry(ServerPlayer sp, int id) {
        ListTag l = list(sp);
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("id") == id) return l.getCompound(i);
        }
        return null;
    }

    /** Patente pela força da criatura: 0 Soldado, 1 Cavaleiro, 2 Elite, 3 Marechal (chefes). */
    public static int grade(float maxHp, boolean boss) {
        if (boss) return 3;
        if (maxHp >= 120F) return 2;
        if (maxHp >= 45F) return 1;
        return 0;
    }

    /** Entra para o exército. Devolve a entrada nova, ou null se o exército está cheio. */
    @Nullable
    public static CompoundTag enlist(ServerPlayer sp, ShadowRemnants.Remnant r) {
        if (size(sp) >= cap(sp)) return null;
        CompoundTag d = data(sp);
        int id = d.getInt("next") + 1;
        d.putInt("next", id);
        CompoundTag e = new CompoundTag();
        e.putInt("id", id);
        e.putString("type", r.type);
        if (r.customName != null) e.putString("name", r.customName);
        e.putFloat("hp", r.maxHp);
        e.putFloat("atk", r.attack);
        e.putFloat("w", r.width);
        e.putFloat("h", r.height);
        e.putBoolean("boss", r.boss);
        e.putInt("grade", grade(r.maxHp, r.boss));
        e.putInt("kills", 0);
        e.putLong("cd", 0L);
        list(sp).add(e);
        return e;
    }

    /** Liberta um soldado para sempre (abre a vaga). */
    public static boolean release(ServerPlayer sp, int id) {
        ListTag l = list(sp);
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("id") == id) {
                l.remove(i);
                ShadowSoldierEntity live = liveMap(sp.getUUID()).remove(id);
                if (live != null && !live.isRemoved()) live.vanish(true);
                return true;
            }
        }
        return false;
    }

    public static void clearCooldowns(ServerPlayer sp) {
        for (CompoundTag e : roster(sp)) e.putLong("cd", 0L);
    }

    public static void addKill(ServerPlayer sp, int id) {
        CompoundTag e = entry(sp, id);
        if (e != null) e.putInt("kills", e.getInt("kills") + 1);
    }

    // =====================================================================================
    //  Manifestações (entidades no mundo)
    // =====================================================================================

    private static Map<Integer, ShadowSoldierEntity> liveMap(UUID owner) {
        return LIVE.computeIfAbsent(owner, k -> new HashMap<>());
    }

    /** Soldados manifestados agora (vivos, no mundo). */
    public static List<ShadowSoldierEntity> live(UUID owner) {
        Map<Integer, ShadowSoldierEntity> m = LIVE.get(owner);
        if (m == null) return List.of();
        m.values().removeIf(s -> s == null || s.isRemoved());
        return new ArrayList<>(m.values());
    }

    public static void track(ShadowSoldierEntity s) {
        if (s.getOwnerUUID() != null) liveMap(s.getOwnerUUID()).put(s.rosterId(), s);
    }

    public static void forget(ShadowSoldierEntity s) {
        Map<Integer, ShadowSoldierEntity> m = s.getOwnerUUID() == null ? null : LIVE.get(s.getOwnerUUID());
        if (m != null && m.get(s.rosterId()) == s) m.remove(s.rosterId());
    }

    /** Chama para fora da sombra um soldado da lista, em {@code at}. Null se não deu (regenerando, já fora...). */
    @Nullable
    public static ShadowSoldierEntity manifest(ServerPlayer sp, CompoundTag e, Vec3 at, boolean rise) {
        int id = e.getInt("id");
        ShadowSoldierEntity old = liveMap(sp.getUUID()).get(id);
        if (old != null && !old.isRemoved()) return null;
        ServerLevel level = sp.serverLevel();
        ShadowSoldierEntity s = LfRegistry.SHADOW_SOLDIER.get().create(level);
        if (s == null) return null;
        s.setup(sp, e, MonarchState.active(sp));
        s.moveTo(at.x, at.y, at.z, sp.getYRot(), 0F);
        s.placeFree(level, at);
        s.setRising(rise ? 30 : 12);
        level.addFreshEntity(s);
        track(s);
        return s;
    }

    /** Chama todos os disponíveis (fora da regeneração). {@code free}: sem custo de mana (Monarca). */
    public static int manifestAll(ServerPlayer sp, boolean free) {
        long now = sp.serverLevel().getGameTime();
        List<CompoundTag> ready = new ArrayList<>();
        int regen = 0;
        for (CompoundTag e : roster(sp)) {
            ShadowSoldierEntity s = liveMap(sp.getUUID()).get(e.getInt("id"));
            if (s != null && !s.isRemoved()) continue;
            if (e.getLong("cd") > now) {
                regen++;
                continue;
            }
            ready.add(e);
        }
        if (ready.isEmpty()) {
            if (regen > 0) {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.army_regen", regen)
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            return 0;
        }
        int n = 0;
        Vec3 back = sp.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        back = back.normalize().scale(-1);
        for (CompoundTag e : ready) {
            if (!free && !LfUtil.spend(sp, SUMMON_COST)) {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.army_no_mana").withStyle(ChatFormatting.RED));
                break;
            }
            double ang = (n - (ready.size() - 1) * 0.5) * 0.55;
            double r = 2.0 + (n / 6) * 1.6;
            Vec3 dir = back.yRot((float) ang);
            Vec3 at = LfUtil.groundBelow(sp.serverLevel(), sp.position().add(dir.scale(r)).add(0, 1.5, 0), 6);
            if (manifest(sp, e, at, false) != null) n++;
        }
        if (n > 0) {
            LfUtil.sound(sp.serverLevel(), sp.position(), SoundEvents.SCULK_CATALYST_BLOOM, 1.2F, 0.5F);
            LfUtil.ring(sp.serverLevel(), sp.position().add(0, 0.1, 0), 0x6A3DD8, 18, 6F);
        }
        return n;
    }

    /** Todos de volta à sombra. */
    public static int recallAll(UUID owner, boolean fx) {
        int n = 0;
        for (ShadowSoldierEntity s : live(owner)) {
            s.vanish(fx);
            n++;
        }
        HOLD.remove(owner);
        FOCUS.remove(owner);
        return n;
    }

    /** O soldado caiu: volta para a sombra e regenera por 60 s. */
    public static void onDown(ShadowSoldierEntity s, @Nullable ServerPlayer owner) {
        if (owner == null) return;
        CompoundTag e = entry(owner, s.rosterId());
        if (e == null) return;
        e.putLong("cd", owner.serverLevel().getGameTime() + REGEN_TICKS);
        LfUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_f.soldier_down", s.getDisplayName(), REGEN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    // =====================================================================================
    //  Ordens
    // =====================================================================================

    public static boolean holding(UUID owner) {
        return HOLD.contains(owner);
    }

    /** Alterna "seguir" / "guardar posição". Devolve true se agora estão guardando posição. */
    public static boolean toggleHold(UUID owner) {
        boolean hold = HOLD.add(owner);
        if (!hold) HOLD.remove(owner);
        for (ShadowSoldierEntity s : live(owner)) s.setGuardHere(hold);
        return hold;
    }

    public static void focus(UUID owner, LivingEntity target) {
        FOCUS.put(owner, target.getId());
        HOLD.remove(owner);
        for (ShadowSoldierEntity s : live(owner)) {
            s.setGuardHere(false);
            s.setTarget(target);
        }
    }

    @Nullable
    public static LivingEntity focusTarget(ServerLevel level, UUID owner) {
        Integer id = FOCUS.get(owner);
        if (id == null) return null;
        Entity e = level.getEntity(id);
        if (e instanceof LivingEntity le && le.isAlive()) return le;
        FOCUS.remove(owner);
        return null;
    }

    /** O dono acabou de ferir {@code target} (golpe, flecha ou magia): o exército vai junto. */
    public static void ownerAttacked(UUID owner, LivingEntity target, long now) {
        if (!LIVE.containsKey(owner)) return;
        LAST_ATTACKED.put(owner, new long[] {target.getId(), now});
    }

    @Nullable
    public static LivingEntity lastAttacked(ServerLevel level, UUID owner, int withinTicks) {
        long[] v = LAST_ATTACKED.get(owner);
        if (v == null || level.getGameTime() - v[1] > withinTicks) return null;
        Entity e = level.getEntity((int) v[0]);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    // =====================================================================================
    //  Manutenção (a cada segundo)
    // =====================================================================================

    static void tick(MinecraftServer server) {
        if (LIVE.isEmpty() || server.overworld().getGameTime() % 20 != 0) return;
        Iterator<Map.Entry<UUID, Map<Integer, ShadowSoldierEntity>>> it = LIVE.entrySet().iterator();
        List<UUID> recall = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<Integer, ShadowSoldierEntity>> en = it.next();
            en.getValue().values().removeIf(s -> s == null || s.isRemoved());
            if (en.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || !sp.isAlive()) {
                recall.add(en.getKey());
                continue;
            }
            // Longe demais / outra dimensão: volta para a sombra (aparece de novo com o próximo Comando)
            for (ShadowSoldierEntity s : new ArrayList<>(en.getValue().values())) {
                if (s.level() != sp.level() || s.distanceToSqr(sp) > 96 * 96) s.vanish(false);
            }
            int count = en.getValue().size();
            if (count == 0 || MonarchState.active(sp)) continue;
            if (!LfUtil.spend(sp, UPKEEP * count)) {
                recall.add(en.getKey());
                LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.army_upkeep").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        for (UUID u : recall) recallAll(u, true);
        LAST_ATTACKED.keySet().removeIf(u -> !LIVE.containsKey(u));
    }

    /** Custo de manutenção por segundo de {@code n} soldados, em mana real (para mensagens). */
    public static int upkeepShown(int n) {
        return Math.round(UPKEEP * ResourceTraining.MANA_SCALE * n);
    }

    static void clear() {
        LIVE.clear();
        HOLD.clear();
        FOCUS.clear();
        LAST_ATTACKED.clear();
    }

    /** Efeito de saída da sombra (para quem chama manifest de fora). */
    public static void ariseFx(ServerLevel level, Vec3 at, float height) {
        LfUtil.fx(level, LfNet.ARISE, at, new Vec3(0, 1, 0), 0x6A3DD8, 34, Math.max(2.5F, height * 1.6F));
        // v1.3: a sombra rasga o chão (portal), sobe um pilar escuro e estoura em fiapos — "ARISE!"
        float r = Math.max(1.2F, height * 0.7F);
        com.renanpontes.isekaimahou.core.ElementFx.portal(level, at.add(0, 0.06, 0), new Vec3(0, 1, 0),
                com.renanpontes.isekaimahou.core.Element.SHADOW, 0x6A3DD8, r, 4, 30);
        com.renanpontes.isekaimahou.core.ElementFx.pillar(level, at, 0x3A1A7A, r * 0.55F, Math.max(4F, height * 3F), 26);
        com.renanpontes.isekaimahou.core.ElementFx.impactFx(level, at.add(0, 0.2, 0),
                com.renanpontes.isekaimahou.core.Element.SHADOW, 0x8A4FF0, 3, null, Math.max(1F, height * 0.6F));
    }

    private ShadowArmy() {}
}
