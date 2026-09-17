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
package ext.mods.gameserver.geoengine.pathfinding
import ext.mods.gameserver.geoengine.geodata.GeoStructure
class Node(
    val geoX: Int,
    val geoY: Int,
    val z: Int,
    val nswe: Byte
) : Comparable<Node> {
    var costG: Int = 0
    var costH: Int = 0
    var costF: Int = 0
    
    var parent: Node? = null
    fun setCost(parentNode: Node?, weight: Int, hCost: Int) {
        costG = weight
        if (parentNode != null) {
            costG += parentNode.costG
        }
        
        costH = hCost
        costF = costG + costH
        parent = parentNode
    }
    override fun compareTo(other: Node): Int {
        return this.costF - other.costF
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Node
        if (geoX != other.geoX) return false
        if (geoY != other.geoY) return false
        if (z != other.z) return false
        return true
    }
    override fun hashCode(): Int {
        var result = geoX
        result = 31 * result + geoY
        result = 31 * result + z
        return result
    }
    fun clean() {
        costG = 0
        costH = 0
        costF = 0
        parent = null
    }
    
    companion object {
        const val START_COST = 0
    }
}