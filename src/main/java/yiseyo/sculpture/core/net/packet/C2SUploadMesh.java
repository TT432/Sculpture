package yiseyo.sculpture.core.net.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.common.ModBlocks;
import yiseyo.sculpture.core.world.StatueBlockEntity;

import java.util.function.Supplier;

public record C2SUploadMesh(BlockPos pos, byte[] mesh) {
    public static void encode(C2SUploadMesh msg, FriendlyByteBuf buf) {
        try {
            buf.writeBlockPos(msg.pos);
            buf.writeByteArray(msg.mesh);
            //            Sculpture.LOGGER.info("encode mesh {} bytes", msg.mesh.length);
        } catch (Exception e) {
            Sculpture.LOGGER.error("encode fail", e);
        }
    }

    public static C2SUploadMesh decode(FriendlyByteBuf buf) {
        try {
            BlockPos pos = buf.readBlockPos();
            byte[] mesh = buf.readByteArray();
            //            Sculpture.LOGGER.info("decode mesh {} bytes", mesh.length);
            return new C2SUploadMesh(pos, mesh);
        } catch (Exception e) {
            Sculpture.LOGGER.error("decode fail", e);
            throw e;
        }
    }

    public static void handle(C2SUploadMesh msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(
                        () -> {
                            var player = ctx.get().getSender();
                            var level = player.serverLevel();

                            if (level.getBlockState(msg.pos).getBlock() == Blocks.AIR) {
                                level.setBlockAndUpdate(msg.pos, ModBlocks.STATUE.get().defaultBlockState());
                                BlockEntity blockEntity = level.getBlockEntity(msg.pos);

                                if (blockEntity instanceof StatueBlockEntity statueBlockEntity) {
                                    statueBlockEntity.acceptMesh(msg.mesh());
                                }
                            }
                        });
        ctx.get().setPacketHandled(true);
    }
}
