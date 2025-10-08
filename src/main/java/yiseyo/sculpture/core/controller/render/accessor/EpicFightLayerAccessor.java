package yiseyo.sculpture.core.controller.render.accessor;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yiseyo.sculpture.core.controller.render.LayerHeaderAccessor;
import yiseyo.sculpture.utils.RenderTextureUtil;

/**
 * @author TT432
 */
public class EpicFightLayerAccessor implements LayerHeaderAccessor {
    @Override
    public boolean supports(RenderType rt) {
        return rt.toString().contains("ui_texture");
    }

    @Override
    public void encode(RenderType rt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(RenderTextureUtil.textureOf(rt));
        if (rt.toString().contains("ui_texture")) {
            buf.writeByte(0);
        }
    }

    @Override
    public RenderType decode(FriendlyByteBuf buf) {
        ResourceLocation tex = buf.readResourceLocation();
        return switch (buf.readByte()) {
            case 0 -> EpicFightRenderTypes.entityUITexture(tex);
            default -> RenderType.entityCutoutNoCull(tex); // cutout / solid
        };
    }
}
