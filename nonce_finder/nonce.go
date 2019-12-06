package main

import (
	"crypto/sha256"
	"fmt"
	"math"
	"time"
)

func findNonce(block []byte, start, delta int32, D int, result chan<- int32) {
	n := start
	newBlock := make([]byte, len(block) + 4)
	copy(newBlock, block)

	for {
		newBlock[len(block) + 0] = byte(n  >>24&0xFF)
		newBlock[len(block) + 1] = byte(n  >>16&0xFF)
		newBlock[len(block) + 2] = byte(n  >>8&0xFF)
		newBlock[len(block) + 3] = byte(n  >>0&0xFF)
		out := sha256.Sum256(newBlock)
		out = sha256.Sum256(out[:])

		bail := false
		for i := 0; i < D; i++ {
			if out[i] != 0 {
				bail = true
				break
			}
		}
		if !bail {
			result <- int32(n)
		}
		n = n + delta
	}
}

func main() {

	offset := int64(math.MinInt32)
	range_ := int64(math.MaxInt32) - int64(math.MinInt32)
	threads := 1
	D := 3
	input := []byte("COMSM0010cloud")
	fmt.Println(offset, "->", range_ , "@", threads)

	start := time.Now()
	result := make(chan int32)
	for j := 0; j < threads; j++ {
		go findNonce(input, int32(offset + int64(j)), int32(threads), D, result)
	}
	r := <-result
	fmt.Println("X=", r, " in ", time.Since(start))
}