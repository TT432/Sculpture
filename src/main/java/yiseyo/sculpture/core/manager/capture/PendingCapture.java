package yiseyo.sculpture.core.manager.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

record PendingCapture(
        ServerLevel level,
        BlockPos pos,
        int ticks,
        Entity entity
) {
}
