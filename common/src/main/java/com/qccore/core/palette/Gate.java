package com.qccore.core.palette;

import com.qccore.core.UnitImportContext;
import com.qccore.core.UnitSide;

/**
 * The gate: a 7x7x1 or 7x7x3 inner wall that leaves a passage open, placed on borders whose shared
 * wall flags are set together with the matching gate flags. Unlike the inner wall it never
 * overwrites blocks that are already there.
 */
public class Gate extends InnerStructure {

    public Gate() {
        super();
    }

    @Override
    public void applyDefaultConfig() {
        setStructure(Defaults.simple(Defaults.state("minecraft:deepslate_bricks"), new byte[][][]{
                {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, 0, -1}, {-1, 0, -1}, {0, 0, 0}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, 0, -1}, {-1, 0, -1}, {0, 0, 0}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, 0, -1}, {-1, 0, -1}, {0, 0, 0}},
                {{-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}}
        }), "[Generated Default: Deepslate Bricks]");
    }

    @Override
    public boolean isValidSize(int x, int y, int z) {
        return x == 7 && y == 7 && (z == 1 || z == 3);
    }

    @Override
    protected boolean acceptsGateSides() {
        return true;
    }

    @Override
    protected boolean preserveOnPlace() {
        return true;
    }

    @Override
    public void onDetectedSide(UnitImportContext ctx, int absX, int absY, int absZ, UnitSide side) {
        if (ctx.gateExtracted()) {
            return;
        }
        extractSide(ctx, absX, absY, absZ, side);
        if (!isEmpty()) {
            ctx.markGateExtracted();
        }
    }
}
