"""Compare the Java protocol parser with independently generated synthetic H.264 inputs."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PreviewPayloadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        home = os.environ.get('JAVA_HOME')
        suffix = '.exe' if os.name == 'nt' else ''
        cls.java = str(Path(home) / 'bin' / ('java' + suffix)) if home else shutil.which('java')
        javac = str(Path(home) / 'bin' / ('javac' + suffix)) if home else shutil.which('javac')
        if not cls.java or not javac:
            raise RuntimeError('JDK 17+ is required for the video parser checks')
        cls.temp = tempfile.TemporaryDirectory()
        subprocess.run([javac, '--release', '8', '-d', cls.temp.name,
                        str(ROOT / 'android/src/dev/lensfix/video/PreviewPayload.java'),
                        str(ROOT / 'tests/java/PreviewPayloadTest.java')], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def check_fixture(self, name, width, height):
        result = subprocess.run([self.java, '-cp', self.temp.name, 'PreviewPayloadTest',
                                 str(ROOT / 'tests/fixtures' / name), str(width), str(height)],
                                check=True, capture_output=True, text=True)
        self.assertIn(f'PASS {width}x{height}', result.stdout)

    def test_baseline_stream_and_envelope_boundaries(self):
        self.check_fixture('synthetic-baseline-320x240.h264', 320, 240)

    def test_high_profile_sps_with_cropped_height(self):
        self.check_fixture('synthetic-high-640x360.h264', 640, 360)
