package yiseyo.sculpture.core.manager.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import yiseyo.sculpture.core.net.ModNet;
import yiseyo.sculpture.core.net.packet.C2SEntityDetection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实体检测管理器 - 负责射线检测和实体权限验证
 * 用于相机HUD系统检测准星指向的实体
 */
public final class EntityDetectionManager {

    // 缓存检测结果以优化性能
    private static Entity lastDetectedEntity = null;
    private static long lastDetectionTime = 0;
    private static final long CACHE_DURATION_MS = 50; // 50ms缓存时间

    // 网络同步的实体信息缓存
    private static final Map<Integer, EntityInfo> networkEntityCache = new ConcurrentHashMap<>();

    /**
     * 实体信息数据类
     */
    public static class EntityInfo {
        public final String name;
        public final boolean canCapture;
        public final String permissionStatus;
        public final float distance;
        public final long timestamp;

        public EntityInfo(String name, boolean canCapture, String permissionStatus, float distance) {
            this.name = name;
            this.canCapture = canCapture;
            this.permissionStatus = permissionStatus;
            this.distance = distance;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS * 4; // 网络缓存时间更长
        }
    }

    private EntityDetectionManager() {
        // 工具类，禁止实例化
    }

    /**
     * 获取玩家准星指向的实体
     * 使用缓存机制优化性能
     *
     * @param player 玩家实体
     * @return 检测到的实体，如果没有则返回null
     */
    public static LivingEntity getTargetedEntity(Player player) {
        long currentTime = System.currentTimeMillis();

        // 检查缓存是否有效
        if (currentTime - lastDetectionTime < CACHE_DURATION_MS && lastDetectedEntity != null) {
            if (lastDetectedEntity.isAlive() && !lastDetectedEntity.isRemoved()) {
                return lastDetectedEntity instanceof LivingEntity ? (LivingEntity) lastDetectedEntity : null;
            }
        }

        // 执行新的检测
        Entity detected = performRaycast(player);

        // 更新缓存
        lastDetectedEntity = detected;
        lastDetectionTime = currentTime;

        return detected instanceof LivingEntity ? (LivingEntity) detected : null;
    }

    /**
     * 执行射线检测获取准星指向的实体
     *
     * @param player 玩家实体
     * @return 检测到的实体，如果没有则返回null
     */
    private static Entity performRaycast(Player player) {
        double detectionRange = CameraConfigHelper.getDetectionRange();

        Vec3 eyePosition = player.getEyePosition();
        Vec3 viewVector = player.getViewVector(1.0F).scale(detectionRange);
        Vec3 endPosition = eyePosition.add(viewVector);

        // 创建边界框用于实体检测
        AABB searchBox = player.getBoundingBox().expandTowards(viewVector).inflate(1.0D);

        // 定义实体过滤条件
        Predicate<Entity> entityFilter = entity -> {
            // 排除观察者模式和不可选择的实体
            if (entity.isSpectator() || !entity.isPickable()) {
                return false;
            }

            // 排除玩家自己
            if (entity == player) {
                return false;
            }

            // 只检测生物实体
            if (!(entity instanceof LivingEntity)) {
                return false;
            }

            // 检查实体权限
            return CameraConfigHelper.isEntityAllowed(entity);
        };

        // 执行射线检测
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player, eyePosition, endPosition, searchBox, entityFilter, detectionRange * detectionRange
        );

        if (hitResult != null) {
            Entity hitEntity = hitResult.getEntity();

            // 验证距离是否在允许范围内
            double distance = player.distanceTo(hitEntity);
            if (distance <= detectionRange) {
                return hitEntity;
            }
        }

