# nf-vsc-kul-uhasselt

A Nextflow executor plugin for the VSC KU Leuven / UHasselt Tier-2 HPC. It wraps the standard SLURM executor and adds partition / `--clusters` / `--account` / `--gres` selection logic based on each task's resource request.

The plugin exists so that [`conf/vsc_kul_uhasselt.config`](../../conf/vsc_kul_uhasselt.config) can be free of custom Groovy and remain valid under Nextflow's v26+ strict config syntax.

## Usage

In your Nextflow config:

```groovy
plugins { id 'nf-vsc-kul-uhasselt@0.1.0' }

process {
    executor = 'vsc-kul-uhasselt'
}

profiles {
    genius     { process { ext.vscCluster = 'genius'     } }
    genius_gpu { process { ext.vscCluster = 'genius_gpu' } }
    wice       { process { ext.vscCluster = 'wice'       } }
    wice_gpu   { process { ext.vscCluster = 'wice_gpu'   } }
    superdome  { process { ext.vscCluster = 'superdome'  } }
}
```

The executor reads three environment variables at startup, each overridable via config:

| Env var                | Config key                                    | Default    |
| ---------------------- | --------------------------------------------- | ---------- |
| `VSC_SCRATCH`          | `executor.'vsc-kul-uhasselt'.scratchDir`      | `/tmp`     |
| `SLURM_ACCOUNT`        | `executor.'vsc-kul-uhasselt'.account`         | (unset)    |
| `VSC_DEDICATED_QUEUES` | `executor.'vsc-kul-uhasselt'.dedicatedQueues` | `` (empty) |

Queue selection thresholds are also tunable:

| Key                                              | Default    |
| ------------------------------------------------ | ---------- |
| `executor.'vsc-kul-uhasselt'.timeThreshold`      | `'72h'`    |
| `executor.'vsc-kul-uhasselt'.geniusMemThreshold` | `'175 GB'` |
| `executor.'vsc-kul-uhasselt'.wiceMemThreshold`   | `'239 GB'` |

## Build & test

```bash
make test       # unit tests
make assemble   # build plugin zip into build/distributions
make install    # install into ~/.nextflow/plugins for local use
```

## Bootstrapping the Gradle wrapper

This plugin does not commit the Gradle wrapper. To bootstrap it once:

```bash
gradle wrapper --gradle-version 8.10
```

## Handoff

This plugin is intended to eventually live under a `vsc-kul-uhasselt` GitHub org maintained by the HPC team. See `MAINTAINERS.md` for the release process once that move happens.
