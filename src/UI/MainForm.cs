using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using BtTakeover.core;

namespace BtTakeover.UI;

public partial class MainForm : Form
{
    private readonly BluetoothHelper _bt = new();
    private readonly AudioHelper _audio = new();
    private string? _lastFoundDeviceName;
    private bool _isPlaying = false;
    private System.Threading.CancellationTokenSource? _playCts;

    public MainForm()
    {
        InitializeComponent();
    }

    private void Log(string message)
    {
        if (txtLog.InvokeRequired)
        {
            txtLog.Invoke(new Action(() => Log(message)));
            return;
        }
        txtLog.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
    }

    private async void btnScanPair_Click(object? sender, EventArgs e)
    {
        var raw = (txtDeviceId.Text ?? string.Empty).Trim();
        if (string.IsNullOrWhiteSpace(raw))
        {
            MessageBox.Show("Enter a Device ID/MAC or name snippet.", "Input required", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        btnScanPair.Enabled = false;
        Log("Scanning for Bluetooth devices (can take ~30s)...");

        try
        {
            var mac = ParseMac(raw);
            var result = await Task.Run(() => _bt.FindDevice(mac, raw));
            if (result == null)
            {
                Log("Device not found. Try bringing it closer or re-scan.");
                return;
            }

            _lastFoundDeviceName = result.DeviceName;
            Log($"Found device: {result.DeviceName} [{result.DeviceAddress}]; Paired={result.Authenticated}");

            if (!result.Authenticated)
            {
                Log("Attempting to pair...");
                var paired = await Task.Run(() => _bt.Pair(result));
                Log(paired ? "Paired successfully." : "Pairing failed or was declined.");
            }
            else
            {
                Log("Already paired.\n");
            }
        }
        catch (Exception ex)
        {
            Log($"Error during scan/pair: {ex.Message}");
        }
        finally
        {
            btnScanPair.Enabled = true;
        }
    }

    private static string? ParseMac(string raw)
    {
        // Accept MAC as 12 hex chars, with or without separators, or extract from Windows DeviceId.
        var cleaned = Regex.Replace(raw, "[^A-Fa-f0-9]", "");
        if (cleaned.Length >= 12)
        {
            var mac = cleaned.Substring(cleaned.Length - 12, 12);
            return mac.ToUpperInvariant();
        }
        return null;
    }

    private void btnBrowse_Click(object? sender, EventArgs e)
    {
        using var ofd = new OpenFileDialog
        {
            Filter = "WAV files (*.wav)|*.wav|All files (*.*)|*.*",
            Title = "Select WAV file"
        };
        if (ofd.ShowDialog(this) == DialogResult.OK)
        {
            txtWavPath.Text = ofd.FileName;
        }
    }

    private async void btnPlay_Click(object? sender, EventArgs e)
    {
        if (_isPlaying)
        {
            StopPlayback();
            return;
        }

        var wav = (txtWavPath.Text ?? string.Empty).Trim();
        if (!File.Exists(wav))
        {
            MessageBox.Show("Select a valid WAV file.", "File missing", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        var targetName = _lastFoundDeviceName;
        if (string.IsNullOrWhiteSpace(targetName))
        {
            // Fall back to text input as name snippet if MAC wasn't parsed
            var raw = (txtDeviceId.Text ?? string.Empty).Trim();
            var mac = ParseMac(raw);
            if (!string.IsNullOrWhiteSpace(mac))
            {
                targetName = null; // prefer MAC-based endpoint match if possible
            }
            else
            {
                targetName = raw; // name contains snippet
            }
        }

        Log("Locating audio endpoint for device...");
        try
        {
            var endpoint = _audio.FindRenderEndpoint(targetName);
            if (endpoint == null)
            {
                Log("Audio endpoint not found. Ensure the device is paired and its A2DP endpoint is available.");
                return;
            }

            Log($"Using endpoint: {endpoint.FriendlyName}");
            Log("Setting endpoint volume to 100%...");
            _audio.SetEndpointVolume100(endpoint);

            Log(chkLoop.Checked ? "Playing WAV on loop (VERY LOUD)..." : "Playing WAV (VERY LOUD)...");
            _isPlaying = true;
            _playCts = new System.Threading.CancellationTokenSource();
            SetUiPlaying(true);

            if (chkLoop.Checked)
            {
                await Task.Run(() => _audio.PlayWavToEndpointLoop(endpoint, wav, _playCts!.Token));
            }
            else
            {
                await Task.Run(() => _audio.PlayWavToEndpoint(endpoint, wav, _playCts!.Token));
            }

            Log("Playback finished.");
        }
        catch (Exception ex)
        {
            Log($"Playback error: {ex.Message}");
        }
        finally
        {
            _isPlaying = false;
            _playCts?.Dispose();
            _playCts = null;
            SetUiPlaying(false);
        }
    }

    private void StopPlayback()
    {
        try
        {
            Log("Stopping playback...");
            _playCts?.Cancel();
        }
        catch { /* ignore */ }
    }

    private void SetUiPlaying(bool playing)
    {
        if (InvokeRequired)
        {
            Invoke(new Action(() => SetUiPlaying(playing)));
            return;
        }
        btnPlay.Text = playing ? "Stop" : "Play (Max Volume)";
        btnScanPair.Enabled = !playing;
        btnBrowse.Enabled = !playing;
        txtDeviceId.Enabled = !playing;
        txtWavPath.Enabled = !playing;
        chkLoop.Enabled = !playing;
    }

    protected override void OnLoad(EventArgs e)
    {
        base.OnLoad(e);
        try
        {
            var loaded = AppConfig.LoadFromExeDirectory();
            if (loaded is { } tuple)
            {
                var (cfg, path) = tuple;
                if (!string.IsNullOrWhiteSpace(cfg.BluetoothId))
                {
                    txtDeviceId.Text = cfg.BluetoothId;
                }
                if (!string.IsNullOrWhiteSpace(cfg.AudioFile))
                {
                    txtWavPath.Text = cfg.AudioFile;
                }
                Log($"Loaded configuration from {System.IO.Path.GetFileName(path)}.");
            }
        }
        catch (Exception ex)
        {
            Log($"Config load error: {ex.Message}");
        }
    }
}
