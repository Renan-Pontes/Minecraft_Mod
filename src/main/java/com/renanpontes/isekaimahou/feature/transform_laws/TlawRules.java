package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformLaws;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As REGRAS das Leis de Transformação (bônus e preço de cada uma). Números:
 * <ul>
 *   <li><b>Corpo de Vidro</b>: +30% em todo dano que causa; +40% de dano recebido.</li>
 *   <li><b>Olhos do Predador</b>: inimigos a 20 blocos brilham através das paredes; +25% de dano em alvos abaixo de 50% da
 *       vida. Você também brilha e monstros a 16 blocos vêm atrás de você.</li>
 *   <li><b>Fúria Cega</b>: +40% de dano corpo a corpo; não pode conjurar magias de cura e recebe só 50% das curas.</li>
 *   <li><b>Sede de Sangue</b>: cada abate cura 5% da vida máxima; perde 1% da vida máxima a cada 2 s (nunca abaixo de 2).</li>
 *   <li><b>Forma Instável</b>: golpes corpo a corpo soltam (1×/s) uma explosão de um elemento ao acaso (3 + 5% do nível de
 *       dano mágico em 2,5 blocos + efeito do elemento); cada explosão te dá 1,5 de dano de recuo.</li>
 *   <li><b>Raiz de Ferro</b>: −35% de dano recebido e imune a empurrões; −30% de velocidade.</li>
 *   <li><b>Voto de Silêncio</b>: +60% de dano físico (corpo a corpo e flechas) e −15% de dano recebido; NENHUMA magia.</li>
 *   <li><b>Último Suspiro</b>: a transformação dura 50% a mais; ao acabar, exausto por 30 s (Lentidão II, Fraqueza II,
 *       Cansaço).</li>
 *   <li><b>Ritmo Implacável</b>: cada golpe seguido dá +8% de dano (até +40%); 3 s sem acertar quebra o ritmo e cobra 0,5 de
 *       vida por acúmulo.</li>
 *   <li><b>Pacto de Mana</b>: magias +40% mais fortes; cada conjuração custa 4% da vida máxima.</li>
 *   <li><b>Juramento do Protetor</b>: aliados a 8 blocos recebem −35% de dano; você recebe +15% e não pode se afastar mais
 *       de 10 blocos de onde se transformou.</li>
 *   <li><b>Chama Breve</b>: +50% em todo dano; a transformação dura metade.</li>
 *   <li><b>Transcender</b> (nível 100): +100% de dano, −40% de dano recebido, +30% de velocidade; drena 2% da vida por
 *       segundo e, ao acabar, a vida cai para 10%, Exaustão Mágica e Lentidão/Fraqueza III por 60 s.</li>
 * </ul>
 */
final class TlawRules {
    private static final ResourceLocation SPEED_ID = IsekaiMahou.id("tlaw_speed");
    private static final ResourceLocation KNOCKBACK_ID = IsekaiMahou.id("tlaw_knockback");
    private static final Element[] BASE = {Element.ARCANE, Element.FIRE, Element.WATER, Element.EARTH, Element.WIND,
            Element.LIGHT, Element.SHADOW};
    private static final double GUARD_RADIUS = 8.0;
    private static final double TETHER = 10.0;

