package io.vsc.kul.uhasselt

import groovy.transform.CompileStatic
import nextflow.util.Duration
import nextflow.util.MemoryUnit

/**
 * Pure port of the {@code determine*Queue} closures that previously lived in
 * {@code conf/vsc_kul_uhasselt.config}. Stateless and side-effect-free:
 * given task resources and a snapshot of available dedicated queues, returns
 * the partition + cluster options + module-load target.
 */
@CompileStatic
class QueueResolver {

    final Duration timeThreshold
    final MemoryUnit geniusMemThreshold
    final MemoryUnit wiceMemThreshold
    final Set<String> dedicatedQueues

    QueueResolver(
        Duration timeThreshold,
        MemoryUnit geniusMemThreshold,
        MemoryUnit wiceMemThreshold,
        Set<String> dedicatedQueues
    ) {
        this.timeThreshold = timeThreshold
        this.geniusMemThreshold = geniusMemThreshold
        this.wiceMemThreshold = wiceMemThreshold
        this.dedicatedQueues = dedicatedQueues ?: ([] as Set<String>)
    }

    /**
     * Resolve a task on a given VSC cluster.
     *
     * @param cluster one of {@code genius}, {@code genius_gpu}, {@code wice},
     *                {@code wice_gpu}, {@code superdome}
     * @param memory  requested task memory ({@code null} treated as zero)
     * @param time    requested task time ({@code null} treated as zero)
     * @param cpus    requested cpus, used for GPU count inference
     * @param accelerators explicit GPU count from {@code task.accelerator?.request} ({@code null} means infer)
     * @param account the SLURM_ACCOUNT for non-dedicated submissions
     */
    QueueDecision resolve(
        String cluster,
        MemoryUnit memory,
        Duration time,
        Integer cpus,
        Integer accelerators,
        String account
    ) {
        switch (cluster) {
            case 'genius':     return resolveGenius(memory, time, account)
            case 'genius_gpu': return resolveGeniusGpu(memory, time, cpus, accelerators, account)
            case 'wice':       return resolveWice(memory, time, account)
            case 'wice_gpu':   return resolveWiceGpu(memory, time, cpus, accelerators, account)
            case 'superdome':  return resolveSuperdome(time, account)
            default: throw new IllegalArgumentException("Unknown VSC cluster: '${cluster}'. Expected one of: genius, genius_gpu, wice, wice_gpu, superdome.")
        }
    }

    // -------- genius (CPU) --------

    QueueDecision resolveGenius(MemoryUnit memory, Duration time, String account) {
        final highMem = isHighMem(memory, geniusMemThreshold)
        final longRun = isLongRun(time)
        final hasDedicated = dedicatedQueues.contains('dedicated_big_bigmem')

        String queue
        if (highMem) {
            queue = longRun ? (hasDedicated ? 'dedicated_big_bigmem' : 'bigmem_long') : 'bigmem'
        } else {
            queue = longRun ? 'batch_long' : 'batch'
        }

        final isDedicated = queue.startsWith('dedicated_')
        final clusterOpts = isDedicated
            ? '--clusters=genius --account=lp_big_genius_cpu'
            : "--clusters=genius --account=${account}".toString()

        return new QueueDecision(
            queue: queue,
            moduleLoadQueue: firstPartition(queue),
            clusterOptions: clusterOpts,
            cappedTime: null
        )
    }

    // -------- genius (GPU) --------

    QueueDecision resolveGeniusGpu(MemoryUnit memory, Duration time, Integer cpus, Integer accelerators, String account) {
        final highMem = isHighMem(memory, geniusMemThreshold)
        final longRun = isLongRun(time)
        final hasDedicatedGpu = dedicatedQueues.contains('dedicated_rega_gpu')
        final hasAmdGpu = dedicatedQueues.contains('amd')

        String queue
        if (highMem) {
            queue = longRun ? 'gpu_v100_long' : 'gpu_v100'
        } else if (longRun) {
            queue = hasDedicatedGpu ? 'dedicated_rega_gpu'
                : (hasAmdGpu ? 'amd_long' : 'gpu_p100_long')
        } else {
            queue = hasAmdGpu ? 'amd' : 'gpu_p100'
        }

        final gpus = inferGpuCount(cpus, accelerators, 9)
        final clusterOpts = "--gres=gpu:${gpus} --clusters=genius --account=${account}".toString()

        return new QueueDecision(
            queue: queue,
            moduleLoadQueue: firstPartition(queue),
            clusterOptions: clusterOpts,
            cappedTime: null
        )
    }

