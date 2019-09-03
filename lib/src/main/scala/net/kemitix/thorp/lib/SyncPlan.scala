package net.kemitix.thorp.lib

import net.kemitix.thorp.domain.{Action, SyncTotals}

final case class SyncPlan private (
    actions: LazyList[Action],
    syncTotals: SyncTotals
)

object SyncPlan {
  val empty: SyncPlan = SyncPlan(LazyList.empty, SyncTotals.empty)
  def create(actions: LazyList[Action], syncTotals: SyncTotals): SyncPlan =
    SyncPlan(actions, syncTotals)
}
