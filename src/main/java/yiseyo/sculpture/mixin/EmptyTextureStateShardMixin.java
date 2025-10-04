package yiseyo.sculpture.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import yiseyo.sculpture.client.TextureStateShard;

import java.util.Optional;

/**
 * @author TT432
 */
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public abstract class EmptyTextureStateShardMixin implements TextureStateShard {
    @Shadow
    protected abstract Optional<ResourceLocation> cutoutTexture();

    @Override
    public Optional<ResourceLocation> getTextureLocation() {
        return cutoutTexture();
    }
}
