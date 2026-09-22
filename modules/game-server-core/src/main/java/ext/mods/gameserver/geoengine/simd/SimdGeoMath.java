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
 * Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
 * Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
 * SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
 * as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.geoengine.simd;

/**
 * By Dhousefe-L2JBR
 * High-performance geometric and distance vector operations designed with Mechanical Sympathy.
 * <p>
 * Employs branchless vector-friendly unrolled loops (4-lane and 8-lane) tailored for HotSpot C2
 * Auto-Vectorization into AVX2 / AVX-512 / ARM NEON instructions with zero-heap allocations.
 */
public final class SimdGeoMath
{
	private SimdGeoMath()
	{
		// Static utility class
	}
	
	/**
	 * Computes Euclidean 2D distance squared between an origin point and multiple target coordinates.
	 * <p>
	 * Processes 8 targets per SIMD chunk to leverage 256-bit (AVX2) and 512-bit (AVX-512) vector lanes.
	 *
	 * @param originX Origin X coordinate
	 * @param originY Origin Y coordinate
	 * @param targetX Array of target X coordinates
	 * @param targetY Array of target Y coordinates
	 * @param results Output array to store distance-squared values
	 * @param count Number of targets to evaluate
	 */
	public static void batchDistanceSquared2D(int originX, int originY, int[] targetX, int[] targetY, long[] results, int count)
	{
		int i = 0;
		final int unrollLimit = count - 7;
		
		// 8-way unrolled vector loop for AVX2 / AVX-512
		for (; i < unrollLimit; i += 8)
		{
			final long dx0 = (long) targetX[i] - originX;
			final long dy0 = (long) targetY[i] - originY;
			results[i] = dx0 * dx0 + dy0 * dy0;
			
			final long dx1 = (long) targetX[i + 1] - originX;
			final long dy1 = (long) targetY[i + 1] - originY;
			results[i + 1] = dx1 * dx1 + dy1 * dy1;
			
			final long dx2 = (long) targetX[i + 2] - originX;
			final long dy2 = (long) targetY[i + 2] - originY;
			results[i + 2] = dx2 * dx2 + dy2 * dy2;
			
			final long dx3 = (long) targetX[i + 3] - originX;
			final long dy3 = (long) targetY[i + 3] - originY;
			results[i + 3] = dx3 * dx3 + dy3 * dy3;
			
			final long dx4 = (long) targetX[i + 4] - originX;
			final long dy4 = (long) targetY[i + 4] - originY;
			results[i + 4] = dx4 * dx4 + dy4 * dy4;
			
			final long dx5 = (long) targetX[i + 5] - originX;
			final long dy5 = (long) targetY[i + 5] - originY;
			results[i + 5] = dx5 * dx5 + dy5 * dy5;
			
			final long dx6 = (long) targetX[i + 6] - originX;
			final long dy6 = (long) targetY[i + 6] - originY;
			results[i + 6] = dx6 * dx6 + dy6 * dy6;
			
			final long dx7 = (long) targetX[i + 7] - originX;
			final long dy7 = (long) targetY[i + 7] - originY;
			results[i + 7] = dx7 * dx7 + dy7 * dy7;
		}
		
		// Tail loop for remaining elements
		for (; i < count; i++)
		{
			final long dx = (long) targetX[i] - originX;
			final long dy = (long) targetY[i] - originY;
			results[i] = dx * dx + dy * dy;
		}
	}
	
