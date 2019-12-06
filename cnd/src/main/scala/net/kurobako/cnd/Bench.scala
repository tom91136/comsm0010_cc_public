package net.kurobako.cnd

import java.time.Duration
import java.util.concurrent.Semaphore

import sys.process._
import cats.implicits._

import scala.language.postfixOps

object Bench {


	def formatRuns(n: Int, d: Int, xs: List[BinResult]): String = {
		import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
		val seconds = xs.map(_.elapsed.toMillis.toDouble / 1000.0)
		val stats   = new DescriptiveStatistics(seconds.toArray)
		f"$n, $d, ${stats.getMean}%.4f, ${stats.getStandardDeviation}%.4f"
	}

	def main(args: Array[String]): Unit = {




		val xs = for {
			d <- List(36)
			n <- (1 to 24).par
		} yield {



			val cmd = s"python .nonce.py ${
				BinResult.mkArgs(
					input = BinResult.DefaultBlock,
					offset = Cnd.IntMin,
					range = Cnd.IntRange,
					D = d, Ncore = n)
			}"
			println(cmd)
			List.fill(1)(0).traverse { _ =>
				val result = (cmd !!)
				BinResult.parseBinOut(result).toEither
			} match {
				case Left(value) => value.getMessage
				case Right(xs)   =>
					val line = formatRuns(n, d, xs)
					println(line)
					line
			}

		}

		println("====")
		println(xs.mkString("\n"))


	}


}
