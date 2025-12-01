using System;
using System.Linq;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace BtTakeover.core;

public class AudioHelper
{
    public MMDevice? FindRenderEndpoint(string? nameContains)
    {
        var enumerator = new MMDeviceEnumerator();
        var devices = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);

        if (!string.IsNullOrWhiteSpace(nameContains))
        {
            var needle = nameContains.Trim().ToLowerInvariant();
            var match = devices.FirstOrDefault(d => d.FriendlyName.ToLowerInvariant().Contains(needle));
            if (match != null) return match;
        }

        // Fallback: prefer a Bluetooth-looking endpoint
        return devices.FirstOrDefault(d => d.FriendlyName.Contains("Bluetooth", StringComparison.OrdinalIgnoreCase))
               ?? devices.FirstOrDefault();
    }

    public void SetEndpointVolume100(MMDevice endpoint)
    {
        endpoint.AudioEndpointVolume.MasterVolumeLevelScalar = 1.0f; // 100%
    }

    public void PlayWavToEndpoint(MMDevice endpoint, string wavPath, System.Threading.CancellationToken token)
    {
        using var reader = new AudioFileReader(wavPath) { Volume = 1.0f };
        using var wasapiOut = new WasapiOut(endpoint, AudioClientShareMode.Shared, false, 50);
        wasapiOut.Init(reader);
        wasapiOut.Play();

        // Wait for playback to complete or cancellation
        while (wasapiOut.PlaybackState == PlaybackState.Playing)
        {
            if (token.IsCancellationRequested)
            {
                wasapiOut.Stop();
                break;
            }
            System.Threading.Thread.Sleep(100);
        }
    }

    public void PlayWavToEndpointLoop(MMDevice endpoint, string wavPath, System.Threading.CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            using var reader = new AudioFileReader(wavPath) { Volume = 1.0f };
            using var wasapiOut = new WasapiOut(endpoint, AudioClientShareMode.Shared, false, 50);
            wasapiOut.Init(reader);
            wasapiOut.Play();

            while (wasapiOut.PlaybackState == PlaybackState.Playing)
            {
                if (token.IsCancellationRequested)
                {
                    wasapiOut.Stop();
                    break;
                }
                System.Threading.Thread.Sleep(100);
            }
        }
    }
}