	/**
	 * Computes Euclidean 2D distances between an origin point and multiple target coordinates.
	 *
	 * @param originX Origin X coordinate
	 * @param originY Origin Y coordinate
	 * @param targetX Array of target X coordinates
	 * @param targetY Array of target Y coordinates
	 * @param results Output array to store distance values
	 * @param count Number of targets to evaluate
	 */
	public static void batchDistance2D(int originX, int originY, int[] targetX, int[] targetY, double[] results, int count)
	{
		int i = 0;
		final int unrollLimit = count - 3;
		
		// 4-way unrolled loop
		for (; i < unrollLimit; i += 4)
		{
			final double dx0 = (double) targetX[i] - originX;
			final double dy0 = (double) targetY[i] - originY;
			results[i] = Math.sqrt(dx0 * dx0 + dy0 * dy0);
			
			final double dx1 = (double) targetX[i + 1] - originX;
			final double dy1 = (double) targetY[i + 1] - originY;
			results[i + 1] = Math.sqrt(dx1 * dx1 + dy1 * dy1);
			
			final double dx2 = (double) targetX[i + 2] - originX;
			final double dy2 = (double) targetY[i + 2] - originY;
			results[i + 2] = Math.sqrt(dx2 * dx2 + dy2 * dy2);
			
			final double dx3 = (double) targetX[i + 3] - originX;
			final double dy3 = (double) targetY[i + 3] - originY;
			results[i + 3] = Math.sqrt(dx3 * dx3 + dy3 * dy3);
		}
		
		// Tail loop
		for (; i < count; i++)
		{
			final double dx = (double) targetX[i] - originX;
			final double dy = (double) targetY[i] - originY;
			results[i] = Math.sqrt(dx * dx + dy * dy);
		}
	}
	
	/**
	 * Fast batch range check: tests if target points lie within a given spherical range radius.
	 * <p>
	 * Avoids square roots by comparing squared distances directly in vector registers.
	 *
	 * @param originX Origin X coordinate
	 * @param originY Origin Y coordinate
	 * @param originZ Origin Z coordinate
	 * @param targetX Array of target X coordinates
	 * @param targetY Array of target Y coordinates
	 * @param targetZ Array of target Z coordinates
	 * @param maxRadius Maximum allowed radius distance
	 * @param inRangeMask Output boolean mask array where true indicates target is within range
	 * @param count Number of targets
	 */
	public static void batchRangeCheck3D(int originX, int originY, int originZ,
	                                     int[] targetX, int[] targetY, int[] targetZ,
	                                     int maxRadius, boolean[] inRangeMask, int count)
	{
		final long radiusSq = (long) maxRadius * maxRadius;
		int i = 0;
		final int unrollLimit = count - 3;
		
		for (; i < unrollLimit; i += 4)
		{
			final long dx0 = (long) targetX[i] - originX;
			final long dy0 = (long) targetY[i] - originY;
			final long dz0 = (long) targetZ[i] - originZ;
			inRangeMask[i] = (dx0 * dx0 + dy0 * dy0 + dz0 * dz0) <= radiusSq;
			
			final long dx1 = (long) targetX[i + 1] - originX;
			final long dy1 = (long) targetY[i + 1] - originY;
			final long dz1 = (long) targetZ[i + 1] - originZ;
			inRangeMask[i + 1] = (dx1 * dx1 + dy1 * dy1 + dz1 * dz1) <= radiusSq;
			
			final long dx2 = (long) targetX[i + 2] - originX;
			final long dy2 = (long) targetY[i + 2] - originY;
			final long dz2 = (long) targetZ[i + 2] - originZ;
			inRangeMask[i + 2] = (dx2 * dx2 + dy2 * dy2 + dz2 * dz2) <= radiusSq;
			
			final long dx3 = (long) targetX[i + 3] - originX;
			final long dy3 = (long) targetY[i + 3] - originY;
			final long dz3 = (long) targetZ[i + 3] - originZ;
			inRangeMask[i + 3] = (dx3 * dx3 + dy3 * dy3 + dz3 * dz3) <= radiusSq;
		}
		
		for (; i < count; i++)
		{
			final long dx = (long) targetX[i] - originX;
			final long dy = (long) targetY[i] - originY;
			final long dz = (long) targetZ[i] - originZ;
			inRangeMask[i] = (dx * dx + dy * dy + dz * dz) <= radiusSq;
		}
	}
	
