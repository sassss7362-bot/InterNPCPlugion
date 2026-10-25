package ua.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI редагування NPC. */
public class NpcMenu implements InventoryHolder {
    public static final int SKIN = 10, NAME = 11, LOOK = 12, RIGHT = 14, LEFT = 15, DELETE = 16;
    /** слот інвентаря -> слот екіпіровки */
    public static final Map<Integer, EquipmentSlot> EQUIP = Map.of(
            29, EquipmentSlot.HEAD, 30, EquipmentSlot.CHEST, 31, EquipmentSlot.LEGS,
            32, EquipmentSlot.FEET, 33, EquipmentSlot.HAND, 34, EquipmentSlot.OFF_HAND);
    private static final Map<EquipmentSlot, String> LABEL = Map.of(
            EquipmentSlot.HEAD, "Шолом", EquipmentSlot.CHEST, "Нагрудник", EquipmentSlot.LEGS, "Штани",
            EquipmentSlot.FEET, "Черевики", EquipmentSlot.HAND, "Права рука", EquipmentSlot.OFF_HAND, "Ліва рука");

    public final Npc npc;
    private final Inventory inv;

    public NpcMenu(Npc npc) {
        this.npc = npc;
        this.inv = Bukkit.createInventory(this, 45, text("&8NPC: " + npc.id));
        render();
    }

    @Override public Inventory getInventory() { return inv; }

    public static void open(Player p, Npc npc) { p.openInventory(new NpcMenu(npc).inv); }

    public void render() {
        inv.clear();
        ItemStack bg = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        inv.setItem(SKIN, item(Material.PLAYER_HEAD, "&eЗмінити скін",
                "&7Зараз: &f" + (npc.skinOwner == null ? "стандартний" : npc.skinOwner), "&7Клік → введи нік у чат"));
        inv.setItem(NAME, item(Material.NAME_TAG, "&eЗмінити ім'я",
                "&7Зараз: &f" + npc.name, "&7Клік → введи ім'я у чат (до 16 симв.)"));
        inv.setItem(LOOK, item(npc.lookClose ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "&eLookClose: " + (npc.lookClose ? "&aУВІМК" : "&cВИМК"), "&7Клік → перемкнути"));
        inv.setItem(RIGHT, actionsItem("&bДії на ПКМ", npc.rightActions));
        inv.setItem(LEFT, actionsItem("&bДії на ЛКМ", npc.leftActions));
        inv.setItem(DELETE, item(Material.BARRIER, "&cВидалити NPC", "&7Shift+клік → підтвердити"));

        inv.setItem(22, item(Material.ARMOR_STAND, "&6Екіпіровка",
                "&7Клікни по слоту з предметом", "&7на курсорі — одягнути.", "&7Клік порожнім курсором — зняти."));
        EQUIP.forEach((slot, eq) -> {
            ItemStack cur = npc.equipment.get(eq);
            inv.setItem(slot, cur != null ? cur.clone()
                    : item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7" + LABEL.get(eq)));
        });
    }

    private ItemStack actionsItem(String title, List<String> actions) {
        List<String> lore = new ArrayList<>();
        if (actions.isEmpty()) lore.add("&8(порожньо)");
        for (String a : actions) lore.add("&f- " + a);
        lore.add("&7Клік → додати дію (у чат)");
        lore.add("&7Shift+клік → очистити");
        return item(Material.COMMAND_BLOCK, title, lore.toArray(new String[0]));
    }

    private static Component text(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text(name));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(NpcMenu::text).toList());
        it.setItemMeta(meta);
        return it;
    }
}
