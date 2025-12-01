using System;
using System.Linq;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;

namespace BtTakeover.core;

public class BluetoothHelper
{
    public BluetoothDeviceInfo? FindDevice(string? macOrNull, string nameFallback)
    {
        using var client = new BluetoothClient();
        // Discover paired & unpaired; allow caching remembered devices too.
        var devices = client.DiscoverDevices(255, authenticated: true, remembered: true, unknown: true);

        if (!string.IsNullOrWhiteSpace(macOrNull) && TryParseAddress(macOrNull!, out var addr))
        {
            var exact = devices.FirstOrDefault(d => d.DeviceAddress == addr);
            if (exact != null)
                return exact;
        }

        if (!string.IsNullOrWhiteSpace(nameFallback))
        {
            var lower = nameFallback.Trim().ToLowerInvariant();
            var byName = devices.FirstOrDefault(d => (d.DeviceName ?? string.Empty).ToLowerInvariant().Contains(lower));
            if (byName != null)
                return byName;
        }

        return null;
    }

    public bool Pair(BluetoothDeviceInfo device)
    {
        // Attempt SSP pairing without PIN; many modern headsets accept this.
        // If a PIN is required, this will return false. (We could extend to prompt for PIN.)
        return BluetoothSecurity.PairRequest(device.DeviceAddress, pin: null);
    }

    private static bool TryParseAddress(string mac, out BluetoothAddress address)
    {
        try
        {
            address = BluetoothAddress.Parse(mac);
            return true;
        }
        catch
        {
            try
            {
                // Accept raw 12-hex without separators
                if (mac.Length == 12)
                {
                    address = BluetoothAddress.Parse(string.Join(":", Enumerable.Range(0, 6).Select(i => mac.Substring(i * 2, 2))));
                    return true;
                }
            }
            catch { }
            address = BluetoothAddress.None;
            return false;
        }
    }
}

