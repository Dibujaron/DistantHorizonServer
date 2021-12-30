package com.dibujaron.distanthorizon.timer

import com.dibujaron.distanthorizon.DHModule
import java.util.concurrent.ConcurrentHashMap

object ScheduledTaskManager: DHModule {

    private val tasks = ConcurrentHashMap<Int, ScheduledTask>()
    val nameIndex = HashMap<String, MutableSet<ScheduledTask>>()
    private var ticks = 0
    override fun tick() {
        tasks.values.asSequence()
            .filter { it.shouldTrigger() }
            .onEach { it.onTrigger() }
            .filter { it.isComplete() }
            .forEach { cleanupCompletedTask(it) }
        ticks++
    }

    fun getTicks(): Int {
        return ticks
    }

    fun hasTasks(): Boolean {
        return tasks.isNotEmpty()
    }

    fun submitTask(task: ScheduledTask) {
        tasks[task.id] = task
        nameIndex.getOrPut(task.taskName) { mutableSetOf() }.add(task)
    }

    fun getTasksByName(name: String): Set<ScheduledTask> {
        return nameIndex[name] ?: setOf()
    }

    fun cancelTasksByName(name: String, reason: CancellationReason) {
        getTasksByName(name).forEach { cancelTask(it, reason) }
    }

    private fun cleanupCompletedTask(task: ScheduledTask) {
        tasks.remove(task.id)
        val nameSet = nameIndex[task.taskName]!!
        nameSet.remove(task)
        if (nameSet.isEmpty()) {
            nameIndex.remove(task.taskName)
        }
    }

    fun runDelayed(taskName: String, delay: Int, task: () -> Unit, onCancel: ((CancellationReason) -> Unit)? = null) {
        submitTask(DelayedTask(taskName, delay, task, onCancel))
    }

    fun cancelTask(task: ScheduledTask, reason: CancellationReason) {
        task.onCancelled(reason)
        cleanupCompletedTask(task)
    }


    private var idCounter = 0
    fun nextId(): Int {
        return idCounter++
    }
}