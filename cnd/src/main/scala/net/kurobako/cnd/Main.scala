package net.kurobako.cnd

//import java.time.Duration

import java.util.Properties
import java.util.concurrent.Executors

import awscala.ec2._
import awscala.{Resource => _, _}
import better.files.File
import cats.{ApplicativeError, Foldable}
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Clock, ExitCode, IO, IOApp}
import cats.implicits._
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.model.{AmazonEC2Exception, InstanceStateName}
import com.decodified.scalassh.SSH.Result
import com.decodified.scalassh.{HostConfig, HostKeyVerifiers, PublicKeyLogin, SSH}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import fs2._
import net.kurobako.cnd.Cnd._
import better.files._
import com.amazonaws.services.ec2.model
import scopt.{DefaultOParserSetup, OParserSetup}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import scala.util.Try


object Main extends IOApp {


	private val LocalBin  = File("./nonce_finder")
	private val RemoteBin = "/home/ec2-user/nonce_finder"


	@tailrec private def retryN[A, F[_]](n: Int, init: F[A])(g: PartialFunction[Throwable, F[A]])
										(implicit ev: ApplicativeError[F, Throwable]): F[A] = {
		if (n == 0) init
		else retryN(n - 1, ev.recoverWith(init)(g))(g)
	}


	private def runBin(pem: File, instance: Instance, retries: Int, input: String, offset: Long, range: Long, D: Int, Ncore: Int)(blockingEC: ExecutionContext)(implicit logger: Logger[IO]) = {
		val config = HostConfig(
			login = PublicKeyLogin("ec2-user", pem.pathAsString),
			hostKeyVerifier = HostKeyVerifiers.DontVerify)
		val run    = Stream.eval(contextShift.evalOn(blockingEC)(IO.suspend(IO.fromTry(SSH(instance.publicDnsName, config) { client =>
			Result(for {
				ac <- client.authenticatedClient
				_ = ac.getConnection.getKeepAlive.setKeepAliveInterval(30)
				_ <- client.upload(LocalBin.pathAsString, RemoteBin)
				_ <- client.exec(s"chmod +x $RemoteBin")
				cmdResult <- client.exec(s"$RemoteBin $input $offset $range $D $Ncore")
				result <- BinResult.parseBinOut(cmdResult.stdOutAsString())
				_ <- Try(client.close())
			} yield result)
		}))))
		retryN(retries, run) {
			case e: Exception => Stream.eval_(logger.warn(s"Unable to connect to ${instance.instanceId}: `${e.getMessage}`; retrying in 1 second...")) ++
								 Stream.sleep_(2 second) ++ run
		}
	}


	sealed trait TerminationReason
	case object TimedOut extends TerminationReason
	case object NoSolution extends TerminationReason
	case object Found extends TerminationReason


	def waitForInstances(xs: List[Instance], expected: InstanceStateName = InstanceStateName.Running)(implicit logger: Logger[IO], ec2: EC2) = {
		val ids = xs.map(_.instanceId)
		def go: Stream[IO, List[Instance]] = Stream.eval(IO {
			val instances = ec2.instances(ids).toList
			if (instances.exists(_.state.getName != expected.toString))
				Stream.sleep_(0.5 second) ++ go
			else Stream.emit(instances).covary[IO]
		}).flatten
		Stream.eval_(logger.info(s"Waiting for instance to reach state `$expected`: ${xs.map(_.instanceId)}")) ++
		go.evalTap(xs => logger.info(s"State `$expected` reached for: ${xs.map(_.instanceId)}"))
	}


	def terminateAll(xs: List[Instance])(implicit logger: Logger[IO], ec2: EC2) = {
		IO {
			println(s"Terminating instances: ${xs.map(_.instanceId)}")
		} *> logger.info(s"Terminating instances: ${xs.map(_.instanceId)}") *>
		xs.parTraverse_(i => IO(i.terminate()) >>= (r => logger.info(s"Terminated: ${i.instanceId} -> $r")))
	}

	def launchInstances(n: Int, rir: com.amazonaws.services.ec2.model.RunInstancesRequest)(implicit logger: Logger[IO], ec2: EC2) =
		Stream.bracket(
			IO {
				println(s"Launching $n instance(s) on $ec2")
			} *> logger.info(s"Launching $n instance(s) on $ec2") *>
			IO {
				import scala.collection.JavaConverters._
				ec2.runInstances(rir.withMinCount(n).withMaxCount(n)).getReservation.getInstances.asScala.map(Instance(_)).toList
			})(terminateAll(_)).flatMap(waitForInstances(_))


	def probePerformance(iam: Ec2RunConfig) = {
		implicit val ec2 = iam.mkEc2Instance
		val rir = iam.mkRunInstanceRequest


	}


