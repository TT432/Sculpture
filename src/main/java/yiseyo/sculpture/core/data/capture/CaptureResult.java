package yiseyo.sculpture.core.data.capture;

import net.minecraft.client.renderer.RenderType;

import java.util.List;
import java.util.Map;

public record CaptureResult(
        Map<RenderType, List<Vertex>> byRenderType
) {
    public CaptureResult(Map<RenderType, List<Vertex>> byRenderType) {
        this.byRenderType = Map.copyOf(byRenderType);
    }
}
