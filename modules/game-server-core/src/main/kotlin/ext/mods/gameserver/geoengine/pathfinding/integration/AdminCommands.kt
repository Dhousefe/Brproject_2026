/*
 * MIT License
 * * Copyright (c) 2024-2026 L2Brproject
 * * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * * Our main Developers: Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
 * Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
 * SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
 * as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.geoengine.pathfinding.integration
import ext.mods.gameserver.geoengine.pathfinding.generation.GeoIndexer
import ext.mods.gameserver.geoengine.pathfinding.generation.GeoIndexer.SourceInfo
import ext.mods.gameserver.geoengine.pathfinding.model.*
import ext.mods.gameserver.geoengine.pathfinding.serialization.*
import ext.mods.gameserver.geoengine.pathfinding.model.Location
import ext.mods.gameserver.geoengine.pathfinding.model.World
import ext.mods.gameserver.geoengine.pathfinding.model.IndexedNode
import ext.mods.gameserver.geoengine.pathfinding.model.BlockType
import ext.mods.commons.pool.CoroutinePool
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.StringTokenizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
class L2BRAdminCommands(
    private val bridge: GeoEngineBridge,
    private val geoDataProvider: GeoIndexer.GeoDataProvider,
    private val outputPath: java.nio.file.Path = Paths.get("data/pathfinder/")
) {
    
    private val activeGenerations = ConcurrentHashMap<Pair<Int, Int>, Boolean>()
    private val serializer = L2BRSerializer()
    
    fun processCommand(command: String, admin: AdminInterface): Boolean {
        val st = StringTokenizer(command)
        if (!st.hasMoreTokens()) return false
        
        val cmd = st.nextToken().lowercase()
        
        return when (cmd) {
            "//l2br_generate" -> handleGenerate(st, admin)
            "//l2br_generate_all" -> handleGenerateAll(st, admin)
            "//l2br_info" -> handleInfo(st, admin)
            "//l2br_status" -> handleStatus(admin)
            "//l2br_reload" -> handleReload(st, admin)
            "//l2br_unload" -> handleUnload(st, admin)
            "//l2br_benchmark" -> handleBenchmark(admin)
            "//l2br_config" -> handleConfig(st, admin)
            "//l2br_help" -> handleHelp(admin)
            else -> false
        }
    }
    
    private fun handleGenerate(st: StringTokenizer, admin: AdminInterface): Boolean {
        if (st.countTokens() < 2) {
            admin.sendMessage("Uso: //l2br_generate <regionX> <regionY> [jps]")
            admin.sendMessage("Exemplo: //l2br_generate 20 22 jps")
            return true
        }
        
        val regionX = st.nextToken().toIntOrNull()
        val regionY = st.nextToken().toIntOrNull()
        val generateJps = st.hasMoreTokens() && st.nextToken().equals("jps", ignoreCase = true)
        
        if (regionX == null || regionY == null) {
            admin.sendMessage("Coordenadas inválidas")
            return true
        }
        
        if (regionX !in World.TILE_X_MIN..World.TILE_X_MAX ||
            regionY !in World.TILE_Y_MIN..World.TILE_Y_MAX) {
            admin.sendMessage("Coordenadas fora dos limites válidos (${World.TILE_X_MIN}..${World.TILE_X_MAX}, ${World.TILE_Y_MIN}..${World.TILE_Y_MAX})")
            return true
        }
        
        val key = Pair(regionX, regionY)
        if (activeGenerations.putIfAbsent(key, true) != null) {
            admin.sendMessage("Geração para [$regionX, $regionY] já está em andamento...")
            return true
        }
        
        admin.sendMessage("Iniciando geração L2BR para região §e[$regionX, $regionY]§7...")
        
        val hasL2J = geoDataProvider.hasL2JData(regionX, regionY)
        val hasConv = geoDataProvider.hasConvDatData(regionX, regionY)
        
        when {
            hasL2J && hasConv -> admin.sendMessage("Fontes detectadas: §a.l2j §7+ §a_conv.dat §7(Mesclando...)")
            hasL2J -> admin.sendMessage("Fonte: §a.l2j")
            hasConv -> admin.sendMessage("Fonte: §a_conv.dat")
            else -> {
                admin.sendMessage("Nenhuma fonte de dados encontrada para esta região!")
                activeGenerations.remove(key)
                return true
            }
        }
        
        CoroutinePool.launchBackground {
            try {
                val indexer = GeoIndexer(geoDataProvider)
                
                admin.sendMessage("[Fase 1/3] Indexando geometria...")
                val result = indexer.indexRegion(
                    regionX = regionX,
                    regionY = regionY,
                    onProgress = { phase, percent -> 
                        if (percent % 20 == 0) admin.sendMessage("  §8[$percent%] $phase")
                    }
                )
                
                val sourceMsg = when (result.sourceInfo.primarySource) {
                    GeoIndexer.DataSource.L2J_ONLY -> "§aL2J"
                    GeoIndexer.DataSource.CONV_DAT_ONLY -> "§aConvDat"
                    GeoIndexer.DataSource.MERGED -> "§aMesclado §7(L2J+ConvDat)"
                    else -> "§cDesconhecido"
                }
                admin.sendMessage("Fonte utilizada: $sourceMsg")
                
                val jumpPoints = if (generateJps) {
                    admin.sendMessage("[Fase 2/3] Calculando Jump Points...")
                    val jps = indexer.calculateJumpPoints(result.nodes) { percent ->
                        if (percent % 25 == 0) admin.sendMessage("  §8[JPS $percent%]")
                    }
                    admin.sendMessage("  Jump Points calculados: §a${jps.size}")
                    jps
                } else null
                
                admin.sendMessage("[Fase 3/3] Serializando para .l2br...")
                
                outputPath.toFile().mkdirs()
                val outputFile = outputPath.resolve("${regionX}_${regionY}.l2br")
                
                val region = ext.mods.gameserver.geoengine.pathfinding.serialization.IndexedRegion(
                    regionX = regionX,
                    regionY = regionY,
                    nodes = result.nodes.mapIndexed { idx, node -> idx to node.toSerializationNode() }.toMap(),
                    walkableCount = result.walkableCount,
                    spatialGrid = buildSpatialGrid(result.nodes),
                    connectivity = calculateConnectivity(result.nodes),
                    sourceType = when (result.sourceInfo.primarySource) {
                        GeoIndexer.DataSource.L2J_ONLY -> ext.mods.gameserver.geoengine.pathfinding.serialization.SourceType.L2J
                        GeoIndexer.DataSource.CONV_DAT_ONLY -> ext.mods.gameserver.geoengine.pathfinding.serialization.SourceType.CONV_DAT
                        else -> ext.mods.gameserver.geoengine.pathfinding.serialization.SourceType.MIXED
                    },
                    generationTimeMs = result.durationMs
                )
                
                serializer.serialize(region, outputFile)
                
                admin.sendMessage("")
                admin.sendMessage("=== GERAÇÃO CONCLUÍDA ===")
                admin.sendMessage("Região: §a[$regionX, $regionY]")
                admin.sendMessage("Total de nós: ${result.nodes.size}")
                admin.sendMessage("Nós walkable: ${result.walkableCount} §7(${((result.walkableCount.toDouble()/result.nodes.size)*100).roundToInt()}%)")
                admin.sendMessage("Multi-layer: ${result.multilayerCount}")
                admin.sendMessage("Tempo total: ${result.durationMs}ms")
                admin.sendMessage("Arquivo: ${outputFile.fileName} (${outputFile.toFile().length()/1024} KB)")
                
                if (generateJps) {
                    admin.sendMessage("JPS: Ativado (${jumpPoints?.size ?: 0} jump points)")
                }
                
                try {
                    bridge.reloadRegion(regionX, regionY)
                    admin.sendMessage("Região carregada no pathfinder automaticamente!")
                } catch (e: Exception) {
                    admin.sendMessage("Use //l2br_reload $regionX $regionY para carregar.")
                }
                
            } catch (e: Exception) {
                admin.sendMessage("[ERRO] ${e.message}")
                e.printStackTrace()
            } finally {
                activeGenerations.remove(key)
            }
        }
        
        return true
    }
    
    private fun handleGenerateAll(st: StringTokenizer, admin: AdminInterface): Boolean {
        val force = st.hasMoreTokens() && st.nextToken().equals("force", ignoreCase = true)
        
        CoroutinePool.launchBackground {
            val availableRegions = mutableListOf<Pair<Int, Int>>()

            for (x in World.TILE_X_MIN..World.TILE_X_MAX) {
                for (y in World.TILE_Y_MIN..World.TILE_Y_MAX) {
                    if (geoDataProvider.hasL2JData(x, y) || geoDataProvider.hasConvDatData(x, y)) {
                        availableRegions.add(Pair(x, y))
                    }
                }
            }

            if (availableRegions.isEmpty()) {
                admin.sendMessage("Nenhuma região encontrada para gerar!")
                return@launchBackground
            }

            admin.sendMessage("Encontradas §a${availableRegions.size} §7regiões para gerar.")
            if (!force) {
                admin.sendMessage("Use §a//l2br_generate_all force §7para iniciar.")
                return@launchBackground
            }

            var success = 0
            var failed = 0
            
            availableRegions.forEachIndexed { index, (x, y) ->
                val percent = ((index + 1) * 100 / availableRegions.size)
                admin.sendMessage("[$percent%] Processando [$x, $y] (${index+1}/${availableRegions.size})...")
                
                try {
                    val indexer = GeoIndexer(geoDataProvider)
                    val result = indexer.indexRegion(x, y)
                    
                    val region = ext.mods.gameserver.geoengine.pathfinding.serialization.IndexedRegion(
                        regionX = x,
                        regionY = y,
                        nodes = result.nodes.mapIndexed { idx, node -> idx to node.toSerializationNode() }.toMap(),
                        walkableCount = result.walkableCount,
                        spatialGrid = buildSpatialGrid(result.nodes),
                        connectivity = calculateConnectivity(result.nodes),
                        sourceType = ext.mods.gameserver.geoengine.pathfinding.serialization.SourceType.MIXED,
                        generationTimeMs = result.durationMs
                    )
                    
                    serializer.serialize(region, outputPath.resolve("${x}_${y}.l2br"))
                    success++
                } catch (e: Exception) {
                    admin.sendMessage("  Falha em [$x, $y]: ${e.message}")
                    failed++
                }
            }
            
            admin.sendMessage("=== BATCH CONCLUÍDO ===")
            admin.sendMessage("Sucesso: $success | Falhas: $failed")
        }
        
        return true
    }
    
    private fun handleInfo(st: StringTokenizer, admin: AdminInterface): Boolean {
        if (st.countTokens() < 2) {
            admin.sendMessage("Uso: //l2br_info <regionX> <regionY>")
            return true
        }
        
        val regionX = st.nextToken().toIntOrNull()
        val regionY = st.nextToken().toIntOrNull()
        
        if (regionX == null || regionY == null) {
            admin.sendMessage("Coordenadas inválidas")
            return true
        }
        
        val hasL2J = geoDataProvider.hasL2JData(regionX, regionY)
        val hasConv = geoDataProvider.hasConvDatData(regionX, regionY)
        val l2brFile = outputPath.resolve("${regionX}_${regionY}.l2br").toFile()
        val hasL2BR = l2brFile.exists()
        
        admin.sendMessage("=== Info Região [$regionX, $regionY] ===")
        admin.sendMessage("Fonte .l2j: ${if (hasL2J) "Disponível" else "Indisponível"}")
        admin.sendMessage("Fonte _conv.dat: ${if (hasConv) "Disponível" else "Indisponível"}")
        admin.sendMessage("Cache .l2br: ${if (hasL2BR) "Existe (${l2brFile.length()/1024} KB)" else "Não gerado"}")
        
        when {
            hasL2J && hasConv -> admin.sendMessage("Modo: Mesclado (ConvDat prioridade)")
            hasL2J -> admin.sendMessage("Modo: Apenas L2J")
            hasConv -> admin.sendMessage("Modo: Apenas ConvDat")
            else -> admin.sendMessage("ERRO: Nenhuma fonte disponível!")
        }
        
        return true
    }
    
    private fun handleStatus(admin: AdminInterface): Boolean {
        val metrics = bridge.getMetrics()
        
        admin.sendMessage("=== L2BR Pathfinder Status ===")
        admin.sendMessage("Regiões ativas: ${metrics["l2brRegions"] ?: 0}")
        admin.sendMessage("Gerações ativas: ${activeGenerations.size}")
        admin.sendMessage("")
        admin.sendMessage("Chamadas L2BR: ${metrics["l2brCalls"] ?: 0}")
        admin.sendMessage("Chamadas Legacy: ${metrics["legacyCalls"] ?: 0}")
        admin.sendMessage("Fallbacks: ${metrics["fallbackCalls"] ?: 0}")
        admin.sendMessage("")
        admin.sendMessage("Uso L2BR: ${metrics["l2brPercentage"] ?: 0}%")
        admin.sendMessage("Latência média L2BR: ${metrics["avgL2BRTimeUs"] ?: 0}μs")
        admin.sendMessage("Latência média Legacy: ${metrics["avgLegacyTimeUs"] ?: 0}μs")
        
        @Suppress("UNCHECKED_CAST")
        val engines = metrics["engines"] as? List<Map<String, Any>>
        if (engines != null && engines.isNotEmpty()) {
            admin.sendMessage("")
            admin.sendMessage("Detalhes por região:")
            engines.take(5).forEach { engine ->
                val region = engine["region"]
                val nodes = engine["nodes"]
                val source = engine["source"] ?: "Unknown"
                admin.sendMessage("  $region: $nodes nós ($source)")
            }
            if (engines.size > 5) {
                admin.sendMessage("  ... e ${engines.size - 5} outras")
            }
        }
        
        return true
    }
    
    private fun handleReload(st: StringTokenizer, admin: AdminInterface): Boolean {
        if (st.countTokens() < 2) {
            admin.sendMessage("Uso: //l2br_reload <regionX> <regionY>")
            return true
        }
        
        val regionX = st.nextToken().toIntOrNull()
        val regionY = st.nextToken().toIntOrNull()
        
        if (regionX == null || regionY == null) {
            admin.sendMessage("Coordenadas inválidas")
            return true
        }
        
        val filePath = outputPath.resolve("${regionX}_${regionY}.l2br")
        admin.sendMessage("Verificando arquivo: ${filePath.fileName}")
        
        if (!filePath.toFile().exists()) {
            admin.sendMessage("Arquivo não encontrado: ${filePath.toAbsolutePath()}")
            admin.sendMessage("Use //l2br_generate $regionX $regionY primeiro")
            return true
        }
        
        val fileSize = filePath.toFile().length() / 1024
        admin.sendMessage("Arquivo encontrado: ${fileSize}KB")
        
        try {
            val header = readHeaderDebug(filePath)
            admin.sendMessage("Header: ${header.nodes} nós, versão ${header.version}")
        } catch (e: Exception) {
            admin.sendMessage("Erro ao ler header: ${e.message}")
        }
        
        admin.sendMessage("Carregando no pathfinder...")
        
        try {
            bridge.reloadRegion(regionX, regionY)
            admin.sendMessage("✓ Região [$regionX, $regionY] carregada com sucesso!")
        } catch (e: Exception) {
            admin.sendMessage("✗ Falha ao carregar: ${e.message}")
            admin.sendMessage("Stack: ${e.stackTrace.take(3).joinToString()}")
        }
        
        return true
    }
    
    private fun readHeaderDebug(path: java.nio.file.Path): HeaderInfo {
        FileInputStream(path.toFile()).use { fis ->
            val header = ByteArray(64)
            fis.read(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(4)
            return HeaderInfo(
                version = buf.int,
                regionX = buf.int,
                regionY = buf.int,
                nodes = buf.int,
                walkable = buf.int
            )
        }
    }
    
    data class HeaderInfo(val version: Int, val regionX: Int, val regionY: Int, val nodes: Int, val walkable: Int)
    
    private fun handleUnload(st: StringTokenizer, admin: AdminInterface): Boolean {
        if (st.countTokens() < 2) {
            admin.sendMessage("Descarregando todas as regiões L2BR...")
            bridge.unloadAll()
            admin.sendMessage("Todas as regiões descarregadas")
            return true
        }
        
        val regionX = st.nextToken().toIntOrNull()
        val regionY = st.nextToken().toIntOrNull()
        
        if (regionX == null || regionY == null) {
            admin.sendMessage("Coordenadas inválidas")
            return true
        }
        
        admin.sendMessage("Descarregando região [$regionX, $regionY]...")
        try {
            bridge.unloadRegion(regionX, regionY)
            admin.sendMessage("Região descarregada")
        } catch (e: Exception) {
            admin.sendMessage("Erro: ${e.message}")
        }
        
        return true
    }
    
    private fun handleBenchmark(admin: AdminInterface): Boolean {
        admin.sendMessage("Executando benchmark...")
        
        CoroutinePool.launchBackground {
            val iterations = 1000
            val metrics = ext.mods.gameserver.geoengine.pathfinding.model.PerformanceMetrics()
            val random = java.util.concurrent.ThreadLocalRandom.current()
            
            repeat(iterations) { i ->
                val x1 = random.nextInt(100000, 200000)
                val y1 = random.nextInt(100000, 200000)
                val x2 = random.nextInt(100000, 200000)
                val y2 = random.nextInt(100000, 200000)
                
                val start = System.nanoTime()
                val path = bridge.findPath(x1, y1, 0, x2, y2, 0)
                val duration = System.nanoTime() - start
                
                metrics.record(duration)
                
                if (i % 100 == 0) {
                    admin.sendMessage("  Progresso: $i/$iterations")
                }
            }
            
            val p50 = metrics.getPercentile(50.0) / 1000.0
            val p99 = metrics.getPercentile(99.0) / 1000.0
            val avg = metrics.getAverage() / 1000.0
            val minVal = metrics.getMin() / 1000.0  
            val maxVal = metrics.getMax() / 1000.0  
            
            admin.sendMessage("=== Resultados do Benchmark ===")
            admin.sendMessage("Iterações: $iterations")
            admin.sendMessage("Mín: ${minVal.format(2)}μs | Máx: ${maxVal.format(2)}μs")
            admin.sendMessage("Média: ${avg.format(2)}μs")
            admin.sendMessage("P50: ${p50.format(2)}μs | P99: ${p99.format(2)}μs")
        }
        
        return true
    }
    
    private fun handleConfig(st: StringTokenizer, admin: AdminInterface): Boolean {
        if (!st.hasMoreTokens()) {
            val config = bridge.getConfig()
            admin.sendMessage("=== Configuração L2BR ===")
            admin.sendMessage("Global enabled: ${config.globalEnabled}")
            admin.sendMessage("Fallback to legacy: ${config.fallbackToLegacy}")
            admin.sendMessage("Metrics enabled: ${config.metricsEnabled}")
            admin.sendMessage("Warmup on load: ${config.warmupOnLoad}")
            admin.sendMessage("Max pathfinding time: ${config.maxPathfindingTimeMicros}μs")
            admin.sendMessage("Output path: ${outputPath.toAbsolutePath()}")
            return true
        }
        
        val key = st.nextToken().lowercase()
        val value = if (st.hasMoreTokens()) st.nextToken() else null
        
        if (value == null) {
            admin.sendMessage("Uso: //l2br_config <key> <value>")
            admin.sendMessage("Keys: global, fallback, metrics, warmup")
            return true
        }
        
        val currentConfig = bridge.getConfig()
        val newConfig = when (key) {
            "global" -> currentConfig.copy(globalEnabled = value.toBooleanStrict())
            "fallback" -> currentConfig.copy(fallbackToLegacy = value.toBooleanStrict())
            "metrics" -> currentConfig.copy(metricsEnabled = value.toBooleanStrict())
            "warmup" -> currentConfig.copy(warmupOnLoad = value.toBooleanStrict())
            else -> {
                admin.sendMessage("Configuração desconhecida: $key")
                return true
            }
        }
        
        bridge.updateConfig(newConfig)
        admin.sendMessage("Configuração '$key' atualizada para: $value")
        
        return true
    }
    
    private fun handleHelp(admin: AdminInterface): Boolean {
        admin.sendMessage("=== Comandos L2BR Pathfinder ===")
        admin.sendMessage("//l2br_generate <x> <y> [jps] - Gera .l2br (com JPS opcional)")
        admin.sendMessage("//l2br_generate_all [force] - Gera todas as regiões")
        admin.sendMessage("//l2br_info <x> <y> - Verifica fontes disponíveis")
        admin.sendMessage("//l2br_status - Status e métricas")
        admin.sendMessage("//l2br_reload <x> <y> - Recarrega região")
        admin.sendMessage("//l2br_unload [x] [y] - Descarrega (todas ou específica)")
        admin.sendMessage("//l2br_benchmark - Teste de performance")
        admin.sendMessage("//l2br_config [key] [value] - Configurações")
        admin.sendMessage("//l2br_help - Esta ajuda")
        return true
    }
    
    private fun Double.format(decimals: Int): String = String.format("%.${decimals}f", this)
    
    private fun IndexedNode.toSerializationNode(): Node {
        return Node(
            x = this.geoX,
            y = this.geoY,
            z = this.worldZ.toInt(),
            originalId = this.id,
            isWalkable = this.isWalkable,
            isCellEdge = (this.nswe.toInt() and 0x0F) != 0x0F
        )
    }
    
    private fun buildSpatialGrid(nodes: List<IndexedNode>): SpatialGrid {
        if (nodes.isEmpty()) return SpatialGrid(64, 1, 1)
        val minX = nodes.minOf { it.geoX }
        val maxX = nodes.maxOf { it.geoX }
        val minY = nodes.minOf { it.geoY }
        val maxY = nodes.maxOf { it.geoY }
        val cellSize = 64
        val width = ((maxX - minX) / cellSize) + 1
        val height = ((maxY - minY) / cellSize) + 1
        
        val grid = SpatialGrid(cellSize, width.coerceAtLeast(1), height.coerceAtLeast(1))
        nodes.forEachIndexed { index, node ->
            val cx = ((node.geoX - minX) / cellSize).coerceIn(0, width - 1)
            val cy = ((node.geoY - minY) / cellSize).coerceIn(0, height - 1)
            grid.addNode(cx, cy, index)
        }
        return grid
    }
    
    private fun calculateConnectivity(nodes: List<IndexedNode>): NodeConnectivity {
        val connections = mutableListOf<Connection>()
        val nodeMap = nodes.associateBy { Pair(it.geoX, it.geoY) }
        
        nodes.forEachIndexed { index, node ->
            if (!node.isWalkable) return@forEachIndexed
            val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            dirs.forEach { (dx, dy) ->
                val neighbor = nodeMap[Pair(node.geoX + dx, node.geoY + dy)]
                if (neighbor?.isWalkable == true) {
                    connections.add(Connection(index, neighbor.id, 16.0, EdgeType.WALK))
                }
            }
        }
        return NodeConnectivity(connections)
    }
    
    interface AdminInterface {
        fun sendMessage(message: String)
        fun getLocation(): Location
    }
    companion object {
        @JvmStatic
        fun createAdminInterface(player: ext.mods.gameserver.model.actor.Player): AdminInterface {
            return object : AdminInterface {
                override fun sendMessage(message: String) = player.sendMessage(message)
                override fun getLocation(): Location = Location(player.x, player.y, player.z)
            }
        }
    }
}