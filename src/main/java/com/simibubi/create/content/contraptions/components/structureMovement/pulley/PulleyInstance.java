package com.simibubi.create.content.contraptions.components.structureMovement.pulley;

import net.minecraft.client.renderer.Vector3f;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ILightReader;
import net.minecraft.world.LightType;

import java.util.Arrays;
import com.simibubi.create.AllBlockPartials;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.relays.encased.ShaftInstance;
import com.simibubi.create.foundation.render.backend.core.OrientedData;
import com.simibubi.create.foundation.render.backend.instancing.*;
import com.simibubi.create.foundation.render.backend.instancing.util.ConditionalInstance;
import com.simibubi.create.foundation.render.backend.instancing.util.InstanceGroup;
import com.simibubi.create.foundation.render.backend.instancing.util.SelectInstance;
import com.simibubi.create.foundation.render.backend.light.GridAlignedBB;
import com.simibubi.create.foundation.render.backend.light.LightUpdateListener;
import com.simibubi.create.foundation.render.backend.light.LightUpdater;
import com.simibubi.create.foundation.utility.AnimationTickHolder;

public class PulleyInstance extends ShaftInstance implements IDynamicInstance, LightUpdateListener {

	final PulleyTileEntity tile = (PulleyTileEntity) super.tile;
	final OrientedData coil;
	final SelectInstance<OrientedData> magnet;
	final InstanceGroup<OrientedData> rope;
	final ConditionalInstance<OrientedData> halfRope;

	private float offset;
	private final Direction rotatingAbout;
	private final Vector3f rotationAxis;

	private byte[] bLight = new byte[1];
	private byte[] sLight = new byte[1];
	private GridAlignedBB volume;

	public PulleyInstance(InstancedTileRenderer<?> dispatcher, PulleyTileEntity tile) {
		super(dispatcher, tile);

		rotatingAbout = Direction.getFacingFromAxis(Direction.AxisDirection.POSITIVE, axis);
		rotationAxis = rotatingAbout.getUnitVector();
		updateOffset();

		coil = getCoilModel()
				.createInstance()
				.setPosition(getInstancePosition());

		magnet = new SelectInstance<>(this::getMagnetModelIndex);
		magnet.addModel(getMagnetModel())
				.addModel(getHalfMagnetModel());

		rope = new InstanceGroup<>(getRopeModel());
		resizeRope();

		halfRope = new ConditionalInstance<>(getHalfRopeModel(), this::shouldRenderHalfRope);

		beginFrame();
	}

	@Override
	public void beginFrame() {
		updateOffset();

		coil.setRotation(rotationAxis.getDegreesQuaternion(offset * 180));
		magnet.update().get().ifPresent(data ->
				{
					int index = Math.max(0, MathHelper.floor(offset));
					data.setPosition(getInstancePosition())
							.nudge(0, -offset, 0)
							.setBlockLight(bLight[index])
							.setSkyLight(sLight[index]);
				}
		);

		halfRope.update().get().ifPresent(rope -> {
			float f = offset % 1;
			float halfRopeNudge = f > .75f ? f - 1 : f;

			rope.setPosition(getInstancePosition())
					.nudge(0, -halfRopeNudge, 0)
					.setBlockLight(bLight[0])
					.setSkyLight(sLight[0]);
		});

		resizeRope();
		if (isRunning()) {
			int size = rope.size();
			for (int i = 0; i < size; i++) {
				rope.get(i)
						.setPosition(getInstancePosition())
						.nudge(0, -offset + i + 1, 0)
						.setBlockLight(bLight[size - i])
						.setSkyLight(sLight[size - i]);
			}
		} else {
			rope.clear();
		}
	}

	@Override
	public void updateLight() {
		super.updateLight();
		relight(pos, coil);
	}

	@Override
	public void remove() {
		super.remove();
		coil.delete();
		magnet.delete();
		rope.clear();
		halfRope.delete();
	}

	protected InstancedModel<OrientedData> getRopeModel() {
		return getOrientedMaterial().getModel(AllBlocks.ROPE.getDefaultState());
	}

	protected InstancedModel<OrientedData> getMagnetModel() {
		return getOrientedMaterial().getModel(AllBlocks.PULLEY_MAGNET.getDefaultState());
	}

	protected InstancedModel<OrientedData> getHalfMagnetModel() {
		return getOrientedMaterial().getModel(AllBlockPartials.ROPE_HALF_MAGNET, blockState);
	}

	protected InstancedModel<OrientedData> getCoilModel() {
		return AllBlockPartials.ROPE_COIL.getModel(getOrientedMaterial(), blockState, rotatingAbout);
	}

	protected InstancedModel<OrientedData> getHalfRopeModel() {
		return getOrientedMaterial().getModel(AllBlockPartials.ROPE_HALF, blockState);
	}

	protected float getOffset() {
		float partialTicks = AnimationTickHolder.getPartialTicks();
		return PulleyRenderer.getTileOffset(partialTicks, tile);
	}

	protected boolean isRunning() {
		return tile.running || tile.isVirtual();
	}

	protected void resizeRope() {
		int neededRopeCount = getNeededRopeCount();
		rope.resize(neededRopeCount);

		int length = MathHelper.ceil(offset);

		if (volume == null || bLight.length < length + 1) {
			volume = GridAlignedBB.from(pos.down(length), pos);
			volume.fixMinMax();

			bLight = Arrays.copyOf(bLight, length + 1);
			sLight = Arrays.copyOf(sLight, length + 1);

			initLight(world, volume);

			LightUpdater.getInstance().startListening(volume, this);
		}
	}

	private void updateOffset() {
		offset = getOffset();
	}

	private int getNeededRopeCount() {
		return Math.max(0, MathHelper.ceil(offset - 1.25f));
	}

	private boolean shouldRenderHalfRope() {
		float f = offset % 1;
		return offset > .75f && (f < .25f || f > .75f);
	}

	private int getMagnetModelIndex() {
		if (isRunning() || offset == 0) {
			return  offset > .25f ? 0 : 1;
		} else {
			return -1;
		}
	}

	@Override
	public boolean decreaseFramerateWithDistance() {
		return false;
	}

	@Override
	public boolean onLightUpdate(ILightReader world, LightType type, GridAlignedBB changed) {
		changed.intersectAssign(volume);

		initLight(world, changed);

		return false;
	}

	private void initLight(ILightReader world, GridAlignedBB changed) {
		int top = this.pos.getY();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		changed.forEachContained((x, y, z) -> {
			pos.setPos(x, y, z);
			byte block = (byte) world.getLightLevel(LightType.BLOCK, pos);
			byte sky = (byte) world.getLightLevel(LightType.SKY, pos);

			int i = top - y;

			bLight[i] = block;
			sLight[i] = sky;
		});
	}
}