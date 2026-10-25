package ua.npc;

import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Дані одного NPC. Сутності на сервері немає — лише пакети. */
public class Npc {
    public final String id;
    public final int entityId;
    public final UUID uuid = UUID.randomUUID();
    public Location loc;
    public String name;            // ім'я над головою (до 16 символів)
    public String skinOwner;       // нік, з якого взято скін
    public String skinValue, skinSig;
    public boolean lookClose = true;
    public final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    public final List<String> rightActions = new ArrayList<>();
    public final List<String> leftActions = new ArrayList<>();
    public final Set<UUID> viewers = new HashSet<>();
    public final Set<UUID> looking = new HashSet<>();

    public Npc(String id, int entityId, Location loc) {
        this.id = id;
        this.entityId = entityId;
        this.loc = loc;
        this.name = id;
    }
}
