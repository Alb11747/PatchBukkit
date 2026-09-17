package org.patchbukkit.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.checkerframework.checker.index.qual.NonNegative;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.PatchBukkitServer;

import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.DamageEntityRequest;
import patchbukkit.entity.SetEntityHealthRequest;

import com.destroystokyo.paper.block.TargetBlockInfo;
import com.destroystokyo.paper.block.TargetBlockInfo.FluidMode;
import com.destroystokyo.paper.entity.TargetEntityInfo;

import io.papermc.paper.world.damagesource.CombatTracker;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;

@SuppressWarnings({ "deprecation", "removal" })
public class PatchBukkitLivingEntity
    extends PatchBukkitEntity
    implements LivingEntity {

    public PatchBukkitLivingEntity(UUID uuid, String name, int entityId) {
        super(uuid, name, entityId);
    }

    public PatchBukkitLivingEntity(UUID uuid, String name) {
        this(uuid, name, -1);
    }
    @Override
    public double getEyeHeight() {
        return 1.62;
    }

    @Override
    public double getEyeHeight(boolean ignorePose) {
        return 1.62;
    }

    @Override
    public @NotNull Location getEyeLocation() {
        return getLocation().add(0, getEyeHeight(), 0);
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {
        return getLocation().getWorld().rayTraceBlocks(getEyeLocation(), getEyeLocation().getDirection(), maxDistance, fluidCollisionMode);
    }

    @Override
    public @Nullable Block getTargetBlockExact(int maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {
        RayTraceResult result = rayTraceBlocks(maxDistance, fluidCollisionMode);
        return result != null ? result.getHitBlock() : null;
    }

    @Override
    public @NotNull Block getTargetBlock(@Nullable Set<Material> transparent, int maxDistance) {
        Block hit = getTargetBlockExact(maxDistance, FluidCollisionMode.NEVER);
        return hit != null ? hit : getLocation().getBlock();
    }

    @Override
    public @NotNull List<Block> getLineOfSight(@Nullable Set<Material> transparent, int maxDistance) {
        List<Block> blocks = new ArrayList<>();
        Block target = getTargetBlock(transparent, maxDistance);
        if (target != null) {
            blocks.add(target);
        }
        return blocks;
    }

    @Override
    public @Nullable Block getTargetBlock(int maxDistance, @NotNull FluidMode fluidMode) {
        return getTargetBlock(null, maxDistance);
    }

    @Override
    public @NotNull List<Block> getLastTwoTargetBlocks(@Nullable Set<Material> transparent, int maxDistance) {
        Block target = getTargetBlock(transparent, maxDistance);
        return List.of(target, target);
    }

    @Override
    public @Nullable Entity getTargetEntity(int maxDistance, boolean ignoreBlocks) {
        RayTraceResult result = rayTraceEntities(maxDistance, ignoreBlocks);
        return result != null ? result.getHitEntity() : null;
    }

    @Override
    public @Nullable TargetEntityInfo getTargetEntityInfo(int maxDistance, boolean ignoreBlocks) {
        Entity entity = getTargetEntity(maxDistance, ignoreBlocks);
        return entity != null ? new TargetEntityInfo(entity, entity.getLocation().toVector().subtract(getEyeLocation().toVector())) : null;
    }

    @Override
    public @Nullable TargetBlockInfo getTargetBlockInfo(int maxDistance, @NotNull FluidMode fluidMode) {
        Block block = getTargetBlockExact(maxDistance, FluidCollisionMode.NEVER);
        return block != null ? new TargetBlockInfo(block, BlockFace.UP) : null;
    }

    @Override
    public @Nullable BlockFace getTargetBlockFace(int maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {
        RayTraceResult result = rayTraceBlocks(maxDistance, fluidCollisionMode);
        return result != null ? result.getHitBlockFace() : null;
    }

    @Override
    public @Nullable BlockFace getTargetBlockFace(int maxDistance, @NotNull FluidMode fluidMode) {
        return getTargetBlockFace(maxDistance, FluidCollisionMode.NEVER);
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(int maxDistance, boolean ignoreBlocks) {
        return getLocation().getWorld().rayTraceEntities(getEyeLocation(), getEyeLocation().getDirection(), (double) maxDistance, (double) maxDistance);
    }
    private TriState frictionState = TriState.NOT_SET;

    @Override
    public TriState getFrictionState() {
        return this.frictionState;
    }

    @Override
    public void setFrictionState(TriState state) {
        this.frictionState = state != null ? state : TriState.NOT_SET;
    }

    private final Map<Attribute, AttributeInstance> attributes = new HashMap<>();

    @Override
    public @Nullable AttributeInstance getAttribute(@NotNull Attribute attribute) {
        return attributes.get(attribute);
    }

    @Override
    public void registerAttribute(@NotNull Attribute attribute) {
    }

    @Override
    public <T extends Projectile> @NotNull T launchProjectile(@NotNull Class<? extends T> projectileClass) {
        return launchProjectile(projectileClass, null);
    }

    @Override
    public <T extends Projectile> @NotNull T launchProjectile(@NotNull Class<? extends T> projectileClass, @Nullable Vector velocity) {
        return launchProjectile(projectileClass, velocity, null);
    }

    @Override
    public <T extends Projectile> @NotNull T launchProjectile(@NotNull Class<? extends T> projectileClass, @Nullable Vector velocity, @Nullable Consumer<? super T> function) {
        Location spawnLoc = getEyeLocation();
        Vector dir = velocity != null ? velocity : spawnLoc.getDirection();
        T entity = spawnLoc.getWorld().spawn(spawnLoc, projectileClass, function);
        entity.setVelocity(dir);
        entity.setShooter(this);
        return entity;
    }



    private Player killer;

    @Override
    public @Nullable Player getKiller() {
        return this.killer;
    }

    @Override
    public void setKiller(@Nullable Player killer) {
        this.killer = killer;
    }

    private double health = 20.0;
    private double maxHealth = 20.0;
    private double absorption = 0.0;
    private int arrowsInBody = 0;
    private int arrowCooldown = 0;
    private int beeStingersInBody = 0;
    private int beeStingerCooldown = 0;

    @Override
    public int getArrowsInBody() {
        return this.arrowsInBody;
    }

    @Override
    public void setArrowsInBody(int count) {
        this.arrowsInBody = count;
    }

    @Override
    public void setArrowsInBody(int count, boolean fireEvent) {
        this.arrowsInBody = count;
    }

    @Override
    public int getArrowCooldown() {
        return this.arrowCooldown;
    }

    @Override
    public void setArrowCooldown(int ticks) {
        this.arrowCooldown = ticks;
    }

    @Override
    public int getBeeStingersInBody() {
        return this.beeStingersInBody;
    }

    @Override
    public void setBeeStingersInBody(int count) {
        this.beeStingersInBody = count;
    }

    @Override
    public int getBeeStingerCooldown() {
        return this.beeStingerCooldown;
    }

    @Override
    public void setBeeStingerCooldown(int ticks) {
        this.beeStingerCooldown = ticks;
    }

    private int noActionTicks = 0;

    @Override
    public int getNoActionTicks() {
        return this.noActionTicks;
    }

    @Override
    public void setNoActionTicks(int ticks) {
        this.noActionTicks = ticks;
    }

    private int itemInUseTicks = 0;
    private ItemStack itemInUse = null;

    @Override
    public @Nullable ItemStack getItemInUse() {
        return this.itemInUse;
    }

    @Override
    public int getItemInUseTicks() {
        return this.itemInUseTicks;
    }

    @Override
    public void setItemInUseTicks(int ticks) {
        this.itemInUseTicks = ticks;
    }

    private int remainingAir = 300;
    private int maximumAir = 300;
    private int noDamageTicks = 0;
    private int maximumNoDamageTicks = 20;
    private double lastDamage = 0.0;
    private final Map<PotionEffectType, PotionEffect> potionEffects = new HashMap<>();

    @Override
    public double getHealth() {
        try {
            var response = NativeBridgeFfi.getEntityHealth(BridgeUtils.convertUuid(this.getUniqueId()));
            if (response != null) {
                return response.getHealth();
            }
        } catch (Throwable ignored) {}
        return this.health;
    }

    @Override
    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(health, getMaxHealth()));
        var request = SetEntityHealthRequest.newBuilder()
            .setUuid(BridgeUtils.convertUuid(this.getUniqueId()))
            .setHealth(this.health)
            .build();
        NativeBridgeFfi.setEntityHealth(request);
    }

    @Override
    public void damage(double amount) {
        var request = DamageEntityRequest.newBuilder()
            .setUuid(BridgeUtils.convertUuid(this.getUniqueId()))
            .setAmount(amount)
            .build();
        NativeBridgeFfi.damageEntity(request);
    }

    @Override
    public void damage(double amount, @Nullable Entity source) {
        setHealth(getHealth() - amount);
    }

    @Override
    public void damage(double amount, @NotNull DamageSource damageSource) {
        setHealth(getHealth() - amount);
    }

    @Override
    public void heal(double amount, @NotNull RegainReason reason) {
        setHealth(getHealth() + amount);
    }

    @Override
    public double getMaxHealth() {
        return this.maxHealth;
    }

    @Override
    public void setMaxHealth(double health) {
        this.maxHealth = Math.max(0.1, health);
        if (this.health > this.maxHealth) {
            this.health = this.maxHealth;
        }
    }

    @Override
    public void resetMaxHealth() {
        setMaxHealth(20.0);
    }

    @Override
    public double getAbsorptionAmount() {
        return this.absorption;
    }

    @Override
    public void setAbsorptionAmount(double amount) {
        this.absorption = Math.max(0.0, amount);
    }

    @Override
    public int getRemainingAir() {
        return this.remainingAir;
    }

    @Override
    public void setRemainingAir(int ticks) {
        this.remainingAir = ticks;
    }

    @Override
    public int getMaximumAir() {
        return this.maximumAir;
    }

    @Override
    public void setMaximumAir(int ticks) {
        this.maximumAir = ticks;
    }

    @Override
    public int getNoDamageTicks() {
        return this.noDamageTicks;
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        this.noDamageTicks = ticks;
    }

    @Override
    public int getMaximumNoDamageTicks() {
        return this.maximumNoDamageTicks;
    }

    @Override
    public void setMaximumNoDamageTicks(int ticks) {
        this.maximumNoDamageTicks = ticks;
    }

    @Override
    public double getLastDamage() {
        return this.lastDamage;
    }

    @Override
    public void setLastDamage(double damage) {
        this.lastDamage = damage;
    }

    @Override
    public boolean addPotionEffect(@NotNull PotionEffect effect) {
        return addPotionEffect(effect, false);
    }

    @Override
    public boolean addPotionEffect(@NotNull PotionEffect effect, boolean force) {
        PotionEffectType type = effect.getType();
        PotionEffect existing = potionEffects.get(type);
        if (existing == null || force || effect.getAmplifier() > existing.getAmplifier()
                || (effect.getAmplifier() == existing.getAmplifier() && effect.getDuration() > existing.getDuration())) {
            potionEffects.put(type, effect);
            return true;
        }
        return false;
    }

    @Override
    public boolean addPotionEffects(@NotNull Collection<PotionEffect> effects) {
        boolean success = false;
        for (PotionEffect effect : effects) {
            if (addPotionEffect(effect)) {
                success = true;
            }
        }
        return success;
    }

    @Override
    public boolean hasPotionEffect(@NotNull PotionEffectType type) {
        return potionEffects.containsKey(type);
    }

    @Override
    public @Nullable PotionEffect getPotionEffect(@NotNull PotionEffectType type) {
        return potionEffects.get(type);
    }

    @Override
    public void removePotionEffect(@NotNull PotionEffectType type) {
        potionEffects.remove(type);
    }

    @Override
    public @NotNull Collection<PotionEffect> getActivePotionEffects() {
        return Collections.unmodifiableCollection(potionEffects.values());
    }

    @Override
    public boolean clearActivePotionEffects() {
        boolean hadEffects = !potionEffects.isEmpty();
        potionEffects.clear();
        return hadEffects;
    }

    private boolean removeWhenFarAway = false;
    private boolean canPickupItems = false;
    private boolean leashed = false;
    private Entity leashHolder = null;
    private boolean gliding = false;
    private boolean swimming = false;
    private boolean riptiding = false;
    private boolean sleeping = false;
    private boolean climbing = false;
    private boolean hasAI = true;
    private boolean collidable = true;
    private boolean jumping = false;
    private float hurtDirection = 0.0f;
    private float bodyYaw = 0.0f;
    private final Set<UUID> collidableExemptions = new HashSet<>();
    private final Map<MemoryKey<?>, Object> memories = new HashMap<>();
    private ItemStack activeItem = ItemStack.empty();
    private EquipmentSlot activeItemHand = EquipmentSlot.HAND;
    private int activeItemRemainingTime = 0;
    private int activeItemUsedTime = 0;
    private Key waypointStyle;
    private Color waypointColor;

    @Override
    public boolean hasLineOfSight(@NotNull Entity other) {
        if (other == null || other.getWorld() != getWorld()) return false;
        RayTraceResult result = getWorld().rayTraceBlocks(getEyeLocation(), other.getLocation().toVector().subtract(getEyeLocation().toVector()), getEyeLocation().distance(other.getLocation()), FluidCollisionMode.NEVER);
        return result == null || result.getHitBlock() == null;
    }

    @Override
    public boolean hasLineOfSight(@NotNull Location location) {
        if (location == null || location.getWorld() != getWorld()) return false;
        RayTraceResult result = getWorld().rayTraceBlocks(getEyeLocation(), location.toVector().subtract(getEyeLocation().toVector()), getEyeLocation().distance(location), FluidCollisionMode.NEVER);
        return result == null || result.getHitBlock() == null;
    }

    @Override
    public boolean getRemoveWhenFarAway() {
        return this.removeWhenFarAway;
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        this.removeWhenFarAway = remove;
    }

    @Override
    public @Nullable EntityEquipment getEquipment() {
        return null;
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        this.canPickupItems = pickup;
    }

    @Override
    public boolean getCanPickupItems() {
        return this.canPickupItems;
    }

    @Override
    public boolean isLeashed() {
        return this.leashed;
    }

    @Override
    public @NotNull Entity getLeashHolder() throws IllegalStateException {
        if (!this.leashed || this.leashHolder == null) {
            throw new IllegalStateException("Entity is not leashed");
        }
        return this.leashHolder;
    }

    @Override
    public boolean setLeashHolder(@Nullable Entity holder) {
        this.leashHolder = holder;
        this.leashed = holder != null;
        return true;
    }

    @Override
    public boolean isGliding() {
        return this.gliding;
    }

    @Override
    public void setGliding(boolean gliding) {
        this.gliding = gliding;
    }

    @Override
    public boolean isSwimming() {
        return this.swimming;
    }

    @Override
    public void setSwimming(boolean swimming) {
        this.swimming = swimming;
    }

    @Override
    public boolean isRiptiding() {
        return this.riptiding;
    }

    @Override
    public void setRiptiding(boolean riptiding) {
        this.riptiding = riptiding;
    }

    @Override
    public boolean isSleeping() {
        return this.sleeping;
    }

    @Override
    public boolean isClimbing() {
        return this.climbing;
    }

    @Override
    public void setAI(boolean ai) {
        this.hasAI = ai;
    }

    @Override
    public boolean hasAI() {
        return this.hasAI;
    }

    @Override
    public void attack(@NotNull Entity target) {
        if (target instanceof LivingEntity living) {
            living.damage(1.0, this);
        }
    }

    @Override
    public void swingMainHand() {
    }

    @Override
    public void swingOffHand() {
    }

    @Override
    public void playHurtAnimation(float yaw) {
    }

    @Override
    public void setCollidable(boolean collidable) {
        this.collidable = collidable;
    }

    @Override
    public boolean isCollidable() {
        return this.collidable;
    }

    @Override
    public @NotNull Set<UUID> getCollidableExemptions() {
        return this.collidableExemptions;
    }

    @Override
    public <T> @Nullable T getMemory(@NotNull MemoryKey<T> memoryKey) {
        return (T) this.memories.get(memoryKey);
    }

    @Override
    public <T> void setMemory(@NotNull MemoryKey<T> memoryKey, @Nullable T memoryValue) {
        if (memoryValue == null) {
            this.memories.remove(memoryKey);
        } else {
            this.memories.put(memoryKey, memoryValue);
        }
    }

    @Override
    public @Nullable Sound getHurtSound() {
        return Sound.ENTITY_GENERIC_HURT;
    }

    @Override
    public @Nullable Sound getDeathSound() {
        return Sound.ENTITY_GENERIC_DEATH;
    }

    @Override
    public @NotNull Sound getFallDamageSound(int fallHeight) {
        return fallHeight > 4 ? Sound.ENTITY_GENERIC_BIG_FALL : Sound.ENTITY_GENERIC_SMALL_FALL;
    }

    @Override
    public @NotNull Sound getFallDamageSoundSmall() {
        return Sound.ENTITY_GENERIC_SMALL_FALL;
    }

    @Override
    public @NotNull Sound getFallDamageSoundBig() {
        return Sound.ENTITY_GENERIC_BIG_FALL;
    }

    @Override
    public @NotNull Sound getDrinkingSound(@NotNull ItemStack itemStack) {
        return Sound.ENTITY_GENERIC_DRINK;
    }

    @Override
    public @NotNull Sound getEatingSound(@NotNull ItemStack itemStack) {
        return Sound.ENTITY_GENERIC_EAT;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return hasPotionEffect(PotionEffectType.WATER_BREATHING);
    }

    @Override
    public @NotNull EntityCategory getCategory() {
        return EntityCategory.NONE;
    }

    @Override
    public float getSidewaysMovement() {
        return 0.0f;
    }

    @Override
    public float getUpwardsMovement() {
        return 0.0f;
    }

    @Override
    public float getForwardsMovement() {
        return 0.0f;
    }

    @Override
    public void startUsingItem(@NotNull EquipmentSlot hand) {
        this.activeItemHand = hand;
        this.activeItemRemainingTime = 72000;
        this.activeItemUsedTime = 0;
    }

    @Override
    public void completeUsingActiveItem() {
        this.activeItemRemainingTime = 0;
        this.activeItem = ItemStack.empty();
    }

    @Override
    public @NotNull ItemStack getActiveItem() {
        return this.activeItem != null ? this.activeItem : ItemStack.empty();
    }

    @Override
    public void clearActiveItem() {
        this.activeItem = ItemStack.empty();
        this.activeItemRemainingTime = 0;
        this.activeItemUsedTime = 0;
    }

    @Override
    public int getActiveItemRemainingTime() {
        return this.activeItemRemainingTime;
    }

    @Override
    public void setActiveItemRemainingTime(@Range(from = 0, to = 2147483647) int ticks) {
        this.activeItemRemainingTime = ticks;
    }

    @Override
    public boolean hasActiveItem() {
        return this.activeItemRemainingTime > 0;
    }

    @Override
    public int getActiveItemUsedTime() {
        return this.activeItemUsedTime;
    }

    @Override
    public @NotNull EquipmentSlot getActiveItemHand() {
        return this.activeItemHand != null ? this.activeItemHand : EquipmentSlot.HAND;
    }

    @Override
    public boolean isJumping() {
        return this.jumping;
    }

    @Override
    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    @Override
    public void playPickupItemAnimation(@NotNull Item item, int quantity) {
    }

    @Override
    public float getHurtDirection() {
        return this.hurtDirection;
    }

    @Override
    public void setHurtDirection(float hurtDirection) {
        this.hurtDirection = hurtDirection;
    }

    @Override
    public void knockback(double strength, double directionX, double directionZ) {
        Vector vel = getVelocity();
        vel.setX(vel.getX() + directionX * strength);
        vel.setZ(vel.getZ() + directionZ * strength);
        setVelocity(vel);
    }

    @Override
    public void broadcastSlotBreak(@NotNull EquipmentSlot slot) {
    }

    @Override
    public void broadcastSlotBreak(@NotNull EquipmentSlot slot, @NotNull Collection<Player> players) {
    }

    @Override
    public @NotNull ItemStack damageItemStack(@NotNull ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty()) return ItemStack.empty();
        return stack;
    }

    @Override
    public void damageItemStack(@NotNull EquipmentSlot slot, int amount) {
    }

    @Override
    public float getBodyYaw() {
        return this.bodyYaw;
    }

    @Override
    public void setBodyYaw(float bodyYaw) {
        this.bodyYaw = bodyYaw;
    }

    @Override
    public boolean canUseEquipmentSlot(@NotNull EquipmentSlot slot) {
        return true;
    }

    @Override
    public @NotNull io.papermc.paper.world.damagesource.CombatTracker getCombatTracker() {
        return new io.papermc.paper.world.damagesource.CombatTracker() {
            @Override
            public org.bukkit.entity.LivingEntity getEntity() {
                return PatchBukkitLivingEntity.this;
            }

            @Override
            public java.util.List<io.papermc.paper.world.damagesource.CombatEntry> getCombatEntries() {
                return Collections.emptyList();
            }

            @Override
            public void setCombatEntries(java.util.List<io.papermc.paper.world.damagesource.CombatEntry> entries) {
            }

            @Override
            public io.papermc.paper.world.damagesource.CombatEntry computeMostSignificantFall() {
                return null;
            }

            @Override
            public boolean isInCombat() {
                return false;
            }

            @Override
            public boolean isTakingDamage() {
                return false;
            }

            @Override
            public int getCombatDuration() {
                return 0;
            }

            @Override
            public void addCombatEntry(io.papermc.paper.world.damagesource.CombatEntry entry) {
            }

            @Override
            public net.kyori.adventure.text.Component getDeathMessage() {
                return net.kyori.adventure.text.Component.empty();
            }

            @Override
            public void resetCombatState() {
            }

            @Override
            public io.papermc.paper.world.damagesource.FallLocationType calculateFallLocationType() {
                return null;
            }

            @Override
            public int getLastDamageTime() {
                return 0;
            }
        };
    }

    @Override
    public void setShieldBlockingDelay(int delay) {
    }

    @Override
    public int getShieldBlockingDelay() {
        return 0;
    }

    public void setWaypointStyle(@Nullable Key key) {
        this.waypointStyle = key;
    }

    public void setWaypointColor(@Nullable Color color) {
        this.waypointColor = color;
    }

    public @NotNull Key getWaypointStyle() {
        return this.waypointStyle != null ? this.waypointStyle : Key.key("minecraft", "default");
    }

    public @Nullable Color getWaypointColor() {
        return this.waypointColor;
    }

    public void kill(DamageSource damageSource) {
        setHealth(0.0);
    }

    public float getSoundVolume() {
        return 1.0f;
    }

    public float getSoundPitch() {
        return 1.0f;
    }

    public @Nullable Sound getHurtSound(@NotNull DamageSource damageSource) {
        return Sound.ENTITY_GENERIC_HURT;
    }}
