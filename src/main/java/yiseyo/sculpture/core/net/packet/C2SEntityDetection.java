package yiseyo.sculpture.core.net.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.manager.camera.CameraConfigHelper;
import yiseyo.sculpture.core.net.ModNet;

import java.util.function.Supplier;

/**
 * 客户端到服务端的实体检测数据包
 * 用于请求服务端验证实体信息和拍摄权限
 */
public record C2SEntityDetection(int entityId) {

    /**
     * 编码数据包到缓冲区
     *
     * @param msg 数据包实例
     * @param buf 网络缓冲区
     */
    public static void encode(C2SEntityDetection msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    /**
     * 从缓冲区解码数据包
     *
     * @param buf 网络缓冲区
     * @return 解码后的数据包实例
     */
    public static C2SEntityDetection decode(FriendlyByteBuf buf) {
        return new C2SEntityDetection(buf.readInt());
    }

    /**
     * 处理服务端接收到的数据包
     *
     * @param msg 数据包实例
     * @param ctx 网络事件上下文
     */
    public static void handle(C2SEntityDetection msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.level() == null) {
                Sculpture.LOGGER.warn("Received C2SEntityDetection from null player or level");
                return;
            }

            try {
                // 根据实体ID获取实体
                var entity = player.level().getEntity(msg.entityId);

                if (entity instanceof LivingEntity livingEntity) {
                    // 验证实体权限和距离
                    boolean isAllowed = CameraConfigHelper.isEntityAllowed(livingEntity);
                    double distance = player.distanceTo(livingEntity);
                    double maxRange = CameraConfigHelper.getDetectionRange();

                    // 检查距离限制
                    boolean inRange = distance <= maxRange;

                    // 获取实体显示名称
                    String entityName = livingEntity.getDisplayName().getString();

                    // 发送响应数据包回客户端
                    S2CEntityInfo response = new S2CEntityInfo(
                            msg.entityId,
                            entityName,
                            isAllowed && inRange,
                            isAllowed ? "允许拍摄" : "禁止拍摄",
                            (float) distance
                    );

                    ModNet.CHANNEL.reply(response, context);

                    Sculpture.LOGGER.debug("Processed entity detection for {} (ID: {}, Distance: {:.2f}, Allowed: {})",
                            entityName, msg.entityId, distance, isAllowed && inRange);
                } else {
                    // 实体不存在或不是生物实体
                    S2CEntityInfo response = new S2CEntityInfo(
                            msg.entityId,
                            "未知实体",
                            false,
                            "实体不存在",
                            -1.0f
                    );

                    ModNet.CHANNEL.reply(response, context);

                    Sculpture.LOGGER.debug("Entity not found or not living entity for ID: {}", msg.entityId);
                }

            } catch (Exception e) {
                Sculpture.LOGGER.error("Error processing entity detection for ID: {}", msg.entityId, e);

                // 发送错误响应
                S2CEntityInfo errorResponse = new S2CEntityInfo(
                        msg.entityId,
                        "错误",
                        false,
                        "服务器错误",
                        -1.0f
                );

                ModNet.CHANNEL.reply(errorResponse, context);
            }
        });

        context.setPacketHandled(true);
    }
}