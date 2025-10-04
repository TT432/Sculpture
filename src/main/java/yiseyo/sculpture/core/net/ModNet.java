package yiseyo.sculpture.core.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import yiseyo.sculpture.Sculpture;
import yiseyo.sculpture.core.net.packet.*;

public final class ModNet {
    public static final String PROTO = "1";
    public static SimpleChannel CHANNEL;

    private static int id = 0;

    private static int next() {
        return id++;
    }

    @SuppressWarnings("removal")
    public static void init() {
        CHANNEL =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(Sculpture.MODID, "net"),
                        () -> PROTO,
                        PROTO::equals,
                        PROTO::equals);

        CHANNEL.registerMessage(
                next(),
                S2CRequestCapture.class,
                S2CRequestCapture::encode,
                S2CRequestCapture::decode,
                S2CRequestCapture::handle);
        CHANNEL.registerMessage(
                next(),
                C2SUploadMesh.class,
                C2SUploadMesh::encode,
                C2SUploadMesh::decode,
                C2SUploadMesh::handle);
        CHANNEL.registerMessage(
                next(), S2CSyncMesh.class, S2CSyncMesh::encode, S2CSyncMesh::decode, S2CSyncMesh::handle);
        CHANNEL.registerMessage(
                next(),
                C2SEntityDetection.class,
                C2SEntityDetection::encode,
                C2SEntityDetection::decode,
                C2SEntityDetection::handle);
        CHANNEL.registerMessage(
                next(),
                S2CEntityInfo.class,
                S2CEntityInfo::encode,
                S2CEntityInfo::decode,
                S2CEntityInfo::handle);
    }
}
