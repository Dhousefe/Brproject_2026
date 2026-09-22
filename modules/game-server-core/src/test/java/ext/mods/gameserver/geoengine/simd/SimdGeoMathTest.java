/*
 * Copyleft © 2024-2026 L2Brproject
 * * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.gameserver.geoengine.simd;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import ext.mods.gameserver.geoengine.geodata.BlockComplex;
import ext.mods.gameserver.geoengine.geodata.BlockFlat;
import ext.mods.gameserver.enums.GeoType;

import static org.junit.jupiter.api.Assertions.*;

public class SimdGeoMathTest
{
	@Test
	public void testBatchDistanceSquared2D()
	{
		final int originX = 1000;
		final int originY = 2000;
		final int count = 25;
		
		final int[] targetX = new int[count];
		final int[] targetY = new int[count];
		final long[] results = new long[count];
		
		for (int i = 0; i < count; i++)
		{
			targetX[i] = 1000 + (i * 37);
			targetY[i] = 2000 - (i * 43);
		}
		
		SimdGeoMath.batchDistanceSquared2D(originX, originY, targetX, targetY, results, count);
		
		for (int i = 0; i < count; i++)
		{
			final long dx = (long) targetX[i] - originX;
			final long dy = (long) targetY[i] - originY;
			final long expected = dx * dx + dy * dy;
			assertEquals(expected, results[i], "Divergência na distância ao quadrado para o elemento " + i);
		}
	}
	
	@Test
	public void testBatchDistance2D()
	{
		final int originX = 500;
		final int originY = 800;
		final int count = 18;
		
		final int[] targetX = new int[count];
		final int[] targetY = new int[count];
		final double[] results = new double[count];
		
		for (int i = 0; i < count; i++)
		{
			targetX[i] = 500 + i * 10;
			targetY[i] = 800 + i * 20;
		}
		
		SimdGeoMath.batchDistance2D(originX, originY, targetX, targetY, results, count);
		
		for (int i = 0; i < count; i++)
		{
			final double dx = (double) targetX[i] - originX;
			final double dy = (double) targetY[i] - originY;
			final double expected = Math.sqrt(dx * dx + dy * dy);
			assertEquals(expected, results[i], 1e-6, "Divergência na distância euclidiana para o elemento " + i);
		}
	}
	
	@Test
	public void testBatchRangeCheck3D()
	{
		final int originX = 0;
		final int originY = 0;
		final int originZ = 0;
		final int maxRadius = 100;
		final int count = 10;
		
		final int[] targetX = new int[count];
		final int[] targetY = new int[count];
		final int[] targetZ = new int[count];
		final boolean[] inRangeMask = new boolean[count];
		
		// In range
		targetX[0] = 50; targetY[0] = 0; targetZ[0] = 0;
		targetX[1] = 0; targetY[1] = 60; targetZ[1] = 0;
		targetX[2] = 0; targetY[2] = 0; targetZ[2] = 90;
		targetX[3] = 50; targetY[3] = 50; targetZ[3] = 50; // sqrt(7500) = 86.6 <= 100
		
		// Out of range
		targetX[4] = 101; targetY[4] = 0; targetZ[4] = 0;
		targetX[5] = 80; targetY[5] = 80; targetZ[5] = 0; // sqrt(12800) = 113.1 > 100
		targetX[6] = 200; targetY[6] = 300; targetZ[6] = 400;
		targetX[7] = 0; targetY[7] = 105; targetZ[7] = 0;
		targetX[8] = 0; targetY[8] = 0; targetZ[8] = 101;
		targetX[9] = 1000; targetY[9] = 1000; targetZ[9] = 1000;
		
		SimdGeoMath.batchRangeCheck3D(originX, originY, originZ, targetX, targetY, targetZ, maxRadius, inRangeMask, count);
		
		assertTrue(inRangeMask[0]);
		assertTrue(inRangeMask[1]);
		assertTrue(inRangeMask[2]);
		assertTrue(inRangeMask[3]);
		
		assertFalse(inRangeMask[4]);
		assertFalse(inRangeMask[5]);
		assertFalse(inRangeMask[6]);
		assertFalse(inRangeMask[7]);
		assertFalse(inRangeMask[8]);
		assertFalse(inRangeMask[9]);
	}
	
	@Test
	public void testCanLeapfrogBlock()
	{
		final short maxBlockZ = 120;
		
		// Ray well above the terrain
		assertTrue(SimdGeoMath.canLeapfrogBlock(maxBlockZ, 250.0, 0.0, 128.0));
		
		// Ray intersecting or too close to the terrain
		assertFalse(SimdGeoMath.canLeapfrogBlock(maxBlockZ, 130.0, -0.5, 128.0));
	}
	
	@Test
	public void testBlockMinMaxElevation()
	{
		// Test BlockFlat
		final ByteBuffer flatBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
		flatBuffer.putShort((short) 500);
		flatBuffer.flip();
		
		final BlockFlat blockFlat = new BlockFlat(flatBuffer, GeoType.L2J);
		assertEquals(500, blockFlat.getMaxZ());
		assertEquals(500, blockFlat.getMinZ());
		
		// Test BlockComplex
		final ByteBuffer complexBuffer = ByteBuffer.allocate(64 * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 64; i++)
		{
			// NSWE in low 4 bits (e.g. 0x0F), height in high bits
			final short height = (short) (-100 + i * 10);
			final short encoded = (short) ((height << 1) | 0x000F);
			complexBuffer.putShort(encoded);
		}
		complexBuffer.flip();
		
		final BlockComplex blockComplex = new BlockComplex(complexBuffer);
		assertTrue(blockComplex.getMaxZ() >= blockComplex.getMinZ(), "MaxZ deve ser maior ou igual a MinZ");
		
		short expectedMin = Short.MAX_VALUE;
		short expectedMax = Short.MIN_VALUE;
		for (int x = 0; x < 8; x++)
		{
			for (int y = 0; y < 8; y++)
			{
				final short h = blockComplex.getHeightNearest(x, y, 0, null);
				if (h < expectedMin) expectedMin = h;
				if (h > expectedMax) expectedMax = h;
			}
		}
		
		assertEquals(expectedMin, blockComplex.getMinZ(), "MinZ deve coincidir com a menor altura das células");
		assertEquals(expectedMax, blockComplex.getMaxZ(), "MaxZ deve coincidir com a maior altura das células");
	}
	
	@Test
	public void testBatchCostH8()
	{
		final int targetX = 500;
		final int targetY = 500;
		final int targetZ = 100;
		final int cellHeight = 16;
		final int heuristicWeight = 12;
		
		final int[] neighborX = new int[] { 500, 501, 499, 500, 501, 499, 502, 498 };
		final int[] neighborY = new int[] { 501, 500, 500, 499, 501, 499, 502, 498 };
		final int[] neighborZ = new int[] { 100, 116, 84,  100, 132, 100, 148, 68 };
		final int[] results = new int[8];
		
		SimdGeoMath.batchCostH8(neighborX, neighborY, neighborZ, targetX, targetY, targetZ, cellHeight, heuristicWeight, results);
		
		for (int i = 0; i < 8; i++)
		{
			final double dx = neighborX[i] - targetX;
			final double dy = neighborY[i] - targetY;
			final double dz = (neighborZ[i] - targetZ) / (double) cellHeight;
			final int expected = (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * heuristicWeight);
			assertEquals(expected, results[i], "Divergência de heurística no vizinho " + i);
		}
	}
	
	@Test
	public void testBatchOctileDistance8()
	{
		final int targetX = 100;
		final int targetY = 100;
		final int orthoCost = 10;
		final int diagCost = 14;
		
		final int[] neighborX = new int[] { 100, 101,  99, 100, 105,  95, 110,  90 };
		final int[] neighborY = new int[] { 101, 100, 100,  99, 105,  95, 105,  85 };
		final int[] results = new int[8];
		
		SimdGeoMath.batchOctileDistance8(neighborX, neighborY, targetX, targetY, orthoCost, diagCost, results);
		
		for (int i = 0; i < 8; i++)
		{
			final int dx = Math.abs(neighborX[i] - targetX);
			final int dy = Math.abs(neighborY[i] - targetY);
			final int minXY = Math.min(dx, dy);
			final int maxXY = Math.max(dx, dy);
			final int expected = maxXY * orthoCost + minXY * (diagCost - orthoCost);
			assertEquals(expected, results[i], "Divergência octile no vizinho " + i);
		}
	}
	
	@Test
	public void testIntersectsAabb()
	{
		// Box A: [0..10, 0..10, 0..10]
		// Overlapping Box B: [5..15, 5..15, 5..15]
		assertTrue(SimdGeoMath.intersectsAabb(0, 10, 0, 10, 0, 10, 5, 15, 5, 15, 5, 15));
		
		// Separated Box C on X axis: [20..30, 0..10, 0..10]
		assertFalse(SimdGeoMath.intersectsAabb(0, 10, 0, 10, 0, 10, 20, 30, 0, 10, 0, 10));
		
		// Separated Box D on Z axis: [0..10, 0..10, 50..60]
		assertFalse(SimdGeoMath.intersectsAabb(0, 10, 0, 10, 0, 10, 0, 10, 0, 10, 50, 60));
	}
	
	@Test
	public void testBatchAabbOverlap()
	{
		final int refMinX = 100; final int refMaxX = 200;
		final int refMinY = 100; final int refMaxY = 200;
		final int refMinZ = 0;   final int refMaxZ = 100;
		
		final int count = 5;
		final int[] bMinX = new int[] { 150, 300, 50,  120, 1000 };
		final int[] bMaxX = new int[] { 250, 400, 150, 180, 2000 };
		final int[] bMinY = new int[] { 150, 300, 120, 120, 1000 };
		final int[] bMaxY = new int[] { 250, 400, 180, 180, 2000 };
		final int[] bMinZ = new int[] { 50,  300, 20,  0,   1000 };
		final int[] bMaxZ = new int[] { 150, 400, 80,  100, 2000 };
		
		final boolean[] results = new boolean[count];
		SimdGeoMath.batchAabbOverlap(refMinX, refMaxX, refMinY, refMaxY, refMinZ, refMaxZ,
		                             bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ, results, count);
		
		assertTrue(results[0], "Candidate 0 should overlap");
		assertFalse(results[1], "Candidate 1 should NOT overlap");
		assertTrue(results[2], "Candidate 2 should overlap");
		assertTrue(results[3], "Candidate 3 should overlap");
		assertFalse(results[4], "Candidate 4 should NOT overlap");
	}
}
