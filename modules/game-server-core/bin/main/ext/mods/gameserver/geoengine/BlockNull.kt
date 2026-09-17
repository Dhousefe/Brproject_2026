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
package ext.mods.gameserver.geoengine.geodata
object BlockNull : ABlock() {
    
    override fun hasGeoPos(): Boolean = false
    override fun getHeightNearest(geoX: Int, geoY: Int, worldZ: Int, ignore: IGeoObject?): Short {
        return worldZ.toShort()
    }
    override fun getNsweNearest(geoX: Int, geoY: Int, worldZ: Int, ignore: IGeoObject?): Byte {
        return GeoStructure.CELL_FLAG_ALL
    }
    override fun getIndexNearest(geoX: Int, geoY: Int, worldZ: Int, ignore: IGeoObject?): Int {
        return 0
    }
    override fun getIndexAbove(geoX: Int, geoY: Int, worldZ: Int, ignore: IGeoObject?): Int {
        return 0
    }
    override fun getIndexBelow(geoX: Int, geoY: Int, worldZ: Int, ignore: IGeoObject?): Int {
        return 0
    }
    override fun getHeight(index: Int, ignore: IGeoObject?): Short {
        return 0
    }
    override fun getNswe(index: Int, ignore: IGeoObject?): Byte {
        return GeoStructure.CELL_FLAG_ALL
    }
}