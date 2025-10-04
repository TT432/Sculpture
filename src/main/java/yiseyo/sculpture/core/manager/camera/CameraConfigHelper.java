package yiseyo.sculpture.core.manager.camera;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import yiseyo.sculpture.Config;

import java.util.List;

/**
 * 相机工具配置辅助类
 * 提供便捷的配置访问方法和实体权限检查功能
 */
public class CameraConfigHelper {

    /**
     * 检查实体是否允许被相机拍摄
     *
     * @param entity 要检查的实体
     * @return true 如果允许拍摄，false 如果禁止拍摄
     */
    public static boolean isEntityAllowed(Entity entity) {
        if (entity == null) {
            return false;
        }

        EntityType<?> entityType = entity.getType();
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);

        if (entityId == null) {
            return false;
        }

        String entityIdString = entityId.toString();

        // 检查黑名单（优先级更高）
        List<? extends String> blockedEntities = Config.BLOCKED_ENTITIES.get();
        if (blockedEntities.contains(entityIdString)) {
            return false;
        }

        // 检查白名单
        List<? extends String> allowedEntities = Config.ALLOWED_ENTITIES.get();
        if (allowedEntities.isEmpty()) {
            // 如果白名单为空，则允许所有未被黑名单阻止的实体
            return true;
        } else {
            // 如果白名单不为空，则只允许白名单中的实体
            return allowedEntities.contains(entityIdString);
        }
    }

    /**
     * 获取实体检测范围
     *
     * @return 检测范围（方块数）
     */
    public static double getDetectionRange() {
        return Config.DETECTION_RANGE.get();
    }

    /**
     * 检查是否应该显示HUD信息
     *
     * @return true 如果应该显示HUD，false 否则
     */
    public static boolean shouldShowHudInfo() {
        return Config.SHOW_HUD_INFO.get();
    }

    /**
     * 获取允许的实体列表
     *
     * @return 允许的实体ID列表
     */
    public static List<? extends String> getAllowedEntities() {
        return Config.ALLOWED_ENTITIES.get();
    }

    /**
     * 获取被阻止的实体列表
     *
     * @return 被阻止的实体ID列表
     */
    public static List<? extends String> getBlockedEntities() {
        return Config.BLOCKED_ENTITIES.get();
    }
}