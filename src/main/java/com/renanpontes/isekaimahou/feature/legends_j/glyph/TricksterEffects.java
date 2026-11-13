package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_j.LjFx;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos "malandros":
 * <ul>
 *   <li>{@link BoogieWoogie} (homenagem a Todo Aoi, Jujutsu Kaisen) — bate palma e troca de lugar com o alvo.</li>
 *   <li>{@link Steal} (homenagem a Kazuma, Konosuba) — "Steal!": rouba um item de uma criatura (às vezes só porcaria).</li>
 * </ul>
 */
public final class TricksterEffects {

    // =====================================================================================
    //  Boogie Woogie
    // =====================================================================================
    /**
     * Palma! Você troca de lugar com a criatura atingida. Em Si Mesmo (ou sem alvo válido): troca com a criatura na sua
     * mira (24 blocos, Expandir +) e, se não houver, com o SEU projétil mais recente ainda no mundo (flecha, bola de
     * neve, tridente... = o "marcador" arremessado). Inimigo trocado fica Atordoado 0,5 s (perde o alvo).
     * <p>Nunca troca com chefes, NPCs, quem está montado/carregando alguém, nem com jogadores que não sejam do seu grupo
     * ou inimigos de verdade (PvP). Não entra nem sai de torres/masmorras em andamento. Uma troca por conjuração.
     */
    public static class BoogieWoogie extends EffectGlyph {
        public static final int COLOR = 0x8FE8FF;

