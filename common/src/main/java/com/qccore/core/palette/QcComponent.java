package com.qccore.core.palette;

import com.qccore.core.QcStructure;

/**
 * A component identified by an uploaded structure instead of a single block id: the inner wall,
 * the gate, the outer wall and the inner column.
 */
public abstract class QcComponent implements UnitComponent {

    private String path = "";
    private QcStructure structure = null;

    public QcComponent() {
    }

    public QcComponent(String path, QcStructure structure) {
        this.path = path;
        this.structure = structure;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public QcStructure getStructure() {
        return structure;
    }

    public int getSizeX() {
        return structure == null ? 0 : structure.sizeX();
    }

    public int getSizeY() {
        return structure == null ? 0 : structure.sizeY();
    }

    public int getSizeZ() {
        return structure == null ? 0 : structure.sizeZ();
    }

    /** Installs a structure, keeping the current one when {@code structure} is {@code null}. */
    public void setStructure(QcStructure structure, String path) {
        if (structure == null) {
            return;
        }
        this.structure = structure;
        this.path = path;
    }

    /** Installs the structure that was read back out of an opened file, or clears the component. */
    public void setExtracted(QcStructure structure) {
        this.structure = structure;
        this.path = structure != null ? "[Extracted from Opened File]" : "[Not Configured]";
    }

    public boolean isEmpty() {
        return structure == null;
    }
}
