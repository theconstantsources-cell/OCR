using System.Runtime.InteropServices;
using InTheHand.Net;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;

namespace ScanHidReceiver;

/// <summary>
/// Receives scanned/OCR'd text from the AI ScanHid Android app over a plain Bluetooth
/// RFCOMM (SPP) connection and types it out locally using Windows' own keystroke
/// injection API - wherever the cursor/focus currently is (Excel, Notepad, anything).
///
/// This replaces the earlier "phone pretends to be a Bluetooth keyboard" approach,
/// which requires a fussier OS-level secure pairing handshake that some Android
/// Bluetooth chipsets don't reliably support with Windows. Plain Bluetooth pairing
/// (not the keyboard/HID kind) is a much more universally supported flow.
/// </summary>
internal static class Program
{
    // Must match BluetoothSppManager.SPP_UUID on the Android side exactly.
    private static readonly Guid ServiceUuid = new("7d2f6c1a-9b3e-4a5d-8f2c-1e6a9d4b7c3f");

    private static async Task Main()
    {
        Console.WriteLine("AI ScanHid receiver");
        Console.WriteLine("====================");
        Console.WriteLine("Waiting for the phone to connect over Bluetooth...");
        Console.WriteLine("(Pair the phone with this PC first via Windows Bluetooth settings, if you haven't already.)");
        Console.WriteLine();

        using var listener = new BluetoothListener(ServiceUuid)
        {
            ServiceName = "AI ScanHid Receiver",
        };
        listener.Start();

        while (true)
        {
            try
            {
                using var client = listener.AcceptBluetoothClient();
                Console.WriteLine($"[{DateTime.Now:T}] Connected: {client.RemoteMachineName}");

                await HandleClientAsync(client);

                Console.WriteLine($"[{DateTime.Now:T}] Disconnected. Waiting for the next connection...");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[{DateTime.Now:T}] Connection error: {ex.Message}");
            }
        }
    }

    private static async Task HandleClientAsync(BluetoothClient client)
    {
        using var stream = client.GetStream();

        while (client.Connected)
        {
            var lengthBytes = await ReadExactAsync(stream, 4);
            if (lengthBytes == null) return; // client closed the connection

            if (BitConverter.IsLittleEndian) Array.Reverse(lengthBytes);
            var length = BitConverter.ToInt32(lengthBytes, 0);
            if (length <= 0 || length > 1_000_000)
            {
                Console.WriteLine($"[{DateTime.Now:T}] Ignoring message with implausible length {length}");
                return;
            }

            var payload = await ReadExactAsync(stream, length);
            if (payload == null) return;

            var text = System.Text.Encoding.UTF8.GetString(payload);
            Console.WriteLine($"[{DateTime.Now:T}] Received {text.Length} characters - typing now...");
            Keystrokes.Type(text);
            Console.WriteLine($"[{DateTime.Now:T}] Done.");
        }
    }

    /// <summary>Reads exactly <paramref name="count"/> bytes, or returns null if the stream closed first.</summary>
    private static async Task<byte[]?> ReadExactAsync(Stream stream, int count)
    {
        var buffer = new byte[count];
        var offset = 0;
        while (offset < count)
        {
            var read = await stream.ReadAsync(buffer.AsMemory(offset, count - offset));
            if (read == 0) return null;
            offset += read;
        }
        return buffer;
    }
}

/// <summary>Types text into whatever currently has keyboard focus, via Win32 SendInput.</summary>
internal static class Keystrokes
{
    private const int InputKeyboard = 1;
    private const uint KeyEventFUnicode = 0x0004;
    private const uint KeyEventFKeyUp = 0x0002;
    private const ushort VkReturn = 0x0D;
    private const ushort VkTab = 0x09;

    /// Delay between keystrokes; too fast and some apps drop or reorder characters.
    private const int KeyDelayMs = 8;

    public static void Type(string text)
    {
        foreach (var c in text)
        {
            switch (c)
            {
                case '\n':
                    SendVirtualKey(VkReturn);
                    break;
                case '\r':
                    break; // paired with \n in CRLF input - already handled by the \n case
                case '\t':
                    SendVirtualKey(VkTab);
                    break;
                default:
                    SendUnicodeChar(c);
                    break;
            }
            Thread.Sleep(KeyDelayMs);
        }
    }

    private static void SendUnicodeChar(char c)
    {
        var down = new Input
        {
            type = InputKeyboard,
            u = new InputUnion { ki = new KeyboardInput { wVk = 0, wScan = c, dwFlags = KeyEventFUnicode } },
        };
        var up = new Input
        {
            type = InputKeyboard,
            u = new InputUnion { ki = new KeyboardInput { wVk = 0, wScan = c, dwFlags = KeyEventFUnicode | KeyEventFKeyUp } },
        };
        SendInput(2, new[] { down, up }, Marshal.SizeOf<Input>());
    }

    private static void SendVirtualKey(ushort vk)
    {
        var down = new Input
        {
            type = InputKeyboard,
            u = new InputUnion { ki = new KeyboardInput { wVk = vk, wScan = 0, dwFlags = 0 } },
        };
        var up = new Input
        {
            type = InputKeyboard,
            u = new InputUnion { ki = new KeyboardInput { wVk = vk, wScan = 0, dwFlags = KeyEventFKeyUp } },
        };
        SendInput(2, new[] { down, up }, Marshal.SizeOf<Input>());
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, Input[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public int type;
        public InputUnion u;
    }

    // SendInput validates that cbSize matches the *native* sizeof(INPUT) exactly and
    // silently fails the whole call otherwise. The native struct is a union sized to
    // its largest member (MOUSEINPUT), so MouseInput must be declared here even though
    // it's never populated - omitting it under-sizes the struct on 64-bit Windows and
    // every SendInput call would fail.
    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MouseInput mi;
        [FieldOffset(0)] public KeyboardInput ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}
