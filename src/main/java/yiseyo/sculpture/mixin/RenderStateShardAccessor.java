package yiseyo.sculpture.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author TT432
 */
@Mixin(RenderStateShard.class)
public interface RenderStateShardAccessor {
    @Accessor
    String getName();
}
