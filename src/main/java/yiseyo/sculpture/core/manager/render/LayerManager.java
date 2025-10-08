package yiseyo.sculpture.core.manager.render;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.ModList;
import yiseyo.sculpture.core.controller.render.LayerHeaderAccessor;
import yiseyo.sculpture.core.controller.render.accessor.VanillaLayerAccessor;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class LayerManager {
    private static final ConcurrentHashMap<Integer, LayerHeaderAccessor> CODECS = new ConcurrentHashMap<>();

    static {
        register(0, new VanillaLayerAccessor());

        if (ModList.get().isLoaded("epicfight")) {
            try {
                register(1, (LayerHeaderAccessor) Class.forName("yiseyo.sculpture.core.controller.render.accessor.EpicFightLayerAccessor").getConstructor().newInstance());
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                     InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void register(int id, LayerHeaderAccessor codec) {
        CODECS.put(id, codec);
    }

    public static boolean writeHeader(RenderType rt, FriendlyByteBuf buf) {
        int id = find(rt);
        buf.writeByte(id); // 先写出“使用第几个 Accessor，再交给 Accessor 写具体头部
        if (id == -1) return false;
        CODECS.get(id).encode(rt, buf);
        return true;
    }

    @Nullable
    public static RenderType readHeader(FriendlyByteBuf buf) {
        int id = buf.readUnsignedByte();
        if (!CODECS.containsKey(id)) return null;
        return CODECS.get(id).decode(buf);
    }

    private static int find(RenderType rt) {
        for (int i = 0; i < CODECS.size(); i++) if (CODECS.get(i).supports(rt)) return i;
        return -1;
    }
}
