package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Ajudantes das condições de status, facções e do "Procurado" usados pelo HUD (cartão, barra de mana, Olho Analítico,
 * barras flutuantes e Janela de Status). Só lê dados já sincronizados no cliente.
 */
final class StatusView {
    /** Efeito "Procurado" do módulo cities (por id: o módulo pode não existir). */
    private static final ResourceLocation WANTED_ID = IsekaiMahou.id("wanted");
    @Nullable private static Optional<Holder<MobEffect>> wanted;

    /** Maior "ticks restantes" visto de cada condição de cada entidade (para a barrinha de tempo esvaziar). */
    private static final Map<Integer, int[]> MAX_TICKS = new HashMap<>();
    private static int pruneTimer;

    private StatusView() {}

    // =====================================================================================
    //  Condições
    // =====================================================================================
    /** Condições da entidade, sem "Molhado" quando ela está dentro d'água (óbvio, e não enche o lago de ícones). */
    static List<ClientStatuses.Entry> visible(Entity e) {
        List<ClientStatuses.Entry> all = ClientStatuses.get(e);
        if (all.isEmpty() || !e.isInWater()) return all;
        List<ClientStatuses.Entry> out = new ArrayList<>(all.size());
        for (ClientStatuses.Entry en : all) if (en.kind() != Statuses.Kind.WET) out.add(en);
        return out;
    }

    /**
     * Fração restante (0..1) da condição: compara com o maior tempo visto desde que ela apareceu (reaplicar renova).
     * Infinita = 1.
     */
    static float fraction(int entityId, ClientStatuses.Entry en) {
        if (en.ticksLeft() < 0) return 1F;
        int[] max = MAX_TICKS.computeIfAbsent(entityId, k -> new int[Statuses.Kind.values().length]);
        int i = en.kind().ordinal();
        if (en.ticksLeft() > max[i]) max[i] = en.ticksLeft();
        return max[i] <= 0 ? 0F : Math.min(1F, en.ticksLeft() / (float) max[i]);
    }

    /** Esquece os máximos das condições que a entidade não tem mais (a próxima aplicação começa do zero). */
    static void forgetMissing(int entityId, List<ClientStatuses.Entry> present) {
        int[] max = MAX_TICKS.get(entityId);
        if (max == null) return;
        boolean any = false;
        for (Statuses.Kind k : Statuses.Kind.values()) {
            boolean has = false;
            for (ClientStatuses.Entry en : present) if (en.kind() == k) has = true;
            if (!has) max[k.ordinal()] = 0;
            else any = true;
        }
        if (!any) MAX_TICKS.remove(entityId);
    }

    /**
     * Uma vez por tick: esquece as condições que acabaram (mesmo de quem não está sendo desenhado agora — senão a
     * próxima aplicação mais curta começaria com a barrinha pela metade) e tira entidades que sumiram do mundo.
     */
    static void tick(Minecraft mc) {
        if (mc.level == null) {
            MAX_TICKS.clear();
            return;
        }
        if (MAX_TICKS.isEmpty()) return;
        boolean prune = ++pruneTimer >= 40;
        if (prune) pruneTimer = 0;
        List<Integer> ids = new ArrayList<>(MAX_TICKS.keySet());
        for (int id : ids) {
            if (prune && mc.level.getEntity(id) == null) MAX_TICKS.remove(id);
            else forgetMissing(id, ClientStatuses.get(id));
        }
    }

    static void clear() {
        MAX_TICKS.clear();
        pruneTimer = 0;
    }

    /** Pisca nos últimos 2 s. */
    static boolean expiring(ClientStatuses.Entry en) {
        return en.ticksLeft() >= 0 && en.ticksLeft() < 40;
    }

    /** "∞", "6s", "1:05". */
    static String time(int ticks) {
        if (ticks < 0) return "∞";
        int s = (ticks + 19) / 20;
        if (s < 60) return s + "s";
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    /** Cor (ARGB) da condição. */
    static int color(Statuses.Kind k) {
        return 0xFF000000 | k.color();
    }

    /** Congelado / Petrificado (pedra): preso — destaque vermelho. */
    static boolean incapacitating(ClientStatuses.Entry en) {
        return en.kind() == Statuses.Kind.FROZEN || en.kind() == Statuses.Kind.STUNNED
                || en.kind() == Statuses.Kind.PETRIFIED && en.stacks() >= 3;
    }

    /** Ícone com alfa (o GuiGraphics.setColor tinge textura, fundo de reserva e número de acúmulos). */
    static void icon(GuiGraphics g, ClientStatuses.Entry en, int x, int y, int size, float alpha) {
        if (alpha <= 0.02F) return;
        g.setColor(1F, 1F, 1F, alpha);
        ClientStatuses.drawIcon(g, en.kind(), en.stacks(), x, y, size);
        g.setColor(1F, 1F, 1F, 1F);
    }

    // =====================================================================================
    //  "Procurado" (efeito isekai_mahou:wanted do módulo cities)
    // =====================================================================================
    @Nullable
    private static Holder<MobEffect> wantedEffect() {
        if (wanted == null) {
            wanted = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, WANTED_ID)).map(h -> (Holder<MobEffect>) h);
        }
        return wanted.orElse(null);
    }

    /** O jogador local está Procurado? (efeitos do próprio jogador chegam ao cliente) */
    static boolean isWanted(@Nullable Player p) {
        Holder<MobEffect> h = wantedEffect();
        return p != null && h != null && p.hasEffect(h);
    }

    /** Ticks restantes do Procurado (-1 = infinito, 0 = não está). */
    static int wantedTicks(@Nullable Player p) {
        Holder<MobEffect> h = wantedEffect();
        if (p == null || h == null) return 0;
        MobEffectInstance inst = p.getEffect(h);
        if (inst == null) return 0;
        return inst.isInfiniteDuration() ? -1 : inst.getDuration();
    }

    // =====================================================================================
    //  Facções
    // =====================================================================================
    /** Facção da criatura (FactionMember ou tags), ou null (sem facção / facção desconhecida no cliente). */
    @Nullable
    static Reputation.Faction factionOf(Entity e) {
        if (e instanceof Player) return null;
        try {
            String id = Reputation.factionOf(e);
            if (id == null || id.isEmpty()) return null;
            return Reputation.faction(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Entidades do módulo cities que reagem ao "Procurado": o guarda caça (e cobra a fiança), o cidadão foge.
     * Por id: o módulo pode não existir.
     */
    private static final java.util.Set<String> CITY_FOLK = java.util.Set.of("city_guard", "citizen");

    /** A criatura reage ao "Procurado"? (não marca goblins da Horda, aldeões nem mercadores) */
    static boolean reactsToWanted(Entity e) {
        ResourceLocation type = net.minecraft.world.entity.EntityType.getKey(e.getType());
        return IsekaiMahou.MODID.equals(type.getNamespace()) && CITY_FOLK.contains(type.getPath());
    }

    /** Emblema da facção (textura 32×32 da art) ou estandarte tingido na cor dela. */
    static void emblem(GuiGraphics g, Reputation.Faction f, int x, int y, int size, float alpha) {
        ResourceLocation tex = f.emblem();
        if (GlyphArt.exists(tex)) {
            g.setColor(1F, 1F, 1F, alpha);
            g.blit(tex, x, y, size, size, 0, 0, 32, 32, 32, 32);
            g.setColor(1F, 1F, 1F, 1F);
        } else {
            HudDraw.sprite(g, HudDraw.BANNER, x + (size - 7) / 2, y + (size - 7) / 2, HudDraw.fade(f.argb(), alpha));
        }
    }
}
