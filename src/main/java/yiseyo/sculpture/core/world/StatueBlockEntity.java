package yiseyo.sculpture.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import yiseyo.sculpture.common.ModBlocks;
import yiseyo.sculpture.core.net.ModNet;
import yiseyo.sculpture.core.net.packet.S2CSyncMesh;

public final class StatueBlockEntity extends BlockEntity {
    private Entity entity;

    private byte[] meshBytes;
    private boolean meshReady = false;

    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STATUE_BE.get(), pos, state);
    }

    public void setEntityData(CompoundTag tag, Pose pose) {
        // todo
//        if (!tag.contains("id", Tag.TAG_STRING)) {
//            tag.putString(
//                    "id",
//                    ForgeRegistries.ENTITY_TYPES
//                            .getKey(EntityType.byString(tag.getString("id")).orElseThrow())
//                            .toString());
//        }
//
//        this.entityNbt = tag;
//        this.pose = p;
//
//        setChanged(); // 存档
//        if (!level.isClientSide)
//            level.sendBlockUpdated(
//                    worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // 重新发包
    }

    public void acceptMesh(byte[] bytes) {
        this.meshBytes = bytes;
        this.meshReady = true;
        setChanged();

        if (!level.isClientSide) {
            // 同步网格数据给观看该区块的玩家
            ModNet.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(
                            () -> level.getChunkAt(worldPosition)),
                    new S2CSyncMesh(worldPosition, bytes));
            // TODO: 持久化雕像数据
        }
    }

    public boolean hasMesh() {
        return meshReady;
    }

    public byte[] meshBytes() {
        return meshBytes;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
//        if (entityNbt != null) tag.put("Entity", entityNbt);
//        tag.putString("Pose", pose.name());
        if (meshReady) tag.putByteArray("Mesh", meshBytes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
//        if (tag.contains("Entity")) entityNbt = tag.getCompound("Entity");
//        if (tag.contains("Pose", Tag.TAG_STRING)) // 8
//            pose = Pose.valueOf(tag.getString("Pose"));
//        else pose = Pose.STANDING; // 兜底
        if (tag.contains("Mesh")) {
            meshBytes = tag.getByteArray("Mesh");
            meshReady = true;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
//        if (entityNbt != null) tag.put("Entity", entityNbt.copy());
//        tag.putInt("Pose", pose.ordinal());
        tag.putByteArray("Mesh", meshBytes);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
//        if (tag.contains("Entity")) this.entityNbt = tag.getCompound("Entity");
//        this.pose = Pose.values()[tag.getInt("Pose")];
        meshBytes = tag.getByteArray("Mesh");
        super.handleUpdateTag(tag);
    }
}
