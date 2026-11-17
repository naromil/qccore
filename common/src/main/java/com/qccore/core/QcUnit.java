package com.qccore.core;

/**
 * The per-unit flags of a QC Unit: the four wall and the four gate faces.
 */
public class QcUnit {
    private boolean wallN, wallW, wallS, wallE;

    private boolean gateN, gateW, gateS, gateE;

    public QcUnit() {
        wallN = wallW = wallS = wallE = false;
        gateN = gateW = gateS = gateE = false;
    }

    public QcUnit(QcUnit unit) {
        wallN = unit.wallN;
        wallW = unit.wallW;
        wallS = unit.wallS;
        wallE = unit.wallE;

        gateN = unit.gateN;
        gateW = unit.gateW;
        gateS = unit.gateS;
        gateE = unit.gateE;
    }

    public void setWallN(boolean val) { wallN = val; }
    public void setWallS(boolean val) { wallS = val; }
    public void setWallW(boolean val) { wallW = val; }
    public void setWallE(boolean val) { wallE = val; }

    public void setGateN(boolean val) { gateN = val; }
    public void setGateS(boolean val) { gateS = val; }
    public void setGateW(boolean val) { gateW = val; }
    public void setGateE(boolean val) { gateE = val; }

    public boolean hasWallN() { return wallN; }
    public boolean hasWallS() { return wallS; }
    public boolean hasWallW() { return wallW; }
    public boolean hasWallE() { return wallE; }

    public boolean isGateN() { return gateN; }
    public boolean isGateS() { return gateS; }
    public boolean isGateW() { return gateW; }
    public boolean isGateE() { return gateE; }

    public boolean hasAnyN() { return wallN || gateN; }
    public boolean hasAnyS() { return wallS || gateS; }
    public boolean hasAnyW() { return wallW || gateW; }
    public boolean hasAnyE() { return wallE || gateE; }
}
