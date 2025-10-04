package yiseyo.sculpture.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class StatueBlock extends Block implements EntityBlock {
    public StatueBlock() {
        super(BlockBehaviour.Properties.of()
                .noOcclusion()
                .strength(2.0F, 6.0F));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatueBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 交由 BER 渲染
        return RenderShape.INVISIBLE;
    }
}