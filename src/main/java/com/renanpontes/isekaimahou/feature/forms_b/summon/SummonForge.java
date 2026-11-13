package com.renanpontes.isekaimahou.feature.forms_b.summon;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellConjurerEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Forja de Invocações (v1.0, onda 4): "invocações controláveis por magia, tipo os domains".
 * <p>
 * Numa receita <b>Invocação + [glifo de um corpo] + efeitos</b>, a forma Invocação não chama o espírito: chama o corpo
 * (Salamandra, Ondina, Golem, Sílfide, Fada, Sombra Viva, Ent — {@link SummonBodies}) e o resto da receita manda nele:
 * <ul>
 *   <li><b>Efeitos</b>: o que o ataque da criatura aplica (uma vez a cada {@link #EFFECT_INTERVAL} ticks por criatura —
 *       o mesmo ritmo do espírito). Efeitos benéficos vão para o dono.</li>
 *   <li><b>Elemento</b>: o elemento da receita tinge o corpo e é o elemento dos efeitos.</li>
 *   <li><b>Aumentos</b>: Dividir = quantidade (até o limite do corpo), Expandir/Concentrar = tamanho, Estender =
 *       duração, Amplificar = força do corpo.</li>
 *   <li><b>Ordem</b> ({@link SummonOrder}): seguir, guardar um ponto, caçar o alvo marcado, ficar parado — por glifo
 *       ({@code summon_guard/summon_hunt/summon_hold}), pela tecla de ordem, por agachar + clicar na criatura ou por
 *       {@code /mahou summons <ordem>}.</li>
 * </ul>
 * Receitas antigas não mudam: sem corpo, a Invocação continua chamando o espírito; os glifos de invocar em outras
 * formas (Projétil + Invocar Golem...) funcionam como sempre.
 * <p>
 * Tudo o que a Forja sabe de uma criatura fica no {@code persistentData} dela, na chave {@link #KEY} (salvo com o
 * mundo). Os módulos dos corpos leem {@code order} e {@code gx/gy/gz} dali por texto, sem depender desta classe.
 */
public final class SummonForge {
    /** Chave no persistentData da criatura (servidor). Sub-chaves: order, gx, gy, gz, mark, spell, color, caster. */
    public static final String KEY = "isekai_mahou_forge";
    /** Chave no persistentData da criatura no CLIENTE: cor (ARGB) com que os renderers dos corpos a tingem. */
    public static final String COLOR_KEY = "isekai_mahou_forge_color";
    /** Ticks entre duas aplicações dos efeitos da receita por criatura (igual ao espírito da Invocação). */
    public static final int EFFECT_INTERVAL = 40;
    /** Quão longe do ponto guardado a criatura persegue um inimigo antes de voltar. */
    public static final double GUARD_LEASH = 16.0;
    /** Alcance para marcar um alvo olhando para ele. */
    public static final double MARK_RANGE = 48.0;
    /** Alcance das ordens por tecla/comando (em volta do dono). */
    public static final double COMMAND_RANGE = 64.0;

    public static final ResourceLocation GLYPH_CONJURE = IsekaiMahou.id("conjure");
    public static final ResourceLocation GLYPH_GUARD = IsekaiMahou.id("summon_guard");
    public static final ResourceLocation GLYPH_HUNT = IsekaiMahou.id("summon_hunt");
    public static final ResourceLocation GLYPH_HOLD = IsekaiMahou.id("summon_hold");

    private static final ResourceLocation SIZE_ID = IsekaiMahou.id("summon_forge_size");
    private static final ResourceLocation HOLD_ID = IsekaiMahou.id("summon_forge_hold");

    /** Criaturas com dados da Forja carregadas agora (poucas: só as invocações com ordem/receita). */
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Donos com invocações em "Caçar" à espera de um alvo: o próximo inimigo que acertarem vira a marca. */
    private static final Set<UUID> AWAIT_MARK = new HashSet<>();
    /** Último uso da tecla de ordem por jogador (tempo de jogo), para não processar rajadas. */
    private static final Map<UUID, Long> LAST_KEY = new HashMap<>();
    /** Estamos aplicando efeitos de uma receita agora (o dano deles não conta como "o dono marcou um alvo"). */
    private static boolean applying;

    private SummonForge() {}

    // =====================================================================================
    //  Estado de uma criatura
    // =====================================================================================
    static final class State {
        final Mob mob;
        SummonOrder order = SummonOrder.FOLLOW;
        @Nullable Vec3 guard;
        @Nullable UUID mark;
        /** Receita de comando salva ({@code SpellContext.save()}); null = a criatura só tem ordem. */
        @Nullable CompoundTag spell;
        int color = -1;
        boolean caster;
        /** Próximo tempo de jogo em que os efeitos podem ser aplicados. */
        long next;
        private SpellContext ctx;
        private long retry;

        State(Mob mob) {
            this.mob = mob;
        }

        @Nullable
        UUID owner() {
            return mob instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        }

        /** Contexto da receita de comando; recarrega se o dono saiu/morreu e voltou. null = sem receita ou dono ausente. */
        @Nullable
        SpellContext context(ServerLevel level) {
            if (spell == null) return null;
            boolean stale = ctx == null || ctx.caster() == null || ctx.caster().isRemoved();
            if (stale && level.getGameTime() >= retry) {
                retry = level.getGameTime() + 40;
                ctx = SpellContext.load(level, spell);
            }
            return ctx;
        }

        void save() {
            CompoundTag t = new CompoundTag();
            t.putInt("order", order.ordinal());
            if (guard != null) {
                t.putDouble("gx", guard.x);
                t.putDouble("gy", guard.y);
                t.putDouble("gz", guard.z);
            }
            if (mark != null) t.putUUID("mark", mark);
            if (spell != null) t.put("spell", spell);
            if (color >= 0) t.putInt("color", color);
            if (caster) t.putBoolean("caster", true);
            mob.getPersistentData().put(KEY, t);
        }

        void load(CompoundTag t) {
            order = SummonOrder.byIndex(t.getInt("order"));
            guard = t.contains("gx") ? new Vec3(t.getDouble("gx"), t.getDouble("gy"), t.getDouble("gz")) : null;
            mark = t.hasUUID("mark") ? t.getUUID("mark") : null;
            spell = t.contains("spell", Tag.TAG_COMPOUND) ? t.getCompound("spell") : null;
            color = t.contains("color") ? t.getInt("color") : -1;
            caster = t.getBoolean("caster");
        }
    }

    /** O estado da criatura; com {@code create}, cria e passa a acompanhar. */
    @Nullable
    private static State state(Mob mob, boolean create) {
        State st = STATES.get(mob.getUUID());
        if (st != null && st.mob != mob) st = null; // a entidade foi recarregada: outro objeto
        if (st == null && create) {
            st = new State(mob);
            STATES.put(mob.getUUID(), st);
            ensureGoal(mob, st);
        }
        return st;
    }

    /** Ordem atual de uma criatura-corpo (Seguir se a Forja nunca mexeu nela). */
    public static SummonOrder orderOf(Mob mob) {
        State st = STATES.get(mob.getUUID());
        return st != null && st.mob == mob ? st.order : SummonOrder.FOLLOW;
    }

    // =====================================================================================
    //  Conjuração: Invocação + corpo
    // =====================================================================================

    /** A ordem pedida pelos glifos de ordem (na forma ou depois do corpo). Sem glifo: Seguir. */
    public static SummonOrder orderOf(SpellStats... stats) {
        SummonOrder out = SummonOrder.FOLLOW;
        for (SpellStats s : stats) {
            if (s == null) continue;
            if (s.custom(GLYPH_HUNT) > 0) return SummonOrder.HUNT;
            if (s.custom(GLYPH_GUARD) > 0) out = SummonOrder.GUARD;
            else if (s.custom(GLYPH_HOLD) > 0 && out == SummonOrder.FOLLOW) out = SummonOrder.STAY;
        }
        return out;
    }

    /** A receita tem o glifo de algum corpo? (a Invocação então chama o corpo em vez do espírito) */
    public static boolean hasBody(SpellContext ctx) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (SummonBodies.byGlyph(e.effect().id()) != null) return true;
        }
        return false;
    }

    /**
     * Chama os corpos pedidos pela receita e entrega a eles o resto dela.
     * @return false se a receita não tem corpo (quem chama segue com o espírito)
     */
    public static boolean summonBodies(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !hasBody(ctx)) return false;
        ServerLevel level = ctx.level();
        SpellStats fs = ctx.formStats();
        SpellContext cmd = commandContext(ctx);
        CompoundTag spell = cmd != null ? cmd.save() : null;
        LivingEntity looked = lookTarget(caster, MARK_RANGE);
        int forged = 0;
        for (ParsedSpell.EffectEntry entry : ctx.spell().effects()) {
            SummonBodies.Body body = SummonBodies.byGlyph(entry.effect().id());
            if (body == null) continue;
            var type = body.type();
            if (type == null) continue;
            SpellStats merged = merge(entry.stats(), fs);
            // Quantidade: Dividir na forma + Dividir no glifo do corpo, até o limite do corpo. Quem conta é o próprio
            // glifo do corpo (um chamado só: eles agem uma vez por conjuração e por tick)
            merged.split = Mth.clamp(fs.count() + Math.max(0, entry.stats().split), 1, body.max()) - 1;
            entry.effect().onEntity(ctx.copy(ctx.powerMult()), caster, merged);
            SummonOrder order = orderOf(fs, entry.stats());
            float size = sizeOf(merged);
            int color = tintFor(cmd, ctx, entry.effect().element());
            UUID ownerId = caster.getUUID();
            // Tudo o que é deste dono e deste corpo obedece à receita nova (inclui os que o corpo só renovou)
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class),
                    m -> m.getType() == type && m.isAlive() && m instanceof OwnableEntity o && ownerId.equals(o.getOwnerUUID()))) {
                State st = state(mob, true);
                st.spell = spell != null ? spell.copy() : null;
                st.ctx = null;
                st.color = color;
                st.caster = body.caster();
                st.next = level.getGameTime() + 20;
                applySize(mob, size);
                LivingEntity mark = order == SummonOrder.HUNT && looked != null && canMark(caster, looked) ? looked : null;
                setOrder(st, order, null, mark);
                sendLook(mob, st);
                forged++;
            }
        }
        if (forged > 0 && caster instanceof ServerPlayer sp) {
            Hints.show(sp, "summon_forge", Component.keybind("key.isekai_mahou.summon_order"));
        }
        return true;
    }

    /** Aumentos da forma somados aos do glifo do corpo (o corpo lê duração e força destes stats). */
    private static SpellStats merge(SpellStats body, SpellStats form) {
        SpellStats m = body.copy();
        m.amplify += form.amplify;
        m.duration += form.duration;
        m.charge += form.charge;
        m.aoe += form.aoe;
        m.persistent |= form.persistent;
        m.concentrate |= form.concentrate;
        m.overload |= form.overload;
        return m;
    }

    /** Tamanho do corpo: Expandir cresce (+25% cada), Concentrar encolhe. Entre 60% e 175%. */
    private static float sizeOf(SpellStats s) {
        float size = 1.0F + 0.25F * s.aoe;
        if (s.concentrate) size *= 0.75F;
        return Mth.clamp(size, 0.6F, 1.75F);
    }

    /** Cor do corpo: a da receita de comando, se ela trocou o elemento do corpo ou o jogador escolheu uma cor. -1 = natural. */
    private static int tintFor(@Nullable SpellContext cmd, SpellContext cast, Element bodyElement) {
        SpellContext c = cmd != null ? cmd : cast;
        boolean custom = c.recipe().color() >= 0;
        if (!custom && (cmd == null || cmd.element() == bodyElement || cmd.element() == Element.ARCANE)) return -1;
        return c.color() & 0xFFFFFF;
    }

    /**
     * A receita de comando: a receita conjurada sem os glifos de corpo (e sem os aumentos colados neles).
     * null = não sobrou efeito nenhum (a criatura só recebe ordem, tamanho e duração).
     */
    @Nullable
    private static SpellContext commandContext(SpellContext ctx) {
        List<ResourceLocation> out = new ArrayList<>();
        boolean skipping = false, any = false;
        for (ResourceLocation id : ctx.recipe().glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) return null;
            if (g.kind() == GlyphKind.EFFECT) {
                skipping = SummonBodies.byGlyph(id) != null;
                if (!skipping) {
                    out.add(id);
                    any = true;
                }
            } else if (g.kind() == GlyphKind.AUGMENT) {
                if (!skipping) out.add(id);
            } else {
                skipping = false;
                out.add(id);
            }
        }
        if (!any) return null;
        SpellRecipe stripped = ctx.recipe().withGlyphs(out);
        boolean player = ctx.isPlayerCast();
        ServerPlayer sp = ctx.casterPlayer();
        MagicData mastery = player && sp != null ? MagicApi.get(sp) : null;
        // data null: a receita já foi validada na conjuração; aqui só remontamos sem o corpo
        SpellParser.Result r = SpellParser.parse(stripped, player ? Element.ARCANE : null, null, mastery);
        if (!r.ok()) return null;
        SpellContext c = new SpellContext(ctx.level(), ctx.caster(), player ? r.spell().primary() : ctx.element(), stripped, r.spell());
        if (player) c.markPlayerCast();
        c.setPowerMult(ctx.powerMult());
        return c;
    }

    private static void applySize(Mob mob, float size) {
        boolean changed = Math.abs(size - 1.0F) > 0.01F;
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(SIZE_ID);
            if (changed) scale.addPermanentModifier(new AttributeModifier(SIZE_ID, size - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        // Corpo maior aguenta mais; menor, menos
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            boolean had = hp.getModifier(SIZE_ID) != null;
            hp.removeModifier(SIZE_ID);
            if (changed) hp.addPermanentModifier(new AttributeModifier(SIZE_ID, (size - 1.0) * 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (changed || had) mob.setHealth(mob.tickCount < 5 ? mob.getMaxHealth() : Math.min(mob.getHealth(), mob.getMaxHealth()));
        }
    }

    // =====================================================================================
    //  Ordens
    // =====================================================================================

    private static void setOrder(State st, SummonOrder order, @Nullable Vec3 point, @Nullable LivingEntity mark) {
        Mob mob = st.mob;
        st.order = order;
        st.guard = order.anchored() ? (point != null ? point : mob.position()) : null;
        st.mark = order == SummonOrder.HUNT && mark != null ? mark.getUUID() : null;
        if (order == SummonOrder.HUNT) {
            if (mark != null) mob.setTarget(mark);
            else if (st.owner() != null) AWAIT_MARK.add(st.owner());
        }
        // Ficar parado: velocidade zero (a criatura ainda ataca o que chegar ao alcance dela)
        setHeld(mob, order == SummonOrder.STAY);
        st.save();
    }

    private static void setHeld(Mob mob, boolean held) {
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance a = mob.getAttribute(attr);
            if (a == null) continue;
            a.removeModifier(HOLD_ID);
            if (held) a.addTransientModifier(new AttributeModifier(HOLD_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (held) mob.getNavigation().stop();
    }

    /** Um dono com invocações em Caçar e sem alvo: o próximo inimigo que ele acertar vira a marca. */
    public static void awaitMark(@Nullable UUID owner) {
        if (owner != null) AWAIT_MARK.add(owner);
    }

    /** Todas as criaturas-corpo e espíritos deste dono, perto dele. */
    private static void owned(ServerPlayer owner, List<Mob> mobs, List<SpellConjurerEntity> spirits) {
        ServerLevel level = owner.serverLevel();
        UUID id = owner.getUUID();
        double r2 = COMMAND_RANGE * COMMAND_RANGE;
        mobs.addAll(level.getEntities(EntityTypeTest.forClass(Mob.class), m -> SummonBodies.isBody(m.getType()) && m.isAlive()
                && m instanceof OwnableEntity o && id.equals(o.getOwnerUUID()) && m.distanceToSqr(owner) <= r2));
        spirits.addAll(level.getEntities(FormsBRegistry.SPELL_CONJURER.get(),
                s -> id.equals(s.getOwnerUUID()) && s.distanceToSqr(owner) <= r2));
    }

    /** Tecla de ordem: passa TODAS as invocações do jogador para a próxima ordem do ciclo. */
    public static void cycleOrder(ServerPlayer owner) {
        long now = owner.serverLevel().getGameTime();
        Long last = LAST_KEY.get(owner.getUUID());
        if (last != null && now - last < 4 && now >= last) return;
        LAST_KEY.put(owner.getUUID(), now);
        List<Mob> mobs = new ArrayList<>();
        List<SpellConjurerEntity> spirits = new ArrayList<>();
        owned(owner, mobs, spirits);
        SummonOrder current = !mobs.isEmpty() ? orderOf(mobs.get(0)) : !spirits.isEmpty() ? spirits.get(0).order() : SummonOrder.FOLLOW;
        command(owner, current.next(), mobs, spirits);
    }

    /** Comando/tecla: dá a ordem a todas as invocações do jogador que estão por perto. @return quantas obedeceram */
    public static int command(ServerPlayer owner, SummonOrder order) {
        List<Mob> mobs = new ArrayList<>();
        List<SpellConjurerEntity> spirits = new ArrayList<>();
        owned(owner, mobs, spirits);
        return command(owner, order, mobs, spirits);
    }

    private static int command(ServerPlayer owner, SummonOrder order, List<Mob> mobs, List<SpellConjurerEntity> spirits) {
        int n = mobs.size() + spirits.size();
        if (n == 0) {
            owner.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.order_none").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        // Guardar: o ponto para onde o jogador olha (até 24 blocos); sem bloco na mira, onde ele está
        Vec3 point = null;
        if (order == SummonOrder.GUARD) {
            Vec3 eye = owner.getEyePosition();
            BlockHitResult hit = owner.level().clip(new ClipContext(eye, eye.add(owner.getLookAngle().scale(24)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            point = hit.getType() == HitResult.Type.BLOCK ? Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()))
                    : owner.position();
        }
        LivingEntity mark = null;
        if (order == SummonOrder.HUNT) {
            LivingEntity looked = lookTarget(owner, MARK_RANGE);
            if (looked != null && canMark(owner, looked)) mark = looked;
        }
        for (Mob mob : mobs) {
            State st = state(mob, true);
            setOrder(st, order, point, mark);
        }
        for (SpellConjurerEntity s : spirits) {
            s.command(order, order == SummonOrder.GUARD ? point.add(0, 1.2, 0) : null, mark);
        }
        if (order == SummonOrder.HUNT && mark == null) AWAIT_MARK.add(owner.getUUID());
        Component msg;
        if (order == SummonOrder.HUNT) {
            msg = mark != null ? Component.translatable("message.isekai_mahou.forms_b.order_hunt_target", mark.getDisplayName())
                    : Component.translatable("message.isekai_mahou.forms_b.order_hunt_wait");
        } else {
            msg = Component.translatable("message.isekai_mahou.forms_b.order", order.displayName());
        }
        owner.displayClientMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return n;
    }

    /** Marca {@code target} para todas as invocações em Caçar deste dono que estão sem alvo. */
    private static void markFor(LivingEntity owner, LivingEntity target) {
        UUID id = owner.getUUID();
        // alvo que não pode ser marcado (aliado, morador em zona segura...): continua esperando o próximo golpe
        if (!canMark(owner, target) || !(owner.level() instanceof ServerLevel level)) return;
        AWAIT_MARK.remove(id);
        int n = 0;
        for (State st : STATES.values()) {
            if (st.order != SummonOrder.HUNT || st.mark != null || !id.equals(st.owner()) || st.mob.level() != level) continue;
            st.mark = target.getUUID();
            st.mob.setTarget(target);
            st.save();
            n++;
        }
        for (SpellConjurerEntity s : level.getEntities(FormsBRegistry.SPELL_CONJURER.get(),
                s -> id.equals(s.getOwnerUUID()) && s.order() == SummonOrder.HUNT && !s.hasMark())) {
            s.command(SummonOrder.HUNT, null, target);
            n++;
        }
        if (n > 0 && owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.order_hunt_target", target.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /**
     * {@code target} pode ser marcado para a caça de {@code owner}? Nunca o próprio dono, as invocações dele, aliados,
     * jogadores em criativo, jogadores com o PvP desligado ou com o Modo Paz do dono ligado — nem moradores pacíficos
     * dentro de uma zona segura (cidades são sagradas).
     */
    public static boolean canMark(LivingEntity owner, LivingEntity target) {
        if (target == null || target == owner || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand) return false;
        if (target instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID())) return false;
        Boolean social = Party.allyOverride(owner, target);
        if (social != null) return !social;
        if (owner.isAlliedTo(target)) return false;
        if (target instanceof Player p) {
            if (p.isCreative()) return false;
            if (owner instanceof Player op && (owner.getServer() == null || !owner.getServer().isPvpAllowed() || PeaceMode.isOn(op))) return false;
            return true;
        }
        if (target instanceof Enemy) return true;
        boolean provoked = target instanceof Mob m && m.getTarget() == owner;
        return provoked || !(target.level() instanceof ServerLevel sl) || !Places.isSafe(sl, target.blockPosition());
    }

    /** A criatura viva para a qual {@code who} está olhando (até {@code range} blocos, sem atravessar paredes). */
    @Nullable
    public static LivingEntity lookTarget(LivingEntity who, double range) {
        Vec3 eye = who.getEyePosition();
        Vec3 look = who.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult wall = who.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = new AABB(eye, end).inflate(1.5);
        for (LivingEntity e : who.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != who && e.isAlive() && !e.isSpectator())) {
            // caixa um pouco maior: mirar de longe numa criatura fina não precisa ser exato
            var hit = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Uma criatura-corpo com dados da Forja entrou no mundo (carregou do disco): volta a ser acompanhada. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof OwnableEntity) || !SummonBodies.isBody(mob.getType())) return;
        CompoundTag pd = mob.getPersistentData();
        if (!pd.contains(KEY, Tag.TAG_COMPOUND)) return;
        State st = new State(mob);
        st.load(pd.getCompound(KEY));
        STATES.put(mob.getUUID(), st);
        ensureGoal(mob, st);
        setHeld(mob, st.order == SummonOrder.STAY);
        if (st.order == SummonOrder.HUNT && st.mark == null) awaitMark(st.owner());
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (STATES.isEmpty() || event.getLevel().isClientSide()) return;
        State st = STATES.get(event.getEntity().getUUID());
        if (st != null && st.mob == event.getEntity()) STATES.remove(event.getEntity().getUUID());
    }

    /** Um jogador passou a ver a criatura: manda a cor do elemento dela. */
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (STATES.isEmpty() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        State st = STATES.get(event.getTarget().getUUID());
        if (st != null && st.color >= 0) PacketDistributor.sendToPlayer(sp, new SummonNet.Look(st.mob.getId(), st.color));
    }

    private static void sendLook(Mob mob, State st) {
        PacketDistributor.sendToPlayersTrackingEntity(mob, new SummonNet.Look(mob.getId(), st.color));
    }

    /** Agachar + clicar na SUA invocação: troca a ordem só dela. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof Mob mob) || !SummonBodies.isBody(mob.getType())) return;
        if (!(mob instanceof OwnableEntity o) || !sp.getUUID().equals(o.getOwnerUUID())) return;
        State st = state(mob, true);
        SummonOrder order = st.order.next();
        setOrder(st, order, null, null);
        Component msg = order == SummonOrder.HUNT ? Component.translatable("message.isekai_mahou.forms_b.order_hunt_wait")
                : Component.translatable("message.isekai_mahou.forms_b.order_one", mob.getDisplayName(), order.displayName());
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Dano causado: (1) um corpo acertou alguém → aplica a receita; (2) o dono acertou alguém → marca de caça. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (STATES.isEmpty() && AWAIT_MARK.isEmpty()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) return;
        Entity direct = event.getSource().getDirectEntity();
        Entity cause = event.getSource().getEntity();
        State st = stateOf(direct);
        if (st == null) st = stateOf(cause);
        if (st == null && direct instanceof Projectile p) st = stateOf(p.getOwner());
        if (st != null) {
            applyEffects(st, victim, false);
            return;
        }
        if (!applying && cause instanceof LivingEntity owner && AWAIT_MARK.contains(owner.getUUID())) markFor(owner, victim);
    }

    @Nullable
    private static State stateOf(@Nullable Entity e) {
        if (e == null || STATES.isEmpty()) return null;
        State st = STATES.get(e.getUUID());
        return st != null && st.mob == e ? st : null;
    }

    /** O ataque da criatura aplica os efeitos da receita no alvo (e os benéficos no dono), respeitando o intervalo. */
    private static void applyEffects(State st, LivingEntity victim, boolean line) {
        if (st.spell == null || applying || !(st.mob.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now < st.next || victim == st.mob || !victim.isAlive()) return;
        SpellContext c = st.context(level);
        if (c == null || c.caster() == null || !c.caster().isAlive() || c.isAlly(victim)) return;
        st.next = now + EFFECT_INTERVAL;
        if (line) ElementFx.line(level, st.mob.getBoundingBox().getCenter(), victim.getBoundingBox().getCenter(), c.element());
        Mob mob = st.mob;
        // No tick seguinte: aqui estamos DENTRO do golpe da criatura (evento de dano) — aplicar já seria um dano dentro
        // do outro. E zera a invencibilidade curta do alvo, senão o golpe que acabou de entrar engoliria o dano da receita.
        SpellScheduler.schedule(1, () -> {
            LivingEntity owner = c.caster();
            if (owner == null || !owner.isAlive() || !victim.isAlive() || victim.isRemoved() || victim.level() != level) return;
            victim.invulnerableTime = 0;
            applying = true;
            try {
                c.resolveOnEntity(victim, null);
                if (mob.isAlive() && owner.distanceToSqr(mob) < 16 * 16) FormsBUtil.applyBeneficialToCaster(c);
            } finally {
                applying = false;
            }
            ElementFx.burst(level, victim.getBoundingBox().getCenter(), c.element(), 6, 0.25);
        });
    }

    /** Receita só de apoio (cura, escudo...): a criatura cuida do dono quando ele precisa, sem esperar um golpe. */
    private static void supportPulse(State st, ServerLevel level, SpellContext c) {
        LivingEntity owner = c.caster();
        if (owner == null || !owner.isAlive() || owner.distanceToSqr(st.mob) > 12 * 12) return;
        boolean hurt = owner.getHealth() < owner.getMaxHealth() - 0.5F;
        boolean fighting = owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100;
        if (!hurt && !fighting) return;
        st.next = level.getGameTime() + EFFECT_INTERVAL * 2L;
        ElementFx.line(level, st.mob.getBoundingBox().getCenter(), owner.getBoundingBox().getCenter(), c.element());
        applying = true;
        try {
            FormsBUtil.applyBeneficialToCaster(c);
        } finally {
            applying = false;
        }
    }

    /** A cada 5 ticks: cumpre as ordens das criaturas acompanhadas e dispara os pulsos de quem lança sozinho. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (STATES.isEmpty() || event.getServer().getTickCount() % 5 != 0) return;
        for (Iterator<State> it = STATES.values().iterator(); it.hasNext();) {
            State st = it.next();
            Mob mob = st.mob;
            if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            switch (st.order) {
                case HUNT -> tickHunt(st, mob, level);
                case GUARD -> {
                    LivingEntity t = mob.getTarget();
                    if (t != null && st.guard != null && t.distanceToSqr(st.guard) > GUARD_LEASH * GUARD_LEASH) mob.setTarget(null);
                }
                case STAY -> {
                    // empurrada para longe do posto (ela não anda): volta
                    if (st.guard != null && mob.distanceToSqr(st.guard) > 3 * 3) mob.teleportTo(st.guard.x, st.guard.y, st.guard.z);
                }
                default -> { }
            }
            if (st.spell == null || level.getGameTime() < st.next) continue;
            SpellContext c = st.context(level);
            if (c == null || c.caster() == null) continue;
            if (FormsBUtil.onlyBeneficial(c)) {
                supportPulse(st, level, c);
            } else if (st.caster) {
                LivingEntity t = mob.getTarget() != null && mob.getTarget().isAlive() && mob.getTarget().distanceToSqr(mob) < 12 * 12
                        ? mob.getTarget() : FormsBUtil.nearestThreat(c, mob.getBoundingBox().getCenter(), 12, true);
                if (t != null) applyEffects(st, t, true);
            }
        }
    }

    private static void tickHunt(State st, Mob mob, ServerLevel level) {
        if (st.mark == null) {
            awaitMark(st.owner());
            return;
        }
        LivingEntity m = level.getEntity(st.mark) instanceof LivingEntity le ? le : null;
        if (m == null || !m.isAlive() || m.distanceToSqr(mob) > 64 * 64 || !mob.canAttack(m)) {
            // a caça acabou: espera a próxima marca (até lá, comporta-se como Seguir)
            st.mark = null;
            st.save();
            awaitMark(st.owner());
            return;
        }
        if (mob.getTarget() != m) mob.setTarget(m);
    }

    /**
     * Dica de primeira vez (uma só, pelo {@link Hints}): o jogador conjurou um corpo SEM a Invocação (ou a Invocação sem
     * corpo) e já conhece a outra metade — então avisamos que as duas juntas fazem a criatura obedecer à receita.
     */
    public static void onCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer sp) || Hints.seen(sp, "summon_forge_tip")) return;
        boolean conjure = false, body = false;
        for (ResourceLocation id : event.getContext().recipe().glyphs()) {
            if (id.equals(GLYPH_CONJURE)) conjure = true;
            else if (SummonBodies.byGlyph(id) != null) body = true;
        }
        if (conjure == body) return;
        MagicData d = MagicApi.get(sp);
        boolean knowsOther = false;
        if (conjure) {
            for (SummonBodies.Body b : SummonBodies.all()) knowsOther |= d.knowsGlyph(b.glyph());
        } else {
            knowsOther = d.knowsGlyph(GLYPH_CONJURE);
        }
        if (knowsOther) Hints.show(sp, "summon_forge_tip");
    }

    /** Servidor fechou: esquece tudo (o que importa está salvo nas criaturas). */
    public static void clear() {
        STATES.clear();
        AWAIT_MARK.clear();
        LAST_KEY.clear();
    }

    // =====================================================================================
    //  Depuração
    // =====================================================================================

    /** Linhas de depuração sobre as invocações do jogador (comando de op). */
    public static List<Component> describe(ServerPlayer owner) {
        List<Mob> mobs = new ArrayList<>();
        List<SpellConjurerEntity> spirits = new ArrayList<>();
        owned(owner, mobs, spirits);
        List<Component> out = new ArrayList<>();
        for (Mob m : mobs) {
            State st = stateOf(m);
            out.add(Component.literal("- ").append(m.getDisplayName()).append(" · ").append(orderOf(m).displayName())
                    .append(Component.literal(st == null ? "" : (st.spell != null ? " · recipe" : "") + (st.mark != null ? " · mark" : "")
                            + (st.guard != null ? String.format(" · %.0f %.0f %.0f", st.guard.x, st.guard.y, st.guard.z) : ""))));
        }
        for (SpellConjurerEntity s : spirits) {
            out.add(Component.literal("- ").append(s.getDisplayName()).append(" · ").append(s.order().displayName()));
        }
        return out;
    }

    // =====================================================================================
    //  IA: segurar o posto
    // =====================================================================================

    private static void ensureGoal(Mob mob, State st) {
        mob.goalSelector.removeAllGoals(g -> g instanceof PostGoal);
        // prioridade acima do "seguir o dono" de qualquer corpo; sai sozinho quando há alvo (o ataque do corpo assume)
        mob.goalSelector.addGoal(-1, new PostGoal(mob, st));
    }

    /**
     * Guardar/Ficar: sem alvo, a criatura volta para o posto e fica nele — ocupa o movimento para o "seguir o dono"
     * e o passeio do corpo não rodarem. Com alvo, sai da frente e o ataque do próprio corpo assume.
     */
    private static final class PostGoal extends Goal {
        private final Mob mob;
        private final State st;
        private int recalc;

        PostGoal(Mob mob, State st) {
            this.mob = mob;
            this.st = st;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return st.order.anchored() && st.guard != null && mob.getTarget() == null;
        }

        @Override
        public void start() {
            recalc = 0;
            mob.getNavigation().stop();
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            Vec3 g = st.guard;
            if (g == null || st.order == SummonOrder.STAY) return;
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (mob.distanceToSqr(g) > 2.5 * 2.5) mob.getNavigation().moveTo(g.x, g.y, g.z, 1.0);
            else mob.getNavigation().stop();
        }
    }
}
