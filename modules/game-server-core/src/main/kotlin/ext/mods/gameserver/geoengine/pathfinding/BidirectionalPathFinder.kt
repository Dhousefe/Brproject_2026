/*
 * MIT License
 * Copyright (c) 2024-2026 L2Brproject
 */
package ext.mods.gameserver.geoengine.pathfinding

import ext.mods.commons.util.PriorityQueueSet
import ext.mods.config.ConfigGeoengine
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.geoengine.geodata.GeoStructure
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive
import java.awt.Color
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Algoritmo A* Bidirecional de alta performance.
 * 
 * Expande simultaneamente a busca a partir da origem (Forward) e do destino (Backward).
 * Em ambientes confinados (lojas, templos, corredores e salas estreitas), a busca a partir
 * do destino afunila rapidamente pelas saídas e encontra a busca externa com um número
 * dramaticamente menor de expansões que o A* unidirecional.
 * 
 * Possui fallback de nó vizinho acessível se a célula exata de destino estiver atrás de balcão ou bloqueada.
 */
class BidirectionalPathFinder {
    private val _geoEngine = GeoEngine.getInstance()

    private val _openedF = PriorityQueueSet<Node>()
    private val _openedB = PriorityQueueSet<Node>()

    private val _closedF = HashMap<Long, Node>()
    private val _closedB = HashMap<Long, Node>()

    private var _meetingNodeF: Node? = null
    private var _meetingNodeB: Node? = null

    private fun nodeKey(gx: Int, gy: Int, z: Int): Long {
        return (gx.toLong() and 0xFFFFL) or 
               ((gy.toLong() and 0xFFFFL) shl 16) or 
               ((z.toLong() and 0xFFFF_FFFFL) shl 32)
    }

