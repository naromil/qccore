package com.qccore.core;

/**
 * The voxel offset a detected framework shell starts at. It is 0 for every aligned grid, but the
 * detection keeps it because imported structures may be offset within their own coordinates.
 */
public record GridOrigin(int x, int y, int z) {
}
