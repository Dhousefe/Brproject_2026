package ext.mods.config;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.gameserver.data.manager.CountryLocaleManager;
import ext.mods.gameserver.enums.GeoType;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.olympiad.enums.OlympiadPeriod;
import ext.mods.protection.hwid.crypt.FirstKey;

/**
 * Phase 4 config domain: ConfigGeoengine.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigGeoengine
{
   private ConfigGeoengine()
   {
   }

   public static String GEODATA_PATH;
   public static GeoType GEODATA_TYPE;
   public static int MAX_GEOPATH_FAIL_COUNT;
   public static int PART_OF_CHARACTER_HEIGHT;
   public static int MAX_OBSTACLE_HEIGHT;
   public static int NPC_Z_INDEX;
   public static int MOVE_WEIGHT;
   public static int MOVE_WEIGHT_DIAG;
   public static int OBSTACLE_WEIGHT;
   public static int OBSTACLE_WEIGHT_DIAG;
   public static boolean ENABLE_BOUNDARY_CELL_PENALTY;
   public static int BOUNDARY_CELL_PENALTY;
   public static int BOUNDARY_BUFFER;
   public static int HEURISTIC_WEIGHT;
   public static int MAX_ITERATIONS;
   public static boolean SISTEMA_PATHFINDING;
   public static boolean USE_OPTIMIZED_MOVEMENT;
   public static boolean ENABLE_CLIENT_SIDE_PREDICTION;
   public static int MOVEMENT_VALIDATION_INTERVAL;
   public static double MOVEMENT_RECONCILIATION_THRESHOLD;
   public static int PATHFINDING_MAX_NODES;
   public static int PATHFINDING_MAX_ITERATIONS;
   public static boolean ENABLE_PATH_SMOOTHING;
   public static int GEOENGINE_CACHE_SIZE;
   public static boolean ENABLE_PATHFINDING_CACHE;
   public static int PATHFINDING_CACHE_SIZE;
   public static int PATHFINDING_CACHE_EXPIRATION;
   public static int PATHFINDING_THREADS;
   public static boolean USE_PATHFINDING_POOL;
   public static boolean USE_L2BR_PATHFINDING;
   public static boolean ENABLE_SMOOTH_OBSTACLE_AVOIDANCE;
   public static int OBSTACLE_SMOOTHING_DISTANCE;
   public static int OBSTACLE_DETECTION_DISTANCE;
   public static int PATHFINDING_SMOOTHING_LEVEL;
   public static boolean ENABLE_REAL_TIME_OBSTACLE_AVOIDANCE;
   public static boolean ATTACK_USE_PATHFINDER;
   public static int GEO_HEIGHT_TOLERANCE;
   public static int SHORT_DISTANCE_THRESHOLD;
   public static int MAX_SHORT_DISTANCE_HEIGHT_DIFF;
   public static int MAX_CONSECUTIVE_BLOCKS;
   public static boolean ENABLE_NPC_MOVEMENT_OPTIMIZATION;
   public static int NPC_MOVEMENT_PLAYER_RANGE;
   public static boolean ENABLE_CREATURE_COLLISION;
   public static int CREATURE_COLLISION_CHECK_RADIUS;
   public static int CREATURE_COLLISION_AVOIDANCE_RADIUS;
   public static int MAX_ALTERNATIVE_POSITIONS;
   public static double CREATURE_COLLISION_PUSH_FORCE;
   public static boolean ENABLE_PATHFIND_BOOST;
   public static int PATHFIND_BOOST_MIN_DISTANCE;
   public static boolean ENABLE_SIMD_HEURISTICS;
   public static boolean ENABLE_SIMD_OBJECT_CULLING;

   public static void load() {
      ExProperties geoengine = Config.initProperties(Config.GEOENGINE_FILE);
      ConfigGeoengine.ENABLE_PATHFIND_BOOST = geoengine.getProperty("EnablePathFindBoost", true);
      ConfigGeoengine.PATHFIND_BOOST_MIN_DISTANCE = geoengine.getProperty("PathFindBoostMinDistance", 128);
      ConfigGeoengine.ENABLE_SIMD_HEURISTICS = geoengine.getProperty("EnableSimdHeuristics", true);
      ConfigGeoengine.ENABLE_SIMD_OBJECT_CULLING = geoengine.getProperty("EnableSimdObjectCulling", true);
      ConfigGeoengine.GEODATA_PATH = Config.getString(geoengine, "GeoDataPath", "./data/geodata/");      ConfigGeoengine.GEODATA_TYPE = Enum.valueOf(GeoType.class, geoengine.getProperty("GeoDataType", "L2OFF"));      ConfigGeoengine.MAX_GEOPATH_FAIL_COUNT = Math.max(15, geoengine.getProperty("MaxGeopathFailCount", 30));      ConfigGeoengine.PART_OF_CHARACTER_HEIGHT = geoengine.getProperty("PartOfCharacterHeight", 75);      ConfigGeoengine.MAX_OBSTACLE_HEIGHT = geoengine.getProperty("MaxObstacleHeight", 32);      ConfigGeoengine.NPC_Z_INDEX = geoengine.getProperty("NpcZindex", 0);      ConfigGeoengine.MOVE_WEIGHT = geoengine.getProperty("MoveWeight", 10);      ConfigGeoengine.MOVE_WEIGHT_DIAG = geoengine.getProperty("MoveWeightDiag", 14);      ConfigGeoengine.OBSTACLE_WEIGHT = geoengine.getProperty("ObstacleWeight", 30);      ConfigGeoengine.OBSTACLE_WEIGHT_DIAG = (int)((double)ConfigGeoengine.OBSTACLE_WEIGHT * Math.sqrt(2.0));      ConfigGeoengine.ENABLE_BOUNDARY_CELL_PENALTY = geoengine.getProperty("EnableBoundaryCellPenalty", true);      ConfigGeoengine.BOUNDARY_CELL_PENALTY = geoengine.getProperty("BoundaryCellPenalty", 25);      ConfigGeoengine.BOUNDARY_BUFFER = geoengine.getProperty("BoundaryBuffer", 20);      ConfigGeoengine.HEURISTIC_WEIGHT = geoengine.getProperty("HeuristicWeight", 12);      ConfigGeoengine.MAX_ITERATIONS = geoengine.getProperty("MaxIterations", 10000);      ConfigGeoengine.SISTEMA_PATHFINDING = geoengine.getProperty("SistemaPathfinding", true);      ConfigGeoengine.USE_OPTIMIZED_MOVEMENT = geoengine.getProperty("UseOptimizedMovement", true);      ConfigGeoengine.ENABLE_CLIENT_SIDE_PREDICTION = geoengine.getProperty("EnableClientSidePrediction", true);      ConfigGeoengine.MOVEMENT_VALIDATION_INTERVAL = geoengine.getProperty("MovementValidationInterval", 500);      ConfigGeoengine.MOVEMENT_RECONCILIATION_THRESHOLD = geoengine.getProperty("MovementReconciliationThreshold", 32.0);      ConfigGeoengine.PATHFINDING_MAX_NODES = geoengine.getProperty("PathfindingMaxNodes", 1500);      ConfigGeoengine.PATHFINDING_MAX_ITERATIONS = geoengine.getProperty("PathfindingMaxIterations", 2000);      ConfigGeoengine.ENABLE_PATH_SMOOTHING = geoengine.getProperty("EnablePathSmoothing", true);      ConfigGeoengine.GEOENGINE_CACHE_SIZE = geoengine.getProperty("GeoEngineCacheSize", 50000);      ConfigGeoengine.ENABLE_PATHFINDING_CACHE = geoengine.getProperty("EnablePathfindingCache", true);      ConfigGeoengine.PATHFINDING_CACHE_SIZE = geoengine.getProperty("PathfindingCacheSize", 5000);      ConfigGeoengine.PATHFINDING_CACHE_EXPIRATION = geoengine.getProperty("PathfindingCacheExpiration", 30) * 1000;      ConfigGeoengine.PATHFINDING_THREADS = geoengine.getProperty("PathfindingThreads", -1);      ConfigGeoengine.USE_PATHFINDING_POOL = geoengine.getProperty("UsePathfindingPool", true);      ConfigGeoengine.USE_L2BR_PATHFINDING = geoengine.getProperty("UseL2BRPathfinding", true);      if (ConfigGeoengine.PATHFINDING_THREADS == -1) {
         ConfigGeoengine.PATHFINDING_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);      }

      ConfigGeoengine.ENABLE_SMOOTH_OBSTACLE_AVOIDANCE = geoengine.getProperty("EnableSmoothObstacleAvoidance", true);      ConfigGeoengine.OBSTACLE_SMOOTHING_DISTANCE = geoengine.getProperty("ObstacleSmoothingDistance", 50);      ConfigGeoengine.OBSTACLE_DETECTION_DISTANCE = geoengine.getProperty("ObstacleDetectionDistance", 32);      ConfigGeoengine.PATHFINDING_SMOOTHING_LEVEL = geoengine.getProperty("PathfindingSmoothingLevel", 7);      ConfigGeoengine.ENABLE_REAL_TIME_OBSTACLE_AVOIDANCE = geoengine.getProperty("EnableRealTimeObstacleAvoidance", true);      ConfigGeoengine.ATTACK_USE_PATHFINDER = geoengine.getProperty("AttackUsePathfinder", false);      ConfigGeoengine.GEO_HEIGHT_TOLERANCE = geoengine.getProperty("GeoHeightTolerance", 512);      ConfigGeoengine.SHORT_DISTANCE_THRESHOLD = geoengine.getProperty("ShortDistanceThreshold", 1000);      ConfigGeoengine.MAX_SHORT_DISTANCE_HEIGHT_DIFF = geoengine.getProperty("MaxShortDistanceHeightDiff", 200);      ConfigGeoengine.MAX_CONSECUTIVE_BLOCKS = geoengine.getProperty("MaxConsecutiveBlocks", 3);      ConfigGeoengine.ENABLE_NPC_MOVEMENT_OPTIMIZATION = geoengine.getProperty("EnableNpcMovementOptimization", true);      ConfigGeoengine.NPC_MOVEMENT_PLAYER_RANGE = geoengine.getProperty("NpcMovementPlayerRange", 2000);      ConfigGeoengine.ENABLE_CREATURE_COLLISION = geoengine.getProperty("EnableCreatureCollision", true);      ConfigGeoengine.CREATURE_COLLISION_CHECK_RADIUS = geoengine.getProperty("CreatureCollisionCheckRadius", 150);      ConfigGeoengine.CREATURE_COLLISION_AVOIDANCE_RADIUS = geoengine.getProperty("CreatureCollisionAvoidanceRadius", 80);      ConfigGeoengine.MAX_ALTERNATIVE_POSITIONS = geoengine.getProperty("MaxAlternativePositions", 8);      ConfigGeoengine.CREATURE_COLLISION_PUSH_FORCE = geoengine.getProperty("CreatureCollisionPushForce", 0.3);   }
}
