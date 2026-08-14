package org.bcnlab.beaconLabsVelocityLink;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies plugin-owned GUI inventories without serializing their titles on every click. */
final class GuiHolder implements InventoryHolder {
    private final String id;
    private Inventory inventory;

    private GuiHolder(String id) {
        this.id = id;
    }

    static Inventory create(String id, int size, Component title) {
        GuiHolder holder = new GuiHolder(id);
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder.inventory;
    }

    String getId() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
