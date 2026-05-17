package io.vsc.kul.uhasselt

import nextflow.util.Duration
import nextflow.util.MemoryUnit
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Locks down the partition-selection semantics from the original
 * {@code conf/vsc_kul_uhasselt.config} closures. Any change to these
 * outcomes is a user-visible behavior change and should be deliberate.
 */
class QueueResolverTest extends Specification {

    static QueueResolver resolver(Set<String> dedicated = [] as Set) {
        new QueueResolver(
            Duration.of('72h'),
            MemoryUnit.of('175 GB'),
            MemoryUnit.of('239 GB'),
            dedicated
        )
    }

    @Unroll
    def 'genius CPU: mem=#mem, time=#time, dedicated=#dedicated => #expectedQueue'() {
        given:
            def r = resolver(dedicated as Set<String>)
        when:
            def d = r.resolve('genius', MemoryUnit.of(mem), Duration.of(time), 4, null, 'lp_test')
        then:
            d.queue == expectedQueue
        d.cappedTime == null
        d.clusterOptions == expectedClusterOpts
        where:
            mem      | time   | dedicated                  || expectedQueue            | expectedClusterOpts
        '4 GB'   | '1h'   | []                         || 'batch'                  | '--clusters=genius --account=lp_test'
        '4 GB'   | '72h'  | []                         || 'batch_long'             | '--clusters=genius --account=lp_test'
        '175 GB' | '1h'   | []                         || 'bigmem'                 | '--clusters=genius --account=lp_test'
        '200 GB' | '72h'  | []                         || 'bigmem_long'            | '--clusters=genius --account=lp_test'
        '200 GB' | '72h'  | ['dedicated_big_bigmem']   || 'dedicated_big_bigmem'   | '--clusters=genius --account=lp_big_genius_cpu'
    }

    @Unroll
    def 'genius_gpu: mem=#mem, time=#time, dedicated=#dedicated => #expectedQueue'() {
        given:
            def r = resolver(dedicated as Set<String>)
        when:
            def d = r.resolve('genius_gpu', MemoryUnit.of(mem), Duration.of(time), 9, null, 'lp_test')
        then:
            d.queue == expectedQueue
        d.clusterOptions == '--gres=gpu:1 --clusters=genius --account=lp_test'
        where:
            mem      | time   | dedicated                  || expectedQueue
        '4 GB'   | '1h'   | []                         || 'gpu_p100'
        '4 GB'   | '1h'   | ['amd']                    || 'amd'
        '4 GB'   | '72h'  | []                         || 'gpu_p100_long'
        '4 GB'   | '72h'  | ['amd']                    || 'amd_long'
        '4 GB'   | '72h'  | ['dedicated_rega_gpu']     || 'dedicated_rega_gpu'
        '200 GB' | '1h'   | []                         || 'gpu_v100'
        '200 GB' | '72h'  | []                         || 'gpu_v100_long'
    }

    @Unroll
    def 'wice CPU: mem=#mem, time=#time, dedicated=#dedicated => #expectedQueue (cap=#expectCap)'() {
        given:
            def r = resolver(dedicated as Set<String>)
        when:
            def d = r.resolve('wice', MemoryUnit.of(mem), Duration.of(time), 4, null, 'lp_test')
        then:
            d.queue == expectedQueue
        (d.cappedTime != null) == expectCap
        d.clusterOptions == expectedClusterOpts
        where:
            mem      | time   | dedicated                  || expectedQueue                                              | expectCap | expectedClusterOpts
        '4 GB'   | '1h'   | []                         || 'batch,batch_sapphirerapids,batch_icelake'                 | false     | '--clusters=wice --account=lp_test'
        '4 GB'   | '72h'  | []                         || 'batch_long,batch_icelake_long,batch_sapphirerapids_long'  | false     | '--clusters=wice --account=lp_test'
        '300 GB' | '1h'   | []                         || 'bigmem,hugemem'                                           | false     | '--clusters=wice --account=lp_test'
        '300 GB' | '120h' | []                         || 'bigmem,hugemem'                                           | true      | '--clusters=wice --account=lp_test'
        '300 GB' | '120h' | ['dedicated_big_bigmem']   || 'dedicated_big_bigmem'                                     | false     | '--clusters=wice --account=lp_big_wice_cpu'
    }

