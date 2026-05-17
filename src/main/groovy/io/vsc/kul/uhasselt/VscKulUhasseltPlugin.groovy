package io.vsc.kul.uhasselt

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * Entry point for the nf-vsc-kul-uhasselt plugin. The plugin contributes a
 * single extension point: the {@link VscKulUhasseltExecutor} custom SLURM
 * executor for the VSC KU Leuven / UHasselt Tier 2 HPC.
 *
 * @author Joon Klaps <joon.klaps@kuleuven.be>
 */
@Slf4j
@CompileStatic
class VscKulUhasseltPlugin extends BasePlugin {

    VscKulUhasseltPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        log.info 'nf-vsc-kul-uhasselt plugin started'
    }

}