    /**
     * Localiza a célula navegável mais próxima caso a coordenada de destino esteja em célula sem passagem.
     */
    private fun findAccessibleNear(gx: Int, gy: Int, gz: Int): Triple<Int, Int, Int> {
        val startNswe = _geoEngine.getNsweNearest(gx, gy, gz)
        if (startNswe != GeoStructure.CELL_FLAG_NONE) {
            return Triple(gx, gy, gz)
        }

        // Tenta nos 8 vizinhos imediatos (raio 1 e raio 2)
        for (radius in 1..2) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    if (dx == 0 && dy == 0) continue
                    val nx = gx + dx
                    val ny = gy + dy
                    if (nx < 0 || nx >= GeoStructure.GEO_CELLS_X || ny < 0 || ny >= GeoStructure.GEO_CELLS_Y) continue
                    val block = _geoEngine.getBlock(nx, ny)
                    val idx = block.getIndexBelow(nx, ny, gz + GeoStructure.CELL_IGNORE_HEIGHT, null)
                    if (idx >= 0) {
                        val nswe = block.getNswe(idx, null)
                        if (nswe != GeoStructure.CELL_FLAG_NONE) {
                            val nz = block.getHeight(idx, null).toInt()
                            return Triple(nx, ny, nz)
                        }
                    }
                }
            }
        }
        return Triple(gx, gy, gz)
    }

    fun findPath(
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        val (adjGtx, adjGty, adjGtz) = findAccessibleNear(gtx, gty, gtz)

        _openedF.clear()
        _openedB.clear()
        _closedF.clear()
        _closedB.clear()
        _meetingNodeF = null
        _meetingNodeB = null

        val startNswe = _geoEngine.getNsweNearest(gox, goy, goz)
        val targetNswe = _geoEngine.getNsweNearest(adjGtx, adjGty, adjGtz)

        val startNode = Node(gox, goy, goz, startNswe)
        startNode.setCost(null, 0, getCostH(gox, goy, goz, adjGtx, adjGty, adjGtz))
        _openedF.add(startNode)

        val targetNode = Node(adjGtx, adjGty, adjGtz, targetNswe)
        targetNode.setCost(null, 0, getCostH(adjGtx, adjGty, adjGtz, gox, goy, goz))
        _openedB.add(targetNode)

        var count = 0
        val maxIters = ConfigGeoengine.MAX_ITERATIONS.coerceAtMost(5000)

        while ((!_openedF.isEmpty() || !_openedB.isEmpty()) && count < maxIters) {
            count++

            // Passo de expansão Forward
            if (!_openedF.isEmpty()) {
                val currF = _openedF.poll()
                if (currF != null) {
                    val keyF = nodeKey(currF.geoX, currF.geoY, currF.z)
                    _closedF[keyF] = currF

                    // Checa se a frente reversa já visitou este nó
                    val matchB = _closedB[keyF]
                    if (matchB != null) {
                        _meetingNodeF = currF
                        _meetingNodeB = matchB
                        return constructPath(debug)
                    }

                    expand(currF, adjGtx, adjGty, adjGtz, _openedF, _closedF)
                }
            }

            // Passo de expansão Backward
            if (!_openedB.isEmpty()) {
                val currB = _openedB.poll()
                if (currB != null) {
                    val keyB = nodeKey(currB.geoX, currB.geoY, currB.z)
                    _closedB[keyB] = currB

                    // Checa se a frente direta já visitou este nó
                    val matchF = _closedF[keyB]
                    if (matchF != null) {
                        _meetingNodeF = matchF
                        _meetingNodeB = currB
                        return constructPath(debug)
                    }

                    expand(currB, gox, goy, goz, _openedB, _closedB)
                }
            }
        }

        // Se não encontrou encontro pelo bidirecional, retorna vazio para fallback
        return emptyList()
    }

    private fun constructPath(debug: ExServerPrimitive?): List<Location> {
        val path = LinkedList<Location>()
        val meetingF = _meetingNodeF ?: return emptyList()
        val meetingB = _meetingNodeB ?: return emptyList()

        // 1. Constrói do nó de encontro até a origem
        var node: Node? = meetingF
        var parent = node?.parent
        var dx = 0
        var dy = 0
        while (parent != null) {
            val nx = parent.geoX - node!!.geoX
            val ny = parent.geoY - node.geoY
            if (dx != nx || dy != ny) {
                path.addFirst(Location(GeoEngine.getWorldX(node.geoX), GeoEngine.getWorldY(node.geoY), node.z))
                dx = nx
                dy = ny
            }
            node = parent
            parent = node.parent
        }

        // Nó de encontro
        path.add(Location(GeoEngine.getWorldX(meetingF.geoX), GeoEngine.getWorldY(meetingF.geoY), meetingF.z))

        // 2. Constrói do nó de encontro até o destino
        node = meetingB.parent
        dx = 0
        dy = 0
        while (node != null) {
            val p = node.parent
            if (p != null) {
                val nx = p.geoX - node.geoX
                val ny = p.geoY - node.geoY
                if (dx != nx || dy != ny) {
                    path.add(Location(GeoEngine.getWorldX(node.geoX), GeoEngine.getWorldY(node.geoY), node.z))
                    dx = nx
                    dy = ny
                }
            } else {
                path.add(Location(GeoEngine.getWorldX(node.geoX), GeoEngine.getWorldY(node.geoY), node.z))
            }
            node = node.parent
        }

        if (debug != null && path.isNotEmpty()) {
            val end = path.last()
            debug.addPoint("Bidirectional OK", Color.GREEN, true, end.x, end.y, end.z + 20)
        }

        return path
    }

    private fun expand(
        current: Node,
        targetGx: Int, targetGy: Int, targetGz: Int,
        opened: PriorityQueueSet<Node>,
        closed: HashMap<Long, Node>
    ) {
        val nswe = current.nswe
        if (nswe == GeoStructure.CELL_FLAG_NONE) return
        val x = current.geoX
        val y = current.geoY
        val z = current.z + GeoStructure.CELL_IGNORE_HEIGHT

        val nsweN = addDirectionalNode(current, x, y, z, nswe, 0, -1, GeoStructure.CELL_FLAG_N, targetGx, targetGy, targetGz, opened, closed)
        val nsweS = addDirectionalNode(current, x, y, z, nswe, 0, 1, GeoStructure.CELL_FLAG_S, targetGx, targetGy, targetGz, opened, closed)
        val nsweW = addDirectionalNode(current, x, y, z, nswe, -1, 0, GeoStructure.CELL_FLAG_W, targetGx, targetGy, targetGz, opened, closed)
        val nsweE = addDirectionalNode(current, x, y, z, nswe, 1, 0, GeoStructure.CELL_FLAG_E, targetGx, targetGy, targetGz, opened, closed)

        addCornerNode(current, x, y, z, -1, -1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_N, nsweW, nsweN, targetGx, targetGy, targetGz, opened, closed)
        addCornerNode(current, x, y, z, 1, -1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_N, nsweE, nsweN, targetGx, targetGy, targetGz, opened, closed)
        addCornerNode(current, x, y, z, -1, 1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_S, nsweW, nsweS, targetGx, targetGy, targetGz, opened, closed)
        addCornerNode(current, x, y, z, 1, 1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_S, nsweE, nsweS, targetGx, targetGy, targetGz, opened, closed)
    }

    private fun addDirectionalNode(
        parent: Node, x: Int, y: Int, z: Int,
        nswe: Byte, dx: Int, dy: Int, directionFlag: Byte,
        targetGx: Int, targetGy: Int, targetGz: Int,
        opened: PriorityQueueSet<Node>,
        closed: HashMap<Long, Node>
    ): Byte {
        if ((nswe.toInt() and directionFlag.toInt()) != 0) {
            return addNode(parent, x + dx, y + dy, z, false, targetGx, targetGy, targetGz, opened, closed)
        }
        return GeoStructure.CELL_FLAG_NONE
    }

    private fun addCornerNode(
        parent: Node, x: Int, y: Int, z: Int,
        dx: Int, dy: Int,
        dirFlagX: Byte, dirFlagY: Byte,
        nsweX: Byte, nsweY: Byte,
        targetGx: Int, targetGy: Int, targetGz: Int,
        opened: PriorityQueueSet<Node>,
        closed: HashMap<Long, Node>
    ) {
        if ((nsweX.toInt() and dirFlagY.toInt()) != 0 && (nsweY.toInt() and dirFlagX.toInt()) != 0) {
            addNode(parent, x + dx, y + dy, z, true, targetGx, targetGy, targetGz, opened, closed)
        }
    }

    private fun addNode(
        parent: Node, gx: Int, gy: Int, checkZ: Int, diagonal: Boolean,
        targetGx: Int, targetGy: Int, targetGz: Int,
        opened: PriorityQueueSet<Node>,
        closed: HashMap<Long, Node>
    ): Byte {
        if (gx < 0 || gx >= GeoStructure.GEO_CELLS_X || gy < 0 || gy >= GeoStructure.GEO_CELLS_Y) {
            return GeoStructure.CELL_FLAG_NONE
        }
        val block = _geoEngine.getBlock(gx, gy)
        val index = block.getIndexBelow(gx, gy, checkZ, null)
        if (index < 0) return GeoStructure.CELL_FLAG_NONE

        val newZ = block.getHeight(index, null).toInt()
        val key = nodeKey(gx, gy, newZ)
        if (closed.containsKey(key)) return block.getNswe(index, null)

        val nswe = block.getNswe(index, null)
        val node = Node(gx, gy, newZ, nswe)

        var weight = if (nswe == GeoStructure.CELL_FLAG_ALL) {
            if (diagonal) ConfigGeoengine.MOVE_WEIGHT_DIAG else ConfigGeoengine.MOVE_WEIGHT
        } else {
            if (diagonal) ConfigGeoengine.OBSTACLE_WEIGHT_DIAG else ConfigGeoengine.OBSTACLE_WEIGHT
        }
        if (ConfigGeoengine.ENABLE_BOUNDARY_CELL_PENALTY && nswe != GeoStructure.CELL_FLAG_NONE &&
            _geoEngine.hasBlockedNeighborAtSameLevel(gx, gy, newZ)) {
            weight += ConfigGeoengine.BOUNDARY_CELL_PENALTY
        }

        val hCost = getCostH(gx, gy, newZ, targetGx, targetGy, targetGz)

        val existing = opened.find { it == node }
        if (existing != null) {
            if (parent.costG + weight < existing.costG) {
                existing.setCost(parent, weight, hCost)
                opened.remove(existing)
                opened.add(existing)
            }
        } else {
            node.setCost(parent, weight, hCost)
            opened.add(node)
        }
        return nswe
    }

    private fun getCostH(gx: Int, gy: Int, gz: Int, tgx: Int, tgy: Int, tgz: Int): Int {
        val dx = abs(gx - tgx)
        val dy = abs(gy - tgy)
        val dz = abs(gz - tgz) / GeoStructure.CELL_HEIGHT
        return (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) * ConfigGeoengine.HEURISTIC_WEIGHT).toInt()
    }
}
