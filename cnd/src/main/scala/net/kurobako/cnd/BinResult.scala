package net.kurobako.cnd

import java.time.Duration

import better.files.File

import scala.util.{Failure, Try}

case class BinResult(elapsed: Duration, nonce: Option[Int])
object BinResult {

	val DefaultBlock: String = "COMSM0010cloud"

	def mkArgs(input: String, offset: Long, range: Long, D: Int, Ncore: Int): String = {
		s"$input $offset $range $D $Ncore"
	}

	val LocalBin: File = File("./nonce_finder")

	def parseBinOut(lines: String): Try[BinResult] = lines
		.linesIterator
		.filterNot(_.isBlank)
		.toList
		.takeRight(2)
		.map(_.trim) match {
		case elapsedMsLine :: "NO_SOLUTION" :: Nil =>
			Try(elapsedMsLine.toLong)
				.map(Duration.ofMillis(_))
				.map(BinResult(_, None))
		case elapsedMsLine :: nonceLine :: Nil     =>
			for {
				time <- Try(elapsedMsLine.toLong)
				nonce <- Try(nonceLine.toInt)
			} yield BinResult(Duration.ofMillis(time), Some(nonce))
		case bad                                   =>
			Failure(new Exception(s"No parse: `$bad`"))
	}
}
