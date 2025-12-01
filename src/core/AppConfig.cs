using System;
using System.IO;
using System.Text.Json;

namespace BtTakeover.core;

public class AppConfig
{
    public string? BluetoothId { get; set; }
    public string? AudioFile { get; set; }

    public static (AppConfig config, string path)? LoadFromExeDirectory()
    {
        var exeDir = AppContext.BaseDirectory;
        var path = Path.Combine(exeDir, "BtTakeover.config.json");
        if (!File.Exists(path))
            return null;

        var json = File.ReadAllText(path);
        var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
        var cfg = JsonSerializer.Deserialize<AppConfig>(json, options) ?? new AppConfig();

        // Resolve relative audio file paths against the exe directory
        if (!string.IsNullOrWhiteSpace(cfg.AudioFile) && !Path.IsPathRooted(cfg.AudioFile))
        {
            cfg.AudioFile = Path.GetFullPath(Path.Combine(exeDir, cfg.AudioFile));
        }

        return (cfg, path);
    }
}

