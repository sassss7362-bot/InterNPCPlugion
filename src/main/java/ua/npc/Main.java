package ua.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin {
    private NpcManager manager;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    @Override
    public void onEnable() {
        manager = new NpcManager(this);
        manager.load();
        manager.startTask();

        NpcCommand cmd = new NpcCommand(this, manager);
        getCommand("npc").setExecutor(cmd);
        getCommand("npc").setTabCompleter(cmd);
        Bukkit.getPluginManager().registerEvents(new Events(this, manager), this);

        // Клік по NPC приходить як пакет USE_ENTITY — сутності на сервері нема
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
                    @Override
                    public void onPacketReceiving(PacketEvent e) {
                        int id = e.getPacket().getIntegers().read(0);
                        Npc npc = manager.byEntityId(id);
                        if (npc == null) return;
                        e.setCancelled(true);

                        var use = e.getPacket().getEnumEntityUseActions().read(0);
                        EnumWrappers.EntityUseAction action = use.getAction();
                        boolean right;
                        if (action == EnumWrappers.EntityUseAction.ATTACK) right = false;
                        else if (action == EnumWrappers.EntityUseAction.INTERACT
                                && use.getHand() == EnumWrappers.Hand.MAIN_HAND) right = true;
                        else return; // INTERACT_AT і offhand ігноруємо (дублі)

                        Player p = e.getPlayer();
                        long now = System.currentTimeMillis();
                        if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 300) return;
                        cooldown.put(p.getUniqueId(), now);

                        Bukkit.getScheduler().runTask(Main.this, () ->
                                runActions(p, right ? npc.rightActions : npc.leftActions));
                    }
                });
        getLogger().info("Завантажено NPC: " + manager.all().size());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    /** Формати: "[console] cmd", "[player] cmd", "[message] текст". %player% підставляється. */
    public void runActions(Player p, Iterable<String> actions) {
        for (String a : actions) {
            String line = a.replace("%player%", p.getName());
            if (line.startsWith("[console] "))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring(10));
            else if (line.startsWith("[player] "))
                p.performCommand(line.substring(9));
            else if (line.startsWith("[message] "))
                p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(line.substring(10)));
        }
    }
}