        public BoogieWoogie() {
            super("boogie_woogie", 10);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public float hintRadius() { return 24F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !LjUtil.once(ctx, "boogie")) return;
            ServerLevel l = ctx.level();
            Entity partner = LjUtil.self(ctx, target) ? null : target;
            if (partner != null && !canSwap(ctx, partner)) partner = null;
            if (partner == null) {
                double range = Mth.clamp(stats.radius(24F), 12F, 40F);
                partner = LjUtil.lookTarget(c, range, e -> canSwap(ctx, e));
            }
            if (partner == null) partner = newestProjectile(c, 40);
            clap(l, c.position().add(0, c.getBbHeight() * 0.7, 0));
            if (partner == null) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.boogie_none").withStyle(ChatFormatting.GRAY));
                return;
            }
            Vec3 a = c.position();
            Vec3 b = partner.position();
            if (LjUtil.wardCrossing(l, a, b, c)) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.warded").withStyle(ChatFormatting.RED));
                return;
            }
            float cy = c.getYRot(), cx = c.getXRot();
            if (!ArcaneUtil.teleport(c, b, cy, cx)) return;
            if (partner instanceof Projectile) {
                partner.setPos(a.x, a.y + 1.0, a.z);
            } else if (!ArcaneUtil.teleport(partner, a, partner.getYRot(), partner.getXRot())) {
                ArcaneUtil.teleport(c, a, cy, cx); // a outra ponta recusou: volta (nada de meia troca)
                return;
            }
            // Inimigo: meio segundo de confusão ("onde ele foi parar?")
            if (partner instanceof LivingEntity le && LjUtil.isFoe(ctx, le)) {
                Statuses.apply(le, Statuses.Kind.STUNNED, 1, 10, c, 1F);
                if (le instanceof Mob m) m.setTarget(null);
            }
            LjFx.poof(l, a, c.getBbHeight(), COLOR);
            LjFx.poof(l, b, partner.getBbHeight(), COLOR);
            ElementFx.flash(l, b.add(0, 1, 0), COLOR, 1.5F);
        }

        private static void clap(ServerLevel l, Vec3 at) {
            LjUtil.sound(l, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.9F);
            LjUtil.sound(l, at, SoundEvents.NOTE_BLOCK_SNARE, 1.0F, 1.4F);
            l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.25);
        }

        static boolean canSwap(SpellContext ctx, Entity e) {
            LivingEntity c = ctx.caster();
            if (e == c || !e.isAlive() || e.isSpectator() || e.level() != ctx.level()) return false;
            if (e.isPassenger() || e.isVehicle()) return false;
            if (e instanceof Player p) return LjUtil.playerConsents(ctx, p);
            if (e instanceof LivingEntity le) return LjUtil.ordinaryMob(le);
            return e instanceof Projectile;
        }

        @Nullable
        private static Projectile newestProjectile(LivingEntity c, double range) {
            Projectile best = null;
            for (Projectile p : c.level().getEntitiesOfClass(Projectile.class, c.getBoundingBox().inflate(range),
                    p -> p.isAlive() && p.getOwner() == c)) {
                if (best == null || p.tickCount < best.tickCount) best = p;
            }
            return best;
        }
    }

    // =====================================================================================
    //  Roubar (Steal)
    // =====================================================================================
    /**
     * "Steal!": tenta roubar UMA coisa de cada criatura atingida (até 3 por conjuração). Chance 50% + 6% por ponto de
     * Sorte + 8% por Amplificar (máx. 90%; chefes: metade). Se der certo:
     * <ul>
     *   <li>15%: porcaria (uma meia listrada... de quem?!);</li>
     *   <li>35%: o item que ela segura na mão (se segurar algo);</li>
     *   <li>senão: um item sorteado da tabela de saque dela (como se tivesse caído ao morrer, até 3 unidades).</li>
     * </ul>
     * Cada criatura só pode ser roubada UMA vez. Chefes só soltam pepitas de ouro (ou a meia). NUNCA rouba de jogadores
     * (a mão escorrega), nem de NPCs, nem dentro de cidades.
     */
    public static class Steal extends EffectGlyph {
        public static final String TAG = "isekai_mahou.lj_stolen";
        public static final int MAX_PER_CAST = 3;

        public Steal() {
            super("kazuma_steal", 8);
            element(Element.ARCANE);
            tier(2);
            minCircle(1);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            LivingEntity victim = target instanceof LivingEntity le && !LjUtil.self(ctx, target) ? le : null;
            if (victim == null) victim = LjUtil.lookTarget(c, 12, e -> !(e instanceof Player) || e != c);
            if (victim == null) return;
            if (!LjUtil.once(ctx, "steal@" + victim.getUUID())) return;
            if (victim instanceof Player) {
                if (LjUtil.once(ctx, "steal_player_msg")) {
                    LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.steal_player").withStyle(ChatFormatting.GRAY));
                }
                return;
            }
            if (victim instanceof Npc || LjUtil.safe(l, victim.position())) {
                if (LjUtil.once(ctx, "steal_town_msg")) {
                    LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.steal_town").withStyle(ChatFormatting.RED));
                }
                return;
            }
            if (!takeSlot(ctx)) return;
            if (victim.getPersistentData().getBoolean(TAG)) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.steal_empty", victim.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            boolean boss = Statuses.isBossLike(victim);
            double luck = c.getAttributes().hasAttribute(Attributes.LUCK) ? c.getAttributeValue(Attributes.LUCK) : 0;
            float chance = Mth.clamp(0.5F + 0.06F * (float) luck + 0.08F * Math.max(0, stats.amplify), 0.1F, 0.9F);
            if (boss) chance *= 0.5F;
            Vec3 vc = LjUtil.center(victim);
            Vec3 hand = c.getEyePosition().add(c.getLookAngle().scale(0.5)).add(0, -0.3, 0);
            if (l.random.nextFloat() >= chance) {
                l.sendParticles(ParticleTypes.SMOKE, vc.x, vc.y, vc.z, 6, 0.2, 0.2, 0.2, 0.02);
                LjUtil.sound(l, vc, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 0.7F);
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.steal_fail", victim.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            ItemStack loot = roll(l, c, victim, boss, (float) luck);
            victim.getPersistentData().putBoolean(TAG, true);
            give(c, loot);
            // Visual: uma "mão" de luz vai do alvo até você + o som de pegar item
            LjFx.chain(l, vc, hand, 0xFFE27A);
            l.sendParticles(ParticleTypes.ENCHANTED_HIT, vc.x, vc.y, vc.z, 10, 0.3, 0.3, 0.3, 0.1);
            LjUtil.sound(l, c.position(), SoundEvents.ITEM_PICKUP, 0.9F, 0.8F);
            LjUtil.sound(l, vc, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.5F);
            boolean junk = loot.has(DataComponents.CUSTOM_NAME) && loot.is(Items.STRING);
            LjUtil.actionbar(c, Component.translatable(junk ? "message.isekai_mahou.legends_j.steal_junk" : "message.isekai_mahou.legends_j.steal_ok",
                    loot.getHoverName(), victim.getDisplayName()).withStyle(junk ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        }

        /** Conta os roubos desta conjuração (formas de área: no máximo {@link #MAX_PER_CAST}). */
        private static boolean takeSlot(SpellContext ctx) {
            for (int i = 0; i < MAX_PER_CAST; i++) if (LjUtil.once(ctx, "steal_slot" + i)) return true;
            return false;
        }

        private static ItemStack roll(ServerLevel l, LivingEntity c, LivingEntity victim, boolean boss, float luck) {
            float r = l.random.nextFloat();
            if (r < 0.15F) return junk();
            if (boss) return new ItemStack(Items.GOLD_NUGGET, 3 + l.random.nextInt(6));
            ItemStack held = victim.getMainHandItem();
            if (r < 0.5F && !held.isEmpty()) {
                ItemStack taken = held.copy();
                victim.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                return taken;
            }
            ItemStack fromTable = fromLootTable(l, c, victim, luck);
            return fromTable.isEmpty() ? junk() : fromTable;
        }

        /** Um item sorteado da tabela de saque da criatura (como se ela tivesse morrido para você). */
        private static ItemStack fromLootTable(ServerLevel l, LivingEntity c, LivingEntity victim, float luck) {
            try {
                ResourceKey<LootTable> key = victim.getLootTable();
                LootTable table = l.getServer().reloadableRegistries().getLootTable(key);
                LootParams.Builder b = new LootParams.Builder(l)
                        .withParameter(LootContextParams.THIS_ENTITY, victim)
                        .withParameter(LootContextParams.ORIGIN, victim.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, l.damageSources().mobAttack(c))
                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, c)
                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, c)
                        .withLuck(luck);
                if (c instanceof ServerPlayer sp) b.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, sp);
                List<ItemStack> drops = new ArrayList<>();
                for (ItemStack s : table.getRandomItems(b.create(LootContextParamSets.ENTITY))) if (!s.isEmpty()) drops.add(s);
                if (drops.isEmpty()) return ItemStack.EMPTY;
                ItemStack pick = drops.get(l.random.nextInt(drops.size())).copy();
                if (pick.getCount() > 3) pick.setCount(3);
                return pick;
            } catch (RuntimeException ex) {
                // Tabela de outro mod com parâmetros exóticos: só não rouba nada de valor
                return ItemStack.EMPTY;
            }
        }

        /** A famosa "porcaria" do Kazuma: uma meia listrada. Inofensiva, inútil e engraçada. */
        public static ItemStack junk() {
            ItemStack s = new ItemStack(Items.STRING);
            s.set(DataComponents.CUSTOM_NAME, Component.translatable("item.isekai_mahou.lj_striped_sock")
                    .withStyle(st -> st.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
            s.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.isekai_mahou.lj_striped_sock.lore")
                    .withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(true)))));
            return s;
        }

        private static void give(LivingEntity c, ItemStack stack) {
            if (stack.isEmpty()) return;
            if (c instanceof ServerPlayer sp) {
                Rewards.give(sp, stack);
                return;
            }
            ItemEntity ie = new ItemEntity(c.level(), c.getX(), c.getY() + 0.5, c.getZ(), stack);
            c.level().addFreshEntity(ie);
        }
    }

    private TricksterEffects() {}
}
