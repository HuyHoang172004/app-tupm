# Demo content and asset provenance

Locality: Hội An. The C demo content uses one consistent locality. All feature prose is
original paraphrased demo copy, not quoted travel articles. No prices, opening
hours, measured travel times or current commercial recommendations are asserted.
Itinerary durations/routes are clearly labelled author-created sample plans.

Factual background checked against Vietnam's national tourism website:

- https://vietnam.travel/things-to-do/the-best-ways-to-explore-the-ancient-town-of-hoi-an/
- https://vietnam.travel/things-to-do/explore-food-hoi-an/
- https://vietnam.travel/things-to-do/beyond-pho-5-awesome-vietnamese-noodles/
- https://vietnam.travel/things-to-do/top-things-to-do-hoi-an/

No images/audio from those websites were downloaded or bundled. Gallery contains
12 distinct Android vector illustrations drawn from original geometric paths;
Food contains four original dish illustrations. Reproduce using
`python core/contracts/verification/generate_illustrations.py`. These are labelled
illustrations, not actual photographs or precise architectural drawings. No
external artwork, icon pack, stock-photo license or generated raster image is used.

Audio contains three original English transcripts in audio_guide_content.xml.
Generate-Narration.ps1 uses the existing Windows Microsoft Zira Desktop en-US
voice to synthesize those texts offline. Files are genuine RIFF/WAV PCM signed
16-bit mono 16 kHz, approximately 20–21 seconds each. There is no copied music or
third-party narrated recording. Generation uses the licensed Windows speech
engine on this machine; no voice model is redistributed. Content provenance is
recorded here; no assertion about rights to third-party source articles is made.

PCM parsing, non-silence checks and full ffmpeg decoding are automated checks.
They do not prove that Android MediaPlayer playback or audio focus works on a
device; device playback and human listening remain separate acceptance tests.

Feature-specific content stays in feature/*/src/main/res. The shared navigation
shell in base contains no feature media or tourism data. Each feature references
its own resources directly so meaningful content is retained by resource shrinking.
