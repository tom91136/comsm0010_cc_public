#include <iostream>
#include <atomic>
#include <chrono>
#include <thread>
#include <cmath>
#include <vector>
#include <optional>
#include <numeric>
#include <iomanip>
#include "sha256.h"

typedef unsigned char byte;
typedef int32_t nonce_type;
using namespace std::chrono;

inline bool isGoldenNonce(std::vector<byte> &block, const nonce_type nonce, const size_t D) {
	constexpr size_t BITS_PER_BYTE = sizeof(byte) * 8;
	block[block.size() - 4 + 0] = nonce >> 24 & 0xFF;
	block[block.size() - 4 + 1] = nonce >> 16 & 0xFF;
	block[block.size() - 4 + 2] = nonce >> 8 & 0xFF;
	block[block.size() - 4 + 3] = nonce >> 0 & 0xFF;
	std::array<byte, SHA256::HashBytes> out{};
	SHA256 sha256;
	sha256.add(block.data(), block.size());
	sha256.getHash(out.data());
	sha256.reset();
	sha256.add(out.data(), out.size());
	sha256.getHash(out.data());
	for (size_t i = 0; i < D; ++i) {
		if (out[i / BITS_PER_BYTE] & (1 << (7 - (i % BITS_PER_BYTE)))) return false;
	}
	return true;
}


std::vector<int64_t> part(int64_t num, size_t div) {
	std::vector<int64_t> bucket(div);
	int64_t remainder = num % div;
	int64_t value = num / div;
	for (size_t i = 0; i < bucket.size(); i++) {
		bucket[i] = value + (static_cast<int64_t>(i) < remainder ? 1 : 0);
	}
	return bucket;
}

// https://bitcoin.stackexchange.com/questions/1781/nonce-size-will-it-always-be-big-enough
int main(int argc, char *argv[]) {

	// 0 - string
	// 0 - offset
	// 0 - range
	// 0 - D
	// 0 - N ?

	if (argc - 1 != 4 && argc - 1 != 5) {
		std::cerr << argv[0]
		          << " args: <input:string> <offset:int64> <range:int64> <d:1~256> <N?:size_t>"
		          << std::endl;
		return EXIT_FAILURE;
	} else {
		const std::string input = std::string(argv[1]);
		const int64_t offset = std::stol(std::string(argv[2]));
		const int64_t range = std::stol(std::string(argv[3]));
		const size_t D = std::stol(std::string(argv[4]));

		if (D < 1 || D > 256) {
			std::cerr << "D( " << D << ") out of range(1 ~ 256)" << std::endl;
			return EXIT_FAILURE;
		}

		const size_t threads =
				argc < 5 ? std::thread::hardware_concurrency() : std::stoi(std::string(argv[5]));
		std::cout << input
		          << ":[" << offset << "->" << range << "]@"
		          << D << "@" << threads << "\n";
		steady_clock::time_point begin = steady_clock::now();
		std::atomic_int64_t completed(0);
		std::atomic<std::optional<nonce_type >> solution(std::nullopt);

		auto monitor = std::thread([range, &completed, &solution] {
			int64_t i = 1;
			while (!solution.load().has_value()) {
				int64_t done = completed;
				if (done >= range) break;
				std::this_thread::sleep_for(100ms);
				if (i % 10 == 0) {
					std::cout << std::setprecision(3)
					          << (static_cast<float>(done) / range) * 100.f
					          << "%...";
				}
				if (i % 100 != 0) std::cout.flush();
				else std::cout << std::endl;
				i++;
			}
		});

		std::vector<int64_t> sizes = part(range, threads);
		std::vector<int64_t> offsets(sizes.size());
		std::exclusive_scan(sizes.begin(), sizes.end(), offsets.begin(), static_cast<int64_t >(0));

		std::vector<std::thread> workers;
		for (size_t n = 0; n < threads; ++n) {
			const int64_t start = offset + offsets[n];
			const int64_t end = start + sizes[n];
			std::cout << "[" << n << "]" << start << "~" << end << std::endl;
			workers.emplace_back(
					std::thread([input, D, start, end, &solution, &completed] {
						std::vector<byte> bytes(input.size() + 4);
						std::copy(input.begin(), input.end(), bytes.begin());
						int64_t current = start;
						while (!solution.load().has_value() && current < end) {
							completed++;
							if (!isGoldenNonce(bytes, current, D)) current++;
							else {
								solution = current;
								break;
							}
						}
					}));
		}
		for (auto &t : workers) t.join();
		steady_clock::time_point end = steady_clock::now();

		monitor.join();
		std::cout << "\n" << std::endl;

		const auto out = solution.load();
		long elapsed = duration_cast<milliseconds>(end - begin).count();
		std::cout << elapsed << std::endl;
		if (out) std::cout << out.value() << std::endl;
		else std::cout << "NO_SOLUTION" << std::endl;
		return EXIT_SUCCESS;
	}
}