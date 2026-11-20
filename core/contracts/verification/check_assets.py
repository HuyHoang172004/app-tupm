"""Check actual media and split declarations without an Android device."""
import hashlib
import math
import struct
import unittest
import wave
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DIST = '{http://schemas.android.com/apk/distribution}'
ANDROID = '{http://schemas.android.com/apk/res/android}'
ACTIVITIES = {'gallery': 'gallery.GalleryActivity', 'audio_guide': 'audioguide.AudioGuideActivity',
              'itinerary': 'itinerary.ItineraryActivity', 'food': 'food.FoodActivity'}

class AssetChecks(unittest.TestCase):
    def test_four_on_demand_internal_activities(self):
        for name, activity in ACTIVITIES.items():
            root = ET.parse(ROOT / 'feature' / name / 'src/main/AndroidManifest.xml').getroot()
            module = root.find(DIST + 'module')
            self.assertIsNotNone(module.find(DIST + 'delivery/' + DIST + 'on-demand'))
            declared = root.find('application/activity')
            self.assertEqual('vn.demo.liteload.feature.' + activity, declared.get(ANDROID + 'name'))
            self.assertEqual('false', declared.get(ANDROID + 'exported'))
            title = module.get(DIST + 'title').removeprefix('@string/')
            strings = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
            self.assertTrue(any(s.get('name') == title for s in strings))

    def test_all_xml_is_well_formed(self):
        for name in ACTIVITIES:
            for path in (ROOT / 'feature' / name / 'src/main').rglob('*.xml'):
                ET.parse(path)

    def test_twelve_distinct_gallery_illustrations(self):
        images = list((ROOT / 'feature/gallery/src/main/res/drawable').glob('*.xml'))
        self.assertEqual(12, len(images))
        self.assertEqual(12, len({hashlib.sha256(p.read_bytes()).digest() for p in images}))
        activity = (ROOT / 'feature/gallery/src/main/java/vn/demo/liteload/feature/gallery/GalleryActivity.kt').read_text('utf-8')
        for image in images:
            self.assertIn('R.drawable.' + image.stem, activity)

    def test_food_illustrations_have_four_distinct_payloads(self):
        images = list((ROOT / 'feature/food/src/main/res/drawable').glob('*.xml'))
        self.assertEqual(4, len(images))
        self.assertEqual(4, len({p.read_bytes() for p in images}))

    def test_audio_is_real_non_silent_decodable_pcm(self):
        clips = list((ROOT / 'feature/audio_guide/src/main/res/raw').glob('*.wav'))
        self.assertEqual(3, len(clips))
        for clip in clips:
            with wave.open(str(clip), 'rb') as source:
                self.assertEqual(1, source.getnchannels())
                self.assertEqual(2, source.getsampwidth())
                self.assertEqual(16000, source.getframerate())
                duration = source.getnframes() / source.getframerate()
                self.assertGreater(duration, 10)
                self.assertLess(duration, 60)
                payload = source.readframes(source.getnframes())
                samples = struct.unpack('<' + 'h' * (len(payload) // 2), payload)
                rms = math.sqrt(sum(x*x for x in samples) / len(samples))
                self.assertGreater(rms, 50, f'{clip} is silent')
                print(f'{clip.name}: {duration:.2f}s; {clip.stat().st_size} bytes; RMS={rms:.1f}')

    def test_audio_resources_match_transcripts(self):
        content = ET.parse(ROOT / 'feature/audio_guide/src/main/res/values/audio_guide_content.xml').getroot()
        activity = (ROOT / 'feature/audio_guide/src/main/java/vn/demo/liteload/feature/audioguide/AudioGuideActivity.kt').read_text('utf-8')
        for name in ('town', 'river', 'food'):
            self.assertIn('R.raw.audio_guide_' + name, activity)
            text = next(s.text for s in content if s.get('name') == f'audio_guide_{name}_body')
            self.assertGreater(len(text.split()), 40)

if __name__ == '__main__':
    unittest.main(verbosity=2)
