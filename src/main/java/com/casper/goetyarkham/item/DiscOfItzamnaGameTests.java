package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.common.entities.ModEntityType;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.entity.DiscOfItzamnaAvoidPlayerGoal;
import com.casper.goetyarkham.entity.ModEntityTypeTags;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiscOfItzamnaGameTests {
    private DiscOfItzamnaGameTests() {
    }

    @GameTest(template = "empty")
    public static void bossOrEliteTagCoversRequiredTypes(GameTestHelper helper) {
        helper.assertTrue(ModEntityTypeTags.isBossOrElite(EntityType.WITHER),
                "Vanilla Wither is missing from boss_or_elite via forge:bosses");
        helper.assertTrue(ModEntityTypeTags.isBossOrElite(
                        ModEntityType.APOSTLE.get()),
                "Goety formal boss is missing from boss_or_elite");
        helper.assertTrue(ModEntityTypeTags.isBossOrElite(
                        ModEntityType.WIGHT.get()),
                "Goety mini boss is missing from boss_or_elite");

        EntityType<?> lich = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath("graveyard", "lich"));
        helper.assertTrue(lich != null,
                "The mandatory Graveyard dependency did not register its lich");
        helper.assertTrue(ModEntityTypeTags.isBossOrElite(lich),
                "Graveyard lich is missing from boss_or_elite");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void durabilityCountsOncePerActiveSecond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new DiscTestPlayer(level, "disc-durability");
        player.setPos(helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D)));

        try {
            ICurioStacksHandler charm = charmHandler(player, helper);
            ItemStack disc = new ItemStack(ModItems.DISC_OF_ITZAMNA.get());
            charm.getStacks().setStackInSlot(0, disc);
            SlotContext context = new SlotContext(
                    CurioSlotIds.CHARM, player, 0, false, true);

            Zombie first = EntityType.ZOMBIE.create(level);
            Zombie second = EntityType.ZOMBIE.create(level);
            helper.assertTrue(first != null && second != null,
                    "Could not create ordinary durability-test enemies");
            first.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
            second.setPos(player.getX() + 5.0D, player.getY(), player.getZ());
            helper.assertTrue(DiscOfItzamnaEffectService.isEligibleEnemy(
                            player, first)
                            && DiscOfItzamnaEffectService.isEligibleEnemy(
                            player, second),
                    "Ordinary enemies did not pass the shared target filter");

            updateDurability(context, disc, player, true,
                    DiscOfItzamnaItem.DURABILITY_INTERVAL_TICKS);
            helper.assertTrue(disc.getDamageValue() == 1,
                    "Two ordinary enemies caused more than one durability roll");

            updateDurability(context, disc, player, false,
                    DiscOfItzamnaItem.DURABILITY_INTERVAL_TICKS * 2);
            helper.assertTrue(disc.getDamageValue() == 1,
                    "Disc lost durability with no eligible enemies");

            WitherBoss wither = EntityType.WITHER.create(level);
            helper.assertTrue(wither != null, "Could not create Wither test boss");
            wither.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
            helper.assertTrue(!DiscOfItzamnaEffectService.isEligibleEnemy(
                            player, wither),
                    "Boss passed the shared Disc target filter");
            updateDurability(context, disc, player, false,
                    DiscOfItzamnaItem.DURABILITY_INTERVAL_TICKS);
            helper.assertTrue(disc.getDamageValue() == 1,
                    "Boss triggered Disc of Itzamna durability");

            Mob servant = ModEntityType.ZOMBIE_SERVANT.get().create(level);
            helper.assertTrue(servant != null,
                    "Could not create Goety servant test entity");
            helper.assertTrue(servant instanceof IOwned,
                    "Goety zombie servant no longer exposes its ownership API");
            ((IOwned) servant).setTrueOwner(player);
            helper.assertTrue(
                    DiscOfItzamnaEffectService.isPlayerOwnedOrSummoned(servant),
                    "Goety-owned servant was not recognized by the shared filter");
            servant.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
            helper.assertTrue(!DiscOfItzamnaEffectService.isEligibleEnemy(
                            player, servant),
                    "Player-owned servant passed the shared Disc target filter");
            updateDurability(context, disc, player, false,
                    DiscOfItzamnaItem.DURABILITY_INTERVAL_TICKS);
            helper.assertTrue(disc.getDamageValue() == 1,
                    "Player-owned servant triggered Disc durability");

            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ordinaryEnemyPathfindsAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 13, 11);

        ServerPlayer player = new DiscTestPlayer(level, "disc-avoidance");
        player.setPos(helper.absoluteVec(new Vec3(3.5D, 1.0D, 5.5D)));
        player.setNoGravity(true);
        player.setInvulnerable(true);

        ICurioStacksHandler charm = charmHandler(player, helper);
        ItemStack disc = new ItemStack(ModItems.DISC_OF_ITZAMNA.get());
        charm.getStacks().setStackInSlot(0, disc);

        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null,
                "Could not create ordinary avoidance-test enemy");
        zombie.setPos(helper.absoluteVec(new Vec3(7.5D, 1.0D, 5.5D)));
        level.addFreshEntity(zombie);
        helper.runAfterDelay(2, () -> {
            level.players().add(player);
            double startingDistance = zombie.distanceToSqr(player);
            DiscOfItzamnaAvoidPlayerGoal goal =
                    new DiscOfItzamnaAvoidPlayerGoal(zombie, 1.0D, 1.2D);
            try {
                helper.assertTrue(
                        DiscOfItzamnaEffectService.isWearingActiveDisc(player),
                        "Avoidance-test player is not recognized as wearing the Disc");
                helper.assertTrue(DiscOfItzamnaEffectService.isEligibleEnemy(
                                player, zombie),
                        "Ordinary avoidance-test enemy failed the shared filter");
                helper.assertTrue(DiscOfItzamnaEffectService
                                .findNearestActiveWearer(zombie) == player,
                        "Avoidance goal did not dynamically find its Disc wearer");
                boolean routeFound = false;
                for (int attempt = 0;
                     attempt < 40 && !routeFound;
                     attempt++) {
                    routeFound = goal.canUse();
                }
                helper.assertTrue(routeFound,
                        "Ordinary enemy could not create an avoidance route");
                goal.start();
                Path path = zombie.getNavigation().getPath();
                helper.assertTrue(path != null,
                        "Avoidance goal did not start normal path navigation");
                Node end = path.getEndNode();
                helper.assertTrue(end != null,
                        "Avoidance route has no destination node");
                double destinationDistance = player.distanceToSqr(
                        end.x + 0.5D,
                        end.y,
                        end.z + 0.5D
                );
                helper.assertTrue(destinationDistance > startingDistance,
                        "Avoidance route does not lead farther from the Disc wearer");
                helper.succeed();
            } finally {
                goal.stop();
                level.players().remove(player);
                zombie.discard();
                player.discard();
            }
        });
    }

    private static ICurioStacksHandler charmHandler(
            ServerPlayer player,
            GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(
                        CurioSlotIds.CHARM))
                .orElse(null);
        helper.assertTrue(handler != null,
                "Test player is missing the Curios charm handler");
        return handler;
    }

    private static void updateDurability(
            SlotContext context,
            ItemStack stack,
            ServerPlayer player,
            boolean active,
            int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            ModItems.DISC_OF_ITZAMNA.get().updateDurability(
                    context, stack, player, active);
        }
    }

    private static void buildFloor(GameTestHelper helper, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                helper.getLevel().setBlock(
                        helper.absolutePos(new BlockPos(x, 0, z)),
                        Blocks.STONE.defaultBlockState(),
                        3
                );
            }
        }
    }

    private static final class DiscTestPlayer extends ServerPlayer {
        private DiscTestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), name));
        }
    }
}
