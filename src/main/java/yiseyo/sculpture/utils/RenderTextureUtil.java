package yiseyo.sculpture.utils;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import yiseyo.sculpture.client.TextureStateShard;

import java.util.Locale;

public abstract class RenderTextureUtil {
    public static ResourceLocation textureOf(RenderType rt) {
        try {
            if (rt instanceof RenderType.CompositeRenderType crt) {
                return ((TextureStateShard) crt.state().textureState).getTextureLocation().orElseThrow();
            }

            throw new IllegalStateException("no ResourceLocation");
        } catch (Exception e) {
            String safe = rt.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            return new ResourceLocation("dummy", safe);
        }
    }
}
