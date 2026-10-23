#!/usr/bin/env python3
"""Copy supported photos from a local folder into the launcher's bundled slideshow."""
import argparse
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path, help='Local photo folder; files directly inside it are imported')
    args = parser.parse_args()
    source = args.folder.expanduser()
    if not source.is_dir():
        parser.error(f'Photo folder is not accessible on this computer: {source}')
    destination = Path(__file__).resolve().parents[1] / 'android/launcher/src/main/assets/slideshow'
    if source.resolve() == destination.resolve():
        parser.error('Source is already the slideshow folder')
    photos = sorted(p for p in source.iterdir() if p.is_file() and not p.name.startswith('.')
                    and p.suffix.lower() in {'.jpg', '.jpeg', '.png', '.webp'})
    if not photos:
        parser.error('No JPG/JPEG/PNG/WebP images found directly inside the folder; convert HEIC photos first')
    destination.mkdir(parents=True, exist_ok=True)
    for photo in photos:
        shutil.copy2(photo, destination / photo.name)
    print(f'Copied {len(photos)} photos to {destination}')
    print('Existing files with the same names were replaced; other existing slideshow photos were kept.')
    print('Rebuild the APK to include the images. They are bundled offline, not used as wallpapers.')


if __name__ == '__main__':
    main()
