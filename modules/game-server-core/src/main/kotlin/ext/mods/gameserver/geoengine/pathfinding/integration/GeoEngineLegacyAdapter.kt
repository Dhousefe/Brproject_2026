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
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.geoengine.geodata.IGeoObject
import ext.mods.gameserver.geoengine.pathfinding.model.Location
import ext.mods.gameserver.model.World
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive
import kotlin.math.abs
class GeoEngineLegacyAdapter(private val geoEngine: GeoEngine) : GeoEngineBridge.GeoEngineInterface {
    override fun findPath(
        ox: Int, oy: Int, oz: Int,
        tx: Int, ty: Int, tz: Int,
        playable: Boolean,
        debug: Any?
    ): List<Location> {
        if (World.isOutOfWorld(tx, ty)) return emptyList()
        val gox = GeoEngine.getGeoX(ox)
        val goy = GeoEngine.getGeoY(oy)
        if (!geoEngine.hasGeoPos(gox, goy)) return emptyList()
        val goz = geoEngine.getHeightNearest(gox, goy, oz).toInt()
        val gtx = GeoEngine.getGeoX(tx)
        val gty = GeoEngine.getGeoY(ty)
        if (!geoEngine.hasGeoPos(gtx, gty)) return emptyList()
        val gtz = geoEngine.getHeightNearest(gtx, gty, tz).toInt()
        if (abs(gtz - tz) > 500) return emptyList()
        val path = geoEngine.computeLegacyPathRaw(gox, goy, goz, gtx, gty, gtz, debug as? ExServerPrimitive)
        return path.map { Location(it.x, it.y, it.z) }
    }
    override fun canMove(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Boolean =
        geoEngine.canMove(ox, oy, oz, tx, ty, tz, null)
    override fun canSee(
        ox: Int, oy: Int, oz: Int, oheight: Double,
        tx: Int, ty: Int, tz: Int, theight: Double,
        ignore: Any?, debug: Any?
    ): Boolean = geoEngine.canSee(ox, oy, oz, oheight, tx, ty, tz, theight, null, debug as? ExServerPrimitive)
    override fun getHeight(worldX: Int, worldY: Int, worldZ: Int): Short =
        geoEngine.getHeight(worldX, worldY, worldZ)
    override fun getValidLocation(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int, debug: Any?): Location {
        val loc = geoEngine.getValidLocation(ox, oy, oz, tx, ty, tz, debug as? ExServerPrimitive)
        return Location(loc.x, loc.y, loc.z)
    }
    override fun addGeoObject(obj: Any) {
        (obj as? IGeoObject)?.let { geoEngine.addGeoObject(it) }
    }
    override fun removeGeoObject(obj: Any) {
        (obj as? IGeoObject)?.let { geoEngine.removeGeoObject(it) }
    }
    override fun getGeoX(worldX: Int): Int = GeoEngine.getGeoX(worldX)
    override fun getGeoY(worldY: Int): Int = GeoEngine.getGeoY(worldY)
    override fun getWorldX(geoX: Int): Int = GeoEngine.getWorldX(geoX)
    override fun getWorldY(geoY: Int): Int = GeoEngine.getWorldY(geoY)
}