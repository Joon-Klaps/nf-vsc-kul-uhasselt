package io.vsc.kul.uhasselt

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

@Slf4j
@CompileStatic
class VscKulUhasseltPlugin extends BasePlugin {

    VscKulUhasseltPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        log.info "nf-vsc-kul-uhasselt plugin started"
    }
}
