/*
 * Anytime.scala
 * Anytime algorithms
 *
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 *
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

/*
 * Additional Updates from our community
 * 
 * Paul Philips		May 23, 2017
 */

package com.cra.figaro.algorithm

import com.cra.figaro.language._
import java.util.concurrent.{CompletableFuture, ExecutionException, LinkedBlockingQueue, TimeUnit, TimeoutException}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * Class of services implemented by the anytime algorithm.
 */
abstract class Service

/**
 * Class of responses to services.
 */
abstract class Response

/**
 * Ack Response (String)
 */
case object AckResponse extends Response

/**
 * Exception Response (String)
 */
case class ExceptionResponse(msg: String) extends Response

/**
 * Messages to or from the actor.
 */
sealed abstract class Message
/**
 * A message to the handler to handle the given service.
 */
case class Handle(service: Service) extends Message


class AnytimeAlgorithmException(s: String) extends RuntimeException(s)

/**
 * An anytime algorithm is able to improve its estimated answers over time. Anytime algorithms run in their
 * own serialized worker thread.
 *
 * An anytime algorithm must implement initialize, runStep, and handle methods. Queries are represented as
 * Services and are handled on the same worker thread as runStep, so algorithm state is never read while a
 * step is mutating it. The handle method returns a Response to the waiting caller.
 */

trait Anytime extends Algorithm {
  private sealed trait RunnerCommand
  private case class Query(service: Service, result: CompletableFuture[Response]) extends RunnerCommand
  private case class StartRunner(result: CompletableFuture[Response]) extends RunnerCommand
  private case class ResumeRunner(result: CompletableFuture[Response]) extends RunnerCommand
  private case class StopRunner(result: CompletableFuture[Response]) extends RunnerCommand
  private case class KillRunner(result: CompletableFuture[Response]) extends RunnerCommand

  @volatile private var commands: LinkedBlockingQueue[RunnerCommand] = _
  @volatile private var worker: Thread = _
  @volatile private var workerFailure: Throwable = _
  @volatile var running = false

  /**
   * default message timeout. Increase if queries to the algorithm fail due to timeout
   */
  @volatile var messageTimeout: FiniteDuration = 5.seconds

  /**
   * Run a single step of the algorithm. The algorithm must be able to provide answers after each step.
   */
  def runStep(): Unit

  /**
   * Optional function to run when the algorithm is stopped (not killed). Used in samplers to update lazy values.
   */
  def stopUpdate(): Unit = {}

  /**
   * A handler of services provided by the algorithm.
   */
  def handle(service: Service): Response


  protected[algorithm] def doStart(): Unit = {
    if (!running) {
      commands = new LinkedBlockingQueue[RunnerCommand]()
      workerFailure = null
      initialize()
      running = true
      worker = new Thread(new Runnable {
        def run(): Unit = runLoop()
      }, s"figaro-anytime-${System.identityHashCode(this)}")
      worker.setDaemon(true)
      worker.start()
    }
    submit(StartRunner)
  }

  protected[algorithm] def doStop(): Unit = {
    submit(StopRunner)
    ()
  }

  protected[algorithm] def doResume(): Unit = {
    submit(ResumeRunner)
    ()
  }

  protected[algorithm] def doKill() = {
    shutdown
  }

  /**
   * Release all resources from this anytime algorithm.
   */
  def shutdown: Unit = {
    if (running) {
      submit(KillRunner)
      val finishedWorker = worker
      if (finishedWorker != null && finishedWorker != Thread.currentThread()) {
        finishedWorker.join(messageTimeout.toMillis)
      }
      running = false
      worker = null
      commands = null
    }
  }

  /** Submit a query to the serialized anytime worker and wait for its response. */
  protected def request(service: Service): Response = submit(result => Query(service, result))

  private def submit(command: CompletableFuture[Response] => RunnerCommand): Response = {
    val result = new CompletableFuture[Response]()
    val queue = commands
    if (!running || queue == null) ExceptionResponse("Anytime algorithm has terminated")
    else {
      queue.put(command(result))
      awaitResponse(result, messageTimeout)
    }
  }

  private def runLoop(): Unit = {
    var computing = false
    var alive = true

    def runOneStep(): Unit = {
      try runStep()
      catch {
        case NonFatal(error) =>
          workerFailure = error
          computing = false
      }
    }

    while (alive) {
      val command = if (computing) commands.poll() else commands.take()
      if (command == null) runOneStep()
      else command match {
        case Query(service, result) =>
          if (workerFailure != null) result.completeExceptionally(workerFailure)
          else {
            try result.complete(handle(service))
            catch { case NonFatal(error) => result.completeExceptionally(error) }
          }
        case StartRunner(result) =>
          runOneStep()
          computing = workerFailure == null
          if (workerFailure == null) result.complete(AckResponse)
          else result.completeExceptionally(workerFailure)
        case ResumeRunner(result) =>
          computing = workerFailure == null
          if (workerFailure == null) result.complete(AckResponse)
          else result.completeExceptionally(workerFailure)
        case StopRunner(result) =>
          computing = false
          try {
            stopUpdate()
            result.complete(AckResponse)
          } catch { case NonFatal(error) => result.completeExceptionally(error) }
        case KillRunner(result) =>
          computing = false
          try {
            cleanUp()
            result.complete(AckResponse)
          } catch { case NonFatal(error) => result.completeExceptionally(error) }
          finally alive = false
      }
    }
  }

  /*
   * A helper function to query the running thread and await a response.
   * In the case that it times out, it will print a message that it timed out and return an exception response.
   * Note, on a time, it does NOT throw an exception.
   */
  protected def awaitResponse(response: CompletableFuture[Response], duration: FiniteDuration): Response = {
    try {
      response.get(duration.toNanos, TimeUnit.NANOSECONDS) match {
        case e: ExceptionResponse =>
          println(e.msg)
          e
        case result => result
      }
    } catch {
      case _: TimeoutException =>
        println("Error! Did not receive a response from algorithm thread - it may be hanging or taking an exceptionally long time to respond. Try increasing messageTimeout.")
        ExceptionResponse("Timeout")
      case error: ExecutionException =>
        error.getCause match {
          case runtime: RuntimeException => throw runtime
          case cause => throw new AnytimeAlgorithmException(cause.getMessage)
        }
      case interrupted: InterruptedException =>
        Thread.currentThread().interrupt()
        throw interrupted
    }
  }
}
