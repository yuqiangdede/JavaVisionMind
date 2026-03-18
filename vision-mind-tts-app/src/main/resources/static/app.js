const voiceEl = document.getElementById('voice');
const textEl = document.getElementById('text');
const speedEl = document.getElementById('speed');
const statusEl = document.getElementById('status');
const audioEl = document.getElementById('audio');
const synthesizeBtn = document.getElementById('synthesize');
const refreshBtn = document.getElementById('refresh');

let objectUrl = null;
const apiBase = `${window.location.pathname.startsWith('/vision-mind-tts') ? '/vision-mind-tts' : ''}/api/v1/tts`;

function setStatus(message) {
    statusEl.textContent = message;
}

async function loadVoices() {
    setStatus('Loading voices...');
    voiceEl.innerHTML = '';

    const response = await fetch(`${apiBase}/voices`);
    const result = await response.json();
    if (result.code !== '0') {
        throw new Error(result.msg || 'Failed to load voices');
    }

    const data = result.data;
    data.voices.forEach((voice) => {
        const option = document.createElement('option');
        option.value = String(voice.id);
        option.textContent = `${voice.name}${voice.default ? ' (default)' : ''}`;
        voiceEl.appendChild(option);
    });

    voiceEl.value = String(data.defaultVoice);
    setStatus(`model=${data.model}\nsampleRate=${data.sampleRate}\nvoices=${data.voices.length}`);
}

async function synthesize() {
    setStatus('Synthesizing...');
    synthesizeBtn.disabled = true;
    const requestStart = performance.now();

    try {
        const response = await fetch(`${apiBase}/synthesize`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                text: textEl.value,
                voice: Number(voiceEl.value),
                speed: Number(speedEl.value)
            })
        });

        const contentType = response.headers.get('content-type') || '';
        if (!response.ok || contentType.includes('application/json')) {
            const result = await response.json();
            throw new Error(result.msg || 'Synthesis failed');
        }

        const blob = await response.blob();
        if (objectUrl) {
            URL.revokeObjectURL(objectUrl);
        }

        objectUrl = URL.createObjectURL(blob);
        audioEl.src = objectUrl;
        audioEl.play().catch(() => {});

        const audioDuration = await waitAudioMetadata(audioEl);
        const serverCost = response.headers.get('x-tts-cost-ms');
        const clientCost = Math.round(performance.now() - requestStart);

        setStatus(`Done\nmodel=${response.headers.get('x-tts-model')}\nvoice=${response.headers.get('x-tts-voice')}\nsampleRate=${response.headers.get('x-tts-sample-rate')}\naudioLength=${formatSeconds(audioDuration)}\nserverCost=${serverCost || 'n/a'}ms\nclientCost=${clientCost}ms\nbytes=${blob.size}`);
    } catch (error) {
        setStatus(`Failed: ${error.message}`);
    } finally {
        synthesizeBtn.disabled = false;
    }
}

function waitAudioMetadata(audio) {
    if (Number.isFinite(audio.duration) && audio.duration > 0) {
        return Promise.resolve(audio.duration);
    }
    return new Promise((resolve) => {
        const handleLoaded = () => {
            audio.removeEventListener('loadedmetadata', handleLoaded);
            resolve(Number.isFinite(audio.duration) ? audio.duration : 0);
        };
        audio.addEventListener('loadedmetadata', handleLoaded, { once: true });
    });
}

function formatSeconds(seconds) {
    if (!Number.isFinite(seconds) || seconds <= 0) {
        return '0.00s';
    }
    return `${seconds.toFixed(2)}s`;
}

synthesizeBtn.addEventListener('click', synthesize);
refreshBtn.addEventListener('click', () => {
    loadVoices().catch((error) => setStatus(`Failed: ${error.message}`));
});

loadVoices().catch((error) => setStatus(`Failed: ${error.message}`));
