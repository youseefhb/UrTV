#!/usr/bin/env python3
"""Run the home feature rules without Android, network, or third-party dependencies."""
import pathlib
import shutil
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
compiler = shutil.which('javac')
if not compiler:
    raise SystemExit('A JDK with javac is required for home feature checks')
with tempfile.TemporaryDirectory(prefix='urtv-home-') as tmp:
    sources = root / 'android/launcher/src/main/java/sa/urtv/home'
    subprocess.run([compiler, '-encoding', 'UTF-8', '-d', tmp,
                    str(sources / 'AppIdentity.java'), str(sources / 'FixtureTime.java'),
                    str(root / 'tests/HomeFeaturesRun.java')], check=True)
    subprocess.run(['java', '-cp', tmp, 'sa.urtv.home.HomeFeaturesRun'], check=True)
