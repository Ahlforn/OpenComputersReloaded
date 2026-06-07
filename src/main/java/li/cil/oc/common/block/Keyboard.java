package li.cil.oc.common.block;

import li.cil.oc.common.tileentity.KeyboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class Keyboard extends OcBlock implements EntityBlock {
    public static final EnumProperty<Direction> PITCH = EnumProperty.create("pitch", Direction.class,
            Direction.NORTH, Direction.UP, Direction.DOWN);
    public static final EnumProperty<Direction> YAW = EnumProperty.create("yaw", Direction.class,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public Keyboard(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PITCH, Direction.NORTH).setValue(YAW, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PITCH, YAW);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyboardBlockEntity(pos, state);
    }
}