    /** Quem está com Juramento do Protetor agora (busca pequena no dano dos aliados). */
    private static final Set<UUID> GUARDIANS = ConcurrentHashMap.newKeySet();

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, TlawRules::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TlawRules::onHeal);
        NeoForge.EVENT_BUS.addListener(TlawRules::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, TlawRules::onCastPre);
        NeoForge.EVENT_BUS.addListener(TlawRules::onCastPost);
    }

    static void clearAll() {
        GUARDIANS.clear();
    }

    static boolean has(List<TransformLawGlyph> laws, String path) {
        for (TransformLawGlyph l : laws) if (l.id().getPath().equals(path)) return true;
        return false;
    }

    // =====================================================================================
    //  Conjuração: começa a sessão das leis da receita; Voto de Silêncio / Fúria Cega / Pacto de Mana
    // =====================================================================================
    private static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        SpellContext ctx = e.getContext();
        ParsedSpell spell = ctx.spell();
        if (spell == null) return;
        List<TransformLawGlyph> laws = TransformLaws.lawsOf(spell);
        TransformTargets.Target t = TransformTargets.of(spell);
        if (t == null) return;
        // Onda 5: toda transformação fixa tem um espaço de receita (id = path do glifo) — sem leis na magia a chamada
        // ainda vale se o jogador MOLDOU essa transformação na aba Votos
        boolean shaped = IsekaiMahou.MODID.equals(t.glyph().getNamespace()) && TransformForge.slot(t.glyph().getPath()) != null
                && !ForgeData.recipe(sp, t.glyph().getPath()).isEmpty();
        if (laws.isEmpty() && !shaped) return;
        Holder<MobEffect> probe = t.probe() == null ? null : effect(t.probe());
        int ticks = spell.formStats().duration(Math.max(20, t.baseTicks()));
        Runnable go = () -> TlawSessions.INSTANCE.begin(sp, t.glyph(), laws, ticks, probe);
        // A receita cobra mana ao entrar: só entra quando a transformação está MESMO em você (a magia pode ter ido para
        // outro alvo, ter Atraso, ou — formas que alternam — ter acabado de desligar a forma)
        if (!shaped || probe == null || sp.hasEffect(probe)) go.run();
        else waitProbe(sp, probe, go, 0);
    }

    private static final int[] PROBE_WAIT = {2, 20, 40, 60, 40};

    private static void waitProbe(ServerPlayer sp, Holder<MobEffect> probe, Runnable go, int step) {
        SpellScheduler.schedule(PROBE_WAIT[step], () -> {
            if (!sp.isAlive() || sp.hasDisconnected()) return;
            if (sp.hasEffect(probe)) go.run();
            else if (step + 1 < PROBE_WAIT.length) waitProbe(sp, probe, go, step + 1);
        });
    }

    private static Holder<MobEffect> effect(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
    }

    private static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        TlawSessions.State st = TlawSessions.state(sp);
        if (st == null || st.active.isEmpty()) return;
        List<TransformLawGlyph> laws = st.active;
        if (has(laws, TlawGlyphs.SILENCE_VOW)) {
            e.setCanceled(true);
            TlawFx.warn(sp, st, Component.translatable("message.isekai_mahou.tlaw.silence_blocked").withColor(0xE8E8F0));
            return;
        }
        ParsedSpell spell = e.getContext().spell();
        if (has(laws, TlawGlyphs.BLIND_FURY) && heals(spell)) {
            e.setCanceled(true);
            TlawFx.warn(sp, st, Component.translatable("message.isekai_mahou.tlaw.fury_blocked").withColor(0xFF6060));
            return;
        }
        if (has(laws, TlawGlyphs.MANA_PACT)) {
            SpellContext ctx = e.getContext();
            ctx.setPowerMult(ctx.powerMult() * 1.4F);
            if (!sp.getAbilities().instabuild) sp.setHealth(Math.max(1F, sp.getHealth() - sp.getMaxHealth() * 0.04F));
            TlawFx.at(sp, ParticleTypes.SOUL, 6, 0.4);
            TlawFx.sound(sp, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1.2F);
        }
    }

    /** A magia (qualquer estágio) tem algum efeito de cura? */
    private static boolean heals(ParsedSpell spell) {
        ParsedSpell s = spell;
        int guard = 0;
        while (s != null && guard++ < 16) {
            for (ParsedSpell.EffectEntry en : s.effects()) if (en.effect().baseHeal() > 0F) return true;
            s = s.next() == null ? null : s.next().spell();
        }
        return false;
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        float amount = e.getAmount();
        if (amount <= 0F) return;
        LivingEntity victim = e.getEntity();
        DamageSource src = e.getSource();
        float mult = 1F;

        // ---- quem BATE transformado
        if (src.getEntity() instanceof ServerPlayer p && p != victim) {
            TlawSessions.State st = TlawSessions.state(p);
            if (st != null && !st.active.isEmpty()) mult *= attackMult(p, st, victim, src);
        }
        // ---- quem APANHA transformado
        if (victim instanceof ServerPlayer v) {
            TlawSessions.State st = TlawSessions.state(v);
            if (st != null && !st.active.isEmpty()) mult *= defenseMult(v, st.active);
        }
        // ---- aliados de quem jurou proteger
        if (!GUARDIANS.isEmpty() && (victim instanceof Player || victim instanceof OwnableEntity)) {
            ServerPlayer g = guardianOf(victim);
            if (g != null) {
                mult *= 0.65F;
                TlawFx.at(victim, ParticleTypes.END_ROD, 3, 0.3);
            }
        }
        if (mult != 1F) e.setAmount(amount * mult);
    }

    private static float attackMult(ServerPlayer p, TlawSessions.State st, LivingEntity victim, DamageSource src) {
        List<TransformLawGlyph> laws = st.active;
        boolean melee = src.getDirectEntity() == p && src.is(DamageTypes.PLAYER_ATTACK);
        boolean physical = melee || src.getDirectEntity() instanceof AbstractArrow;
        long now = p.serverLevel().getGameTime();
        float m = 1F;
        if (has(laws, TlawGlyphs.GLASS_BODY)) m *= 1.30F;
        if (has(laws, TlawGlyphs.BRIEF_FLAME)) m *= 1.50F;
        if (has(laws, TlawGlyphs.TRANSCEND)) {
            m *= 2.0F;
            TlawFx.at(victim, ParticleTypes.END_ROD, 3, 0.3);
        }
        if (has(laws, TlawGlyphs.PREDATOR_EYES) && victim.getHealth() < victim.getMaxHealth() * 0.5F) {
            m *= 1.25F;
            TlawFx.at(victim, TlawFx.dust(0xFFB347, 1.0F), 4, 0.3);
        }
        if (melee && has(laws, TlawGlyphs.BLIND_FURY)) {
            m *= 1.40F;
            TlawFx.at(victim, ParticleTypes.ANGRY_VILLAGER, 1, 0.2);
        }
        if (physical && has(laws, TlawGlyphs.SILENCE_VOW)) {
            m *= 1.60F;
            TlawFx.at(victim, ParticleTypes.CRIT, 6, 0.3);
        }
        if (melee && has(laws, TlawGlyphs.RELENTLESS_RHYTHM)) {
            m *= 1F + 0.08F * st.rhythm;
            st.rhythm = Math.min(5, st.rhythm + 1);
            st.lastHit = now;
            TlawFx.at(victim, ParticleTypes.NOTE, st.rhythm, 0.3);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.rhythm", st.rhythm, st.rhythm * 8).withColor(0xFF9A4A), true);
        }
        if (melee && has(laws, TlawGlyphs.UNSTABLE_FORM) && now - st.lastBurst >= 20) {
            st.lastBurst = now;
            // No tick seguinte: nada de dano aninhado dentro do evento de dano do próprio alvo
            SpellScheduler.schedule(1, () -> unstableBurst(p, victim));
        }
        return m;
    }

    private static float defenseMult(ServerPlayer v, List<TransformLawGlyph> laws) {
        float m = 1F;
        if (has(laws, TlawGlyphs.GLASS_BODY)) {
            m *= 1.40F;
            TlawFx.at(v, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 6, 0.3);
        }
        if (has(laws, TlawGlyphs.IRON_ROOT)) {
            m *= 0.65F;
            TlawFx.at(v, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), 5, 0.3);
        }
        if (has(laws, TlawGlyphs.SILENCE_VOW)) m *= 0.85F;
        if (has(laws, TlawGlyphs.GUARDIAN_OATH)) m *= 1.15F;
        if (has(laws, TlawGlyphs.TRANSCEND)) m *= 0.60F;
        return m;
    }

    /** Guardião (Juramento do Protetor) a até 8 blocos que protege esta vítima, ou null. */
    private static ServerPlayer guardianOf(LivingEntity victim) {
        if (victim.level().getServer() == null) return null;
        for (UUID id : GUARDIANS) {
            ServerPlayer g = victim.level().getServer().getPlayerList().getPlayer(id);
            if (g == null || g == victim || g.level() != victim.level() || g.distanceToSqr(victim) > GUARD_RADIUS * GUARD_RADIUS) continue;
            if (victim instanceof Player vp && Party.sameParty(g, vp)) return g;
            if (victim instanceof OwnableEntity o && g.getUUID().equals(o.getOwnerUUID())) return g;
        }
        return null;
    }

    /** Forma Instável: explosão de um elemento ao acaso em volta do alvo + recuo em quem bateu. */
    private static void unstableBurst(ServerPlayer p, LivingEntity target) {
        if (!p.isAlive() || !target.isAlive() || !(p.level() instanceof ServerLevel level) || target.level() != level) return;
        Element el = BASE[level.random.nextInt(BASE.length)];
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        ElementFx.burst(level, c, el, 18, 0.6);
        TlawFx.ring(level, c, 2.0, 16, ElementFx.dust(el, 1.2F));
        TlawFx.sound(target, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.6F);
        float dmg = 3F + MagicApi.get(p).level() * 0.05F;
        int n = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.5), x -> x != p && x.isAlive())) {
            if (++n > 8) break;
            if (le instanceof Player op && (Party.sameParty(p, op) || op.isSpectator())) continue;
            if (le instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) continue;
            le.invulnerableTime = 0;
            le.hurt(p.damageSources().indirectMagic(p, p), dmg);
            elementHit(le, el, c);
        }
        // Recuo: a forma instável cobra de quem a usa
        p.setHealth(Math.max(1F, p.getHealth() - 1.5F));
        TlawFx.at(p, ParticleTypes.DAMAGE_INDICATOR, 2, 0.2);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.unstable", el.displayName()).withColor(el.color), true);
    }

    private static void elementHit(LivingEntity le, Element el, Vec3 from) {
        switch (el) {
            case FIRE -> le.igniteForSeconds(3F);
            case WATER -> le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            case EARTH -> {
                le.push(0, 0.45, 0);
                le.hurtMarked = true;
            }
            case WIND -> {
                Vec3 d = le.position().subtract(from);
                d = new Vec3(d.x, 0, d.z);
                d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
                le.push(d.x * 0.9, 0.25, d.z * 0.9);
                le.hurtMarked = true;
            }
            case LIGHT -> {
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0));
            }
            case SHADOW -> le.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0));
            default -> le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
    }

    private static void onHeal(LivingHealEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && TlawSessions.has(p, TlawGlyphs.BLIND_FURY)) {
            e.setAmount(e.getAmount() * 0.5F);
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && p != e.getEntity() && TlawSessions.has(p, TlawGlyphs.BLOODLUST)) {
            p.heal(p.getMaxHealth() * 0.05F);
            TlawFx.at(p, TlawFx.dust(0xB0102A, 1.4F), 10, 0.4);
            TlawFx.at(e.getEntity(), TlawFx.dust(0x7A0010, 1.2F), 12, 0.4);
            TlawFx.sound(p, SoundEvents.GENERIC_DRINK, 0.7F, 0.7F);
        }
    }

    // =====================================================================================
    //  Início, fim e tick
    // =====================================================================================
    static void onStart(ServerPlayer p, List<TransformLawGlyph> laws) {
        ServerLevel level = p.serverLevel();
        if (has(laws, TlawGlyphs.TRANSCEND)) {
            level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1.0, 0.5, 0.15);
            TlawFx.sound(p, SoundEvents.TOTEM_USE, 0.8F, 1.3F);
        }
        if (has(laws, TlawGlyphs.GUARDIAN_OATH)) {
            TlawFx.ring(level, p.position().add(0, 0.2, 0), TETHER, 40, TlawFx.dust(0xFFD86B, 1.4F));
            p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.oath_start", (int) TETHER).withColor(0xFFD86B), true);
        }
        if (has(laws, TlawGlyphs.BRIEF_FLAME)) {
            level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
        }
    }

    /** Preços cobrados quando a transformação acaba. */
    static void onEnd(ServerPlayer p, List<TransformLawGlyph> laws) {
        if (has(laws, TlawGlyphs.LAST_BREATH)) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 0));
            TlawFx.at(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, 6, 0.3);
            TlawFx.sound(p, SoundEvents.PLAYER_BREATH, 1.0F, 0.6F);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.last_breath_end").withColor(0x7FD8FF), true);
        }
        if (has(laws, TlawGlyphs.TRANSCEND)) {
            p.setHealth(Math.max(1F, Math.min(p.getHealth(), p.getMaxHealth() * 0.10F)));
            p.addEffect(new MobEffectInstance(CoreRegistries.MAGIC_EXHAUSTION, 1200, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 2));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 2));
            TlawFx.at(p, ParticleTypes.ASH, 20, 0.5);
            TlawFx.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.5F);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.transcend_end").withColor(0xFFF4C0), true);
        }
    }

    /** Atributos (Raiz de Ferro, Transcender) e a lista de guardiões, sempre que as leis em vigor mudam. */
    static void applyModifiers(ServerPlayer p, List<TransformLawGlyph> laws) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance knock = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        double sp = 0;
        if (has(laws, TlawGlyphs.IRON_ROOT)) sp -= 0.30;
        if (has(laws, TlawGlyphs.TRANSCEND)) sp += 0.30;
        if (speed != null) {
            if (sp != 0) speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, sp, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else speed.removeModifier(SPEED_ID);
        }
        if (knock != null) {
            if (has(laws, TlawGlyphs.IRON_ROOT)) {
                knock.addOrUpdateTransientModifier(new AttributeModifier(KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
            } else {
                knock.removeModifier(KNOCKBACK_ID);
            }
        }
        if (has(laws, TlawGlyphs.GUARDIAN_OATH)) GUARDIANS.add(p.getUUID());
        else GUARDIANS.remove(p.getUUID());
        if (laws.isEmpty()) {
            MobEffectInstance vow = p.getEffect(TlawContent.VOW);
            if (vow != null) p.removeEffect(TlawContent.VOW);
        } else {
            p.addEffect(new MobEffectInstance(TlawContent.VOW, 60, laws.size() - 1, true, false, true));
        }
    }

    /** A cada 5 ticks com alguma lei valendo. */
    static void tickActive(ServerPlayer p, TlawSessions.State st, long now) {
        List<TransformLawGlyph> laws = st.active;
        int t = p.tickCount;
        ServerLevel level = p.serverLevel();
        if (t % 20 == 0) p.addEffect(new MobEffectInstance(TlawContent.VOW, 60, laws.size() - 1, true, false, true));

        if (has(laws, TlawGlyphs.BLOODLUST) && t % 40 == 0 && !p.getAbilities().instabuild) {
            float hp = p.getHealth();
            if (hp > 2F) {
                p.setHealth(Math.max(2F, hp - p.getMaxHealth() * 0.01F));
                TlawFx.at(p, TlawFx.dust(0x8A0018, 0.9F), 3, 0.25);
            }
        }
        if (has(laws, TlawGlyphs.TRANSCEND) && t % 20 == 0) {
            if (!p.getAbilities().instabuild) p.setHealth(Math.max(1F, p.getHealth() - p.getMaxHealth() * 0.02F));
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.4, 0.7, 0.4, 0.02);
        }
        if (has(laws, TlawGlyphs.RELENTLESS_RHYTHM) && st.rhythm > 0 && now - st.lastHit > 60) {
            float cost = st.rhythm * 0.5F;
            st.rhythm = 0;
            if (!p.getAbilities().instabuild) p.setHealth(Math.max(1F, p.getHealth() - cost));
            TlawFx.at(p, ParticleTypes.SMOKE, 6, 0.3);
            TlawFx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.tlaw.rhythm_broken").withColor(0xFF7A1A), true);
        }
        if (has(laws, TlawGlyphs.GUARDIAN_OATH)) tether(p, st, t);
        if (has(laws, TlawGlyphs.PREDATOR_EYES) && t % 20 == 0) predator(p, level);
    }

    /** Juramento do Protetor: puxa de volta para o posto (e teleporta se fugir longe demais). */
    private static void tether(ServerPlayer p, TlawSessions.State st, int t) {
        Vec3 anchor = null;
        for (TlawSessions.Session s : st.sessions) {
            if (s.seen && s.anchor != null && s.has(TlawGlyphs.GUARDIAN_OATH) && p.level().dimension().equals(s.anchorDim)) {
                anchor = s.anchor;
                break;
            }
        }
        if (anchor == null) return;
        ServerLevel level = p.serverLevel();
        Vec3 flat = new Vec3(p.getX() - anchor.x, 0, p.getZ() - anchor.z);
        double d = flat.length();
        if (t % 20 == 0 && d > TETHER - 3) {
            TlawFx.ring(level, new Vec3(anchor.x, p.getY() + 0.2, anchor.z), TETHER, 32, TlawFx.dust(0xFFD86B, 1.2F));
        }
        if (d <= TETHER) return;
        Vec3 back = flat.normalize().scale(-1);
        if (d > TETHER + 3) {
            Vec3 to = anchor.add(flat.normalize().scale(TETHER - 0.8));
            p.teleportTo(to.x, p.getY(), to.z);
        } else {
            p.push(back.x * 0.6, 0.1, back.z * 0.6);
            p.hurtMarked = true;
        }
        TlawFx.sound(p, SoundEvents.CHAIN_HIT, 0.7F, 1.2F);
        TlawFx.warn(p, st, Component.translatable("message.isekai_mahou.tlaw.oath_tether").withColor(0xFFD86B));
    }

    /** Olhos do Predador: marca os inimigos por perto — e eles também te veem. */
    private static void predator(ServerPlayer p, ServerLevel level) {
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20), x -> x instanceof Enemy && x.isAlive())) {
            if (++n > 24) break;
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
            if (m.getTarget() == null && !p.getAbilities().instabuild && !p.isSpectator() && m.distanceToSqr(p) < 16 * 16) m.setTarget(p);
        }
    }

    private TlawRules() {}
}