	/**
	 * Determines whether a line-of-sight ray can safely leapfrog an entire geodata block.
	 * <p>
	 * If the lowest projected point of the ray over this block is higher than the maximum
	 * terrain elevation of the block, no terrain obstacle can intersect the ray.
	 *
	 * @param maxBlockZ Maximum terrain altitude of the entire 8x8 block
	 * @param rayCurrentZ Current Z elevation of the ray entering the block
	 * @param raySlopeZ Delta Z per distance unit along ray trajectory
	 * @param blockSpanDistance Estimated Euclidean distance traversed across this block
	 * @return true if the ray stays strictly above maximum terrain elevation across the entire block
	 */
	public static boolean canLeapfrogBlock(short maxBlockZ, double rayCurrentZ, double raySlopeZ, double blockSpanDistance)
	{
		// Projected ray height at block exit
		final double rayExitZ = rayCurrentZ + (raySlopeZ * blockSpanDistance);
		
		// Lowest ray elevation across the block span
		final double minRayElevation = Math.min(rayCurrentZ, rayExitZ);
		
		// Safe leapfrog if min ray elevation exceeds max block altitude with margin
		return minRayElevation > (maxBlockZ + 32);
	}
	
	/**
	 * Computes Euclidean 3D A* heuristic cost for up to 8 neighbor nodes simultaneously.
	 * <p>
	 * Fully 8-way unrolled for Auto-Vectorization into 256-bit (AVX2) / 512-bit (AVX-512) vector lanes.
	 *
	 * @param neighborX Array of 8 neighbor X coordinates
	 * @param neighborY Array of 8 neighbor Y coordinates
	 * @param neighborZ Array of 8 neighbor Z coordinates
	 * @param targetX Target destination X
	 * @param targetY Target destination Y
	 * @param targetZ Target destination Z
	 * @param cellHeight Height scale divisor (GeoStructure.CELL_HEIGHT)
	 * @param heuristicWeight Weight multiplier for heuristic distance
	 * @param outputHCost Output array of size 8 for computed heuristic costs
	 */
	public static void batchCostH8(int[] neighborX, int[] neighborY, int[] neighborZ,
	                               int targetX, int targetY, int targetZ,
	                               int cellHeight, int heuristicWeight, int[] outputHCost)
	{
		final double invCellHeight = 1.0 / cellHeight;
		
		// Lane 0
		final double dx0 = neighborX[0] - targetX;
		final double dy0 = neighborY[0] - targetY;
		final double dz0 = (neighborZ[0] - targetZ) * invCellHeight;
		outputHCost[0] = (int) (Math.sqrt(dx0 * dx0 + dy0 * dy0 + dz0 * dz0) * heuristicWeight);
		
		// Lane 1
		final double dx1 = neighborX[1] - targetX;
		final double dy1 = neighborY[1] - targetY;
		final double dz1 = (neighborZ[1] - targetZ) * invCellHeight;
		outputHCost[1] = (int) (Math.sqrt(dx1 * dx1 + dy1 * dy1 + dz1 * dz1) * heuristicWeight);
		
		// Lane 2
		final double dx2 = neighborX[2] - targetX;
		final double dy2 = neighborY[2] - targetY;
		final double dz2 = (neighborZ[2] - targetZ) * invCellHeight;
		outputHCost[2] = (int) (Math.sqrt(dx2 * dx2 + dy2 * dy2 + dz2 * dz2) * heuristicWeight);
		
		// Lane 3
		final double dx3 = neighborX[3] - targetX;
		final double dy3 = neighborY[3] - targetY;
		final double dz3 = (neighborZ[3] - targetZ) * invCellHeight;
		outputHCost[3] = (int) (Math.sqrt(dx3 * dx3 + dy3 * dy3 + dz3 * dz3) * heuristicWeight);
		
		// Lane 4
		final double dx4 = neighborX[4] - targetX;
		final double dy4 = neighborY[4] - targetY;
		final double dz4 = (neighborZ[4] - targetZ) * invCellHeight;
		outputHCost[4] = (int) (Math.sqrt(dx4 * dx4 + dy4 * dy4 + dz4 * dz4) * heuristicWeight);
		
		// Lane 5
		final double dx5 = neighborX[5] - targetX;
		final double dy5 = neighborY[5] - targetY;
		final double dz5 = (neighborZ[5] - targetZ) * invCellHeight;
		outputHCost[5] = (int) (Math.sqrt(dx5 * dx5 + dy5 * dy5 + dz5 * dz5) * heuristicWeight);
		
		// Lane 6
		final double dx6 = neighborX[6] - targetX;
		final double dy6 = neighborY[6] - targetY;
		final double dz6 = (neighborZ[6] - targetZ) * invCellHeight;
		outputHCost[6] = (int) (Math.sqrt(dx6 * dx6 + dy6 * dy6 + dz6 * dz6) * heuristicWeight);
		
		// Lane 7
		final double dx7 = neighborX[7] - targetX;
		final double dy7 = neighborY[7] - targetY;
		final double dz7 = (neighborZ[7] - targetZ) * invCellHeight;
		outputHCost[7] = (int) (Math.sqrt(dx7 * dx7 + dy7 * dy7 + dz7 * dz7) * heuristicWeight);
	}
	
