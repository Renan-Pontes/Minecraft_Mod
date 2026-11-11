package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Furto (glifo Mão Leve e o Golpe do Ladrão Fantasma). Do que se rouba:
 * <ul>
 *   <li><b>Mob</b> segurando um item (não chefe): 30% de DESARMAR (leva o item da mão);</li>
 *   <li><b>Mob comum</b>: um item sorteado da própria tabela de saque dele (o que ele "carrega no bolso");</li>
 *   <li><b>Aldeões e NPCs</b> (sem tabela): esmeraldas, pão, moedas da Guilda (por id, se o módulo existir);</li>
 *   <li><b>Criatura do mod</b> sem saque: Cristal de Mana, essência ou esmeralda;</li>
 *   <li><b>Chefe</b>: diamante, esmeralda, essência arcana, Cristal de Mana e 5% de um pergaminho de Magia Lendária
 *   (cada chefe só pode ser roubado uma vez — contador {@code rogue.steal.elite});</li>
 *   <li><b>Jogador</b> (só com PvP valendo entre os dois): um quarto de uma pilha da mochila (fora da barra rápida),
 *   1× a cada 60 s por vítima.</li>
 * </ul>
 * Cada mob só tem um "bolso" (marca {@code isekai_mahou.rogue_robbed}); no rank V do Livro-Razão, dois. Chance: 55%
 * + poder, +30% se o alvo não está te vendo/pelas costas, +4% por rank; chefes ×0,6. Falhar: o alvo percebe.
 * <p><b>Crime</b>: roubar um NPC de facção (que não é monstro) à vista de alguém da facção (ou do próprio, de frente):
 * −6 de reputação na facção, +2 Submundo, contadores {@code crime.theft} e {@code crime.theft.<facção>} (ganchos para
 * o módulo cities, via {@code MagicProgressEvent.Counter}) e quem viu e sabe lutar parte para cima — menos moradores e
 * guardas das cidades, cuja reação (abordagem, persuadir, propina) é do módulo cities. Sem testemunhas: +1 Submundo.
 */
public final class RogueSteal {
    public static final String ROBBED_TAG = "isekai_mahou.rogue_robbed";
    private static final int PLAYER_COOLDOWN = 1200;
    private static final Map<String, Long> PVP_COOLDOWN = new HashMap<>();

    public enum Result { STOLEN, DISARMED, EMPTY, FAILED, NOT_ALLOWED, COOLDOWN }

    /**
     * Tenta furtar {@code victim}.
     *
     * @param power      poder da magia (stats.power × ctx.powerMult)
     * @param guaranteed true = não falha (Golpe do Ladrão Fantasma)
     */
    public static Result steal(ServerPlayer thief, LivingEntity victim, float power, boolean guaranteed) {
        if (!(thief.level() instanceof ServerLevel level) || victim == thief || !victim.isAlive()) return Result.NOT_ALLOWED;
        boolean back = RogueCombat.exposedBack(victim, thief);
        if (victim instanceof Player vp) return stealFromPlayer(level, thief, vp, power, guaranteed, back);

        boolean boss = Statuses.isBossLike(victim);
        int robbed = robbedCount(victim);
        int pockets = RogueCombat.rank(thief) >= 5 && !boss ? 2 : 1;
        if (robbed >= pockets) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.steal.empty", victim.getDisplayName()), 0xA89EC0);
            level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            return Result.EMPTY;
        }
        float chance = 0.55F + 0.15F * (power - 1F) + (back ? 0.30F : 0F) + 0.04F * RogueCombat.rank(thief);
        if (boss) chance *= 0.6F;
        if (!guaranteed && thief.getRandom().nextFloat() > Math.min(0.95F, chance)) {
            fail(level, thief, victim);
            return Result.FAILED;
        }
        markRobbed(victim, robbed + 1);

