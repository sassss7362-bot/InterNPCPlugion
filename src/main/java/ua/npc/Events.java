package ua.npc;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Events implements Listener {
    private enum Input { SKIN, NAME, RIGHT, LEFT }
    private record Pending(Npc npc, Input type) {}

    private final Main plugin;
    private final NpcManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public Events(Main plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        for (Npc n : manager.all()) { n.viewers.remove(e.getPlayer().getUniqueId()); n.looking.remove(e.getPlayer().getUniqueId()); }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        for (Npc n : manager.all()) { n.viewers.remove(e.getPlayer().getUniqueId()); n.looking.remove(e.getPlayer().getUniqueId()); }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof NpcMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof NpcMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        Npc npc = menu.npc;
        int slot = e.getRawSlot();

        EquipmentSlot eq = NpcMenu.EQUIP.get(slot);
        if (eq != null) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir()) npc.equipment.put(eq, cursor.clone());
            else npc.equipment.remove(eq);
            manager.refresh(npc);
            menu.render();
            return;
        }

        switch (slot) {
            case NpcMenu.SKIN -> ask(p, npc, Input.SKIN, "Введи нік гравця, чий скін потрібен (cancel — скасувати):");
            case NpcMenu.NAME -> ask(p, npc, Input.NAME, "Введи нове ім'я NPC (до 16 символів):");
            case NpcMenu.LOOK -> { npc.lookClose = !npc.lookClose; manager.save(); menu.render(); }
            case NpcMenu.RIGHT -> {
                if (e.isShiftClick()) { npc.rightActions.clear(); manager.save(); menu.render(); }
                else ask(p, npc, Input.RIGHT, "Введи дію: [console] cmd | [player] cmd | [message] текст");
            }
            case NpcMenu.LEFT -> {
                if (e.isShiftClick()) { npc.leftActions.clear(); manager.save(); menu.render(); }
                else ask(p, npc, Input.LEFT, "Введи дію: [console] cmd | [player] cmd | [message] текст");
            }
            case NpcMenu.DELETE -> {
                if (e.isShiftClick()) { manager.remove(npc); p.closeInventory(); p.sendMessage("§cNPC видалено."); }
            }
            default -> {}
        }
    }

    private void ask(Player p, Npc npc, Input type, String msg) {
        pending.put(p.getUniqueId(), new Pending(npc, type));
        p.closeInventory();
        p.sendMessage("§e" + msg);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Pending pend = pending.remove(e.getPlayer().getUniqueId());
        if (pend == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Npc npc = pend.npc();
            if (msg.equalsIgnoreCase("cancel")) { NpcMenu.open(p, npc); return; }
            switch (pend.type()) {
                case NAME -> {
                    npc.name = msg;
                    manager.refresh(npc);
                    NpcMenu.open(p, npc);
                }
                case RIGHT -> { npc.rightActions.add(msg); manager.save(); NpcMenu.open(p, npc); }
                case LEFT -> { npc.leftActions.add(msg); manager.save(); NpcMenu.open(p, npc); }
                case SKIN -> {
                    p.sendMessage("§7Завантажую скін...");
                    SkinFetcher.byName(msg).thenAccept(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (skin == null) p.sendMessage("§cСкін не знайдено для: " + msg);
                        else manager.setSkin(npc, msg, skin);
                        NpcMenu.open(p, npc);
                    }));
                }
            }
        });
    }
}
