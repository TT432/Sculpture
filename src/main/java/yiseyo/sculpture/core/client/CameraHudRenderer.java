package yiseyo.sculpture.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.manager.camera.EntityDetectionManager;

/**
 * 相机HUD渲染器 - 负责在屏幕上显示实体检测信息
 * 当玩家持有相机物品时，在准星位置显示实体名称和拍摄权限状态
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Sculpture.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraHudRenderer {

    // HUD显示的颜色常量
    private static final int COLOR_ALLOWED = 0x55FF55;    // 绿色 - 允许拍摄
    private static final int COLOR_FORBIDDEN = 0xFF5555;  // 红色 - 禁止拍摄
    private static final int COLOR_BACKGROUND = 0x80000000; // 半透明黑色背景
    private static final int COLOR_TEXT_SHADOW = 0x222222; // 文字阴影颜色

    // HUD布局常量
    private static final int HUD_OFFSET_X = 10; // 相对准星的X偏移
    private static final int HUD_OFFSET_Y = -30; // 相对准星的Y偏移
    private static final int PADDING = 4; // 文字内边距
    private static final int BORDER_WIDTH = 1; // 边框宽度

    private CameraHudRenderer() {
        // 工具类，禁止实例化
    }

    /**
     * 监听GUI覆盖层渲染事件
     * 在所有原版HUD元素渲染完成后绘制相机信息
     */
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        try {
            // 检查是否应该显示HUD
            if (!EntityDetectionManager.shouldShowHud()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;

            if (player == null || mc.level == null) {
                return;
            }

            // 获取准星指向的实体
            LivingEntity targetEntity;
            try {
                targetEntity = EntityDetectionManager.getTargetedEntity(player);
            } catch (Exception detectionError) {
                System.err.println("Error during entity detection: " + detectionError.getMessage());
                return;
            }

            if (targetEntity != null) {
                try {
                    renderEntityInfo(event.getGuiGraphics(), targetEntity,
                            event.getWindow().getGuiScaledWidth(),
                            event.getWindow().getGuiScaledHeight());
                } catch (Exception renderError) {
                    System.err.println("Error rendering entity info for " + targetEntity.getName().getString() + ": " + renderError.getMessage());
                    // 尝试渲染简化的错误信息
                    try {
                        renderErrorInfo(event.getGuiGraphics(), "渲染错误",
                                event.getWindow().getGuiScaledWidth(),
                                event.getWindow().getGuiScaledHeight());
                    } catch (Exception fallbackError) {
                        // 如果连错误信息都无法渲染，则静默失败
                        System.err.println("Failed to render error info: " + fallbackError.getMessage());
                    }
                }
            }
        } catch (Exception generalError) {
            System.err.println("Unexpected error in HUD rendering: " + generalError.getMessage());
            generalError.printStackTrace();
        }
    }

    /**
     * 渲染实体信息到屏幕上
     *
     * @param guiGraphics  渲染图形上下文
     * @param entity       目标实体
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    private static void renderEntityInfo(GuiGraphics guiGraphics, LivingEntity entity,
                                         int screenWidth, int screenHeight) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;

            if (font == null) {
                System.err.println("Font is null, cannot render entity info");
                return;
            }

            // 获取实体信息
            String entityName;
            String permissionStatus;
            boolean isAllowed;

            try {
                entityName = entity != null && entity.getDisplayName() != null ?
                        entity.getDisplayName().getString() : "未知实体";
                permissionStatus = EntityDetectionManager.getEntityPermissionStatus(entity);
                if (permissionStatus == null) {
                    permissionStatus = "状态未知";
                }
                isAllowed = permissionStatus.equals("允许拍摄");
            } catch (Exception infoError) {
                System.err.println("Error getting entity info: " + infoError.getMessage());
                entityName = "信息获取失败";
                permissionStatus = "错误";
                isAllowed = false;
            }

            // 计算准星中心位置
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            // 计算HUD显示位置
            int hudX = centerX + HUD_OFFSET_X;
            int hudY = centerY + HUD_OFFSET_Y;

            // 计算文字尺寸
            int nameWidth = font.width(entityName);
            int statusWidth = font.width(permissionStatus);
            int maxWidth = Math.max(nameWidth, statusWidth);
            int totalWidth = maxWidth + PADDING * 2;
            int totalHeight = font.lineHeight * 2 + PADDING * 3; // 两行文字 + 间距

            // 确保HUD不会超出屏幕边界
            hudX = Math.max(0, Math.min(hudX, screenWidth - totalWidth));
            hudY = Math.max(0, Math.min(hudY, screenHeight - totalHeight));

            // 渲染背景
            try {
                guiGraphics.fill(hudX, hudY, hudX + totalWidth, hudY + totalHeight, COLOR_BACKGROUND);
            } catch (Exception bgError) {
                System.err.println("Error rendering background: " + bgError.getMessage());
            }

            // 渲染边框
            int borderColor = isAllowed ? COLOR_ALLOWED : COLOR_FORBIDDEN;
            try {
                renderBorder(guiGraphics, hudX, hudY, totalWidth, totalHeight, borderColor);
            } catch (Exception borderError) {
                System.err.println("Error rendering border: " + borderError.getMessage());
            }

            // 渲染实体名称
            int textX = hudX + PADDING;
            int textY = hudY + PADDING;

            try {
                // 绘制文字阴影
                guiGraphics.drawString(font, entityName, textX + 1, textY + 1, COLOR_TEXT_SHADOW, false);
                // 绘制实体名称（白色）
                guiGraphics.drawString(font, entityName, textX, textY, 0xFFFFFF, false);
            } catch (Exception nameError) {
                System.err.println("Error rendering entity name: " + nameError.getMessage());
            }

            // 渲染权限状态
            textY += font.lineHeight + PADDING;

            try {
                // 绘制文字阴影
                guiGraphics.drawString(font, permissionStatus, textX + 1, textY + 1, COLOR_TEXT_SHADOW, false);
                // 绘制权限状态（根据权限使用不同颜色）
                guiGraphics.drawString(font, permissionStatus, textX, textY, borderColor, false);
            } catch (Exception statusError) {
                System.err.println("Error rendering permission status: " + statusError.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error in renderEntityInfo: " + e.getMessage());
            e.printStackTrace();
            throw e; // 重新抛出异常以便上层处理
        }
    }

    /**
     * 渲染边框
     *
     * @param guiGraphics 渲染图形上下文
     * @param x           起始X坐标
     * @param y           起始Y坐标
     * @param width       宽度
     * @param height      高度
     * @param color       边框颜色
     */
    private static void renderBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        // 上边框
        guiGraphics.fill(x, y, x + width, y + BORDER_WIDTH, color);
        // 下边框
        guiGraphics.fill(x, y + height - BORDER_WIDTH, x + width, y + height, color);
        // 左边框
        guiGraphics.fill(x, y, x + BORDER_WIDTH, y + height, color);
        // 右边框
        guiGraphics.fill(x + width - BORDER_WIDTH, y, x + width, y + height, color);
    }

    /**
     * 渲染错误信息
     *
     * @param guiGraphics  渲染图形上下文
     * @param errorMessage 错误消息
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    private static void renderErrorInfo(GuiGraphics guiGraphics, String errorMessage,
                                        int screenWidth, int screenHeight) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;

            if (font == null) {
                return;
            }

            // 计算准星中心位置
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            // 计算HUD显示位置
            int hudX = centerX + HUD_OFFSET_X;
            int hudY = centerY + HUD_OFFSET_Y;

            // 计算文字尺寸
            int messageWidth = font.width(errorMessage);
            int totalWidth = messageWidth + PADDING * 2;
            int totalHeight = font.lineHeight + PADDING * 2;

            // 确保HUD不会超出屏幕边界
            hudX = Math.max(0, Math.min(hudX, screenWidth - totalWidth));
            hudY = Math.max(0, Math.min(hudY, screenHeight - totalHeight));

            // 渲染背景（使用更深的颜色表示错误）
            guiGraphics.fill(hudX, hudY, hudX + totalWidth, hudY + totalHeight, 0x80000000);

            // 渲染红色边框表示错误
            renderBorder(guiGraphics, hudX, hudY, totalWidth, totalHeight, COLOR_FORBIDDEN);

            // 渲染错误消息
            int textX = hudX + PADDING;
            int textY = hudY + PADDING;

            // 绘制文字阴影
            guiGraphics.drawString(font, errorMessage, textX + 1, textY + 1, COLOR_TEXT_SHADOW, false);
            // 绘制错误消息（红色）
            guiGraphics.drawString(font, errorMessage, textX, textY, COLOR_FORBIDDEN, false);
        } catch (Exception e) {
            // 如果连错误信息都无法渲染，则静默失败
            System.err.println("Failed to render error info: " + e.getMessage());
        }
    }

    /**
     * 渲染准星增强效果（可选功能）
     * 当检测到可拍摄实体时，改变准星颜色
     *
     * @param guiGraphics       渲染图形上下文
     * @param screenWidth       屏幕宽度
     * @param screenHeight      屏幕高度
     * @param isTargetingEntity 是否正在瞄准实体
     * @param isAllowed         是否允许拍摄
     */
    @SuppressWarnings("unused")
    private static void renderCrosshairEnhancement(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                                   boolean isTargetingEntity, boolean isAllowed) {
        if (!isTargetingEntity) {
            return;
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int color = isAllowed ? COLOR_ALLOWED : COLOR_FORBIDDEN;

        // 在准星周围绘制小圆点指示器
        int radius = 8;
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            int dotX = centerX + (int) (Math.cos(angle) * radius);
            int dotY = centerY + (int) (Math.sin(angle) * radius);

            // 绘制2x2像素的小圆点
            guiGraphics.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, color);
        }
    }
}