    // -------- wice (CPU) --------

    QueueDecision resolveWice(MemoryUnit memory, Duration time, String account) {
        final highMem = isHighMem(memory, wiceMemThreshold)
        final longRun = isLongRun(time)
        final hasDedicated = dedicatedQueues.contains('dedicated_big_bigmem')

        String queue
        Duration cappedTime = null
        if (highMem) {
            if (longRun && hasDedicated) {
                queue = 'dedicated_big_bigmem'
            } else {
                cappedTime = capTime(time)
                queue = 'bigmem,hugemem'
            }
        } else {
            queue = longRun
                ? 'batch_long,batch_icelake_long,batch_sapphirerapids_long'
                : 'batch,batch_sapphirerapids,batch_icelake'
        }

        final isDedicated = queue.startsWith('dedicated_')
        final clusterOpts = isDedicated
            ? '--clusters=wice --account=lp_big_wice_cpu'
            : "--clusters=wice --account=${account}".toString()

        return new QueueDecision(
            queue: queue,
            moduleLoadQueue: firstPartition(queue),
            clusterOptions: clusterOpts,
            cappedTime: cappedTime
        )
    }

    // -------- wice (GPU) --------

    QueueDecision resolveWiceGpu(MemoryUnit memory, Duration time, Integer cpus, Integer accelerators, String account) {
        final highMem = isHighMem(memory, wiceMemThreshold)
        final longRun = isLongRun(time)
        final hasDedicated = highMem
            ? dedicatedQueues.contains('dedicated_big_gpu_h100')
            : dedicatedQueues.contains('dedicated_big_gpu')

        Duration cappedTime = null
        if (longRun && !hasDedicated) {
            cappedTime = capTime(time)
        }

        String queue
        if (highMem) {
            queue = (longRun && hasDedicated) ? 'dedicated_big_gpu_h100' : 'gpu_h100'
        } else {
            queue = (longRun && hasDedicated) ? 'dedicated_big_gpu' : 'gpu_a100,gpu'
        }

        final gpus = inferGpuCount(cpus, accelerators, 16)
        String clusterOpts
        if (queue == 'dedicated_big_gpu_h100') {
            clusterOpts = "--clusters=wice --account=lp_big_wice_gpu_h100 --gres=gpu:${gpus}".toString()
        } else if (queue == 'dedicated_big_gpu') {
            clusterOpts = "--clusters=wice --account=lp_big_wice_gpu --gres=gpu:${gpus}".toString()
        } else {
            clusterOpts = "--clusters=wice --account=${account} --gres=gpu:${gpus}".toString()
        }

        return new QueueDecision(
            queue: queue,
            moduleLoadQueue: firstPartition(queue),
            clusterOptions: clusterOpts,
            cappedTime: cappedTime
        )
    }

    // -------- superdome --------

    QueueDecision resolveSuperdome(Duration time, String account) {
        // superdome uses a different threshold (72h) — same value as TIME_THRESHOLD, but a
        // strict less-than-or-equal rather than greater-than-or-equal split, matching the
        // original `task.time <= 72.h ? 'superdome' : 'superdome_long'`.
        final queue = (time != null && time.toMillis() > timeThreshold.toMillis())
            ? 'superdome_long' : 'superdome'

        return new QueueDecision(
            queue: queue,
            moduleLoadQueue: 'superdome',
            clusterOptions: "--clusters=genius --account=${account}".toString(),
            cappedTime: null
        )
    }

    // -------- helpers --------

    private boolean isHighMem(MemoryUnit memory, MemoryUnit threshold) {
        if (memory == null) return false
        return memory.toBytes() >= threshold.toBytes()
    }

    private boolean isLongRun(Duration time) {
        if (time == null) return false
        return time.toMillis() >= timeThreshold.toMillis()
    }

    private Duration capTime(Duration time) {
        if (time == null || time.toMillis() <= timeThreshold.toMillis()) return time
        return timeThreshold
    }

    static int inferGpuCount(Integer cpus, Integer accelerators, int cpusPerGpu) {
        if (accelerators != null) return accelerators
        final c = cpus ?: 1
        return Math.max(1, (int) Math.floor((double) c / cpusPerGpu))
    }

    static String firstPartition(String queue) {
        final i = queue.indexOf(',')
        return i < 0 ? queue : queue.substring(0, i)
    }

}