    @Unroll
    def 'wice_gpu: mem=#mem, time=#time, dedicated=#dedicated => #expectedQueue (cap=#expectCap)'() {
        given:
            def r = resolver(dedicated as Set<String>)
        when:
            def d = r.resolve('wice_gpu', MemoryUnit.of(mem), Duration.of(time), 16, null, 'lp_test')
        then:
            d.queue == expectedQueue
        (d.cappedTime != null) == expectCap
        d.clusterOptions == expectedClusterOpts
        where:
            mem      | time   | dedicated                        || expectedQueue              | expectCap | expectedClusterOpts
        '4 GB'   | '1h'   | []                               || 'gpu_a100,gpu'             | false     | '--clusters=wice --account=lp_test --gres=gpu:1'
        '4 GB'   | '120h' | []                               || 'gpu_a100,gpu'             | true      | '--clusters=wice --account=lp_test --gres=gpu:1'
        '4 GB'   | '120h' | ['dedicated_big_gpu']            || 'dedicated_big_gpu'        | false     | '--clusters=wice --account=lp_big_wice_gpu --gres=gpu:1'
        '300 GB' | '1h'   | []                               || 'gpu_h100'                 | false     | '--clusters=wice --account=lp_test --gres=gpu:1'
        '300 GB' | '120h' | []                               || 'gpu_h100'                 | true      | '--clusters=wice --account=lp_test --gres=gpu:1'
        '300 GB' | '120h' | ['dedicated_big_gpu_h100']       || 'dedicated_big_gpu_h100'   | false     | '--clusters=wice --account=lp_big_wice_gpu_h100 --gres=gpu:1'
    }

    @Unroll
    def 'superdome: time=#time => #expectedQueue'() {
        when:
            def d = resolver().resolve('superdome', MemoryUnit.of('4 GB'), Duration.of(time), 4, null, 'lp_test')
        then:
            d.queue == expectedQueue
        d.moduleLoadQueue == 'superdome'
        d.clusterOptions == '--clusters=genius --account=lp_test'
        where:
            time   || expectedQueue
        '1h'   || 'superdome'
        '72h'  || 'superdome'      // boundary: <= 72h means 'superdome' per original closure
        '73h'  || 'superdome_long'
    }

    @Unroll
    def 'moduleLoadQueue strips comma-separated partitions: #queue => #expectedFirst'() {
        expect:
            QueueResolver.firstPartition(queue) == expectedFirst
        where:
            queue                                                      || expectedFirst
        'batch'                                                    || 'batch'
        'bigmem,hugemem'                                           || 'bigmem'
        'batch_long,batch_icelake_long,batch_sapphirerapids_long'  || 'batch_long'
        'gpu_a100,gpu'                                             || 'gpu_a100'
    }

    @Unroll
    def 'inferGpuCount: cpus=#cpus, accelerators=#acc, cpusPerGpu=#cpg => #expected'() {
        expect:
            QueueResolver.inferGpuCount(cpus, acc, cpg) == expected
        where:
            cpus | acc  | cpg || expected
        null | null | 9   || 1
        8    | null | 9   || 1
        9    | null | 9   || 1
        18   | null | 9   || 2
        36   | null | 9   || 4
        16   | null | 16  || 1
        32   | null | 16  || 2
        64   | null | 16  || 4
        36   | 3    | 9   || 3      // explicit accelerator wins
        8    | 2    | 9   || 2
    }

    def 'wice CPU caps task time when high-mem long-running without a dedicated queue'() {
        when:
            def d = resolver().resolve('wice', MemoryUnit.of('300 GB'), Duration.of('120h'), 4, null, 'lp_test')
        then:
            d.cappedTime == Duration.of('72h')
    }

    def 'wice_gpu caps task time when no matching dedicated queue is available'() {
        when:
            def d = resolver().resolve('wice_gpu', MemoryUnit.of('4 GB'), Duration.of('120h'), 16, null, 'lp_test')
        then:
            d.cappedTime == Duration.of('72h')
    }

    def 'unknown cluster name throws'() {
        when:
            resolver().resolve('not_a_real_cluster', MemoryUnit.of('4 GB'), Duration.of('1h'), 4, null, 'lp_test')
        then:
            thrown(IllegalArgumentException)
    }

}
