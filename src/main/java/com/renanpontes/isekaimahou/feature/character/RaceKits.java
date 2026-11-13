package com.renanpontes.isekaimahou.feature.character;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * v1.7 — kits das raças "de anime" que não ganharam classe própria ({@link SlimePredator} = Rimuru,
 * {@link KumoThreads} = Kumoko):
 * <ul>
 *   <li><b>Shift + tecla racial</b> ({@link #onShiftKey}): Aracnídeo = Fio de Aranha, Slime = relatório do Grande Sábio,
 *       Dracônico = Devorar Elemento; as outras raças continuam com a visão no escuro.</li>
 *   <li><b>Dracônico = Dragon Slayer</b> (Fairy Tail; sabor de Veldora): <b>Devorar Elemento</b> — come o elemento da
 *       ancestralidade por perto (fogo/lava/brasa, água/chuva, pedra/metal, ar livre, sol/luz, escuridão) e recupera
 *       vida, mana e fome; por 10 s fica <b>Saciado</b> (+15% com magias da ancestralidade). Recarga 30/30/20/20 s
 *       (Dragon Force: 6 s).</li>
 *   <li><b>Meio-Orc = Geld</b> (Tensura): <b>Faminto</b> — comer ou abater corpo a corpo dá 1 acúmulo de Fome Voraz
 *       (+2% de dano corpo a corpo cada, 30 s) até 2/3/4/5 (Desastre Orc: 8); abater "devora" (+1 de vida e de fome).</li>
 *   <li><b>Demônio = Anos Voldigoad</b> (Maou Gakuin no Futekigousha): <b>Olhos Mágicos da Destruição</b> (nível IV) —
 *       15% de chance de desfazer uma magia inimiga que te acertaria (1× a cada 20 s), com uma fala confiante; abater um
 *       chefe também rende uma (rara).</li>
 *   <li><b>Elfo = Frieren</b>: <b>Mana Oculta</b> (nível III) — agachado, a sua mana some (o mesmo efeito da magia
 *       "Ocultar Mana" dos lendários: monstros longe perdem o rastro, Detectar Mana não te vê e o 1º golpe surpreende).</li>
 * </ul>
 */
public final class RaceKits {
    private static final ResourceLocation ORC_HUNGER = IsekaiMahou.id("character.race.orc_hunger");
    private static final int HUNGER_TICKS = 30 * 20;
    private static final int FED_TICKS = 10 * 20;
    private static final int NULLIFY_GAP = 20 * 20;
    private static final int BOAST_LINES = 4;
    /** Efeito "Mana Oculta" do módulo legends_f (procurado pelo id: se o módulo não existir, a passiva só não faz nada). */
    private static final ResourceKey<MobEffect> MANA_CONCEALED = ResourceKey.create(net.minecraft.core.registries.Registries.MOB_EFFECT,
            IsekaiMahou.id("mana_concealed"));

    private record Hunger(int stacks, long until) {}

    private static final Map<UUID, Long> EAT_READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Hunger> HUNGER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NULLIFY_READY = new ConcurrentHashMap<>();
    /** Mana Oculta do Elfo: aplicada por nós / quando pode esconder de novo (depois de um golpe surpresa). */
    private static final Map<UUID, Boolean> CONCEAL_ON = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CONCEAL_READY = new ConcurrentHashMap<>();
    private static final int CONCEAL_GAP = 10 * 20;

    static void init() {
        NeoForge.EVENT_BUS.addListener(RaceKits::onEat);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceKits::onKill);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
    }

    static void forget(UUID id) {
        EAT_READY.remove(id);
        FED_UNTIL.remove(id);
        HUNGER.remove(id);
        NULLIFY_READY.remove(id);
        CONCEAL_ON.remove(id);
        CONCEAL_READY.remove(id);
    }

    static void clearAll() {
        EAT_READY.clear();
        FED_UNTIL.clear();
        HUNGER.clear();
        NULLIFY_READY.clear();
        CONCEAL_ON.clear();
        CONCEAL_READY.clear();
    }

    // =====================================================================================
    //  Shift + tecla racial
    // =====================================================================================
    static void onShiftKey(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        if (!d.created()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.not_created"), true);
            return;
        }
        switch (d.race()) {
            case ARACHNID -> KumoThreads.thread(sp);
            case SLIME -> SlimePredator.report(sp);
            case DRAGONBORN -> eatElement(sp, d);
            default -> RacePassives.toggleVisionKey(sp);
        }
    }

    // =====================================================================================
    //  Dracônico — Devorar Elemento (Dragon Slayer)
    // =====================================================================================
    static void eatElement(ServerPlayer sp, CharacterData d) {
        long now = RacePassives.now(sp);
        Long ready = EAT_READY.get(sp.getUUID());
        Element el = d.ancestry();
        if (ready != null && now < ready && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.dragon_eat_cooldown",
                    (int) Math.ceil((ready - now) / 20.0)).withColor(0xA89EC0), true);
            return;
        }
        Vec3 src = findSource(sp, el);
        if (src == null) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.dragon_eat_none",
                    Component.translatable("message.isekai_mahou.character.dragon_eat_where." + el.id)));
            return;
        }
        int lv = RaceProgress.level(sp);
        boolean force = RaceForms.formOf(sp) == Race.DRAGONBORN;
        EAT_READY.put(sp.getUUID(), now + (force ? 6 * 20 : (long) RacePassives.at(lv, 30, 30, 20, 20) * 20));
        FED_UNTIL.put(sp.getUUID(), now + FED_TICKS);
        sp.heal(RacePassives.at(lv, 3, 4, 5, 6));
        var md = MagicApi.get(sp);
        md.addMana(md.maxMana() * RacePassives.at(lv, 0.15F, 0.20F, 0.25F, 0.30F));
        sp.getFoodData().eat(2, 0.3F);
        if (el == Element.FIRE) sp.clearFire();
        ServerLevel level = sp.serverLevel();
        Vec3 mouth = sp.getEyePosition().add(sp.getLookAngle().scale(0.3)).subtract(0, 0.15, 0);
        RaceFx.line(level, src, mouth, ElementFx.dust(el, 1.2F));
        level.sendParticles(ElementFx.signature(el), src.x, src.y, src.z, 10, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ElementFx.dust(el, 1.0F), mouth.x, mouth.y, mouth.z, 8, 0.15, 0.15, 0.15, 0);
        RaceFx.sound(level, mouth, SoundEvents.GENERIC_EAT, 1.0F, 0.7F);
        RaceFx.sound(level, mouth, SoundEvents.PLAYER_BURP, 0.6F, 0.8F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.dragon_eat",
                el.displayName()).withColor(el.color), true);
    }

    /** Saciado (comeu o elemento nos últimos 10 s): magias da ancestralidade mais fortes. */
    static boolean fed(ServerPlayer sp) {
        Long until = FED_UNTIL.get(sp.getUUID());
        return until != null && RacePassives.now(sp) <= until;
    }

    /** Onde está o elemento para devorar (null = nada por perto). Varre um cubo pequeno só ao apertar a tecla. */
    @Nullable
    private static Vec3 findSource(ServerPlayer sp, Element el) {
        ServerLevel level = sp.serverLevel();
        BlockPos eye = BlockPos.containing(sp.getEyePosition());
        Vec3 c = RaceFx.center(sp);
        switch (el) {
            case FIRE -> {
                if (sp.isOnFire()) return c;
            }
            case WATER -> {
                if (level.isRainingAt(eye.above())) return sp.getEyePosition().add(0, 1.2, 0);
                if (sp.isInWater()) return c;
            }
            case WIND -> {
                // Ar livre: céu aberto acima (ou bem alto no mundo)
                if (level.canSeeSky(eye) || sp.getY() > 110) return sp.getEyePosition().add(sp.getLookAngle().scale(1.5));
                return null;
            }
            case LIGHT -> {
                if (level.isDay() && !level.isRaining() && level.canSeeSky(eye)) return sp.getEyePosition().add(0, 2.5, 0);
            }
            case SHADOW -> {
                if (level.getMaxLocalRawBrightness(eye) <= 5) return c.subtract(0, 0.6, 0);
                return null;
            }
            default -> { }
        }
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(eye.offset(-3, -3, -3), eye.offset(3, 2, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !matches(level, p, s, el)) continue;
            double dd = p.distToCenterSqr(c);
            if (dd < bestD) {
                bestD = dd;
                best = Vec3.atCenterOf(p);
            }
        }
        return best;
    }

    private static boolean matches(ServerLevel level, BlockPos p, BlockState s, Element el) {
        return switch (el) {
            case FIRE -> s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(s)
                    || s.is(Blocks.MAGMA_BLOCK);
            case WATER -> s.getFluidState().is(FluidTags.WATER);
            // Terra: pedra e metal (o Dragon Slayer de Ferro come até minério)
            case EARTH -> s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT)
                    || s.is(Blocks.IRON_BLOCK) || s.is(BlockTags.IRON_ORES) || s.is(Blocks.COBBLESTONE);
            case LIGHT -> s.getLightEmission(level, p) >= 12;
            default -> false;
        };
    }

    // =====================================================================================
    //  Meio-Orc — Faminto (Geld)
    // =====================================================================================
    /** +{@code n} acúmulos de Fome Voraz (renova os 30 s). */
    static void feed(ServerPlayer sp, int n) {
        CharacterData d = CharacterContent.data(sp);
        if (!d.is(Race.HALF_ORC) || n <= 0) return;
        boolean form = RaceForms.formOf(sp) == Race.HALF_ORC;
        int cap = form ? 8 : (int) RacePassives.at(RaceProgress.level(sp), 2, 3, 4, 5);
        Hunger h = HUNGER.get(sp.getUUID());
        int before = h == null ? 0 : h.stacks();
        int stacks = Math.min(cap, before + n);
        HUNGER.put(sp.getUUID(), new Hunger(stacks, RacePassives.now(sp) + HUNGER_TICKS));
        applyHunger(sp, stacks);
        ServerLevel level = sp.serverLevel();
        level.sendParticles(RaceFx.dust(0x8A1010, 0.9F), sp.getX(), sp.getY() + 1.0, sp.getZ(), 4 + stacks, 0.3, 0.4, 0.3, 0);
        if (stacks >= cap && before < cap) {
            // Só ao encher: sem uma mensagem por abate
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.orc_starved", stacks * 2)
                    .withColor(Race.HALF_ORC.rgb), true);
            RaceFx.sound(level, sp.position(), SoundEvents.RAVAGER_AMBIENT, 0.6F, 1.2F);
        }
    }

    /** Acúmulos atuais de Fome Voraz. */
    static int hunger(Player p) {
        Hunger h = HUNGER.get(p.getUUID());
        return h == null ? 0 : h.stacks();
    }

    private static void applyHunger(ServerPlayer sp, int stacks) {
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (inst == null) return;
        if (stacks <= 0) {
            inst.removeModifier(ORC_HUNGER);
            return;
        }
        // Transitório (não vai para o save): some sozinho se o servidor fechar
        inst.addOrUpdateTransientModifier(new AttributeModifier(ORC_HUNGER, stacks * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** A cada segundo: a Fome Voraz passa; a Mana Oculta do Elfo; o Olho Maligno e o Predador. */
    static void tickSecond(ServerPlayer sp, CharacterData d) {
        Hunger h = HUNGER.get(sp.getUUID());
        if (h != null && (RacePassives.now(sp) > h.until() || !d.is(Race.HALF_ORC))) {
            HUNGER.remove(sp.getUUID());
            applyHunger(sp, 0);
        }
        tickConceal(sp, d);
        KumoThreads.tickEvilEye(sp, d);
        SlimePredator.tickSecond(sp, d);
    }

    private static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        ItemStack it = e.getItem();
        if (it.isEmpty() || it.getFoodProperties(sp) == null) return;
        feed(sp, 1);
    }

    private static void onKill(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getSource().getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity victim = e.getEntity();
        if (victim == sp) return;
        CharacterData d = CharacterContent.data(sp);
        if (!d.created()) return;
        if (d.race() == Race.HALF_ORC && e.getSource().getDirectEntity() == sp && !(victim instanceof Player)) {
            // Devorar: o Orc se alimenta da presa
            if (sp.getHealth() < sp.getMaxHealth()) sp.heal(1F);
            sp.getFoodData().eat(1, 0.2F);
            feed(sp, 1);
        } else if (d.race() == Race.DEMON && Statuses.isBossLike(victim)) {
            boast(sp);
        }
    }

    // =====================================================================================
    //  Demônio — Olhos Mágicos da Destruição (Anos)
    // =====================================================================================
    /** Magia inimiga chegando num Demônio nível IV+: às vezes ela é destruída antes de acertar. */
    static void onSpellAtDemon(SpellDamageEvent.Pre e, ServerPlayer sp, int lv) {
        boolean form = RaceForms.formOf(sp) == Race.DEMON;
        if (lv < 4 && !form) return;
        if (e.ctx().casterPlayer() == sp || e.getAmount() <= 0) return;
        long now = RacePassives.now(sp);
        Long ready = NULLIFY_READY.get(sp.getUUID());
        if (ready != null && now < ready) return;
        if (sp.getRandom().nextFloat() >= (form ? 0.30F : 0.15F)) return;
        NULLIFY_READY.put(sp.getUUID(), now + NULLIFY_GAP);
        e.setAmount(0F);
        e.setCanceled(true);
        ServerLevel level = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        RaceFx.ring(level, RaceFx.center(sp), 1.3, 18, RaceFx.dust(0x1A0A10, 1.4F));
        RaceFx.ring(level, RaceFx.center(sp), 1.0, 12, ParticleTypes.SOUL_FIRE_FLAME);
        level.sendParticles(RaceFx.dust(0xFF3040, 1.0F), eye.x, eye.y, eye.z, 6, 0.15, 0.05, 0.15, 0);
        RaceFx.sound(level, eye, SoundEvents.GLASS_BREAK, 0.8F, 0.6F);
        RaceFx.sound(level, eye, SoundEvents.WITHER_AMBIENT, 0.3F, 1.6F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.demon_nullify",
                Component.translatable("message.isekai_mahou.character.demon_boast." + sp.getRandom().nextInt(BOAST_LINES)))
                .withColor(Race.DEMON.rgb), true);
    }

    private static void boast(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.demon_boss." + sp.getRandom().nextInt(3))
                .withColor(Race.DEMON.rgb), true);
    }

    // =====================================================================================
    //  Elfo — Mana Oculta (Frieren)
    // =====================================================================================
    private static void tickConceal(ServerPlayer sp, CharacterData d) {
        Optional<Holder.Reference<MobEffect>> eff = BuiltInRegistries.MOB_EFFECT.getHolder(MANA_CONCEALED);
        if (eff.isEmpty()) return;
        Holder<MobEffect> h = eff.get();
        MobEffectInstance cur = sp.getEffect(h);
        boolean ours = cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon();
        boolean wants = d.is(Race.ELF) && RaceProgress.level(sp) >= 3 && sp.isShiftKeyDown() && sp.isAlive();
        UUID id = sp.getUUID();
        long now = RacePassives.now(sp);
        if (wants) {
            if (cur == null && CONCEAL_ON.remove(id) != null) {
                // Sumiu sem você se levantar = o golpe surpresa revelou a mana: ela demora a se esconder de novo
                CONCEAL_READY.put(id, now + CONCEAL_GAP);
            }
            Long ready = CONCEAL_READY.get(id);
            if (ready != null && now < ready) return;
            // Não pisa na magia "Ocultar Mana" (efeito visível, mais longo)
            if (cur == null || (ours && cur.getDuration() < 40)) {
                sp.addEffect(new MobEffectInstance(h, 60, 0, true, false, false));
                CONCEAL_ON.put(id, true);
            }
        } else {
            CONCEAL_ON.remove(id);
            if (ours) sp.removeEffect(h);
        }
    }

    private RaceKits() {}
}
