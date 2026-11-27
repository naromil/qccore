package com.qccore.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The baked ghost mesh (T27): a block model's quads, written the way {@code ModelBlockRenderer}
 * writes them (one call per vertex with every attribute, overlay included), go through the ghost's
 * alpha tint into a BLOCK-format buffer and come out as whole, sortable quads.
 */
class QcBlockPreviewMeshTest {

    @Test
    void ghostQuadsFillTheBlockFormatAndSort() {
        BufferBuilder builder = new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        QcBlockPreview.AlphaConsumer ghost = new QcBlockPreview.AlphaConsumer(builder, 110, 1F, 0.25F, 0.25F);
        for (int quad = 0; quad < 3; quad++) {
            for (int v = 0; v < 4; v++) {
                ghost.vertex(quad + (v & 1), v >> 1, 0, 1F, 1F, 1F, 1F, 0F, 0F, 0, 0xF000F0, 0F, 0F, 1F);
            }
        }
        builder.setQuadSorting(VertexSorting.byDistance(0F, 0F, 10F));
        BufferBuilder.RenderedBuffer baked = builder.endOrDiscardIfEmpty();
        assertNotNull(baked);
        assertEquals(12, baked.drawState().vertexCount());
        assertEquals(DefaultVertexFormat.BLOCK.getVertexSize() * 12, baked.vertexBuffer().remaining());
        baked.release();
    }
}
