package org.domaja;

import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SulfurCube;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SulfurListener implements Listener {

    private static final long IGNITER_TTL_MS = 5 * 60_000L;
    private static final long BUCKET_USE_TTL_MS = 3_000L;

    private final CoreProtectAPI cp;

    private final NamespacedKey placerUuid, placerName, placerTime;
    private final NamespacedKey igniterUuid, igniterName, igniterTime;

    private record Actor(UUID uuid, String name) {}

    private record BucketUse(Actor actor, Location loc, long time) {}

    private final Map<UUID, BucketUse> bucketUses = new HashMap<>();

    public SulfurListener(Sulfurlogger plugin, CoreProtectAPI cp) {
        this.cp = cp;
        placerUuid = new NamespacedKey(plugin, "tnt_placer_uuid");
        placerName = new NamespacedKey(plugin, "tnt_placer_name");
        placerTime = new NamespacedKey(plugin, "tnt_placer_time");
        igniterUuid = new NamespacedKey(plugin, "igniter_uuid");
        igniterName = new NamespacedKey(plugin, "igniter_name");
        igniterTime = new NamespacedKey(plugin, "igniter_time");
    }

    private static boolean isSulfurBucket(ItemStack i) {
        if (i == null || i.isEmpty()) return false;
        String n = i.getType().name();
        return n.contains("SULFUR_CUBE") && n.contains("BUCKET");
    }

    private void store(SulfurCube c, NamespacedKey u, NamespacedKey n, NamespacedKey t, Actor a) {
        PersistentDataContainer p = c.getPersistentDataContainer();
        p.set(u, PersistentDataType.STRING, a.uuid().toString());
        p.set(n, PersistentDataType.STRING, a.name());
        p.set(t, PersistentDataType.LONG, System.currentTimeMillis());
    }

    private Actor load(SulfurCube c, NamespacedKey u, NamespacedKey n, NamespacedKey t, long ttlMs) {
        PersistentDataContainer p = c.getPersistentDataContainer();
        String us = p.get(u, PersistentDataType.STRING);
        String nm = p.get(n, PersistentDataType.STRING);
        if (us == null || nm == null) return null;
        if (ttlMs > 0) {
            Long tm = p.get(t, PersistentDataType.LONG);
            if (tm == null || System.currentTimeMillis() - tm > ttlMs) return null;
        }
        try {
            return new Actor(UUID.fromString(us), nm);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void clearAll(SulfurCube c) {
        PersistentDataContainer p = c.getPersistentDataContainer();
        for (NamespacedKey k : new NamespacedKey[]{placerUuid, placerName, placerTime,
                igniterUuid, igniterName, igniterTime}) {
            p.remove(k);
        }
    }

    private Actor placer(SulfurCube c) {
        return load(c, placerUuid, placerName, placerTime, 0);
    }

    private Actor igniter(SulfurCube c) {
        return load(c, igniterUuid, igniterName, igniterTime, IGNITER_TTL_MS);
    }

    private void setIgniter(SulfurCube c, Actor a) {
        store(c, igniterUuid, igniterName, igniterTime, a);
    }

    private Actor responsible(SulfurCube c) {
        Actor i = igniter(c);
        return i != null ? i : placer(c);
    }

    private static Actor of(Player p) {
        return new Actor(p.getUniqueId(), p.getName());
    }

    private Actor resolve(Entity d) {
        if (d instanceof Player p) return of(p);
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return of(p);
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return of(p);
        if (d instanceof SulfurCube sc) return responsible(sc);
        return null;
    }

    private Actor findBucketUser(Location spawn) {
        long now = System.currentTimeMillis();
        bucketUses.values().removeIf(b -> now - b.time() > BUCKET_USE_TTL_MS);
        Actor best = null;
        double bestDist = 100.0;
        for (BucketUse b : bucketUses.values()) {
            if (b.loc().getWorld() != spawn.getWorld()) continue;
            double d = b.loc().distanceSquared(spawn);
            if (d < bestDist) {
                bestDist = d;
                best = b.actor();
            }
        }
        return best;
    }

    private boolean isIgnitingDamage(Entity d) {
        if (d instanceof Player p) {
            return p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.FIRE_ASPECT);
        }
        if (d instanceof Projectile pr) return pr instanceof Fireball || pr.getFireTicks() > 0;
        return d instanceof TNTPrimed || d instanceof SulfurCube;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof SulfurCube c)) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUCKET) return;
        Actor a = findBucketUser(c.getLocation());
        ItemStack held = c.getEquipped();
        if (a != null && held != null && held.getType() == Material.TNT && placer(c) == null) {
            store(c, placerUuid, placerName, placerTime, a);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketUse(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        if (!isSulfurBucket(e.getItem())) return;
        Player p = e.getPlayer();
        bucketUses.put(p.getUniqueId(), new BucketUse(of(p), p.getLocation(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwallow(SulfurCubeSwallowItemEvent e) {
        SulfurCube c = e.getEntity();
        Player p = e.getPlayer();
        ItemStack oldItem = e.getOldItem();
        ItemStack newItem = e.getNewItem();

        if (newItem != null && newItem.getType() == Material.TNT) {
            if (p != null) store(c, placerUuid, placerName, placerTime, of(p));
            PersistentDataContainer pdc = c.getPersistentDataContainer();
            pdc.remove(igniterUuid);
            pdc.remove(igniterName);
            pdc.remove(igniterTime);
        } else if (oldItem != null && oldItem.getType() == Material.TNT) {
            clearAll(c);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof SulfurCube c)) return;
        Player p = e.getPlayer();
        Material m = p.getInventory().getItem(e.getHand()).getType();
        if ((m == Material.FLINT_AND_STEEL || m == Material.FIRE_CHARGE) && c.canExplode()) {
            setIgniter(c, of(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof SulfurCube c)) return;
        if (!(e instanceof EntityDamageByEntityEvent be)) return;
        Entity d = be.getDamager();
        Actor a = resolve(d);
        if (a != null && isIgnitingDamage(d)) {
            setIgniter(c, a);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof SulfurCube c)) return;
        Actor a = responsible(c);
        String user = "#sulfur-" + (a == null ? "unknown" : a.name());

        for (Block block : e.blockList()) {
            Material type = block.getType();
            if (type.isAir()) continue;
            cp.logRemoval(user, block.getLocation(), type, block.getBlockData());
        }
    }
}