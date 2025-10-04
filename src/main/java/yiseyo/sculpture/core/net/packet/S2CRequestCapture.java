package yiseyo.sculpture.core.net.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkEvent;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.manager.capture.CaptureManager;
import yiseyo.sculpture.core.net.MeshCompressor;
import yiseyo.sculpture.core.net.ModNet;

import java.util.function.Supplier;

public record S2CRequestCapture(
        BlockPos pos,
        int entityId
) {
    public static void encode(S2CRequestCapture msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.entityId);
    }

    public static S2CRequestCapture decode(FriendlyByteBuf buf) {
        return new S2CRequestCapture(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(S2CRequestCapture msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(
                        () -> {
                            var level = Minecraft.getInstance().level;
                            if (level.getBlockState(msg.pos).getBlock() == Blocks.AIR) {
                                try {
                                    CaptureManager.capture(msg.entityId, result ->
                                            ModNet.CHANNEL.sendToServer(new C2SUploadMesh(msg.pos(), MeshCompressor.compress(result))));
                                } catch (Exception e) {
                                    Sculpture.LOGGER.error("Statue mesh pipeline failed @ {}", msg.pos(), e);
                                }
                            }
                        });
        ctx.get().setPacketHandled(true);
    }
}
