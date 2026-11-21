package com.qccore.client;

import com.qccore.core.nbt.PaletteCodec;
import com.qccore.core.nbt.PlanCodec;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.plan.QcPlan;
import com.qccore.net.QcPackets.QcPlanC2S;
import com.qccore.platform.QcPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Sends a plan request to the server, gzip-compressed and split into {@link QcPlanC2S} chunks. */
public final class QcPlanTransfer {
	private static int transfer;

	private QcPlanTransfer() {
	}

	/** Asks the server to apply {@code plan} at {@code anchor} with {@code palette}. */
	public static void sendApply(BlockPos anchor, QcPlan plan, BlockPalette palette) {
		CompoundTag request = new CompoundTag();
		request.putString("action", "apply");
		request.putIntArray("anchor", new int[]{anchor.getX(), anchor.getY(), anchor.getZ()});
		request.put("plan", PlanCodec.toNbt(plan));
		request.put("palette", PaletteCodec.toNbt(palette));
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(request, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			QcClientState.say("QC: could not encode the plan: " + e.getMessage());
			return;
		}
		// the server answers a transfer with exactly one result, whether it applies or refuses it
		QcClientState.requests.expect(QcRequests.Kind.APPLY, QcClientState.history.revision());
		int id = ++transfer;
		int total = Math.max(1, (bytes.length + QcPlanC2S.CHUNK - 1) / QcPlanC2S.CHUNK);
		for (int i = 0; i < total; i++) {
			int from = i * QcPlanC2S.CHUNK;
			int to = Math.min(bytes.length, from + QcPlanC2S.CHUNK);
			QcPlatform.sendToServer(new QcPlanC2S(id, i, total, Arrays.copyOfRange(bytes, from, to)));
		}
	}
}
