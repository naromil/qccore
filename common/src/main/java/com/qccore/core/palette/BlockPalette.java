package com.qccore.core.palette;

/**
 * The nine components a QC Unit is assembled from, in one place.
 *
 * <p>One palette instance belongs to one session or one server build request; it is deliberately
 * not a singleton, so a build never inherits block configuration from an unrelated editor session.
 */
public final class BlockPalette {

    private final FrameworkBlock frameworkBlock = new FrameworkBlock();
    private final RowBlock rowBlock = new RowBlock();
    private final ColumnBlock columnBlock = new ColumnBlock();
    private final WallBlock wallBlock = new WallBlock();
    private final FloorBlock floorBlock = new FloorBlock();

    private final InnerWall innerWall = new InnerWall();
    private final Gate gate = new Gate();
    private final OuterWall outerWall = new OuterWall();
    private final InnerColumn innerColumn = new InnerColumn();

    public BlockPalette() {
    }

    public FrameworkBlock frameworkBlock() {
        return frameworkBlock;
    }

    public RowBlock rowBlock() {
        return rowBlock;
    }

    public ColumnBlock columnBlock() {
        return columnBlock;
    }

    public WallBlock wallBlock() {
        return wallBlock;
    }

    public FloorBlock floorBlock() {
        return floorBlock;
    }

    public InnerWall innerWall() {
        return innerWall;
    }

    public Gate gate() {
        return gate;
    }

    public OuterWall outerWall() {
        return outerWall;
    }

    public InnerColumn innerColumn() {
        return innerColumn;
    }

    /** Whether the palette can export at all: without a framework id nothing can be built. */
    public boolean isConfigured() {
        return frameworkBlock.isConfigured();
    }

    /** Applies the built-in defaults: balanced between deepslate and spruce. */
    public void applyDefaultConfig() {
        frameworkBlock.applyDefaultConfig();
        rowBlock.applyDefaultConfig();
        columnBlock.applyDefaultConfig();
        floorBlock.applyDefaultConfig();
        wallBlock.applyDefaultConfig();

        innerWall.applyDefaultConfig();
        gate.applyDefaultConfig();
        outerWall.applyDefaultConfig();
        innerColumn.applyDefaultConfig();
    }
}
