# nf-vsc-kul-uhasselt: Changelog

# Version 0.1.0

Initial release.

## New features

1. Custom SLURM executor `vsc-kul-uhasselt` that picks the right partition, `--clusters`, `--account`, `--gres`, and `module load cluster/...` `beforeScript` based on each task's requested resources.
2. Five supported cluster branches selected via `process.ext.vscCluster`: `genius`, `genius_gpu`, `wice`, `wice_gpu`, `superdome`.
3. Configurable via `executor.'vsc-kul-uhasselt'.{scratchDir,account,dedicatedQueues,timeThreshold,geniusMemThreshold,wiceMemThreshold}`; defaults read from `VSC_SCRATCH`, `SLURM_ACCOUNT`, `VSC_DEDICATED_QUEUES`.
4. Task time is automatically capped to `timeThreshold` for `wice` / `wice_gpu` jobs whose resources do not match a dedicated long-run queue (preserving the prior in-config behavior).

## Notes

Replaces the custom Groovy in `conf/vsc_kul_uhasselt.config` of nf-core/configs so the institutional config is valid under Nextflow v26+ strict syntax.
