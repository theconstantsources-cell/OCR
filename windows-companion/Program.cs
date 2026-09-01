using System.Runtime.InteropServices;
using System.Text;
using InTheHand.Net;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;
using Microsoft.Win32;

namespace ScanHidReceiver;

/// <summary>
/// Receives scanned/OCR'd text from the AI Scan Android app over a plain Bluetooth
/// RFCOMM (SPP) connection and types it out locally using Windows' own keystroke
/// injection API - wherever the cursor/focus currently is (Excel, Notepad, anything).
///
/// Runs as a background tray app rather than a console window: no terminal to keep
/// open, a tray icon shows connection status, and it registers itself to start
/// automatically at Windows login so nobody has to remember to launch it.
/// </summary>
internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        RegisterForAutoStart();

        Application.Run(new TrayApplicationContext());
    }

    /// <summary>
    /// Adds this program to the current user's Windows startup list, so it's already
    /// running (in the tray) the next time they log in - a one-time, silent action on
    /// first run rather than something the user has to set up themselves.
    /// </summary>
    private static void RegisterForAutoStart()
    {
        try
        {
            var exePath = Environment.ProcessPath;
            if (string.IsNullOrEmpty(exePath)) return;

            using var key = Registry.CurrentUser.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Run", writable: true);
            key?.SetValue("AIScanReceiver", $"\"{exePath}\"");
        }
        catch
        {
            // Auto-start is a convenience, not a requirement for this run to work - a
            // failure here (e.g. restricted registry permissions) shouldn't block startup.
        }
    }
}

/// <summary>
/// Owns the tray icon and the Bluetooth accept loop - there's no visible window, this
/// is what keeps the process alive and reflects status via the tray icon/balloon tips.
/// </summary>
internal sealed class TrayApplicationContext : ApplicationContext
{
    // Must match BluetoothSppManager.SPP_UUID on the Android side exactly.
    private static readonly Guid ServiceUuid = new("7d2f6c1a-9b3e-4a5d-8f2c-1e6a9d4b7c3f");

    private readonly NotifyIcon _trayIcon;
    private readonly CancellationTokenSource _cts = new();

    public TrayApplicationContext()
    {
        var menu = new ContextMenuStrip();
        menu.Items.Add("AI Scan Receiver", null, (_, _) => { }).Enabled = false;
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Exit", null, OnExit);

        _trayIcon = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "AI Scan Receiver: starting...",
            ContextMenuStrip = menu,
            Visible = true,
        };

        _ = RunListenerLoopAsync(_cts.Token);
    }

    private async Task RunListenerLoopAsync(CancellationToken token)
    {
        using var listener = new BluetoothListener(ServiceUuid)
        {
            ServiceName = "AI Scan Receiver",
        };
        listener.Start();
        UpdateStatus("Waiting for the phone to connect...", ToolTipIcon.None, showBalloon: false);

        while (!token.IsCancellationRequested)
        {
            try
            {
                using var client = listener.AcceptBluetoothClient();
                UpdateStatus($"Connected: {client.RemoteMachineName}", ToolTipIcon.Info, showBalloon: true);

                await HandleClientAsync(client, token);

                UpdateStatus("Waiting for the phone to connect...", ToolTipIcon.None, showBalloon: false);
            }
            catch (Exception ex)
            {
                UpdateStatus($"Connection error: {ex.Message}", ToolTipIcon.Warning, showBalloon: true);
            }
        }
    }

    private async Task HandleClientAsync(BluetoothClient client, CancellationToken token)
    {
        using var stream = client.GetStream();

        while (client.Connected && !token.IsCancellationRequested)
        {
            var lengthBytes = await ReadExactAsync(stream, 4);
            if (lengthBytes == null) return; // client closed the connection

            if (BitConverter.IsLittleEndian) Array.Reverse(lengthBytes);
            var length = BitConverter.ToInt32(lengthBytes, 0);
            if (length <= 0 || length > 1_000_000)
            {
                UpdateStatus($"Ignoring message with implausible length {length}", ToolTipIcon.Warning, showBalloon: true);
                return;
            }

            var payload = await ReadExactAsync(stream, length);
            if (payload == null) return;

            var text = Encoding.UTF8.GetString(payload);
            UpdateStatus($"Typing {text.Length} characters...", ToolTipIcon.Info, showBalloon: true);
            Keystrokes.Type(text);
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

    private void UpdateStatus(string message, ToolTipIcon icon, bool showBalloon)
    {
        _trayIcon.Text = Truncate($"AI Scan Receiver: {message}", 127); // NotifyIcon.Text has a 127-char limit
        if (showBalloon)
        {
            _trayIcon.BalloonTipTitle = "AI Scan Receiver";
            _trayIcon.BalloonTipText = message;
            _trayIcon.BalloonTipIcon = icon;
            _trayIcon.ShowBalloonTip(3000);
        }
    }

    private static string Truncate(string value, int maxLength) => value.Length <= maxLength ? value : value[..maxLength];

    private void OnExit(object? sender, EventArgs e)
    {
        _cts.Cancel();
        _trayIcon.Visible = false;
        Application.Exit();
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
