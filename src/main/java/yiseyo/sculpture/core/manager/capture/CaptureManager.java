package yiseyo.sculpture.core.manager.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import yiseyo.sculpture.core.data.capture.CaptureResult;
import yiseyo.sculpture.core.data.capture.MeshBufferSource;
import yiseyo.sculpture.core.net.ModNet;
import yiseyo.sculpture.core.net.packet.S2CRequestCapture;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(Dist.CLIENT)
public abstract class CaptureManager {
    private static final Int2ObjectMap<CaptureCallback> capturing = new Int2ObjectOpenHashMap<>();
    private static final IntSet captureLocks = new IntOpenHashSet();

    @SubscribeEvent
    public static void onEvent(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int id = entity.getId();

        if (capturing.containsKey(id) && !captureLocks.contains(id)) {
            captureLocks.add(id);
            MeshBufferSource recorder = new MeshBufferSource();
            Minecraft mc = Minecraft.getInstance();
            float partialTick = event.getPartialTick();
            float f = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5, 0, 0.5);

            mc.getEntityRenderDispatcher().render(entity, 0, 0, 0, f,
                    event.getPartialTick(), poseStack, recorder, 0xF000F0);

            capturing.get(id).accept(new CaptureResult(recorder.freeze()));
            capturing.remove(id);
            captureLocks.remove(id);
        }
    }

    @FunctionalInterface
    public interface CaptureCallback extends Consumer<CaptureResult> {
    }

    public static void capture(int entityId, CaptureCallback callback) {
        capturing.put(entityId, callback);
    }

    public static void pendingCapturePacket(Player player, BlockPos pos, Entity entity) {
        ModNet.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new S2CRequestCapture(pos, entity.getId()));
    }
}
