#!/usr/bin/env bash
set -euo pipefail

font="${1:?usage: generate-media.sh <path to an OFL font such as NotoSans-Regular.ttf>}"
out="$(cd "$(dirname "$0")" && pwd)/src/media/demo"
mkdir -p "$out"
rm -f "$out"/*

label() {
  printf "drawtext=fontfile=%s:text='%s':fontcolor=white:fontsize=h/12:box=1:boxcolor=black@0.6:boxborderw=12:x=(w-tw)/2:y=(h-th)/2" "$font" "$1"
}

still() {
  local name="$1" size="$2" text="$3"
  ffmpeg -loglevel error -f lavfi -i "testsrc2=size=$size:rate=1" -frames:v 1 -vf "$(label "$text")" -q:v 4 "$out/$name.jpg"
}

preview() {
  local name="$1" width="$2"
  ffmpeg -loglevel error -i "$out/$name.$3" -frames:v 1 -vf "scale=$width:-2" -q:v 6 "$out/$name-preview.jpg"
}

video() {
  local name="$1" size="$2" text="$3"
  ffmpeg -loglevel error -f lavfi -i "testsrc2=size=$size:rate=24:duration=6" \
    -f lavfi -i "sine=frequency=440:duration=6" \
    -vf "$(label "$text")" -c:v libx264 -profile:v main -pix_fmt yuv420p -crf 32 -preset slow \
    -c:a aac -b:a 48k -movflags +faststart -shortest "$out/$name.mp4"
}

still portrait-image 720x1280 "Portrait image"
preview portrait-image 300 jpg
still landscape-image 1280x720 "Landscape image"
preview landscape-image 300 jpg
still many-tags 900x900 "Many tags"
preview many-tags 300 jpg

ffmpeg -loglevel error -f lavfi -i "testsrc2=size=360x360:rate=10:duration=2" \
  -vf "$(label "GIF"),split[a][b];[a]palettegen=max_colors=64[p];[b][p]paletteuse" "$out/animated-gif.gif"
preview animated-gif 300 gif

video portrait-video 360x640 "Portrait video"
preview portrait-video 300 mp4
video landscape-video 640x360 "Landscape video"
preview landscape-video 300 mp4

for n in 1 2 3 4 5 6; do
  case $n in
    1 | 4) size=600x800 ;;
    2 | 5) size=800x600 ;;
    *) size=700x700 ;;
  esac
  still "filler-$n" "$size" "Filler $n"
  preview "filler-$n" 300 jpg
done

ls -l "$out"
