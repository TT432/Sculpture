package yiseyo.sculpture.core.net.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.manager.camera.EntityDetectionManager;

import java.util.function.Supplier;

/**
 * 服务端到客户端的实体信息数据包
 * 用于响应客户端的实体检测请求，包含实体信息和权限状态
 */
public record S2CEntityInfo(
        int entityId,
        String entityName,
        boolean canCapture,
        String permissionStatus,
        float distance
) {

    /**
     * 编码数据包到缓冲区
     *
     * @param msg 数据包实例
     * @param buf 网络缓冲区
     */
    public static void encode(S2CEntityInfo msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.entityName, 256); // 限制实体名称长度
        buf.writeBoolean(msg.canCapture);
        buf.writeUtf(msg.permissionStatus, 64); // 限制状态文本长度
        buf.writeFloat(msg.distance);
    }

    /**
     * 从缓冲区解码数据包
     *
     * @param buf 网络缓冲区
     * @return 解码后的数据包实例
     */
    public static S2CEntityInfo decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        String entityName = buf.readUtf(256);
        boolean canCapture = buf.readBoolean();
        String permissionStatus = buf.readUtf(64);
        float distance = buf.readFloat();

        return new S2CEntityInfo(entityId, entityName, canCapture, permissionStatus, distance);
    }

    /**
     * 处理客户端接收到的数据包
     *
     * @param msg 数据包实例
     * @param ctx 网络事件上下文
     */
    @OnlyIn(Dist.CLIENT)
    public static void handle(S2CEntityInfo msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            try {
                // 确保在客户端线程中执行
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null) {
                    Sculpture.LOGGER.warn("Received S2CEntityInfo but player or level is null");
                    return;
                }

                // 更新实体检测管理器的缓存信息
                EntityDetectionManager.updateEntityInfo(
                        msg.entityId,
                        msg.entityName,
                        msg.canCapture,
                        msg.permissionStatus,
                        msg.distance
                );

                Sculpture.LOGGER.debug("Updated entity info for {} (ID: {}, Distance: {:.2f}, Can Capture: {})",
                        msg.entityName, msg.entityId, msg.distance, msg.canCapture);

            } catch (Exception e) {
                Sculpture.LOGGER.error("Error handling S2CEntityInfo for entity ID: {}", msg.entityId, e);
            }
        });

        context.setPacketHandled(true);
    }

    /**
     * 创建一个表示实体不存在的响应
     *
     * @param entityId 实体ID
     * @return 错误响应数据包
     */
    public static S2CEntityInfo createNotFoundResponse(int entityId) {
        return new S2CEntityInfo(
                entityId,
                "实体不存在",
                false,
                "未找到实体",
                -1.0f
        );
    }

    /**
     * 创建一个表示权限被拒绝的响应
     *
     * @param entityId   实体ID
     * @param entityName 实体名称
     * @param distance   距离
     * @return 权限拒绝响应数据包
     */
    public static S2CEntityInfo createPermissionDeniedResponse(int entityId, String entityName, float distance) {
        return new S2CEntityInfo(
                entityId,
                entityName,
                false,
                "禁止拍摄",
                distance
        );
    }

    /**
     * 创建一个表示距离超出范围的响应
     *
     * @param entityId   实体ID
     * @param entityName 实体名称
     * @param distance   距离
     * @return 距离超出范围响应数据包
     */
    public static S2CEntityInfo createOutOfRangeResponse(int entityId, String entityName, float distance) {
        return new S2CEntityInfo(
                entityId,
                entityName,
                false,
                "距离过远",
                distance
        );
    }

    /**
     * 创建一个表示允许拍摄的响应
     *
     * @param entityId   实体ID
     * @param entityName 实体名称
     * @param distance   距离
     * @return 允许拍摄响应数据包
     */
    public static S2CEntityInfo createAllowedResponse(int entityId, String entityName, float distance) {
        return new S2CEntityInfo(
                entityId,
                entityName,
                true,
                "允许拍摄",
                distance
        );
    }
}