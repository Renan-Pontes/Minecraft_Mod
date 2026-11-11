package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.feature.class_quincy.glyph.DrainReishiEffect;
import com.renanpontes.isekaimahou.feature.class_quincy.glyph.QuincyGlyphs;
import com.renanpontes.isekaimahou.feature.class_quincy.glyph.ReleaseEffect;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bônus de classe e regras do Quincy (passivas):
 * <ul>
 *   <li><b>Absorção natural</b>: 25% (+5% por rank, até 50%) dos projéteis que atingiriam o Quincy são ENGOLIDOS
 *   (somem, viram 3% de Foco e 1 de Reishi); os que passam causam 15% menos dano.</li>
 *   <li><b>Reishi do ambiente</b>: segurando a Cruz, a regeneração de Foco sobe 25%; parado com ela por 5 s, +1 Reishi.</li>
 *   <li><b>Rank do Auswählen</b>: técnicas com glifo Quincy ganham +4% de poder por rank.</li>
 *   <li><b>Ampola de Prata</b>: magia com o aumento gasta 1 Ampola e sai com +35% (sem Ampola: não conjura).</li>
 *   <li><b>Letras</b>: passivas modestas (ver {@link Quincy.Letter}).</li>
 * </ul>
 * Também: feitos dos ranks (abates, Schrift), magias iniciais da classe nos slots, Auswählen entre jogadores (agachar
 * + clicar com a Cruz) e a limpeza do estado em memória.
 * <p>
 * Técnicas avançadas: aprendizado automático ({@link QuincyTechniques#check}), Letzt Stil (×2) e o Todo-Poderoso
 * (esquiva/reescrita, {@link QuincyFinalForms}), a Luva Sanrei (×0,7 e o treino), o tempo "sem poder Quincy", o
 * registro de feridas da Antítese ({@link WoundLedger}) e as passivas das Letras novas (H, J, X, B, D).
 */
public final class QuincyEvents {
    public static final ResourceLocation GATHER_REGEN = IsekaiMahou.id("quincy_reishi_gather");
    public static final ResourceLocation IRON_ARMOR = IsekaiMahou.id("quincy_letter_iron");
    public static final ResourceLocation WIND_SPEED = IsekaiMahou.id("quincy_letter_wind");
    private static final String WELCOME_FLAG = "quincy.welcomed";

    /** Ticks parados segurando a Cruz (Reishi do ambiente). */
    private static final Map<UUID, Integer> STILL = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onCastPre);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onCastPost);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(QuincyEvents::onInteractEntity);
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> WoundLedger.record(e.getEntity(), e.getNewDamage()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                syncPassives(sp);
                QuincyTechniques.check(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) syncPassives(sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clearAll());
        NeoForge.EVENT_BUS.addListener((com.renanpontes.isekaimahou.core.event.OnboardingEvent.TutorialDone e) -> welcome(e.getPlayer()));
    }

    // =====================================================================================
    //  Absorção natural
    // =====================================================================================
    /** Chance de engolir um projétil: 25% + 5% por rank (máx. 50%). */
    public static float absorbChance(MagicData d) {
        return Math.min(0.5F, 0.25F + 0.05F * Quincy.rank(d));
    }

    private static void onProjectileImpact(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer sp)) return;
        Projectile p = e.getProjectile();
        if (p.level().isClientSide || !Quincy.isQuincy(sp) || p.getOwner() == sp) return;
        MagicData d = MagicApi.get(sp);
        // sorteio estável por projétil (o mesmo projétil pode ser testado em ticks seguidos)
        long seed = p.getUUID().getLeastSignificantBits() ^ sp.getUUID().getMostSignificantBits();
        float roll = (Math.floorMod(seed * 0x9E3779B97F4A7C15L, 1000L)) / 1000F;
        if (roll >= absorbChance(d)) return;
        e.setCanceled(true);
        QuincyAbsorption.swallow(sp.serverLevel(), sp, p);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        // O Todo-Poderoso: esquiva / golpe reescrito (qualquer criatura que esteja na forma)
        QuincyFinalForms.onIncoming(e);
        if (e.isCanceled()) return;
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Quincy.isQuincy(sp)) return;
        float mult = 1.0F;
        if (e.getSource().getDirectEntity() instanceof Projectile) mult *= 0.85F;
        Quincy.Letter letter = Quincy.letter(MagicApi.get(sp));
        if (letter == Quincy.Letter.IRON) mult *= 0.9F;
        // Letra B (O Equilíbrio): ferido (abaixo de 50%), recebe 8% menos
        if (letter == Quincy.Letter.BALANCE && sp.getHealth() < sp.getMaxHealth() * 0.5F) mult *= 0.92F;
        if (mult < 1.0F) e.setAmount(e.getAmount() * mult);
    }

    // =====================================================================================
    //  Conjuração: rank, Ampola de Prata, Schrift
    // =====================================================================================
    private static void onCastPre(SpellCastEvent.Pre e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || !ctx.isPlayerCast()) return;
        SpellRecipe recipe = ctx.recipe();
        if (!Quincy.hasQuincyGlyph(recipe)) return;
        // depois da Letzt Stil: sem poder Quincy
        if (QuincyTechniques.blockIfPowerless(sp)) {
            e.setCanceled(true);
            return;
        }
        MagicData d = MagicApi.get(sp);
        float mult = Quincy.isQuincy(sp) ? Quincy.rankPower(d) : 1.0F;
        mult *= QuincyFinalForms.castPower(sp);
        if (QuincyTechniques.holdingSanrei(sp)) mult *= QuincyTechniques.SANREI_POWER;
        if (Quincy.hasGlyph(recipe, Quincy.SILVER_TUBE)) {
            if (!sp.getAbilities().instabuild && !consumeTube(sp)) {
                sp.displayClientMessage(Quincy.msg("no_tube").withStyle(ChatFormatting.RED), true);
                sp.playNotifySound(SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
                e.setCanceled(true);
                return;
            }
            mult *= QuincyGlyphs.SILVER_TUBE_POWER;
            ServerLevel sl = sp.serverLevel();
            sl.sendParticles(QuincyFx.WHITE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
            QuincyFx.sound(sl, sp.position(), SoundEvents.GLASS_BREAK, 0.5F, 1.7F);
        }
        if (mult != 1.0F) ctx.setPowerMult(ctx.powerMult() * mult);
    }

    private static boolean consumeTube(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(QuincyRegistries.SILVER_TUBE.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || !Quincy.isQuincy(sp)) return;
        if (Quincy.hasGlyph(e.getContext().recipe(), Quincy.SCHRIFT) && Quincy.letter(MagicApi.get(sp)) != null) {
            MagicApi.addCounter(sp, Quincy.SCHRIFT_USES, 1);
        }
    }

    // =====================================================================================
    //  Abates (feitos do rank) e passivas de Letra em acertos
    // =====================================================================================
    private static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.killed() || !(e.ctx().caster() instanceof ServerPlayer sp) || !Quincy.isQuincy(sp)) return;
        if (!Quincy.hasQuincyGlyph(e.ctx().recipe())) return;
        countKill(sp, e.target());
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player)) WoundLedger.forget(e.getEntity().getUUID());
        if (!(e.getSource().getDirectEntity() instanceof SpiritArrowEntity arrow) || arrow.isSpell()) return;
        if (arrow.getOwner() instanceof ServerPlayer sp && Quincy.isQuincy(sp)) countKill(sp, e.getEntity());
    }

    private static void countKill(ServerPlayer sp, LivingEntity victim) {
        MagicApi.addCounter(sp, Quincy.KILLS, 1);
        QuincyTechniques.onKill(sp);
        if (Quincy.letter(MagicApi.get(sp)) == Quincy.Letter.EXPLODE && victim.level() instanceof ServerLevel sl) {
            // Letra E: quem cai pelas técnicas Quincy explode em Reishi (2 de dano em volta, sem quebrar blocos)
            Vec3 c = victim.position().add(0, victim.getBbHeight() / 2, 0);
            sl.sendParticles(QuincyFx.dust(Quincy.Letter.EXPLODE.rgb, 1.4F), c.x, c.y, c.z, 20, 0.6, 0.5, 0.6, 0.05);
            QuincyFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8F, 1.2F);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(2.0),
                    x -> x != sp && x != victim && x.isAlive() && x instanceof net.minecraft.world.entity.monster.Enemy)) {
                e.hurt(sp.damageSources().indirectMagic(sp, sp), 2.0F * com.renanpontes.isekaimahou.glyph.SpellCost.damageScale());
            }
        }
        QuincyRanks.hintIfReady(sp);
    }

    /** Acerto de flecha simples (Cruz) de um jogador: Letra T às vezes dá Choque. */
    public static void onPlainHit(Player p, LivingEntity target) {
        if (!(p instanceof ServerPlayer sp) || !Quincy.isQuincy(sp)) return;
        if (Quincy.letter(MagicApi.get(sp)) == Quincy.Letter.THUNDER && sp.getRandom().nextFloat() < 0.2F) {
            target.addEffect(new MobEffectInstance(CoreRegistries.SHOCKED, 40, 0), sp);
        }
    }

    // =====================================================================================
    //  Ticks
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post e) {
        QuincyAbsorption.tick(e.getServer());
        QuincyVollstandig.tick(e.getServer());
        QuincyFinalForms.tick(e.getServer());
        Auswahlen.tick(e.getServer());
        if (e.getServer().getTickCount() % 100 == 0) WoundLedger.purge(e.getServer().overworld().getGameTime());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        QuincyTechniques.secondTick(sp);
        boolean quincy = Quincy.isQuincy(sp);
        if (quincy) letterSecond(sp, Quincy.letter(MagicApi.get(sp)));
        boolean holding = quincy && (QuincyRegistries.isCross(sp.getMainHandItem()) || QuincyRegistries.isCross(sp.getOffhandItem()));
        setModifier(sp.getAttribute(CoreRegistries.MANA_REGEN), GATHER_REGEN, holding ? 0.25 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (holding && sp.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
            int t = STILL.merge(sp.getUUID(), 1, Integer::sum);
            if (t % 5 == 0 && Quincy.addReishi(sp, 1) > 0) {
                ServerLevel sl = sp.serverLevel();
                for (int i = 0; i < 3; i++) {
                    Vec3 from = sp.position().add((sp.getRandom().nextDouble() - 0.5) * 6, 0.3 + sp.getRandom().nextDouble() * 2,
                            (sp.getRandom().nextDouble() - 0.5) * 6);
                    QuincyFx.stream(sl, from, sp.position().add(0, 1.0, 0), 2);
                }
                sp.displayClientMessage(Quincy.reishiLine(MagicApi.get(sp)), true);
            }
        } else {
            STILL.remove(sp.getUUID());
        }
        if (quincy && Quincy.letter(MagicApi.get(sp)) == Quincy.Letter.FEAR && sp.tickCount % 40 == 0) fearPulse(sp);
        if (sp.tickCount % 100 == 0) syncPassives(sp);
    }

    /** Passivas por segundo das Letras novas: H não arde por mais de 2 s; D é imune a veneno. */
    private static void letterSecond(ServerPlayer sp, Quincy.Letter l) {
        if (l == Quincy.Letter.BURNT && sp.getRemainingFireTicks() > 40) sp.setRemainingFireTicks(40);
        if (l == Quincy.Letter.DEATHDEALING && sp.hasEffect(MobEffects.POISON)) sp.removeEffect(MobEffects.POISON);
    }

    /** Letra F: monstros que miram o Quincy a até 6 blocos às vezes hesitam (Lentidão I por 1 s). */
    private static void fearPulse(ServerPlayer sp) {
        for (Mob mob : sp.serverLevel().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(6), m -> m.getTarget() == sp)) {
            if (sp.getRandom().nextFloat() < 0.2F) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
                sp.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, mob.getX(), mob.getEyeY(), mob.getZ(),
                        2, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    /** Passivas de atributo das Letras (I: +2 armadura; W: +8% velocidade). */
    public static void syncPassives(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        Quincy.Letter l = d.path() == MagicPath.QUINCY ? Quincy.letter(d) : null;
        setModifier(sp.getAttribute(Attributes.ARMOR), IRON_ARMOR, l == Quincy.Letter.IRON ? 2.0 : 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(sp.getAttribute(Attributes.MOVEMENT_SPEED), WIND_SPEED, l == Quincy.Letter.WIND ? 0.08 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
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
        if (d.path() != MagicPath.QUINCY) return;
        for (String g : Quincy.STARTER_GLYPHS) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        boolean pt = sp.clientInformation().language().toLowerCase(java.util.Locale.ROOT).startsWith("pt");
        List<SpellRecipe> starters = List.of(
                new SpellRecipe(List.of(IsekaiMahou.id(Quincy.BOW), IsekaiMahou.id("arcane_damage")),
                        pt ? "Flecha Sagrada" : "Holy Arrow", 0x8FD0FF, ""),
                new SpellRecipe(List.of(IsekaiMahou.id(Quincy.BOW), IsekaiMahou.id(Quincy.DRAIN)),
                        pt ? "Flecha Drenante" : "Draining Arrow", 0xA8DFFF, ""),
                new SpellRecipe(List.of(IsekaiMahou.id(Quincy.HIRENKYAKU)),
                        pt ? "Passo Voador" : "Flying Step", 0xCFEFFF, ""));
        // só troca as magias genéricas de Destreza que o núcleo acabou de gravar (nunca as do jogador)
        boolean fresh = true;
        for (int l = 1; l < MagicData.LOADOUTS; l++) fresh &= d.loadoutEmpty(l);
        if (!fresh) return;
        for (int i = 0; i < starters.size(); i++) d.setSlotRecipe(0, i, MoveTrigger.NORMAL, starters.get(i));
        d.markDirty();
        // a entrada no jogo fica quieta até o tutorial: o boas-vindas sai depois (TutorialDone)
        if (!com.renanpontes.isekaimahou.core.api.Onboarding.quiet(sp)) welcome(sp);
    }

    /** Boas-vindas da classe (uma vez por personagem). */
    private static void welcome(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.QUINCY || d.hasFlag(WELCOME_FLAG)) return;
        d.setFlag(WELCOME_FLAG, true);
        sp.sendSystemMessage(Quincy.msg("welcome").withColor(Quincy.RGB));
    }

    private static void onCounter(MagicProgressEvent.Counter e) {
        String k = e.key;
        if (k.equals(Quincy.ABSORBED) || k.equals(Quincy.SCHRIFT_USES) || k.startsWith("boss.")) {
            QuincyRanks.hintIfReady(e.getPlayer());
        }
        if (k.equals(Quincy.RANK) || k.equals(Quincy.ABSORBED) || k.equals(Quincy.SCHRIFT_USES) || k.equals(Quincy.SANREI)
                || k.equals(Quincy.AUSWAHLEN) || k.startsWith("boss.")) {
            QuincyTechniques.check(e.getPlayer());
        }
    }

    // =====================================================================================
    //  Auswählen entre jogadores: agachado + clicar com a Cruz
    // =====================================================================================
    private static void onInteractEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getTarget() instanceof ServerPlayer target)) return;
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !sp.isShiftKeyDown()) return;
        if (!QuincyRegistries.isCross(sp.getMainHandItem()) || !Quincy.isQuincy(sp)) return;
        Auswahlen.request(sp, target);
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    private static void forget(Entity e) {
        UUID id = e.getUUID();
        STILL.remove(id);
        QuincyAbsorption.forget(id);
        if (e instanceof LivingEntity le) QuincyVollstandig.forget(le);
        QuincyFinalForms.forget(e);
        WoundLedger.forget(id);
        Auswahlen.forget(id);
        DrainReishiEffect.forget(id);
        ReleaseEffect.forget(id);
    }

    private static void clearAll() {
        STILL.clear();
        QuincyAbsorption.clear();
        QuincyVollstandig.clear();
        QuincyFinalForms.clear();
        WoundLedger.clearAll();
        Auswahlen.clear();
        DrainReishiEffect.clear();
        ReleaseEffect.clear();
        QuincyArmor.clear();
    }

    private QuincyEvents() {}
}
