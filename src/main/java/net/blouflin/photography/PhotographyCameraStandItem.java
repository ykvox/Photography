package net.blouflin.photography;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PostSpawnProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PhotographyCameraStandItem extends Item {
    public PhotographyCameraStandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction direction = context.getClickedFace();
        if (direction == Direction.DOWN) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos pos = placeContext.getClickedPos();
        Vec3 center = Vec3.atBottomCenterOf(pos);
        AABB bounds = Photography.CAMERA_STAND_ENTITY.getDimensions().makeBoundingBox(center.x(), center.y(), center.z());
        if (!level.noCollision(null, bounds) || !level.getEntities((Entity) null, bounds, entity -> !entity.isSpectator()).isEmpty()) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            PostSpawnProcessor<PhotographyCameraStandEntity> processor = EntityType.createDefaultStackConfig(serverLevel, stack, context.getPlayer());
            PhotographyCameraStandEntity stand = Photography.CAMERA_STAND_ENTITY.create(serverLevel, processor, pos, EntitySpawnReason.SPAWN_ITEM_USE, true, true);
            if (stand == null) {
                return InteractionResult.FAIL;
            }
            if (context.getPlayer() != null) {
                stand.setOwnerPlayer(context.getPlayer());
            }
            stand.snapTo(center.x(), center.y(), center.z(), context.getRotation(), 0.0f);
            serverLevel.addFreshEntity(stand);
            stand.playPlaceSound();
            stand.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}
