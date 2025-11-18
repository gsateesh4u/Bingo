import { useCallback, useRef } from 'react';

type AudioContextLike = AudioContext;

export function useShuffleSound() {
  const audioContextRef = useRef<AudioContextLike | null>(null);
  const sourceRef = useRef<AudioBufferSourceNode | null>(null);

  const stopShuffle = useCallback(() => {
    if (sourceRef.current) {
      try {
        sourceRef.current.stop();
      } catch {
        // ignore
      }
      sourceRef.current.disconnect();
      sourceRef.current = null;
    }
  }, []);

  const playShuffle = useCallback(
    async (durationMs: number) => {
      if (typeof window === 'undefined') {
        return;
      }
      const ctor: typeof AudioContext | undefined =
          window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!ctor) {
        return;
      }
      const ctx = audioContextRef.current ?? new ctor();
      audioContextRef.current = ctx;
      if (ctx.state === 'suspended') {
        try {
          await ctx.resume();
        } catch {
          // ignore resume errors
        }
      }
      stopShuffle();
      const durationSeconds = Math.max(durationMs, 250) / 1000;
      const sampleRate = ctx.sampleRate;
      const length = Math.floor(sampleRate * durationSeconds);
      const buffer = ctx.createBuffer(1, length, sampleRate);
      const data = buffer.getChannelData(0);
      for (let i = 0; i < length; i += 1) {
        data[i] = (Math.random() * 2 - 1) * 0.25;
      }
      const source = ctx.createBufferSource();
      source.buffer = buffer;
      source.connect(ctx.destination);
      source.start();
      source.onended = () => {
        if (sourceRef.current === source) {
          sourceRef.current = null;
        }
      };
      sourceRef.current = source;
    },
    [stopShuffle]
  );

  return { playShuffle, stopShuffle };
}