        return null;
    }

    /**
     * 检查实体是否可以被拍摄
     * 综合考虑权限和距离限制
     *
     * @param player 玩家实体
     * @param target 目标实体
     * @return true 如果可以拍摄，false 否则
     */
    public static boolean canCaptureEntity(Player player, LivingEntity target) {
        // 检查实体权限
        if (!CameraConfigHelper.isEntityAllowed(target)) {
            return false;
        }

        // 检查距离限制
        double distance = player.distanceTo(target);
        return distance <= CameraConfigHelper.getDetectionRange();
    }

    /**
     * 获取实体的拍摄权限状态
     *
     * @param entity 目标实体
     * @return 权限状态描述
     */
    public static String getEntityPermissionStatus(LivingEntity entity) {
        if (entity == null) {
            return "无目标";
        }

        // 首先检查网络缓存
        EntityInfo cachedInfo = networkEntityCache.get(entity.getId());
        if (cachedInfo != null && !cachedInfo.isExpired()) {
            return cachedInfo.permissionStatus;
        }

        // 发送网络请求获取最新信息
        requestEntityInfo(entity);

        // 返回本地计算的结果作为临时显示
        if (CameraConfigHelper.isEntityAllowed(entity)) {
            return "允许拍摄";
        } else {
            return "禁止拍摄";
        }
    }

    /**
     * 向服务端请求实体信息
     *
     * @param entity 目标实体
     */
    private static void requestEntityInfo(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        try {
            // 检查网络连接状态
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
                System.err.println("Cannot send entity detection request: not connected to server");
                return;
            }

            C2SEntityDetection packet = new C2SEntityDetection(entity.getId());
            ModNet.CHANNEL.sendToServer(packet);
        } catch (Exception e) {
            // 网络请求失败时记录详细日志
            System.err.println("Failed to send entity detection request for entity ID " + entity.getId() + ": " + e.getMessage());
            e.printStackTrace();

            // 清理可能损坏的缓存条目
            networkEntityCache.remove(entity.getId());
        }
    }

    /**
     * 更新网络同步的实体信息（由S2CEntityInfo数据包调用）
     *
     * @param entityId         实体ID
     * @param name             实体名称
     * @param canCapture       是否可以拍摄
     * @param permissionStatus 权限状态文本
     * @param distance         距离
     */
    public static void updateEntityInfo(int entityId, String name, boolean canCapture,
                                        String permissionStatus, float distance) {
        try {
            // 验证输入参数
            if (name == null) {
                name = "未知实体";
            }
            if (permissionStatus == null) {
                permissionStatus = "状态未知";
            }

            EntityInfo info = new EntityInfo(name, canCapture, permissionStatus, distance);
            networkEntityCache.put(entityId, info);

            // 清理过期的缓存条目
            try {
                networkEntityCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            } catch (Exception cleanupError) {
                System.err.println("Error during cache cleanup: " + cleanupError.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error updating entity info for ID " + entityId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取缓存的实体信息
     *
     * @param entityId 实体ID
     * @return 实体信息，如果不存在或已过期则返回null
     */
    public static EntityInfo getCachedEntityInfo(int entityId) {
        EntityInfo info = networkEntityCache.get(entityId);
        if (info != null && info.isExpired()) {
            networkEntityCache.remove(entityId);
            return null;
        }
        return info;
    }

    /**
     * 清除检测缓存
     * 在配置更改或其他需要强制重新检测的情况下调用
     */
    public static void clearCache() {
        lastDetectedEntity = null;
        lastDetectionTime = 0;
    }

    /**
     * 检查是否应该显示HUD信息
     * 结合配置和当前游戏状态
     *
     * @return true 如果应该显示HUD，false 否则
     */
    public static boolean shouldShowHud() {
        // 检查配置是否启用HUD显示
        if (!CameraConfigHelper.shouldShowHudInfo()) {
            return false;
        }

        // 检查客户端状态
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }

        // 检查玩家是否持有相机物品
        return mc.player.getMainHandItem().getItem() instanceof yiseyo.sculpture.core.world.CameraItem ||
                mc.player.getOffhandItem().getItem() instanceof yiseyo.sculpture.core.world.CameraItem;
    }
}