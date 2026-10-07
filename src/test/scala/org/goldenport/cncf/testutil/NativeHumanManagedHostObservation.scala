package org.goldenport.cncf.testutil

import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap

/** Test-only observation of the real host. No production capability or access
  * to a provider/connection/claim is exposed to the application.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
object NativeHumanManagedHostObservation {
  def counts(host: SubsystemEventStoreBootstrap): (Int, Int) =
    (host.subsystem.systemNode.managedDataStoreCount, host.subsystem.systemNode.activeLeaseCount)
}
