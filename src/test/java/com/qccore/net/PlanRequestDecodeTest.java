package com.qccore.net;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reassembled plan request is inflated under a size budget: a real request reads back intact,
 * a small gzip stream that inflates far past the budget is refused instead of filling the heap.
 */
class PlanRequestDecodeTest {

    @Test
    void ordinaryRequestRoundTrips() throws IOException {
        NbtCompound request = new NbtCompound();
        request.putString("action", "apply");
        request.putIntArray("anchor", new int[] {1, 64, -3});
        request.put("plan", new NbtCompound());

        NbtCompound decoded = QcServerNetworking.decode(gzip(request));

        assertEquals("apply", decoded.getString("action"));
        assertArrayEquals(new int[] {1, 64, -3}, decoded.getIntArray("anchor"));
    }

    @Test
    void inflationBombIsRefused() throws IOException {
        // 64 MiB of zeros compresses to well under a single 30000-byte chunk
        NbtCompound bomb = new NbtCompound();
        bomb.putByteArray("payload", new byte[64 * 1024 * 1024]);
        byte[] compressed = gzip(bomb);

        assertEquals(true, compressed.length < 256 * 30000, "the bomb must fit in a legal transfer");
        assertThrows(RuntimeException.class, () -> QcServerNetworking.decode(compressed));
    }

    private static byte[] gzip(NbtCompound tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }
}
