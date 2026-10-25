package ua.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NpcManager {
    private static final double SHOW_DIST = 48, HIDE_DIST = 56, LOOK_DIST = 8;

    private final Main plugin;
    private final File file;
    private final Map<String, Npc> npcs = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1_100_000_000);

    public NpcManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public Collection<Npc> all() { return npcs.values(); }
    public Npc get(String id) { return npcs.get(id.toLowerCase()); }

    public Npc byEntityId(int entityId) {
        for (Npc n : npcs.values()) if (n.entityId == entityId) return n;
        return null;
    }

    public Npc create(String id, Location loc) {
        Npc n = new Npc(id.toLowerCase(), ids.getAndIncrement(), loc.clone());
        npcs.put(n.id, n);
        save();
        return n;
    }

    public void remove(Npc n) {
        for (UUID u : new ArrayList<>(n.viewers)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) hide(n, p);
        }
        npcs.remove(n.id);
        save();
    }

    /** Перемалювати NPC для всіх, хто його бачить (після зміни скіна/броні/імені). */
    public void refresh(Npc n) {
        for (UUID u : new ArrayList<>(n.viewers)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            hide(n, p);
            show(n, p);
        }
        save();
    }

    public void setSkin(Npc n, String owner, SkinFetcher.Skin s) {
        n.skinOwner = owner;
        n.skinValue = s.value();
        n.skinSig = s.signature();
        refresh(n);
    }

    // ------------------------------------------------------------ packets

    private void send(Player p, PacketContainer packet) {
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet);
        } catch (Exception ex) {
            plugin.getLogger().warning("Packet error: " + ex.getMessage());
        }
    }

    private static byte angle(float deg) { return (byte) (deg * 256.0f / 360.0f); }

    private String profileName(Npc n) {
        String s = n.name.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    public void show(Npc n, Player p) {
        n.viewers.add(p.getUniqueId());

        WrappedGameProfile gp = new WrappedGameProfile(n.uuid, profileName(n));
        if (n.skinValue != null)
            gp.getProperties().put("textures", new WrappedSignedProperty("textures", n.skinValue, n.skinSig));

        // 1) Додаємо в tab (потрібно, щоб клієнт завантажив скін)
        PacketContainer info = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        info.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        info.getPlayerInfoDataLists().write(1, List.of(new PlayerInfoData(
                gp, 0, EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(profileName(n)))));
        send(p, info);

        // 2) Спавн
        PacketContainer spawn = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, n.entityId);
        spawn.getUUIDs().write(0, n.uuid);
        spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawn.getDoubles().write(0, n.loc.getX()).write(1, n.loc.getY()).write(2, n.loc.getZ());
        spawn.getBytes().write(0, angle(n.loc.getPitch())).write(1, angle(n.loc.getYaw()));
        send(p, spawn);

        // 3) Другий шар скіна (плащ/капюшон/куртка тощо)
        PacketContainer meta = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, n.entityId);
        meta.getDataValueCollectionModifier().write(0, List.of(
                new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x7F)));
        send(p, meta);

        // 4) Броня та предмети в руках
        if (!n.equipment.isEmpty()) {
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> list = new ArrayList<>();
            n.equipment.forEach((slot, item) -> list.add(new Pair<>(toItemSlot(slot), item)));
            PacketContainer eq = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
            eq.getIntegers().write(0, n.entityId);
            eq.getSlotStackPairLists().write(0, list);
            send(p, eq);
        }

        // 5) Через 1 сек прибираємо з tab, скін уже завантажений
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PacketContainer rem = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            rem.getUUIDLists().write(0, List.of(n.uuid));
            send(p, rem);
        }, 20L);
    }

    public void hide(Npc n, Player p) {
        n.viewers.remove(p.getUniqueId());
        n.looking.remove(p.getUniqueId());
        PacketContainer destroy = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(n.entityId));
        send(p, destroy);
    }

    private void rotate(Npc n, Player p, float yaw, float pitch) {
        PacketContainer look = new PacketContainer(PacketType.Play.Server.ENTITY_LOOK);
        look.getIntegers().write(0, n.entityId);
        look.getBytes().write(0, angle(yaw)).write(1, angle(pitch));
        look.getBooleans().write(0, true);
        send(p, look);

        PacketContainer head = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        head.getIntegers().write(0, n.entityId);
        head.getBytes().write(0, angle(yaw));
        send(p, head);
    }

    private static EnumWrappers.ItemSlot toItemSlot(EquipmentSlot s) {
        return switch (s) {
            case HAND -> EnumWrappers.ItemSlot.MAINHAND;
            case OFF_HAND -> EnumWrappers.ItemSlot.OFFHAND;
            case FEET -> EnumWrappers.ItemSlot.FEET;
            case LEGS -> EnumWrappers.ItemSlot.LEGS;
            case CHEST -> EnumWrappers.ItemSlot.CHEST;
            default -> EnumWrappers.ItemSlot.HEAD;
        };
    }

    // ------------------------------------------------------------ tick

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    private void tick() {
        for (Npc n : npcs.values()) {
            if (n.loc.getWorld() == null) continue;
            for (Player p : n.loc.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(n.loc);
                boolean seen = n.viewers.contains(p.getUniqueId());

                if (!seen) {
                    if (d <= SHOW_DIST * SHOW_DIST) show(n, p);
                    continue;
                }
                if (d > HIDE_DIST * HIDE_DIST) { hide(n, p); continue; }

                if (n.lookClose && d <= LOOK_DIST * LOOK_DIST) {
                    Location eye = p.getEyeLocation();
                    double dx = eye.getX() - n.loc.getX();
                    double dy = eye.getY() - (n.loc.getY() + 1.62);
                    double dz = eye.getZ() - n.loc.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    rotate(n, p, yaw, pitch);
                    n.looking.add(p.getUniqueId());
                } else if (n.looking.remove(p.getUniqueId())) {
                    rotate(n, p, n.loc.getYaw(), n.loc.getPitch()); // повернути на місце
                }
            }
        }
    }

    // ------------------------------------------------------------ збереження

    public void load() {
        npcs.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Location loc = s.getLocation("location");
            if (loc == null) continue;
            Npc n = new Npc(id, ids.getAndIncrement(), loc);
            n.name = s.getString("name", id);
            n.skinOwner = s.getString("skin.owner");
            n.skinValue = s.getString("skin.value");
            n.skinSig = s.getString("skin.signature");
            n.lookClose = s.getBoolean("look-close", true);
            n.rightActions.addAll(s.getStringList("actions.right"));
            n.leftActions.addAll(s.getStringList("actions.left"));
            ConfigurationSection eq = s.getConfigurationSection("equipment");
            if (eq != null) for (String k : eq.getKeys(false)) {
                ItemStack it = eq.getItemStack(k);
                if (it != null) n.equipment.put(EquipmentSlot.valueOf(k), it);
            }
            npcs.put(n.id, n);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Npc n : npcs.values()) {
            String p = "npcs." + n.id + ".";
            cfg.set(p + "location", n.loc);
            cfg.set(p + "name", n.name);
            cfg.set(p + "skin.owner", n.skinOwner);
            cfg.set(p + "skin.value", n.skinValue);
            cfg.set(p + "skin.signature", n.skinSig);
            cfg.set(p + "look-close", n.lookClose);
            cfg.set(p + "actions.right", n.rightActions);
            cfg.set(p + "actions.left", n.leftActions);
            n.equipment.forEach((slot, it) -> cfg.set(p + "equipment." + slot.name(), it));
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Не вдалось зберегти npcs.yml: " + e.getMessage());
        }
    }
}
