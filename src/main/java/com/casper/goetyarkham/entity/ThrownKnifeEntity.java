package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.item.KnifeDurabilityService;
import com.casper.goetyarkham.item.KnifeItem;
import com.casper.goetyarkham.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A single-target thrown {@link KnifeItem}, ballistically an {@link
 * AbstractArrow} (gravity, drag, collision, owner tracking, shield
 * blocking, and Projectile Protection all inherited for free) but with none
 * of {@code AbstractArrow}'s stick-in-block/pickup-arrow behavior: both
 * {@link #onHitEntity} and {@link #onHitBlock} are fully replaced with a
 * one-shot "settle" that either drops this entity's own carried item stack
 * (full NBT/enchantments/damage/name, set once at construction and never
 * re-derived) or discards without dropping, then removes the entity - see
 * {@link #settleEntityHit}, {@link #settleBlockHit}, {@link
 * #settleTimeout}. {@link #settled} guards every one of those paths so a
 * duplicate collision callback (same-tick entity+block hit, a stray second
 * {@code onHit} call, etc.) can never double-settle.
 */
public final class ThrownKnifeEntity extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> DATA_KNIFE_STACK =
            SynchedEntityData.defineId(ThrownKnifeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int MAX_LIFETIME_TICKS = 200;

    private boolean settled;
    private boolean creativeThrow;

    public ThrownKnifeEntity(EntityType<? extends ThrownKnifeEntity> type, Level level) {
        super(type, level);
    }

    public ThrownKnifeEntity(Level level, LivingEntity owner, ItemStack knifeStack) {
        super(ModEntities.THROWN_KNIFE.get(), owner, level);
        ItemStack carried = knifeStack.copy();
        carried.setCount(1);
        this.entityData.set(DATA_KNIFE_STACK, carried);
        double baseDamage = KnifeItem.THROWN_BASE_DAMAGE;
        int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, carried);
        if (powerLevel > 0) {
            baseDamage += powerLevel * 0.5D + 0.5D;
        }
        this.setBaseDamage(baseDamage);
        this.pickup = Pickup.DISALLOWED;
    }

    public void setCreativeThrow(boolean creativeThrow) {
        this.creativeThrow = creativeThrow;
    }

    public ItemStack getKnifeItem() {
        ItemStack stored = this.entityData.get(DATA_KNIFE_STACK);
        return stored.isEmpty() ? new ItemStack(ModItems.KNIFE.get()) : stored;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_KNIFE_STACK, ItemStack.EMPTY);
    }

    @Override
    protected ItemStack getPickupItem() {
        return getKnifeItem().copy();
    }

    @Override
    public void tick() {
        if (!settled && !this.level().isClientSide && this.tickCount > MAX_LIFETIME_TICKS) {
            settleTimeout();
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (settled) {
            return;
        }
        Entity target = result.getEntity();
        Entity ownerEntity = getOwner();
        DamageSource damageSource = this.damageSources().arrow(this, ownerEntity);
        boolean hurtSucceeded = target.hurt(damageSource, (float) getBaseDamage());
        if (hurtSucceeded && ownerEntity instanceof LivingEntity livingOwner
                && target instanceof LivingEntity livingTarget) {
            livingOwner.setLastHurtMob(livingTarget);
            if (!this.level().isClientSide) {
                EnchantmentHelper.doPostHurtEffects(livingTarget, ownerEntity);
                EnchantmentHelper.doPostDamageEffects(livingOwner, livingTarget);
            }
        }
        playSound(SoundEvents.ARROW_HIT, 1.0F, 1.2F);
        settleEntityHit(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (settled) {
            return;
        }
        playSound(SoundEvents.ARROW_HIT, 1.0F, 1.2F);
        settleBlockHit(result);
    }

    /** Entity hits always consume one durability point (subject to Unbreaking), win or lose. */
    private void settleEntityHit(Vec3 hitPos) {
        settled = true;
        if (this.level().isClientSide) {
            return;
        }
        ItemStack dropStack = getKnifeItem().copy();
        boolean broke = false;
        if (!creativeThrow) {
            ServerPlayer ownerPlayer = getOwner() instanceof ServerPlayer sp ? sp : null;
            broke = KnifeDurabilityService.damageOnEntityHit(
                    dropStack, this.level().getRandom(), ownerPlayer);
        }
        if (broke) {
            this.level().playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F + this.random.nextFloat() * 0.4F);
        } else if (!creativeThrow) {
            spawnAtLocation(dropStack, 0.1F);
        }
        discard();
    }

    /** Block hits never consume durability and always produce a pickup-able drop. */
    private void settleBlockHit(BlockHitResult result) {
        settled = true;
        if (!this.level().isClientSide && !creativeThrow) {
            Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            Vec3 dropPos = result.getLocation().add(normal.scale(0.2D));
            ItemEntity itemEntity = new ItemEntity(
                    this.level(), dropPos.x, dropPos.y, dropPos.z, getKnifeItem().copy());
            itemEntity.setDefaultPickUpDelay();
            this.level().addFreshEntity(itemEntity);
        }
        discard();
    }

    /** Mid-flight timeout: converts to a drop without ever touching durability. */
    private void settleTimeout() {
        settled = true;
        if (!this.level().isClientSide && !creativeThrow) {
            spawnAtLocation(getKnifeItem().copy(), 0.0F);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("KnifeItem", getKnifeItem().save(new CompoundTag()));
        tag.putBoolean("Settled", settled);
        tag.putBoolean("CreativeThrow", creativeThrow);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("KnifeItem", 10)) {
            this.entityData.set(DATA_KNIFE_STACK, ItemStack.of(tag.getCompound("KnifeItem")));
        }
        settled = tag.getBoolean("Settled");
        creativeThrow = tag.getBoolean("CreativeThrow");
    }
}
