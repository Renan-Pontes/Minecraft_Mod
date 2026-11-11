package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.SpiritTrials;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bônus de classe e regras do Shinigami (passivas):
 * <ul>
 *   <li><b>Regeneração espiritual</b>: meditando (agachado e parado), cura vida, recupera Vigor e solta efeitos ruins;</li>
 *   <li><b>Reiatsu na lâmina</b>: cada golpe com a zanpakutō devolve 2% do Vigor; liberada, ×1,25 (Shikai) / ×1,6
 *   (Bankai) de dano e a assinatura do arquétipo em cada corte;</li>
 *   <li><b>Hohō</b> (passos): +4% de velocidade, +1% por rank do Caminho;</li>
 *   <li><b>Caminho da Zanpakutō</b>: técnicas com glifo Shinigami ganham +4% de poder por rank;</li>
 *   <li><b>Lâmina ligada à alma</b>: a zanpakutō não cai ao morrer (volta no renascimento).</li>
 * </ul>
 * Também: feitos dos ranks, magias iniciais nos slots, proteção do Mundo Interior (sem quebrar/colocar blocos, a
 * "morte" lá é cancelada) e a limpeza do estado em memória.
 * <p>
 * Técnicas avançadas: aprendizado automático ({@link ShinigamiTechniques#check}), a Máscara do Vazio e o Vasto Lorde
 * ({@link HollowForms}: poder, golpes ×1,5, voz dupla no chat e nas técnicas) e o tempo "sem poder" do Mugetsu.
 */
public final class ShinigamiEvents {
    public static final ResourceLocation HOHO_SPEED = IsekaiMahou.id("shinigami_hoho");

    /** Zanpakutō guardadas entre a morte e o renascimento. */
    private static final Map<UUID, List<ItemStack>> KEPT = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onDrops);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onClone);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onCastPre);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onCastPost);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(ShinigamiEvents::onChat);
        NeoForge.EVENT_BUS.addListener((DialogueEvent.Finished e) -> SpiritTrials.onDialogueFinished(e));
        NeoForge.EVENT_BUS.addListener(ShinigamiVfx::onPendingCast);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                syncPassives(sp);
                InnerWorld.onLogin(sp);
                ShinigamiTechniques.check(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) syncPassives(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && e.getFrom() == InnerWorld.KEY) SpiritTrials.abort(sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clearAll());
    }

    // =====================================================================================
    //  Golpes com a zanpakutō
    // =====================================================================================
    /** Golpe corpo a corpo de um jogador com a zanpakutō na mão principal. */
    private static ServerPlayer bladeAttacker(DamageSource src) {
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || !src.is(DamageTypes.PLAYER_ATTACK)) return null;
        return sp.getMainHandItem().getItem() instanceof ZanpakutoItem ? sp : null;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        // Vasto Lorde: golpes corpo a corpo ×1,5 (com qualquer arma)
        if (src.getEntity() instanceof LivingEntity att && src.getDirectEntity() == att && HollowForms.kind(att) == HollowForms.VASTO
                && (src.is(DamageTypes.PLAYER_ATTACK) || src.is(DamageTypes.MOB_ATTACK))) {
            e.setAmount(e.getAmount() * HollowForms.VASTO_MELEE);
        }
        ServerPlayer sp = bladeAttacker(src);
        if (sp == null) return;
        float mult = Release.meleeMultiplier(sp);
        if (mult != 1.0F) e.setAmount(e.getAmount() * mult);
    }

    private static void onDamagePost(LivingDamageEvent.Post e) {
        ServerPlayer sp = bladeAttacker(e.getSource());
        if (sp == null || !Shinigami.isShinigami(sp) || e.getNewDamage() <= 0) return;
        Shinigami.gainVigor(sp, 0.02F);
        LivingEntity target = e.getEntity();
        if (!target.isAlive() || !Release.released(sp)) return;
        Archetype a = Shinigami.archetype(MagicApi.get(sp));
        if (a != null) Signatures.apply(sp, target, a, Release.mode(sp) == Release.Mode.BANKAI ? 1.0F : 0.5F, 60);
    }

    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        // "morte" no Mundo Interior: cancelada (perder a prova só leva de volta à praça)
        if (dead instanceof ServerPlayer sp && InnerWorld.is(sp.level())) {
            e.setCanceled(true);
            SpiritTrials.onFatal(sp);
            return;
        }
        ServerPlayer killer = bladeAttacker(e.getSource());
        if (killer != null && Shinigami.isShinigami(killer)) countKill(killer);
        Signatures.forget(dead.getUUID());
    }

    private static void countKill(ServerPlayer sp) {
        MagicApi.addCounter(sp, Shinigami.KILLS, 1);
        ShinigamiRanks.hintIfReady(sp);
    }

    /** A zanpakutō ligada à alma não cai: volta no renascimento. */
    private static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Shinigami.isShinigami(sp)) return;
        Iterator<ItemEntity> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity ie = it.next();
            ItemStack st = ie.getItem();
            if (st.getItem() instanceof ZanpakutoItem && sp.getUUID().equals(ZanpakutoItem.soul(st))) {
                ItemStack keep = st.copy();
                ZanpakutoItem.setRelease(keep, 0, ZanpakutoItem.archetype(keep), 0L);
                KEPT.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>()).add(keep);
                it.remove();
            }
        }
    }

    private static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        List<ItemStack> kept = KEPT.remove(sp.getUUID());
        if (kept == null) return;
        for (ItemStack st : kept) {
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
        sp.displayClientMessage(Shinigami.msg("blade_kept").withColor(Shinigami.RGB), true);
    }

    // =====================================================================================
    //  Conjuração: rank, feitos
    // =====================================================================================
    private static void onCastPre(SpellCastEvent.Pre e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || !ctx.isPlayerCast()) return;
        if (!Shinigami.hasShinigamiGlyph(ctx.recipe())) return;
        // depois do Mugetsu: sem poder espiritual
        if (ShinigamiTechniques.blockIfPowerless(sp)) {
            e.setCanceled(true);
            return;
        }
        float mult = HollowForms.castPower(sp);
        if (Shinigami.isShinigami(sp)) mult *= Shinigami.rankPower(MagicApi.get(sp));
        if (mult != 1.0F) ctx.setPowerMult(ctx.powerMult() * mult);
    }

    private static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer sp)) return;
        // voz dupla: com a máscara, cada técnica ecoa num tom grave e distorcido
        if (HollowForms.masked(sp)) {
            ShinigamiFx.sound(sp.serverLevel(), sp.position(), SoundEvents.EVOKER_CAST_SPELL, 0.6F, 0.5F);
            ShinigamiFx.sound(sp.serverLevel(), sp.position(), SoundEvents.WARDEN_AMBIENT, 0.25F, 1.6F);
        }
        if (!Shinigami.isShinigami(sp)) return;
        if (Shinigami.hasKido(e.getContext().recipe())) MagicApi.addCounter(sp, Shinigami.KIDO, 1);
    }

    /** Voz dupla: com a máscara, o que o jogador fala ecoa distorcido em vermelho-escuro. */
    private static void onChat(ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (sp == null || !HollowForms.masked(sp)) return;
        String raw = e.getRawText();
        if (raw == null || raw.isBlank()) return;
        String echo = raw.length() > 64 ? raw.substring(0, 64) : raw;
        e.setMessage(e.getMessage().copy().append(Component.literal("  ⟪" + echo.toUpperCase(java.util.Locale.ROOT) + "⟫")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
    }

    private static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.killed() || !(e.ctx().caster() instanceof ServerPlayer sp) || !Shinigami.isShinigami(sp)) return;
        if (Shinigami.hasShinigamiGlyph(e.ctx().recipe())) countKill(sp);
    }

    // =====================================================================================
    //  Ticks
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        ShinigamiTasks.tick();
        Release.tick(server);
        PetalSwarm.tick(server);
        BankaiState.tick(server);
        Barriers.tick(server);
        HollowForms.tick(server);
        Meditation.tick(server);
        InnerWorld.tick(server);
        SpiritTrials.tick(server);
        if (server.getTickCount() % 200 == 0) Signatures.purge(server.overworld().getGameTime());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        ShinigamiTechniques.secondTick(sp);
        if (Shinigami.isShinigami(sp)) Meditation.spiritualRegen(sp);
        if (sp.tickCount % 100 == 0) syncPassives(sp);
    }

    /** Hohō: +4% de velocidade, +1% por rank (só Shinigami). */
    public static void syncPassives(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        double v = d.path() == MagicPath.SHINIGAMI ? 0.04 + 0.01 * Shinigami.rank(d) : 0;
        setModifier(sp.getAttribute(Attributes.MOVEMENT_SPEED), HOHO_SPEED, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void setModifier(AttributeInstance inst, ResourceLocation id, double value, AttributeModifier.Operation op) {
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (value == 0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && cur.amount() == value && cur.operation() == op) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, value, op));
    }

    // =====================================================================================
    //  Classe escolhida: glifos e magias iniciais; avisos de rank
    // =====================================================================================
    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        ServerPlayer sp = e.getPlayer();
        MagicData d = MagicApi.get(sp);
        syncPassives(sp);
        if (d.path() != MagicPath.SHINIGAMI) return;
        for (String g : Shinigami.STARTER_GLYPHS) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        boolean pt = sp.clientInformation().language().toLowerCase(java.util.Locale.ROOT).startsWith("pt");
        List<SpellRecipe> starters = List.of(
                new SpellRecipe(List.of(IsekaiMahou.id(Shinigami.ZANJUTSU), IsekaiMahou.id("arcane_damage")),
                        pt ? "Corte Espiritual" : "Spirit Slash", 0xD9483B, ""),
                new SpellRecipe(List.of(IsekaiMahou.id(Shinigami.SHUNPO), IsekaiMahou.id("arcane_damage")),
                        pt ? "Passo Cortante" : "Flash Cut", 0x1A1622, ""),
                new SpellRecipe(List.of(IsekaiMahou.id(Shinigami.HADO_4), IsekaiMahou.id("arcane_damage")),
                        pt ? "Hadō nº 4: Raio Branco" : "Hadō #4: White Lightning", 0xF4F6FF,
                        pt ? "Caminho da destruição, número quatro... Raio Branco!" : "Way of Destruction, number four... White Lightning!"));
        // só troca as magias genéricas que o núcleo acabou de gravar (nunca as do jogador)
        boolean fresh = true;
        for (int l = 1; l < MagicData.LOADOUTS; l++) fresh &= d.loadoutEmpty(l);
        if (!fresh) return;
        for (int i = 0; i < starters.size(); i++) d.setSlotRecipe(0, i, MoveTrigger.NORMAL, starters.get(i));
        d.markDirty();
        if (!Onboarding.quiet(sp)) sp.sendSystemMessage(Shinigami.msg("welcome").withColor(Shinigami.RGB));
    }

    private static void onCounter(MagicProgressEvent.Counter e) {
        String k = e.key;
        if (k.equals(Shinigami.KIDO) || k.equals(Shinigami.SHIKAI_HITS) || k.equals(Shinigami.RELEASES) || k.startsWith("boss.")) {
            ShinigamiRanks.hintIfReady(e.getPlayer());
        }
        if (k.equals(Shinigami.RANK)) syncPassives(e.getPlayer());
        if (k.equals(Shinigami.RANK) || k.equals(Shinigami.KIDO) || k.equals(Shinigami.VISORED_USES) || k.equals(Shinigami.GETSUGA_USES)
                || k.equals(Shinigami.RELEASES) || k.startsWith("boss.")) {
            ShinigamiTechniques.check(e.getPlayer());
        }
    }

    // =====================================================================================
    //  Mundo Interior: a paisagem é a alma — não se quebra nem se constrói nela
    // =====================================================================================
    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() != null && !e.getPlayer().getAbilities().instabuild && e.getLevel() instanceof net.minecraft.world.level.Level l && InnerWorld.is(l)) {
            e.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        Entity who = e.getEntity();
        if (e.getLevel() instanceof net.minecraft.world.level.Level l && InnerWorld.is(l)
                && !(who instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild)) {
            e.setCanceled(true);
        }
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    private static void forget(Entity e) {
        UUID id = e.getUUID();
        if (e instanceof ServerPlayer sp) {
            SpiritTrials.forget(id);
            Release.seal(sp, false);
        }
        Release.forget(id);
        PetalSwarm.forget(id);
        BankaiState.forget(id);
        HollowForms.forget(e);
        Barriers.forgetOwner(id);
        Meditation.forget(id);
        InnerWorld.forget(id);
    }

    private static void clearAll() {
        KEPT.clear();
        Release.clear();
        PetalSwarm.clear();
        BankaiState.clear();
        HollowForms.clear();
        Barriers.clear();
        Meditation.clear();
        Signatures.clear();
        ShinigamiTasks.clear();
        InnerWorld.clear();
        SpiritTrials.clear();
        ShinigamiArmor.clear();
    }

    private ShinigamiEvents() {}
}
