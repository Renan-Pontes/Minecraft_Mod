package com.renanpontes.isekaimahou.core.stack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Estado interno de uma conjuração pendente. Leitura pública (a visão {@link SpellStack.Pending} delega para cá);
 * só o {@link StackManager} muda os campos.
 */
public final class PendingState {
    /** De onde veio a pendente. */
    public enum Source { CUSTOM, TELEGRAPH, CHANNEL }

    final int id;
    final UUID casterId;
    @Nullable LivingEntity caster;
    final ResourceKey<Level> dim;
    final Source source;
    final long startTick;
    final boolean boss;
    Vec3 pos;
    float radius;
    long endTick;
    SpellStack.Tier tier;
    @Nullable Component castName;
    Component name;
    @Nullable Element element;
    boolean counterable;
    @Nullable Runnable onCountered;

    boolean done;
    boolean countered;
    /** O PendingCastEvent já saiu (o HUD só mostra depois disso). */
    boolean announced;
    /** Muda quando algo que o HUD mostra muda (o servidor reenvia a linha). */
    int version;

    // ---- golpe telegrafado: grupo de formas do mesmo dono no mesmo tick ----
    final List<Integer> telegraphs = new ArrayList<>();
    long groupTick = Long.MIN_VALUE;
    boolean firedAny;
    /** Maior aquecimento do grupo (define o tier quando nenhuma forma deu um explícito). */
    int maxWarmup;
    @Nullable SpellStack.Tier explicitTier;

    // ---- conjuração de jogador ----
    long channelStart;

    private final SpellStack.Pending view;

    PendingState(int id, LivingEntity caster, ResourceKey<Level> dim, Source source, long startTick, boolean boss) {
        this.id = id;
        this.caster = caster;
        this.casterId = caster.getUUID();
        this.dim = dim;
        this.source = source;
        this.startTick = startTick;
        this.boss = boss;
        this.view = new SpellStack.Pending(this);
    }

    public SpellStack.Pending view() { return view; }
    public int id() { return id; }

    @Nullable
    public LivingEntity caster() {
        LivingEntity c = caster;
        return c == null || c.isRemoved() ? null : c;
    }

    public UUID casterId() { return casterId; }
    public ResourceKey<Level> dim() { return dim; }
    public Source source() { return source; }
    public long startTick() { return startTick; }
    public long endTick() { return endTick; }
    public Vec3 pos() { return pos; }
    public float radius() { return radius; }
    public SpellStack.Tier tier() { return tier; }
    @Nullable public Component castName() { return castName; }
    public Component name() { return name; }
    @Nullable public Element element() { return element; }
    public boolean counterable() { return counterable; }
    public boolean countered() { return countered; }
    public boolean done() { return done; }
    public boolean boss() { return boss; }
}
