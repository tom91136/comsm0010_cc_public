from __future__ import print_function


from multiprocessing import Pool, Process, Value
import hashlib
import argparse
import sys
import binascii
import struct
import time
from ctypes import c_bool, c_int64

BITS_PER_BYTE = 8

try:
    range = xrange
except NameError:
    pass


def part(num, div):
    bucket = [None] * div
    remainder = num % div
    value = int(num / div)
    for i in range(0, div):
        bucket[i] = value + (1 if i < remainder else 0)
    return bucket


def is_nonce(block, d):
    hash = hashlib.sha256(
        hashlib.sha256(block).digest()
    ).digest()
    arr = bytearray(hash)
    for i in range(0, d):
        if (arr[i / BITS_PER_BYTE] & (1 << (7 - (i % BITS_PER_BYTE)))):
            return False
    return True


def worker(start, end, block, d, id, terminate, completed, result):
    print("[%d]%d~%d" % (id, start, end))
    done = 0
    for i in range(start, end):
        if(terminate.value):
            break

        if is_nonce(block + struct.pack(">i", i), d):
            terminate.value = True
            result.value = i
            break
        done += 1
        if(i % 10000 == 0): 
            # python's lock is very very slow for some reason, batch up changes for later
            with completed.get_lock():
                completed.value += done
            done = 0


def monitor(range_, completed, terminate):
    i = 1
    while(not terminate.value):
        time.sleep(0.1)
        if(i % 10 == 0):
            print("{:.2f}%...".format(
                (float(completed.value) / range_) * 100.0), end='')
        if (i % 100 != 0):
            sys.stdout.flush()
        else:
            print("")
        i += 1


def find(block, offset, range_, d, N):

    terminate = Value(c_bool, False)
    completed = Value('i', 0)
    result = Value(c_int64, 0)

    sizes = part(range_, N)
    offsets = [sum(sizes[:i]) for i, _ in enumerate(sizes)]

    ps = [Process(target=monitor, args=(range_, completed, terminate))]
    for n in range(0, N):
        start = offset + offsets[n]
        end = start + sizes[n]
        ps.append(Process(target=worker,
                          args=(start, end, block, d, n, terminate, completed, result)))

    for p in ps:
        p.start()

    for p in ps:
        p.join()

    return result.value


parser = argparse.ArgumentParser(description="Find nonces")
parser.add_argument("block", type=str,   help="<input:string>")
parser.add_argument("offset", type=int,   help="<offset:int64>")
parser.add_argument("range", type=int,   help="<range:int64> ")
parser.add_argument("d", type=int,   help="<d:1~256>")
parser.add_argument("N", type=int,   help="<N?:size_t>")

args = parser.parse_args()

if args.d < 1 or args.d > 256:
    print("D(%d) out of range(1 ~ 256)" % args.d)
    sys.exit(-1)


if __name__ == '__main__':

    print(args)

    start = time.time()

    result = find(args.block, args.offset, args.range, args.d, args.N)
    print("")

    end = time.time()

    print(str(int((end - start) * 1000.0)))
    print(str(result))
