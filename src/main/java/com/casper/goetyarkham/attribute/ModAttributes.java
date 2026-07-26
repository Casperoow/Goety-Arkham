package com.casper.goetyarkham.attribute;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModAttributes {
    private static final double MIN_VALUE = -1024.0D;
    private static final double MAX_VALUE = 1024.0D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, GoetyArkham.MOD_ID);

    public static final RegistryObject<Attribute> STRENGTH =
            register("strength", "attribute.name.goetyarkham.strength");
    public static final RegistryObject<Attribute> AGILITY =
            register("agility", "attribute.name.goetyarkham.agility");
    public static final RegistryObject<Attribute> WILLPOWER =
            register("willpower", "attribute.name.goetyarkham.willpower");
    public static final RegistryObject<Attribute> INTELLECT =
            register("intellect", "attribute.name.goetyarkham.intellect");
    public static final RegistryObject<Attribute> CRITICAL_CHANCE =
            ATTRIBUTES.register("critical_chance", () ->
                    new RangedAttribute(
                            "attribute.name.goetyarkham.critical_chance",
                            50.0D,
                            0.0D,
                            100.0D
                    ).setSyncable(true));
    public static final RegistryObject<Attribute> CRITICAL_DAMAGE =
            ATTRIBUTES.register("critical_damage", () ->
                    new RangedAttribute(
                            "attribute.name.goetyarkham.critical_damage",
                            100.0D,
                            100.0D,
                            102400.0D
                    ).setSyncable(true));

    private ModAttributes() {
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.register(ModAttributes.class);
    }

    @SubscribeEvent
    public static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, STRENGTH.get());
        event.add(EntityType.PLAYER, AGILITY.get());
        event.add(EntityType.PLAYER, WILLPOWER.get());
        event.add(EntityType.PLAYER, INTELLECT.get());
        event.add(EntityType.PLAYER, CRITICAL_CHANCE.get());
        event.add(EntityType.PLAYER, CRITICAL_DAMAGE.get());
    }

    private static RegistryObject<Attribute> register(String name, String translationKey) {
        return ATTRIBUTES.register(name, () ->
                new RangedAttribute(translationKey, 0.0D, MIN_VALUE, MAX_VALUE)
                        .setSyncable(true));
    }
}
