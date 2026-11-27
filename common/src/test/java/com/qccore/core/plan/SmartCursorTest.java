package com.qccore.core.plan;

import com.qccore.core.UnitSide;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The smart cursor's rules (T29, review #52 picks 1, 3, 4). */
class SmartCursorTest {

    private static final class Grid implements SmartCursor.Around {
        final Set<Cell> units = new HashSet<>();
        final Set<Cell> roads = new HashSet<>();
        final Set<Cell> fronts = new HashSet<>();

        @Override
        public boolean unit(Cell cell) {
            return units.contains(cell);
        }

        @Override
        public boolean road(Cell cell) {
            return roads.contains(cell);
        }

        @Override
        public boolean doorFront(Cell cell) {
            return fronts.contains(cell);
        }
    }

    private static final Cell AIMED = new Cell(0, 1, 0);

    @Test
    void eastAndSouthWallsOnTheSharedPlaneBelongToTheUnitBehindThem() {
        // unit (0,0) spans blocks 0..8; its east wall stands at x = 8, which floorDiv puts in cell 1
        assertEquals(new Cell(0, 1, 0), SmartCursor.aimedUnit(8, 3, 1, UnitSide.EAST));
        assertEquals(new Cell(0, 1, 0), SmartCursor.aimedUnit(3, 8, 1, UnitSide.SOUTH));
        // its west and north walls (x = 0, z = 0) and blocks inside it are its own
        assertEquals(new Cell(0, 1, 0), SmartCursor.aimedUnit(0, 3, 1, UnitSide.WEST));
        assertEquals(new Cell(0, 1, 0), SmartCursor.aimedUnit(3, 0, 1, UnitSide.NORTH));
        assertEquals(new Cell(0, 1, 0), SmartCursor.aimedUnit(5, 3, 1, UnitSide.EAST));
        // negative cells: unit (-1,-1) spans -8..0, its east wall is x = 0
        assertEquals(new Cell(-1, 1, -1), SmartCursor.aimedUnit(0, -3, 1, UnitSide.EAST));
        assertEquals(new Cell(-1, 1, -1), SmartCursor.aimedUnit(-3, -8, 1, UnitSide.NORTH));
    }

    @Test
    void theMiddleOfAFaceTakesTheCellAcrossIt() {
        Grid grid = new Grid();
        grid.units.add(AIMED);
        grid.units.add(new Cell(0, 1, 1));
        assertEquals(new Cell(1, 1, 0), SmartCursor.beside(AIMED, UnitSide.EAST, 4.0, grid));
    }

    @Test
    void nearACornerTheCellThatFillsAGapWins() {
        // a one-cell gap in a row: aimed (0,0), another unit at (2,0). Aiming at the south face near its east end:
        // straight (0,1) touches nothing else, round the corner (1,0) closes the gap to (2,0)
        Grid grid = new Grid();
        grid.units.add(AIMED);
        grid.units.add(new Cell(2, 1, 0));
        assertEquals(new Cell(1, 1, 0), SmartCursor.beside(AIMED, UnitSide.SOUTH, 7.5, grid));
        assertEquals(new Cell(0, 1, 1), SmartCursor.beside(AIMED, UnitSide.SOUTH, 0.5, grid), "the other end has no reason to move");
    }

    @Test
    void aRoadFrontageCountsButTiesKeepTheStraightCell() {
        Grid grid = new Grid();
        grid.units.add(AIMED);
        grid.roads.add(new Cell(-1, 1, -1));
        // aiming at the north face near its west end: round the corner (-1,0) fronts the road, straight (0,-1) does too
        assertEquals(new Cell(0, 1, -1), SmartCursor.beside(AIMED, UnitSide.NORTH, 1.0, grid));
        grid.roads.clear();
        grid.roads.add(new Cell(-2, 1, 0));
        assertEquals(new Cell(-1, 1, 0), SmartCursor.beside(AIMED, UnitSide.NORTH, 1.0, grid), "only the corner cell fronts the road");
    }

    @Test
    void aDoorFrontIsNeverPickedOverAFreeCell() {
        Grid grid = new Grid();
        grid.units.add(AIMED);
        grid.fronts.add(new Cell(1, 1, 0));
        assertEquals(new Cell(0, 1, 1), SmartCursor.beside(AIMED, UnitSide.EAST, 7.0, grid), "straight is a door front");
        grid.units.add(new Cell(1, 1, 1));
        grid.fronts.clear();
        grid.fronts.add(new Cell(1, 1, 0));
        grid.fronts.add(new Cell(0, 1, 1));
        assertEquals(new Cell(1, 1, 0), SmartCursor.beside(AIMED, UnitSide.EAST, 7.0, grid), "both fronts: keep straight, the tool says why");
        grid.fronts.clear();
        grid.fronts.add(new Cell(0, 1, 1));
        assertEquals(new Cell(1, 1, 0), SmartCursor.beside(AIMED, UnitSide.EAST, 7.0, grid), "never move onto a door front");
    }

    @Test
    void neverMovesOntoAUnitOrARoad() {
        Grid grid = new Grid();
        grid.units.add(AIMED);
        grid.units.add(new Cell(2, 1, 1));
        grid.roads.add(new Cell(0, 1, 1));
        assertEquals(new Cell(1, 1, 0), SmartCursor.beside(AIMED, UnitSide.EAST, 7.0, grid));
    }

    @Test
    void theGroundTakesTheLayerOfTheNearestUnit() {
        Grid grid = new Grid();
        assertEquals(2, SmartCursor.groundLayer(new Cell(0, 2, 0), grid), "nothing near: the floor-plane rule stands");
        grid.units.add(new Cell(1, 1, 0));
        assertEquals(1, SmartCursor.groundLayer(new Cell(0, 2, 0), grid), "a neighbour one layer down");
        grid.units.add(new Cell(-2, 2, 0));
        assertEquals(1, SmartCursor.groundLayer(new Cell(0, 2, 0), grid), "the nearer one wins");
        grid.units.add(new Cell(0, 2, 1));
        assertEquals(2, SmartCursor.groundLayer(new Cell(0, 2, 0), grid), "on a tie the rule's own layer wins");
        Grid far = new Grid();
        far.units.add(new Cell(0, 4, 1));
        assertEquals(2, SmartCursor.groundLayer(new Cell(0, 2, 0), far), "more than one layer away: ignored");
        Grid distant = new Grid();
        distant.units.add(new Cell(3, 1, 0));
        assertEquals(2, SmartCursor.groundLayer(new Cell(0, 2, 0), distant), "three cells away: ignored");
    }
}
