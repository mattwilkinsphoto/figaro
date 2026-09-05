/*
 * Reflection.scala
 * Supports creating Figaro elements from names in compilers
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2013
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.language

import scala.util.control.NonFatal

/**
 * Figaro's reflection allows you to create a Figaro element by providing the name of the element class as a string and its arguments as elements.
 * This can be useful, e.g., for compilers.
 * In order to be able to create an instance of a particular element class, the class must implement the Creatable trait.
 */

trait Creatable {
  /** The type over which the element is defined. */ 
  type ResultType
  
  /** Create an element of this type with the given arguments. */
  def create(args: List[Element[?]]): Element[ResultType]
}

object Create {
  /** Create an element with the given class name and inputs. The class name must name a creatable element class. */
  def apply[T](className: String, inputs: Element[?]*): Element[T] = {
    // Scala 2 and Scala 3 singleton objects share the JVM MODULE$ convention.
    // Invoke the Creatable contract directly; Scala 2 runtime mirrors cannot
    // inspect Scala 3 TASTy metadata.
    val moduleName = className.stripSuffix("$") + "$"
    val module = try Class.forName(moduleName) catch {
      case _: ClassNotFoundException =>
        Class.forName(className) // Preserve ClassNotFoundException for unknown names.
        throw new IllegalArgumentException("Attempt to use non-reflectable external distribution")
    }
    val obj = module.getField("MODULE$").get(null) match {
      case creatable: Creatable => creatable
      case _ => throw new IllegalArgumentException("Attempt to use non-reflectable external distribution")
    }
    try {
      obj.create(inputs.toList).asInstanceOf[Element[T]]
    } catch {
      case NonFatal(cause) => throw new RuntimeException("Wrong argument types for external distribution", cause)
    }
  }
}