        ItemStack loot = ItemStack.EMPTY;
        boolean disarm = false;
        ItemStack held = victim.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!boss && !held.isEmpty() && victim instanceof Mob && thief.getRandom().nextFloat() < 0.30F) {
            loot = held.copy();
            victim.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            disarm = true;
        }
        if (loot.isEmpty()) loot = boss ? bossLoot(level, thief) : pocketLoot(level, thief, victim);
        if (loot.isEmpty()) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.steal.empty", victim.getDisplayName()), 0xA89EC0);
            return Result.EMPTY;
        }
        deliver(level, thief, victim, loot, disarm);
        MagicApi.addCounter(thief, "rogue.steal", 1);
        if (boss) MagicApi.addCounter(thief, "rogue.steal.elite", 1);
        crime(level, thief, victim, back);
        return disarm ? Result.DISARMED : Result.STOLEN;
    }

    // =====================================================================================
    //  Jogadores
    // =====================================================================================
    private static Result stealFromPlayer(ServerLevel level, ServerPlayer thief, Player victim, float power, boolean guaranteed,
                                         boolean back) {
        if (victim.isCreative() || victim.isSpectator() || !thief.canHarmPlayer(victim)) return Result.NOT_ALLOWED;
        String key = thief.getStringUUID() + "/" + victim.getStringUUID();
        long now = level.getGameTime();
        Long last = PVP_COOLDOWN.get(key);
        if (last != null && now - last < PLAYER_COOLDOWN) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.steal.cooldown", victim.getDisplayName()), 0xA89EC0);
            return Result.COOLDOWN;
        }
        float chance = 0.40F + 0.15F * (power - 1F) + (back ? 0.30F : 0F) + 0.04F * RogueCombat.rank(thief);
        if (!guaranteed && thief.getRandom().nextFloat() > Math.min(0.9F, chance)) {
            fail(level, thief, victim);
            return Result.FAILED;
        }
        PVP_COOLDOWN.put(key, now);
        if (PVP_COOLDOWN.size() > 256) PVP_COOLDOWN.entrySet().removeIf(e -> now - e.getValue() > PLAYER_COOLDOWN);
        var inv = victim.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i < 36; i++) if (!inv.items.get(i).isEmpty()) slots.add(i);
        if (slots.isEmpty()) {
            for (int i = 0; i < 9; i++) if (i != inv.selected && !inv.items.get(i).isEmpty()) slots.add(i);
        }
        if (slots.isEmpty()) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.steal.empty", victim.getDisplayName()), 0xA89EC0);
            return Result.EMPTY;
        }
        int slot = slots.get(thief.getRandom().nextInt(slots.size()));
        ItemStack src = inv.items.get(slot);
        int take = Math.max(1, (int) Math.ceil(src.getCount() * 0.25));
        ItemStack loot = src.split(take);
        victim.inventoryMenu.broadcastChanges();
        deliver(level, thief, victim, loot, false);
        if (victim instanceof ServerPlayer vsp) {
            vsp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.steal.victim_player", thief.getDisplayName(),
                    loot.getHoverName()).withColor(0xFF6A6A), true);
        }
        MagicApi.addCounter(thief, "rogue.steal", 1);
        MagicApi.addCounter(thief, "rogue.steal.player", 1);
        return Result.STOLEN;
    }

    // =====================================================================================
    //  Saque
    // =====================================================================================

    /** Um item sorteado da tabela de saque do mob; sem tabela, a tabela própria do Ladino. */
    private static ItemStack pocketLoot(ServerLevel level, ServerPlayer thief, LivingEntity victim) {
        ResourceKey<LootTable> key = victim.getLootTable();
        if (key != null && !(victim instanceof AbstractVillager)) {
            try {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, victim)
                        .withParameter(LootContextParams.ORIGIN, victim.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(thief))
                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, thief)
                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, thief)
                        .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, thief)
                        .withLuck(thief.getLuck())
                        .create(LootContextParamSets.ENTITY);
                List<ItemStack> drops = new ArrayList<>();
                for (ItemStack s : table.getRandomItems(params)) if (!s.isEmpty()) drops.add(s);
                if (!drops.isEmpty()) return drops.get(thief.getRandom().nextInt(drops.size()));
            } catch (RuntimeException ignored) {
                // tabela estranha de outro mod: cai na tabela do Ladino
            }
        }
        return fallbackLoot(thief.getRandom(), victim);
    }

    private static ItemStack fallbackLoot(RandomSource r, LivingEntity victim) {
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getNamespace();
        boolean npc = victim instanceof AbstractVillager || (!(victim instanceof Enemy) && ns.equals(IsekaiMahou.MODID));
        if (npc) {
            float x = r.nextFloat();
            if (x < 0.45F) return new ItemStack(Items.EMERALD, 1 + r.nextInt(3));
            if (x < 0.65F) {
                ItemStack coin = byId("guild_coin", 1 + r.nextInt(3));
                if (!coin.isEmpty()) return coin;
            }
            if (x < 0.80F) return new ItemStack(Items.BREAD, 1 + r.nextInt(2));
            if (x < 0.92F) return new ItemStack(Items.GOLD_NUGGET, 2 + r.nextInt(5));
            return new ItemStack(Items.PAPER, 1);
        }
        if (ns.equals(IsekaiMahou.MODID)) {
            float x = r.nextFloat();
            if (x < 0.45F) return new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
            if (x < 0.80F) return new ItemStack(CoreRegistries.ESSENCES.get(Element.BASE.get(r.nextInt(Element.BASE.size()))).get());
            return new ItemStack(Items.EMERALD);
        }
        float x = r.nextFloat();
        if (x < 0.5F) return new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(3));
        if (x < 0.8F) return new ItemStack(Items.GOLD_NUGGET, 1 + r.nextInt(2));
        return new ItemStack(Items.STRING, 1);
    }

    private static ItemStack bossLoot(ServerLevel level, ServerPlayer thief) {
        RandomSource r = thief.getRandom();
        if (r.nextFloat() < 0.05F) {
            ItemStack scroll = Legends.randomScroll(r, MagicApi.get(thief).circle());
            if (!scroll.isEmpty()) return scroll;
        }
        float x = r.nextFloat();
        if (x < 0.30F) return new ItemStack(Items.DIAMOND, 1 + r.nextInt(2));
        if (x < 0.55F) return new ItemStack(Items.EMERALD, 3 + r.nextInt(5));
        if (x < 0.80F) return new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 1 + r.nextInt(2));
        return new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 2 + r.nextInt(3));
    }

    private static ItemStack byId(String path, int count) {
        Item it = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).orElse(Items.AIR);
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    // =====================================================================================
    //  Feedback, marca e crime
    // =====================================================================================
    private static void deliver(ServerLevel level, ServerPlayer thief, LivingEntity victim, ItemStack loot, boolean disarm) {
        Component name = loot.getHoverName();
        int count = loot.getCount();
        Rewards.give(thief, loot);
        Vec3 from = RogueFx.center(victim);
        Vec3 to = RogueFx.center(thief);
        RogueFx.line(level, from, to, RogueFx.dust(RogueFx.GOLD, 0.8F), 0.5);
        level.sendParticles(ParticleTypes.WAX_OFF, from.x, from.y, from.z, 8, 0.25, 0.25, 0.25, 0.1);
        RogueFx.sound(level, to, SoundEvents.BUNDLE_INSERT, 0.9F, 1.2F);
        RogueFx.sound(level, from, SoundEvents.ITEM_PICKUP, 0.5F, 1.6F);
        thief.displayClientMessage(Component.translatable(disarm ? "message.isekai_mahou.class_rogue.steal.disarm"
                : "message.isekai_mahou.class_rogue.steal.ok", count, name, victim.getDisplayName()).withColor(RogueFx.GOLD), true);
    }

    private static void fail(ServerLevel level, ServerPlayer thief, LivingEntity victim) {
        thief.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.steal.fail", victim.getDisplayName())
                .withColor(0xFF6A6A), true);
        RogueFx.sound(level, victim.position(), SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3, victim.getZ(),
                2, 0.2, 0.1, 0.2, 0);
        if (victim instanceof Mob mob && !(mob instanceof AbstractVillager) && !isCityNpc(mob)
                && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.setTarget(thief);
        }
        if (victim instanceof ServerPlayer vp) {
            vp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.steal.caught_player", thief.getDisplayName())
                    .withColor(0xFF6A6A), true);
        }
        crime(level, thief, victim, false);
    }

    private static int robbedCount(LivingEntity e) {
        int n = 0;
        for (String t : e.getTags()) {
            if (t.equals(ROBBED_TAG)) n = Math.max(n, 1);
            else if (t.startsWith(ROBBED_TAG + ".")) {
                try {
                    n = Math.max(n, Integer.parseInt(t.substring(ROBBED_TAG.length() + 1)));
                } catch (NumberFormatException ignored) {
                    n = Math.max(n, 1);
                }
            }
        }
        return n;
    }

    private static void markRobbed(LivingEntity e, int n) {
        e.getTags().removeIf(t -> t.startsWith(ROBBED_TAG));
        e.addTag(n <= 1 ? ROBBED_TAG : ROBBED_TAG + "." + n);
    }

    /**
     * Roubo a um NPC de facção (não monstro): se alguém da facção viu (ou o próprio, se você não estava às costas
     * dele), é crime. Contador {@code crime.theft} e {@code rogue.crime}.
     */
    private static void crime(ServerLevel level, ServerPlayer thief, LivingEntity victim, boolean unseenByVictim) {
        if (victim instanceof Player || victim instanceof Enemy) return;
        String faction = Reputation.factionOf(victim);
        if (faction == null || faction.isEmpty()) return;
        LivingEntity witness = unseenByVictim ? null : victim;
        if (witness == null) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, thief.getBoundingBox().inflate(16),
                    e -> e != victim && e.isAlive() && !(e instanceof Player) && faction.equals(Reputation.factionOf(e)))) {
                if (e.hasLineOfSight(thief) && !thief.isInvisible()) {
                    witness = e;
                    break;
                }
            }
        }
        if (witness == null) {
            Reputation.addDaily(thief, "underworld", 1, "rogue.clean_theft", 10,
                    Component.translatable("reason.isekai_mahou.class_rogue.clean_theft"));
            return;
        }
        Component reason = Component.translatable("reason.isekai_mahou.class_rogue.theft");
        Reputation.add(thief, faction, -6, reason);
        Reputation.add(thief, "underworld", 2, reason);
        // ganchos para o módulo cities (só por contador: MagicProgressEvent.Counter): o geral e o do reino/facção
        MagicApi.addCounter(thief, "crime.theft", 1);
        MagicApi.addCounter(thief, "crime.theft." + faction.replaceAll("[^a-z0-9_]", "_"), 1);
        MagicApi.addCounter(thief, "rogue.crime", 1);
        thief.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.steal.seen", witness.getDisplayName())
                .withColor(0xF0A043), false);
        RogueFx.sound(level, witness.position(), SoundEvents.VILLAGER_NO, 1.0F, 0.9F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, witness.getX(), witness.getY() + witness.getBbHeight() + 0.3, witness.getZ(),
                3, 0.2, 0.1, 0.2, 0);
        // quem viu e sabe lutar (aventureiro, membro de facção) parte para cima; o resto só reclama. Os moradores e
        // guardas das CIDADES ficam com o módulo cities (abordagem da guarda: persuadir, propina, devolver, brigar):
        // aqui eles só reclamam, e o guarda ataca sozinho se a reputação cair para Hostil.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, thief.getBoundingBox().inflate(16),
                e -> e instanceof Mob && e.isAlive() && faction.equals(Reputation.factionOf(e)))) {
            Mob mob = (Mob) e;
            if (isCityNpc(mob)) continue;
            if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null && mob.hasLineOfSight(thief) && !(mob instanceof AbstractVillager)) {
                mob.setTarget(thief);
            }
        }
    }

    /** Morador/guarda/mercador/lorde das cidades (pelo id do tipo; o módulo cities pode não existir). */
    private static boolean isCityNpc(LivingEntity e) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return key.getNamespace().equals(IsekaiMahou.MODID) && CITY_NPCS.contains(key.getPath());
    }

    private static final java.util.Set<String> CITY_NPCS = java.util.Set.of("city_guard", "citizen", "city_merchant", "city_lord");

    public static void forget(UUID player) {
        String p = player.toString();
        PVP_COOLDOWN.keySet().removeIf(k -> k.startsWith(p));
    }

    public static void clear() {
        PVP_COOLDOWN.clear();
    }

    private RogueSteal() {}
}
