package yiseyo.sculpture;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = Sculpture.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // 相机工具配置
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_ENTITIES;
    public static final ForgeConfigSpec.DoubleValue DETECTION_RANGE;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD_INFO;

    static {
        BUILDER.comment("Camera Tool Configuration").push("camera");

        ALLOWED_ENTITIES = BUILDER
                .comment("List of entities that are allowed to be photographed by the camera tool.",
                        "Use entity type IDs (e.g., 'minecraft:cow', 'minecraft:villager').",
                        "Empty list means all entities are allowed (unless blocked).")
                .defineListAllowEmpty("allowedEntities", List.of(), obj -> obj instanceof String);

        BLOCKED_ENTITIES = BUILDER
                .comment("List of entities that are blocked from being photographed by the camera tool.",
                        "Use entity type IDs (e.g., 'minecraft:player', 'minecraft:ender_dragon').",
                        "This list takes priority over the allowed list.")
                .defineListAllowEmpty("blockedEntities",
                        List.of(),
                        obj -> obj instanceof String);

        DETECTION_RANGE = BUILDER
                .comment("Maximum range for entity detection by the camera tool (in blocks).")
                .defineInRange("detectionRange", 16.0, 1.0, 64.0);

        SHOW_HUD_INFO = BUILDER
                .comment("Whether to show HUD information when holding the camera tool.")
                .define("showHudInfo", true);

        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}