	/**
	 * Computes integer Octile distance for 8 neighbor nodes simultaneously.
	 * <p>
	 * Formula: (max(dx, dy) - min(dx, dy)) * orthoCost + min(dx, dy) * diagCost.
	 * Avoids floating-point and sqrt entirely while matching 8-connectivity grid geometry.
	 *
	 * @param neighborX Array of 8 neighbor X coordinates
	 * @param neighborY Array of 8 neighbor Y coordinates
	 * @param targetX Target destination X
	 * @param targetY Target destination Y
	 * @param orthoCost Orthogonal movement cost (e.g. 10)
	 * @param diagCost Diagonal movement cost (e.g. 14)
	 * @param outputHCost Output array of size 8 for octile distances
	 */
	public static void batchOctileDistance8(int[] neighborX, int[] neighborY,
	                                        int targetX, int targetY,
	                                        int orthoCost, int diagCost,
	                                        int[] outputHCost)
	{
		final int diffCost = diagCost - orthoCost;
		
		// Lane 0
		final int dx0 = Math.abs(neighborX[0] - targetX);
		final int dy0 = Math.abs(neighborY[0] - targetY);
		final int min0 = Math.min(dx0, dy0);
		final int max0 = Math.max(dx0, dy0);
		outputHCost[0] = max0 * orthoCost + min0 * diffCost;
		
		// Lane 1
		final int dx1 = Math.abs(neighborX[1] - targetX);
		final int dy1 = Math.abs(neighborY[1] - targetY);
		final int min1 = Math.min(dx1, dy1);
		final int max1 = Math.max(dx1, dy1);
		outputHCost[1] = max1 * orthoCost + min1 * diffCost;
		
		// Lane 2
		final int dx2 = Math.abs(neighborX[2] - targetX);
		final int dy2 = Math.abs(neighborY[2] - targetY);
		final int min2 = Math.min(dx2, dy2);
		final int max2 = Math.max(dx2, dy2);
		outputHCost[2] = max2 * orthoCost + min2 * diffCost;
		
		// Lane 3
		final int dx3 = Math.abs(neighborX[3] - targetX);
		final int dy3 = Math.abs(neighborY[3] - targetY);
		final int min3 = Math.min(dx3, dy3);
		final int max3 = Math.max(dx3, dy3);
		outputHCost[3] = max3 * orthoCost + min3 * diffCost;
		
		// Lane 4
		final int dx4 = Math.abs(neighborX[4] - targetX);
		final int dy4 = Math.abs(neighborY[4] - targetY);
		final int min4 = Math.min(dx4, dy4);
		final int max4 = Math.max(dx4, dy4);
		outputHCost[4] = max4 * orthoCost + min4 * diffCost;
		
		// Lane 5
		final int dx5 = Math.abs(neighborX[5] - targetX);
		final int dy5 = Math.abs(neighborY[5] - targetY);
		final int min5 = Math.min(dx5, dy5);
		final int max5 = Math.max(dx5, dy5);
		outputHCost[5] = max5 * orthoCost + min5 * diffCost;
		
		// Lane 6
		final int dx6 = Math.abs(neighborX[6] - targetX);
		final int dy6 = Math.abs(neighborY[6] - targetY);
		final int min6 = Math.min(dx6, dy6);
		final int max6 = Math.max(dx6, dy6);
		outputHCost[6] = max6 * orthoCost + min6 * diffCost;
		
		// Lane 7
		final int dx7 = Math.abs(neighborX[7] - targetX);
		final int dy7 = Math.abs(neighborY[7] - targetY);
		final int min7 = Math.min(dx7, dy7);
		final int max7 = Math.max(dx7, dy7);
		outputHCost[7] = max7 * orthoCost + min7 * diffCost;
	}
	
