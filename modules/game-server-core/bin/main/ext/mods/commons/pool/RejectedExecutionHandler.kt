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
package ext.mods.commons.pool
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.logging.Logger
class RejectedExecutionHandlerOptimized : RejectedExecutionHandler {
    
    private val LOGGER: Logger = Logger.getLogger(RejectedExecutionHandlerOptimized::class.java.name)
    
    override fun rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor) {
        if (executor.isShutdown) {
            return
        }
        
        val currentThread = Thread.currentThread()
        val threadPriority = currentThread.priority
        
        when {
            threadPriority > Thread.NORM_PRIORITY -> {
                Thread(runnable, "Rejected-Task-${System.nanoTime()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY
                }.start()
                
                LOGGER.fine("Tarefa rejeitada executada em nova thread (thread crítica: prioridade $threadPriority)")
            }
            
            threadPriority == Thread.NORM_PRIORITY -> {
                try {
                    if (isVirtualThreadsAvailable()) {
                        Thread.ofVirtual().name("Rejected-Virtual-${System.nanoTime()}").start(runnable)
                        LOGGER.fine("Tarefa rejeitada executada em Virtual Thread (Java 21)")
                    } else {
                        runnable.run()
                        LOGGER.fine("Tarefa rejeitada executada na thread atual (Virtual Threads não disponíveis)")
                    }
                } catch (e: Exception) {
                    runnable.run()
                    LOGGER.fine("Fallback final: tarefa executada diretamente")
                }
            }
            
            else -> {
                runnable.run()
                LOGGER.fine("Tarefa rejeitada executada diretamente (thread de baixa prioridade: $threadPriority)")
            }
        }
    }
    
    private fun isVirtualThreadsAvailable(): Boolean {
        return try {
            Thread::class.java.getMethod("ofVirtual")
            true
        } catch (e: Exception) {
            false
        }
    }
}