	case class Ec2RunConfig(accessKey: String,
							secretKey: String,
							keyName: String,
							securityGroup: String,
							instanceTpe: com.amazonaws.services.ec2.model.InstanceType) {
		def mkEc2Instance: EC2 = EC2(BasicCredentialsProvider(accessKey, secretKey))(Region(Regions.US_EAST_2))
		def mkRunInstanceRequest: model.RunInstancesRequest = RunInstancesRequest("ami-00c03f7f7f2ec15c3")
			.withKeyName(keyName)
			.withInstanceType(instanceTpe)
			.withSecurityGroupIds(securityGroup)

	}

	def runOnTheClould(pem: File, iam: Ec2RunConfig,
					   block: String, D: Int, N: Either[Double, Int], corePerInstance: Int,
					   retries: Int = 10, timeout: Duration = (365 * 100) days) = {

		//		val InstanceTpe = com.amazonaws.services.ec2.model.InstanceType.T3aNano


		//		implicit val ec2: EC2 = EC2(BasicCredentialsProvider(iam.accessKey, iam.secretKey))(Region(Regions.US_EAST_2))
		//
		//		val rir = RunInstancesRequest("ami-00c03f7f7f2ec15c3")
		//			.withKeyName("cnd")
		//			.withInstanceType(InstanceTpe)
		//			.withSecurityGroupIds("sg-e13ca183")


		implicit val ec2: EC2 = iam.mkEc2Instance
		val rir = iam.mkRunInstanceRequest


		def probeSingle(instance: Instance, block: String, testRange: Long)(blockingEc: ExecutionContext)(implicit logger: Logger[IO]) =
			Stream.eval_(IO {
				println("Preparing to probe performance...")
			}) ++
			runBin(pem = pem,
				instance = instance,
				retries = retries,
				input = block,
				offset = IntMax,
				range = testRange,
				D = 256,
				Ncore = corePerInstance)(blockingEc) >>= {
				case BinResult(e, Some(x)) =>
					Stream.raiseError[IO](new Exception(s"Probing on ${instance.instanceId} produced an result: $x in $e"))
				case BinResult(e, None)    =>
					val extrapolated = e multipliedBy (IntRange.toFloat / testRange).toLong
					val bytes        = (block.length + 4L) * testRange
					val megaBytes    = bytes.toFloat / 1e6
					val mbPerSecond  = megaBytes / (e.toMillis / 1000.0)

					val line = s"Probed ${megaBytes}MB on ${instance.instanceId}, total runtime=$e, extrapolated = $extrapolated, throughput=${mbPerSecond}MB/s"
					Stream.eval(IO(println(line)) *> logger.info(line)).as(extrapolated)
			}


		val blockingEC = ExecutionContext.fromExecutor(
			Executors.newCachedThreadPool(
				new ThreadFactoryBuilder()
					.setNameFormat("sshj-%d")
					.setDaemon(true) // otherwise JVM will not terminate, this is OK because ioapp's EC will not be cleared before this
					.build()))


		(for {
			logger <- Stream.eval(Slf4jLogger.create[IO])

			nInstances <- N match {
				case Right(n)         => Stream[IO, Int](n)
				case Left(confidence) =>
					for {
						probeInstances <- (launchInstances(1, rir)(logger, ec2))
						probeInstance <- probeInstances match {
							case x :: Nil => Stream(x).covary[IO]
							case xs       => Stream.raiseError[IO](new Exception(s"More than one instances(${xs.map {_.instanceId}}) produced for probing"))
						}
						r <- Stream.sleep_(2 second) ++ // wait for the machine to quiet down
							 probeSingle(probeInstance, block, IntRange / 1024L)(blockingEC)(logger)
						df = Duration(r.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
					} yield {
						val n = if (timeout > df) 1 else ((df / timeout) * (confidence / 100.0)).round.toInt
						println(s"For a confidence $confidence%, with a timeout of $timeout, $n will be used for N, the instances of machine to launch")
						n
					}
			}



			solution <- Stream.eval(Ref[IO].of[Option[BinResult]](None))
			terminate <- Stream.eval(Deferred[IO, TerminationReason])
			start <- Stream.eval(Clock[IO].realTime(MILLISECONDS))
			timeoutHandler = Stream.sleep_[IO](timeout match {
				case infinite: Duration.Infinite => 365 days
				case duration: FiniteDuration    => duration
			}) ++
							 Stream.eval_(logger.info("Timed out")) ++
							 Stream.eval(terminate.complete(TimedOut))

			result <- (timeoutHandler.interruptWhen(terminate.get.void.attempt) mergeHaltBoth
					   launchInstances(nInstances, rir)(logger, ec2).flatMap { xs =>
						   Stream.emits(Cnd.part(IntRange, nInstances)
							   .zip(xs)
							   .map { case ((size, offset), x) =>
								   runBin(
									   pem = pem,
									   instance = x,
									   retries = retries,
									   input = block,
									   offset = IntMin + offset,
									   range = size,
									   D = D,
									   Ncore = corePerInstance)(blockingEC)(logger).flatMap {
									   case BinResult(e, None)          => Stream.eval(logger.info(s"Nothing for ${x.instanceId} after $e"))
									   case r@BinResult(e, Some(value)) => Stream.eval(
										   logger.info(s"Found $value on ${x.instanceId} after $e") *>
										   solution.set(Some(r)) *> // terminateAll(xs)(logger) *>
										   terminate.complete(Found))
								   }.interruptWhen(terminate.get.void.attempt)
							   }).covary[IO]
					   }.parJoinUnbounded).drain ++
					  Stream.eval_(terminate.complete(NoSolution).attempt) ++
					  Stream.eval(for {
						  end <- Clock[IO].realTime(MILLISECONDS)
						  elapsed = java.time.Duration.ofMillis(end - start)
						  _ <- logger.info("All done")
						  reason <- terminate.get
						  nonce <- solution.get
						  _ <- reason match {
							  case TimedOut           => logger.info(s"Search timed-out($timeout), result was $nonce")
							  case Found | NoSolution => logger.info(s"Search completed after ${elapsed},  result was $nonce")
						  }
					  } yield (reason, elapsed, nonce))
		} yield result).compile.lastOrError


	}

	sealed trait Constraint
	case class DirectN(n: Int) extends Constraint
	case class Indirect(confidence: Double) extends Constraint


	case class Config(block: String = "foobar",
					  difficulty: Int = 30,
					  constraint: Constraint = Indirect(100),
					  timeout: Duration = 10 minutes,
					  maxRetries: Int = 10,
					  instanceType: String = com.amazonaws.services.ec2.model.InstanceType.T3aNano.toString,
					  credential: File = File("./credentials.properties"),
					  keys: File = File("./cnd.pem"))


	def loadProps(f: File): Map[String, String] = {
		import scala.collection.JavaConverters._
		val properties = new Properties()
		properties.load(f.newInputStream)
		properties.asScala.toMap
	}

	override def run(args: List[String]): IO[ExitCode] = {

		import scopt.OParser
		val builder = OParser.builder[Config]
		val parser1 = {
			import builder._
			OParser.sequence(
				programName("cnd"),
				help("help").text("prints this usage text"),
				head("cnd", "1.0.0"),
				opt[String]("block").action((x, c) => c.copy(block = x)).text("the block to use"),
				opt[Int]("d").action((x, c) => c.copy(difficulty = x)).text("the difficulty"),

				opt[Int]("n").optional().action((x, c) => c.copy(constraint = DirectN(x))).text("the number of instances to start, this is mutually exclusive with the -c option"),
				opt[Double]("c").optional().action((x, c) => c.copy(constraint = Indirect(x))).text("the confidence level, this is mutually exclusive with the -n option"),

				opt[Duration]("timeout").action((x, c) => c.copy(timeout = x)).text("the max timeout"),

				opt[Int]("retries").action((x, c) => c.copy(maxRetries = x)).text("the block to use"),
				opt[java.io.File]("credential").action((x, c) => c.copy(credential = x.toScala)).text(
					"""AWS IAM credentials file , see https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html;
					  |The file needs to follow this format:
					  |	accessKey=<your access key>
					  |	secretKey=<your secret>
					  |	keyName=<your pem key name>
					  |	securityGroup=sg-<your-id>
					  | """.stripMargin),
				opt[java.io.File]("keys").action((x, c) => c.copy(keys = x.toScala)).text(".pem key files for the EC2 instances, see https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html"),
			)
		}


		val setup: OParserSetup = new DefaultOParserSetup {
			override def showUsageOnError = Some(true)
		}
		val result              = OParser.parse(parser1, args, Config(), setup)


		result match {
			case None    =>
				IO {
					println("Bad parameters")
				}.as(ExitCode.Error)
			case Some(c) =>

				if (!c.keys.exists) IO.raiseError(new Exception(s"Key files does not exist: ${c.keys}"))
				else if (!c.credential.exists) IO.raiseError(new Exception(s"Credential files does not exist:${c.credential}"))
				else {

					IO {
						val map = loadProps(c.credential)
						(for {
							accessKey <- map.get("accessKey")
							secretKey <- map.get("secretKey")
							keyName <- map.get("keyName")
							sg <- map.get("securityGroup")

						} yield Ec2RunConfig(accessKey, secretKey, keyName, sg, _)) match {
							case None    => IO.raiseError(new Exception(s"Credential file has missing properties: ${map}"))
							case Some(f) =>

								println(s"Using config: timeout = ${c.timeout}, block = ${c.block}, D=${c.difficulty} on ${c.instanceType}")
								println(s"Launching with ${c.constraint}")

								val config = f(com.amazonaws.services.ec2.model.InstanceType.fromValue(c.instanceType))



								runOnTheClould(c.keys,
									config,
									block = c.block,
									D = c.difficulty,
									N = c.constraint match {
										case DirectN(n)           => Right(n)
										case Indirect(confidence) => Left(confidence)
									},
									corePerInstance = 2,
									retries = c.maxRetries, timeout = c.timeout)


						}
					}.flatten.flatMap { case (reason, d, result) =>
						IO {
							println(s"Result: Stopped after ${d}(${reason}), result was ${result} ")
						}
					}.as(ExitCode.Success)


				}


		}



	}


}
