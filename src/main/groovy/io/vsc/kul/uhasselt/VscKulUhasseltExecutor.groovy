package io.vsc.kul.uhasselt

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.BashWrapperBuilder
import nextflow.executor.SlurmExecutor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.MemoryUnit
import nextflow.util.ServiceName

/**
 * Custom SLURM executor for VSC KU Leuven / UHasselt (Tier 2).
 *
 * Picks the right partition, --clusters / --account, --gres, and the
 * {@code module load cluster/...} {@code beforeScript} based on the task's
 * requested resources and an env-derived snapshot of available dedicated
 * queues. All the Groovy that used to live in
 * {@code conf/vsc_kul_uhasselt.config} now lives here.
 *
 * The cluster branch ({@code genius} / {@code genius_gpu} / {@code wice} /
 * {@code wice_gpu} / {@code superdome}) is selected by setting
 * {@code process.ext.vscCluster} from a profile.
 */
@Slf4j
@ServiceName('vsc-kul-uhasselt')
@CompileStatic
class VscKulUhasseltExecutor extends SlurmExecutor {

    private QueueResolver resolver
    private String account

    @Override
    void register() {
        super.register()

        final scratchDir       = configValue('scratchDir',       System.getenv('VSC_SCRATCH') ?: '/tmp')
        final accountFromEnv   = System.getenv('SLURM_ACCOUNT')
        this.account           = configValue('account',          accountFromEnv) as String

        final dedicatedString  = configValue('dedicatedQueues',  System.getenv('VSC_DEDICATED_QUEUES') ?: '') as String
        final dedicated        = parseDedicatedQueues(dedicatedString)

        final timeThreshold       = parseDuration(configValue('timeThreshold',      '72h'))
        final geniusMemThreshold  = parseMemory(configValue('geniusMemThreshold', '175 GB'))
        final wiceMemThreshold    = parseMemory(configValue('wiceMemThreshold',   '239 GB'))

        this.resolver = new QueueResolver(timeThreshold, geniusMemThreshold, wiceMemThreshold, dedicated)

        log.debug(
            "[vsc-kul-uhasselt] scratchDir='${scratchDir}' account='${this.account}' " +
            "dedicatedQueues=${dedicated} timeThreshold=${timeThreshold} " +
            "geniusMemThreshold=${geniusMemThreshold} wiceMemThreshold=${wiceMemThreshold}"
        )

        if (!this.account) {
            log.warn(
                '[vsc-kul-uhasselt] No SLURM account configured. ' +
                "Set the SLURM_ACCOUNT env var or executor.'vsc-kul-uhasselt'.account in your config."
            )
        }
    }

    @Override
    protected BashWrapperBuilder createBashWrapperBuilder(TaskRun task) {
        applyResolution(task)
        return super.createBashWrapperBuilder(task)
    }

    private void applyResolution(TaskRun task) {
        if (resolver == null) {
            // register() wasn't called for some reason — bail and let the base
            // executor produce a vanilla SLURM submission.
            return
        }

        final ext = task.config.ext as Map
        final cluster = ext?.get('vscCluster') as String
        if (!cluster) {
            // No vsc-specific branch requested for this process; leave task.config alone.
            return
        }

        final memory       = task.config.getMemory()
        final time         = task.config.getTime()
        final cpus         = task.config.getCpus()
        final accelerator  = task.config.getAccelerator()
        final accelerators = accelerator?.request != null ? accelerator.request as Integer : null

        final decision = resolver.resolve(cluster, memory, time, cpus, accelerators, account)

        task.config.put('queue',          decision.queue)
        task.config.put('clusterOptions', decision.clusterOptions)
        final beforeScript = "module load cluster/${moduleClusterFor(cluster)}/${decision.moduleLoadQueue}"
        task.config.put('beforeScript', beforeScript.toString())

        if (decision.cappedTime != null) {
            log.warn(
                "[vsc-kul-uhasselt] Capping requested time ${time} to ${decision.cappedTime} " +
                "for task '${task.name}' (no dedicated long-run queue available on '${cluster}')."
            )
            task.config.put('time', decision.cappedTime)
        }
    }

    private static String moduleClusterFor(String cluster) {
        switch (cluster) {
            case 'genius':
            case 'genius_gpu':
            case 'superdome':
                return 'genius'
            case 'wice':
            case 'wice_gpu':
                return 'wice'
            default:
                throw new IllegalArgumentException("Unknown VSC cluster: '${cluster}'")
        }
    }

    private Object configValue(String key, Object defaultVal) {
        final v = session?.getExecConfigProp(name, key, null)
        return v != null ? v : defaultVal
    }

    private static Set<String> parseDedicatedQueues(String csv) {
        if (!csv) {
            return [] as Set<String>
        }
        return (csv.split(',').collect { it.trim() }.findAll { it } as Set<String>)
    }

    private static Duration parseDuration(Object v) {
        if (v instanceof Duration) {
            return (Duration) v
        }
        return Duration.of(v.toString())
    }

    private static MemoryUnit parseMemory(Object v) {
        if (v instanceof MemoryUnit) {
            return (MemoryUnit) v
        }
        return MemoryUnit.of(v.toString())
    }

}
