package net.kurobako.cnd

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets



object Cnd {

	type Block = Array[Byte]

	final def isGoldenNonce(input: String, nonce: Int, D: Int): Boolean = {
		val block = input.getBytes(StandardCharsets.UTF_8)
		import java.security.MessageDigest
		val SHA256: MessageDigest = MessageDigest.getInstance("SHA-256")
		val buffer                = ByteBuffer.allocate(block.length + 4).put(block).putInt(nonce)
		val bytes                 = SHA256.digest(SHA256.digest(buffer.array))


		var idx = 1
		while (idx <= D && (bytes(idx / 8) & (1 << (7 - (idx % 8)))) == 0) idx += 1
		idx == D
	}

	def part(range: Long, div: Int): Vector[(Long, Long)] = {
		val remainder = range % div
		val value     = range / div
		val sizes     = Vector.tabulate(div)(i => value + (if (i < remainder) 1L else 0L))
		val offsets   = sizes.dropRight(1).scan(0L)(_ + _)
		sizes zip offsets
	}

	val IntMin  : Long = Int.MinValue.toLong
	val IntMax  : Long = Int.MaxValue.toLong
	val IntRange: Long = IntMax - IntMin

}
