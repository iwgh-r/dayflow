"""Extract timestamped frames for review, keeping all private media out of Git."""
import argparse
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools' / 'python'))
import imageio_ffmpeg
from PIL import Image, ImageDraw

parser = argparse.ArgumentParser()
parser.add_argument('video')
parser.add_argument('--interval', type=int, default=15)
args = parser.parse_args()
out = ROOT / 'reference_private' / 'frames'
out.mkdir(parents=True, exist_ok=True)
result = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-hide_banner', '-i', args.video, '-vf', f'fps=1/{args.interval},scale=360:-1', '-y', str(out / 'frame_%04d.jpg')], capture_output=True, text=True)
print(result.stderr[-2200:])
result.check_returncode()
frames = sorted(out.glob('frame_*.jpg'))
for offset in range(0, len(frames), 20):
    sheet = Image.new('RGB', (360*5, 270*4), '#e5e7eb')
    draw = ImageDraw.Draw(sheet)
    for i, frame in enumerate(frames[offset:offset+20]):
        pic = Image.open(frame)
        pic.thumbnail((354, 235))
        x, y = (i % 5)*360, (i//5)*270
        sheet.paste(pic, (x, y+28))
        # fps filter samples at approximately the midpoint of each interval.
        draw.text((x+8,y+5), f'frame {offset+i+1} ~{(offset+i+0.5)*args.interval:.1f}s', fill='black')
    path = out.parent / f'contact_{offset//20+1:02d}.jpg'
    sheet.save(path, quality=88)
    print(path)
