package yiseyo.sculpture.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.manager.camera.CameraConfigHelper;
import yiseyo.sculpture.core.manager.camera.EntityDetectionManager;
import yiseyo.sculpture.core.manager.capture.CaptureManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 相机物品类 - 用于拍摄生物并生成雕像
 * 支持实体白名单/黑名单检查和检测范围限制
 */
public final class CameraItem extends Item {

    // 拍摄冷却时间（毫秒）
    private static final long CAPTURE_COOLDOWN_MS = 2000; // 2秒冷却

    // 玩家冷却时间记录
    private static final Map<UUID, Long> playerCooldowns = new HashMap<>();

    public CameraItem(Properties props) {
        super(props);
    }

    /**
     * 执行实体拍摄的核心逻辑
     * 从interactLivingEntity方法中提取出来，供use方法调用
     *
     * @param stack     相机物品堆
     * @param player    玩家
     * @param target    目标实体
     * @param hand      使用的手
     * @param startTime 开始时间（用于性能监控）
     * @return 交互结果
     */
    private InteractionResult performEntityCapture(ItemStack stack, Player player, LivingEntity target, InteractionHand hand, long startTime) {
        Sculpture.LOGGER.debug("[Camera] Player {} attempting to capture entity {} at {}",
                player.getName().getString(), target.getName().getString(), target.blockPosition());

        try {
            if (!(player.level() instanceof ServerLevel level)) return InteractionResult.FAIL;

            // 边界情况检查：实体为空
            if (target == null) {
                player.sendSystemMessage(Component.literal("§c错误：目标实体不存在"));
                Sculpture.LOGGER.warn("[Camera] Player {} attempted to capture null entity", player.getName().getString());
                return InteractionResult.FAIL;
            }

            // 边界情况检查：实体已死亡
            if (!target.isAlive()) {
                Sculpture.LOGGER.debug("[Camera] Entity {} is not alive, capture failed", target.getName().getString());
                player.sendSystemMessage(Component.literal("§c无法拍摄已死亡的实体"));
                return InteractionResult.FAIL;
            }

            Sculpture.LOGGER.debug("[Camera] Basic validation passed for entity {}", target.getName().getString());

            // 检查配置系统是否正常工作
            long configCheckStart = System.currentTimeMillis();
            try {
                // 检查实体是否允许拍摄
                if (!CameraConfigHelper.isEntityAllowed(target)) {
                    Sculpture.LOGGER.debug("[Camera] Entity {} not allowed by configuration", target.getName().getString());
                    player.sendSystemMessage(Component.literal("§c该实体不允许拍摄"));
                    return InteractionResult.FAIL;
                }

                // 检查距离是否在允许范围内
                double distance = player.distanceTo(target);
                double maxRange = CameraConfigHelper.getDetectionRange();
                Sculpture.LOGGER.debug("[Camera] Distance check: {:.2f}m (max: {:.2f}m)", distance, maxRange);
                if (distance > maxRange) {
                    player.sendSystemMessage(Component.literal(String.format("§c距离过远 (%.1fm > %.1fm)", distance, maxRange)));
                    return InteractionResult.FAIL;
                }

                long configCheckTime = System.currentTimeMillis() - configCheckStart;
                Sculpture.LOGGER.debug("[Camera] Configuration check completed in {}ms", configCheckTime);
            } catch (Exception configError) {
                player.sendSystemMessage(Component.literal("§c配置系统错误，请检查配置文件"));
                Sculpture.LOGGER.error("[Camera] Configuration error while checking entity permissions", configError);
                return InteractionResult.FAIL;
            }

            // 智能查找合适的雕像放置位置
            long positionSearchStart = System.currentTimeMillis();
            BlockPos pos = findOptimalStatuePosition(level, target, player);
            long positionSearchTime = System.currentTimeMillis() - positionSearchStart;

            if (pos == null) {
                Sculpture.LOGGER.debug("[Camera] No suitable position found for statue placement (search took {}ms)", positionSearchTime);
                player.sendSystemMessage(Component.literal("§c附近没有合适的位置放置雕像"));
                return InteractionResult.FAIL;
            }

            Sculpture.LOGGER.debug("[Camera] Found statue position {} in {}ms", pos, positionSearchTime);


            // 网络通信可能失败，需要处理异常
            long networkStart = System.currentTimeMillis();
            try {
                CaptureManager.pendingCapturePacket(player, pos, target);
                long networkTime = System.currentTimeMillis() - networkStart;

                player.sendSystemMessage(Component.literal("§a成功拍摄 ").append(target.getName()).append(" §a并生成雕像"));

                long totalTime = System.currentTimeMillis() - startTime;
                Sculpture.LOGGER.info("[Camera] Player {} successfully captured entity {} at {} (total time: {}ms, network: {}ms)",
                        player.getName().getString(), target.getName().getString(), pos, totalTime, networkTime);

                // 播放拍摄音效
                long soundStart = System.currentTimeMillis();
                try {
                    // 使用经验球收集音效模拟相机快门声
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                            0.6f, 1.8f);
                    // 添加第二个音效增强效果
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
                            0.4f, 2.0f);
                    long soundTime = System.currentTimeMillis() - soundStart;
                    Sculpture.LOGGER.debug("[Camera] Sound effects played in {}ms", soundTime);
                } catch (Exception soundError) {
                    // 音效播放失败不影响主要功能
                    long soundTime = System.currentTimeMillis() - soundStart;
                    Sculpture.LOGGER.debug("[Camera] Failed to play camera sound effect (after {}ms)", soundTime, soundError);
                }

                // 生成拍摄粒子特效
                long particleStart = System.currentTimeMillis();
                try {
                    if (level instanceof ServerLevel) {
                        ServerLevel serverLevel = level;
                        // 在目标实体周围生成闪光粒子
                        double entityX = target.getX();
                        double entityY = target.getY() + target.getBbHeight() / 2;
                        double entityZ = target.getZ();

                        // 生成环形闪光效果
                        for (int i = 0; i < 12; i++) {
                            double angle = (i / 12.0) * 2 * Math.PI;
                            double offsetX = Math.cos(angle) * 1.5;
                            double offsetZ = Math.sin(angle) * 1.5;

                            serverLevel.sendParticles(ParticleTypes.FLASH,
                                    entityX + offsetX, entityY, entityZ + offsetZ,
                                    1, 0, 0, 0, 0);
                        }

                        // 在雕像位置生成成功粒子
                        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                8, 0.3, 0.3, 0.3, 0.1);

                        long particleTime = System.currentTimeMillis() - particleStart;
                        Sculpture.LOGGER.debug("[Camera] Particle effects spawned in {}ms (20 particles total)", particleTime);
                    }
                } catch (Exception particleError) {
                    // 粒子特效失败不影响主要功能
                    long particleTime = System.currentTimeMillis() - particleStart;
                    Sculpture.LOGGER.debug("[Camera] Failed to spawn camera particles (after {}ms)", particleTime, particleError);
                }

            } catch (Exception networkError) {
                long networkTime = System.currentTimeMillis() - networkStart;
                player.sendSystemMessage(Component.literal("§e雕像已生成，但网络同步可能失败"));
                Sculpture.LOGGER.error("[Camera] Network error while processing capture for player {} (failed after {}ms)",
                        player.getName().getString(), networkTime, networkError);
                // 即使网络失败，雕像仍然生成，返回成功
            }

            // 耗损物品
            long durabilityStart = System.currentTimeMillis();
            try {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                long durabilityTime = System.currentTimeMillis() - durabilityStart;
                Sculpture.LOGGER.debug("[Camera] Item durability updated in {}ms", durabilityTime);
            } catch (Exception durabilityError) {
                long durabilityTime = System.currentTimeMillis() - durabilityStart;
                Sculpture.LOGGER.warn("[Camera] Error applying durability damage to camera item (after {}ms)", durabilityTime, durabilityError);
                // 耐久度错误不影响主要功能
            }

            // 设置冷却时间
            setCooldown(player);

            long totalTime = System.currentTimeMillis() - startTime;
            Sculpture.LOGGER.info("[Camera] Capture process completed successfully in {}ms for player {} -> entity {}",
                    totalTime, player.getName().getString(), target.getName().getString());

            return InteractionResult.CONSUME;

        } catch (Exception generalError) {
            long totalTime = System.currentTimeMillis() - startTime;
            player.sendSystemMessage(Component.literal("§c拍摄过程中发生未知错误"));
            Sculpture.LOGGER.error("[Camera] Unexpected error during entity capture by player {} (failed after {}ms)",
                    player.getName().getString(), totalTime, generalError);
            return InteractionResult.FAIL;
        }
    }

    /**
     * 检查玩家是否在冷却时间内
     */
    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUUID();
        Long lastUseTime = playerCooldowns.get(playerId);
        if (lastUseTime == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUseTime) < CAPTURE_COOLDOWN_MS;
    }

    /**
     * 设置玩家冷却时间
     */
    private void setCooldown(Player player) {
        playerCooldowns.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    private double getRemainingCooldown(Player player) {
        UUID playerId = player.getUUID();
        Long lastUseTime = playerCooldowns.get(playerId);
        if (lastUseTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastUseTime;
        if (elapsed >= CAPTURE_COOLDOWN_MS) {
            return 0;
        }

        return (CAPTURE_COOLDOWN_MS - elapsed) / 1000.0;
    }

    /**
     * 处理右键点击空气或方块的情况
     * 现在包含完整的实体检测和拍摄逻辑，以绕过村民等实体的交易界面
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long startTime = System.currentTimeMillis();

        // 客户端不处理逻辑
        if (level.isClientSide) {
            Sculpture.LOGGER.debug("[Camera] Client-side use() called for player {}", player.getName().getString());
            return InteractionResultHolder.success(stack);
        }

        Sculpture.LOGGER.debug("[Camera] Server-side use() called for player {} at position {}",
                player.getName().getString(), player.blockPosition());

        // 检查冷却时间
        if (isOnCooldown(player)) {
            double remainingTime = getRemainingCooldown(player);
            Sculpture.LOGGER.debug("[Camera] Player {} on cooldown in use(), remaining: {:.1f}s",
                    player.getName().getString(), remainingTime);
            player.sendSystemMessage(Component.literal(String.format("§c相机冷却中，还需等待 %.1f 秒", remainingTime)));
            return InteractionResultHolder.fail(stack);
        }

        // 尝试检测并拍摄玩家瞄准的实体
        try {
            // 使用EntityDetectionManager进行精确的实体检测
            LivingEntity target = EntityDetectionManager.getTargetedEntity(player);

            if (target == null) {
                Sculpture.LOGGER.debug("[Camera] No target entity found, showing usage hint");
                player.sendSystemMessage(Component.literal("§e请将准星对准要拍摄的生物，然后右键点击"));
                return InteractionResultHolder.success(stack);
            }

            Sculpture.LOGGER.debug("[Camera] Target entity found: {} at distance {:.2f}",
                    target.getName().getString(), player.distanceTo(target));

            // 执行拍摄逻辑（从interactLivingEntity方法移植过来）
            InteractionResult captureResult = performEntityCapture(stack, player, target, hand, startTime);

            if (captureResult == InteractionResult.CONSUME) {
                return InteractionResultHolder.consume(stack);
            } else if (captureResult == InteractionResult.FAIL) {
                return InteractionResultHolder.fail(stack);
            } else {
                return InteractionResultHolder.success(stack);
            }

        } catch (Exception e) {
            Sculpture.LOGGER.error("[Camera] Error during entity detection and capture in use() method", e);
            player.sendSystemMessage(Component.literal("§c拍摄过程中发生错误"));
            return InteractionResultHolder.fail(stack);
        }
    }

    /**
     * 智能查找合适的雕像放置位置
     * 优先级：实体脚下 > 实体周围 > 玩家附近
     */
    private BlockPos findOptimalStatuePosition(ServerLevel level, LivingEntity target, Player player) {
        BlockPos targetPos = target.blockPosition();
        Sculpture.LOGGER.debug("[Camera] Searching for statue position near target at {}", targetPos);

        // 1. 首先尝试实体脚下位置
        if (isValidStatuePosition(level, targetPos)) {
            Sculpture.LOGGER.debug("[Camera] Using target position {} for statue", targetPos);
            return targetPos;
        }

        // 2. 尝试实体周围的位置（3x3范围）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // 跳过中心位置（已检查）

                BlockPos candidatePos = targetPos.offset(dx, 0, dz);
                if (isValidStatuePosition(level, candidatePos)) {
                    Sculpture.LOGGER.debug("[Camera] Using nearby position {} for statue", candidatePos);
                    return candidatePos;
                }

                // 也尝试上下一格的位置
                BlockPos upperPos = candidatePos.above();
                BlockPos lowerPos = candidatePos.below();

                if (isValidStatuePosition(level, upperPos)) {
                    Sculpture.LOGGER.debug("[Camera] Using upper position {} for statue", upperPos);
                    return upperPos;
                }
                if (isValidStatuePosition(level, lowerPos)) {
                    Sculpture.LOGGER.debug("[Camera] Using lower position {} for statue", lowerPos);
                    return lowerPos;
                }
            }
        }

        // 3. 最后尝试玩家附近的位置（5x5范围）
        BlockPos playerPos = player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos candidatePos = playerPos.offset(dx, 0, dz);

                // 确保不会太远离目标实体
                if (candidatePos.distSqr(targetPos) > 25) { // 5格距离限制
                    continue;
                }

                if (isValidStatuePosition(level, candidatePos)) {
                    Sculpture.LOGGER.debug("[Camera] Using player-nearby position {} for statue", candidatePos);
                    return candidatePos;
                }
            }
        }

        Sculpture.LOGGER.debug("[Camera] No suitable position found for statue placement");
        return null; // 没有找到合适的位置
    }

    /**
     * 检查位置是否适合放置雕像
     */
    private boolean isValidStatuePosition(ServerLevel level, BlockPos pos) {
        try {
            BlockState currentState = level.getBlockState(pos);

            // 检查当前方块是否可以被替换
            if (!currentState.canBeReplaced()) {
                return false;
            }

            // 检查下方是否有支撑方块
            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (belowState.isAir()) {
                return false; // 雕像需要支撑
            }

            // 检查上方是否有足够空间（雕像可能需要2格高度）
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (!aboveState.canBeReplaced()) {
                return false;
            }

            // 检查是否在世界边界内
            return level.isInWorldBounds(pos);

        } catch (Exception e) {
            Sculpture.LOGGER.debug("Error checking statue position validity at {}", pos, e);
            return false;
        }
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return performEntityCapture(stack, player, target, hand, System.currentTimeMillis());
    }
}