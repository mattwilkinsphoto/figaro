package com.cra.figaro.util

import scala.collection.mutable

/** Mutable storage with a stable registration hash, independent of its entries.
 *
 * Universe registration stores cache objects in a hash set. Changing a cache's
 * entries must not change the hash used to find and deregister that cache.
 * Delegation avoids subclassing the deprecated mutable HashMap implementation.
 */
private[figaro] final class RegisteredMap[K, V](registrationHash: Int)
  extends mutable.AbstractMap[K, V] with mutable.Map[K, V] {
  private val entries = mutable.HashMap.empty[K, V]
  override def hashCode(): Int = registrationHash
  override def get(key: K): Option[V] = entries.get(key)
  override def iterator: Iterator[(K, V)] = entries.iterator
  override def addOne(entry: (K, V)): this.type = {
    entries.addOne(entry)
    this
  }
  override def subtractOne(key: K): this.type = {
    entries.subtractOne(key)
    this
  }
  override def clear(): Unit = entries.clear()
}
