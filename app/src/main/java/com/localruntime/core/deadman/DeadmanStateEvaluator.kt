package com.localruntime.core.deadman

/**
 * Pure classification of Dead Man state already rebased to the current boot.
 *
 * Enforcement is based only on elapsed realtime. Wall-clock time is not used
 * by this evaluator.
 */
object DeadmanStateEvaluator {

    enum class Classification {
        Disabled,
        Invalid,
        Expired,
        Armed,
    }

    fun classify(
        state: DeadmanDirectBootStore.State,
        nowElapsedRealtimeMs: Long,
        currentBootCount: Int,
    ): Classification {
        if (!state.enabled) {
            return Classification.Disabled
        }

        if (!state.isReady()) {
            return Classification.Invalid
        }

        if (state.bootCount != currentBootCount) {
            return Classification.Invalid
        }

        if (
            state.isExpiredSameBoot(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    currentBootCount,
            )
        ) {
            return Classification.Expired
        }

        return Classification.Armed
    }
}