	/**
	 * Tests whether two 3D Axis-Aligned Bounding Boxes (AABB) overlap.
	 * <p>
	 * Highly branch-predictor friendly scalar check with zero heap allocations.
	 *
	 * @return true if box A and box B intersect in all 3 spatial dimensions
	 */
	public static boolean intersectsAabb(int minA_X, int maxA_X, int minA_Y, int maxA_Y, int minA_Z, int maxA_Z,
	                                     int minB_X, int maxB_X, int minB_Y, int maxB_Y, int minB_Z, int maxB_Z)
	{
		return (minA_X < maxB_X && maxA_X > minB_X)
			&& (minA_Y < maxB_Y && maxA_Y > minB_Y)
			&& (minA_Z <= maxB_Z && maxA_Z >= minB_Z);
	}
	
	/**
	 * Vectorized batch check of multiple 3D Bounding Boxes against a reference bounding box (e.g. Geodata Block AABB).
	 * <p>
	 * Processes 4 bounding boxes per unrolled iteration for AVX2 execution.
	 *
	 * @param refMinX Reference block min X
	 * @param refMaxX Reference block max X
	 * @param refMinY Reference block min Y
	 * @param refMaxY Reference block max Y
	 * @param refMinZ Reference block min Z
	 * @param refMaxZ Reference block max Z
	 * @param boxMinX Array of candidate boxes min X
	 * @param boxMaxX Array of candidate boxes max X
	 * @param boxMinY Array of candidate boxes min Y
	 * @param boxMaxY Array of candidate boxes max Y
	 * @param boxMinZ Array of candidate boxes min Z
	 * @param boxMaxZ Array of candidate boxes max Z
	 * @param results Output boolean mask array (true = overlaps with reference box)
	 * @param count Number of candidate boxes to evaluate
	 */
	public static void batchAabbOverlap(int refMinX, int refMaxX, int refMinY, int refMaxY, int refMinZ, int refMaxZ,
	                                    int[] boxMinX, int[] boxMaxX,
	                                    int[] boxMinY, int[] boxMaxY,
	                                    int[] boxMinZ, int[] boxMaxZ,
	                                    boolean[] results, int count)
	{
		int i = 0;
		final int unrollLimit = count - 3;
		
		for (; i < unrollLimit; i += 4)
		{
			results[i] = (refMinX < boxMaxX[i] && refMaxX > boxMinX[i])
				&& (refMinY < boxMaxY[i] && refMaxY > boxMinY[i])
				&& (refMinZ <= boxMaxZ[i] && refMaxZ >= boxMinZ[i]);
			
			results[i + 1] = (refMinX < boxMaxX[i + 1] && refMaxX > boxMinX[i + 1])
				&& (refMinY < boxMaxY[i + 1] && refMaxY > boxMinY[i + 1])
				&& (refMinZ <= boxMaxZ[i + 1] && refMaxZ >= boxMinZ[i + 1]);
			
			results[i + 2] = (refMinX < boxMaxX[i + 2] && refMaxX > boxMinX[i + 2])
				&& (refMinY < boxMaxY[i + 2] && refMaxY > boxMinY[i + 2])
				&& (refMinZ <= boxMaxZ[i + 2] && refMaxZ >= boxMinZ[i + 2]);
			
			results[i + 3] = (refMinX < boxMaxX[i + 3] && refMaxX > boxMinX[i + 3])
				&& (refMinY < boxMaxY[i + 3] && refMaxY > boxMinY[i + 3])
				&& (refMinZ <= boxMaxZ[i + 3] && refMaxZ >= boxMinZ[i + 3]);
		}
		
		for (; i < count; i++)
		{
			results[i] = (refMinX < boxMaxX[i] && refMaxX > boxMinX[i])
				&& (refMinY < boxMaxY[i] && refMaxY > boxMinY[i])
				&& (refMinZ <= boxMaxZ[i] && refMaxZ >= boxMinZ[i]);
		}
	}
}
