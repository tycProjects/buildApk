import 'dotenv/config';
import express from 'express';
import multer from 'multer';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const exec = promisify(execFile), app = express(), upload = multer({ dest: path.join(os.tmpdir(), 'subtitle-upload') });
const PORT = process.env.PORT || 3000;
const outputs = new Map();
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
const GROQ_MODEL = process.env.GROQ_MODEL || 'whisper-large-v3-turbo';
app.use(express.json({ limit: '2mb' }));

async function groqTranscribe(audioPath, language) {
  const form = new FormData();
  form.append('file', new Blob([await fs.readFile(audioPath)]), 'audio.wav');
  form.append('model', GROQ_MODEL); form.append('response_format', 'verbose_json');
  if (language !== 'auto') form.append('language', language);
  const r = await fetch('https://api.groq.com/openai/v1/audio/transcriptions', { method: 'POST', headers: { Authorization: `Bearer ${process.env.GROQ_API_KEY}` }, body: form });
  if (!r.ok) throw new Error(`Groq STT ${r.status}: ${await r.text()}`);
  return await r.json();
}

async function gemini(prompt, inlineData = null) {
  const parts = [{ text: prompt }]; if (inlineData) parts.push({ inlineData });
  const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${process.env.GEMINI_API_KEY}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ contents: [{ role: 'user', parts }], generationConfig: { temperature: 0.15 } }) });
  if (!r.ok) throw new Error(`Gemini ${r.status}: ${await r.text()}`);
  return (await r.json()).candidates?.[0]?.content?.parts?.map(p => p.text || '').join('') || '';
}

function srtTime(sec) { const ms = Math.max(0, Math.round(sec * 1000)), h = Math.floor(ms / 3600000), m = Math.floor(ms % 3600000 / 60000), s = Math.floor(ms % 60000 / 1000), z = ms % 1000; return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')},${String(z).padStart(3,'0')}`; }
function toSrt(segments) { return segments.map((x, i) => `${i+1}\n${srtTime(x.start)} --> ${srtTime(x.end)}\n${x.text.trim()}\n`).join('\n'); }
function parseJsonText(text) { const clean = text.replace(/^```json\s*/,'').replace(/```$/,'').trim(); return JSON.parse(clean); }

async function translateSegments(segments, source, target) {
  const prompt = `Translate each subtitle text from ${source} to ${target}. Preserve timing and return ONLY a JSON array of objects with start,end,text. Input: ${JSON.stringify(segments)}`;
  return parseJsonText(await gemini(prompt));
}

async function burnIn(videoPath, srt, outPath) {
  const srtPath = `${outPath}.srt`; await fs.writeFile(srtPath, srt, 'utf8');
  await exec('ffmpeg', ['-y','-i',videoPath,'-vf',`subtitles=${srtPath.replaceAll('\\','/').replaceAll(':','\\:')}:force_style=FontName=Arial,FontSize=22,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BorderStyle=1,Outline=2,Alignment=2`, '-c:a','copy',outPath]);
  await fs.unlink(srtPath).catch(()=>{});
}

app.post('/process', upload.single('video'), async (req, res) => {
  const work = await fs.mkdtemp(path.join(os.tmpdir(), 'subtitle-job-')), input = path.join(work, 'input.mp4'), audio = path.join(work, 'audio.wav'), out = path.join(work, 'translated.mp4');
  try {
    await fs.copyFile(req.file.path, input); const mode = req.body.mode || 'stt', source = req.body.sourceLanguage || 'auto', target = req.body.targetLanguage || 'vi'; let segments;
    if (mode === 'stt') {
      await exec('ffmpeg', ['-y','-i',input,'-vn','-ac','1','-ar','16000','-c:a','pcm_s16le',audio]);
      const data = await groqTranscribe(audio, source); segments = (data.segments || []).map(s => ({ start:s.start, end:s.end, text:s.text }));
    } else {
      const frames = path.join(work, 'frame-%04d.jpg'); await exec('ffmpeg', ['-y','-i',input,'-vf','fps=1/8,scale=1280:-1','-q:v','3',frames]);
      const names = (await fs.readdir(work)).filter(n => n.endsWith('.jpg')).sort(); const all = [];
      for (const name of names) { const b64 = (await fs.readFile(path.join(work,name))).toString('base64'); const raw = await gemini(`Read any visible subtitle text in this video frame. Return ONLY JSON array of {text}. If none, return [].`, { mimeType:'image/jpeg', data:b64 }); all.push(...parseJsonText(raw)); }
      segments = all.map((s,i) => ({ start:i*8, end:i*8+7.5, text:s.text }));
    }
    const translated = await translateSegments(segments, source, target), srt = toSrt(translated); await burnIn(input, srt, out);
    const jobId = path.basename(work); outputs.set(jobId, out); res.json({ srt, videoUrl: `/download/${jobId}`, jobId });
  } catch (e) { res.status(500).json({ error: e.message }); }
  finally { await fs.unlink(req.file.path).catch(()=>{}); }
});
app.get('/download/:jobId', (req,res) => { const file = outputs.get(req.params.jobId); if (!file) return res.status(404).send('Expired job'); res.download(file, 'translated-video.mp4'); });
app.listen(PORT, () => console.log(`Subtitle backend listening on ${PORT}`));
