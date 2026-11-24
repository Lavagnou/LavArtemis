// Exports one frame of LavApollo's synthetic picture as raw BGRA: export_frame <w> <h> <frame> <out.raw>
// Built by test_frames.py with the msys64 g++ (header-only: benchmark_scene_formula.h).
#include <cstdio>
#include <cstdlib>
#include <vector>

#include "benchmark_scene_formula.h"

int main(int argc, char **argv) {
  if (argc < 5) {
    return 2;
  }
  int w = std::atoi(argv[1]);
  int h = std::atoi(argv[2]);
  unsigned long long frame = std::strtoull(argv[3], nullptr, 10);
  std::vector<std::uint8_t> buf(static_cast<size_t>(w) * h * 4);
  benchmark_scene::render_bgra(buf.data(), w, h, w * 4, frame);
  FILE *f = std::fopen(argv[4], "wb");
  if (!f) {
    return 3;
  }
  std::fwrite(buf.data(), 1, buf.size(), f);
  std::fclose(f);
  return 0;
}
