package io.vsc.kul.uhasselt

import groovy.transform.CompileStatic
import groovy.transform.ToString
import nextflow.util.Duration

/**
 * Resolved SLURM submission parameters for a single task.
 *
 * {@code queue} is the partition string passed to {@code -p} (may be a
 * comma-separated list of partitions, e.g. {@code 'bigmem,hugemem'}).
 *
 * {@code moduleLoadQueue} is the first partition in {@code queue}; this is what
 * goes into {@code module load cluster/<cluster>/<moduleLoadQueue>}.
 *
 * {@code cappedTime} is non-null only when the resolver had to cap a long-running
 * task to the time threshold because no dedicated queue was available.
 */
@CompileStatic
@ToString(includeNames = true)
class QueueDecision {
    String queue
    String moduleLoadQueue
    String clusterOptions
    Duration cappedTime
}
